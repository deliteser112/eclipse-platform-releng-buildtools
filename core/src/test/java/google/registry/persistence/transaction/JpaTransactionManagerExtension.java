// Copyright 2019 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.persistence.transaction;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.testing.DatabaseHelper.insertSimpleResources;
import static org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.common.io.Resources;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.Registrar.State;
import google.registry.model.registrar.RegistrarAddress;
import google.registry.model.registrar.RegistrarPoc;
import google.registry.persistence.HibernateSchemaExporter;
import google.registry.persistence.NomulusPostgreSql;
import google.registry.persistence.PersistenceModule;
import google.registry.persistence.PersistenceXmlUtility;
import google.registry.persistence.transaction.JpaTestExtensions.JpaIntegrationTestExtension;
import google.registry.persistence.transaction.JpaTestExtensions.JpaUnitTestExtension;
import google.registry.util.Clock;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.Entity;
import javax.persistence.EntityManagerFactory;
import org.hibernate.cfg.Environment;
import org.hibernate.jpa.boot.internal.ParsedPersistenceXmlDescriptor;
import org.hibernate.jpa.boot.spi.Bootstrap;
import org.joda.money.CurrencyUnit;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class of JUnit extensions to provision {@link JpaTransactionManagerImpl} backed by {@link
 * PostgreSQLContainer}.
 *
 * <p>This class is not for direct use. Use the specialized subclasses {@link
 * JpaIntegrationTestExtension} or {@link JpaUnitTestExtension} as befits the use case.
 *
 * <p>This extension also replaces the {@link JpaTransactionManagerImpl} provided by {@link
 * TransactionManagerFactory} with the {@link JpaTransactionManagerImpl} generated by the extension
 * itself, so that all SQL queries will be sent to the database instance created by {@link
 * PostgreSQLContainer} to achieve test purpose.
 */
public abstract class JpaTransactionManagerExtension
    implements BeforeEachCallback, AfterEachCallback {

  private static final String DB_CLEANUP_SQL_PATH =
      "google/registry/persistence/transaction/cleanup_database.sql";
  private static final String POSTGRES_DB_NAME = "postgres";
  // The type of JDBC connections started by the tests. This string value
  // is documented in PSQL's official user guide.
  private static final String CONNECTION_BACKEND_TYPE = "client backend";
  private static final int ACTIVE_CONNECTIONS_CAP = 5;

  private final Clock clock;
  private final Optional<String> initScriptPath;
  private final ImmutableList<Class<?>> extraEntityClasses;
  private final ImmutableMap<String, String> userProperties;

  private static final JdbcDatabaseContainer database = create();
  private static final HibernateSchemaExporter exporter =
      HibernateSchemaExporter.create(
          database.getJdbcUrl(), database.getUsername(), database.getPassword());
  // The EntityManagerFactory for the current schema in the test db. This instance may be
  // reused between test methods if the requested schema remains the same.
  private static EntityManagerFactory emf;
  // Hash of the ORM entity names in the current schema in the test db.
  private static int emfEntityHash;

  private JpaTransactionManager cachedTm;
  // Hash of the ORM entity names requested by this extension instance.
  private final int entityHash;

  // Whether to create nomulus tables in the test db. Right now, only the JpaTestExtensions set this
  // to false.
  private boolean includeNomulusSchema = true;

  // Whether to pre-polulate some registrars for ease of testing.
  private final boolean withCannedData;

  private TimeZone originalDefaultTimeZone;

  JpaTransactionManagerExtension(
      Clock clock,
      Optional<String> initScriptPath,
      boolean includeNomulusSchema,
      ImmutableList<Class<?>> extraEntityClasses,
      ImmutableMap<String, String> userProperties,
      boolean withCannedData) {
    this.clock = clock;
    this.initScriptPath = initScriptPath;
    this.includeNomulusSchema = includeNomulusSchema;
    this.extraEntityClasses = extraEntityClasses;
    this.userProperties = userProperties;
    this.entityHash = getOrmEntityHash(initScriptPath, extraEntityClasses);
    this.withCannedData = withCannedData;
  }

  private static JdbcDatabaseContainer create() {
    PostgreSQLContainer container =
        new PostgreSQLContainer(NomulusPostgreSql.getDockerTag())
            .withDatabaseName(POSTGRES_DB_NAME);
    container.start();
    return container;
  }

  private static int getOrmEntityHash(
      Optional<String> initScriptPath, ImmutableList<Class<?>> extraEntityClasses) {
    return Streams.concat(
            Stream.of(initScriptPath.orElse("")),
            extraEntityClasses.stream().map(Class::getCanonicalName))
        .sorted()
        .collect(Collectors.toList())
        .hashCode();
  }

  /**
   * Drops and recreates the 'public' schema and all tables, then creates a new {@link
   * EntityManagerFactory} and save it in {@link #emf}.
   */
  private void recreateSchema() throws Exception {
    if (emf != null) {
      emf.close();
      emf = null;
      emfEntityHash = 0;
      assertReasonableNumDbConnections();
    }
    executeSql(readSqlInClassPath(DB_CLEANUP_SQL_PATH));
    initScriptPath.ifPresent(path -> executeSql(readSqlInClassPath(path)));
    if (!includeNomulusSchema) {
      File tempSqlFile = File.createTempFile("tempSqlFile", ".sql");
      tempSqlFile.deleteOnExit();
      exporter.export(extraEntityClasses, tempSqlFile);
      executeSql(Files.readString(tempSqlFile.toPath()));
    }
    assertReasonableNumDbConnections();
    emf = createEntityManagerFactory(getJpaProperties());
    emfEntityHash = entityHash;
  }

  /**
   * Returns the full set of properties for setting up JPA {@link EntityManagerFactory} to the test
   * database. This allows creation of customized JPA by individual tests.
   *
   * <p>Test that create {@code EntityManagerFactory} instances are reponsible for tearing them
   * down.
   */
  public ImmutableMap<String, String> getJpaProperties() {
    Map<String, String> mergedProperties =
        Maps.newHashMap(PersistenceModule.provideDefaultDatabaseConfigs());
    if (!userProperties.isEmpty()) {
      mergedProperties.putAll(userProperties);
    }
    mergedProperties.put(Environment.URL, getJdbcUrl());
    mergedProperties.put(Environment.USER, database.getUsername());
    mergedProperties.put(Environment.PASS, database.getPassword());
    // Tell Postgresql JDBC driver to retry on errors caused by out-of-band schema change between
    // tests while the connection pool stays open (e.g., "cached plan must not change result type").
    // We don't set this property in production since it has performance impact, and production
    // schema is always compatible with the binary (enforced by our release process).
    mergedProperties.put("hibernate.hikari.dataSource.autosave", "conservative");

    // Forbid Hibernate push to stay consistent with flyway-based schema management.
    checkState(
        Objects.equals(mergedProperties.get(Environment.HBM2DDL_AUTO), "none"),
        "The HBM2DDL_AUTO property must be 'none'.");

    return ImmutableMap.copyOf(mergedProperties);
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    originalDefaultTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    if (entityHash == emfEntityHash) {
      checkState(emf != null, "Missing EntityManagerFactory.");
      resetTablesAndSequences();
    } else {
      recreateSchema();
    }
    JpaTransactionManagerImpl txnManager = new JpaTransactionManagerImpl(emf, clock);
    JpaTransactionManagerImpl readOnlyTxnManager = new JpaTransactionManagerImpl(emf, clock, true);
    cachedTm = TransactionManagerFactory.tm();
    TransactionManagerFactory.setJpaTm(Suppliers.ofInstance(txnManager));
    TransactionManagerFactory.setReplicaJpaTm(Suppliers.ofInstance(readOnlyTxnManager));
    // Reset SQL Sequence based id allocation so that ids are deterministic in tests.
    TransactionManagerFactory.tm()
        .transact(
            () ->
                TransactionManagerFactory.tm()
                    .getEntityManager()
                    .createNativeQuery(
                        "alter sequence if exists project_wide_unique_id_seq start 1 minvalue 1"
                            + " restart with 1")
                    .executeUpdate());
    if (withCannedData) {
      loadInitialData();
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    TransactionManagerFactory.setJpaTm(Suppliers.ofInstance(cachedTm));
    TransactionManagerFactory.setReplicaJpaTm(Suppliers.ofInstance(cachedTm));
    cachedTm = null;
    TimeZone.setDefault(originalDefaultTimeZone);
  }

  public JdbcDatabaseContainer getDatabase() {
    return database;
  }

  private void resetTablesAndSequences() {
    try (Connection conn = createConnection();
        Statement statement = conn.createStatement()) {
      ResultSet rs =
          statement.executeQuery(
              "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';");
      ImmutableList.Builder<String> tableNamesBuilder = new ImmutableList.Builder<>();
      while (rs.next()) {
        tableNamesBuilder.add('"' + rs.getString(1) + '"');
      }
      ImmutableList<String> tableNames = tableNamesBuilder.build();
      if (!tableNames.isEmpty()) {
        String sql =
            String.format("TRUNCATE %s RESTART IDENTITY CASCADE", Joiner.on(',').join(tableNames));
        executeSql(sql);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Asserts that the number of connections to the test database is reasonable, i.e. less than 5.
   * Ideally, it should be 0 if the connection is closed by the test as we don't use a connection
   * pool. However, Hibernate may still maintain some connection by it self. In addition, the
   * metadata table we use to detect active connection may not remove the closed connection
   * immediately. So, we decide to relax the condition to check if the number of active connection
   * is less than 5 to reduce flakiness.
   */
  private void assertReasonableNumDbConnections() {
    try (Connection conn = createConnection();
        Statement statement = conn.createStatement()) {
      // Note: Since we use the admin user (returned by container's getUserName() method)
      // in tests, we need to filter connections by database name and/or backend type to filter out
      // connections for management tasks.
      ResultSet rs =
          statement.executeQuery(
              String.format(
                  "SELECT COUNT(1) FROM pg_stat_activity WHERE usename = '%1s'"
                      + " and datname = '%2s' "
                      + " and backend_type = '%3s'",
                  database.getUsername(), POSTGRES_DB_NAME, CONNECTION_BACKEND_TYPE));
      rs.next();
      assertWithMessage("Too many active connections to database")
          .that(rs.getLong(1))
          .isLessThan(ACTIVE_CONNECTIONS_CAP);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String readSqlInClassPath(String sqlScriptPath) {
    try {
      return Resources.toString(Resources.getResource(sqlScriptPath), Charsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void executeSql(String sqlScript) {
    try (Connection conn = createConnection();
        Statement statement = conn.createStatement()) {
      statement.execute(sqlScript);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String getJdbcUrl() {
    // Disable Postgres driver use of java.util.logging to reduce noise at startup time
    return "jdbc:postgresql://"
        + database.getHost()
        + ":"
        + database.getMappedPort(POSTGRESQL_PORT)
        + "/"
        + POSTGRES_DB_NAME
        + "?loggerLevel=OFF";
  }

  private static Connection createConnection() {
    final Properties info = new Properties();
    info.put("user", database.getUsername());
    info.put("password", database.getPassword());
    final Driver jdbcDriverInstance = database.getJdbcDriverInstance();
    try {
      return jdbcDriverInstance.connect(getJdbcUrl(), info);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private static Registrar.Builder makeRegistrarCommon() {
    return new Registrar.Builder()
        .setType(Registrar.Type.REAL)
        .setState(State.ACTIVE)
        .setIcannReferralEmail("lol@sloth.test")
        .setUrl("http://my.fake.url")
        .setInternationalizedAddress(
            new RegistrarAddress.Builder()
                .setStreet(ImmutableList.of("123 Example Boulevard"))
                .setCity("Williamsburg")
                .setState("NY")
                .setZip("11211")
                .setCountryCode("US")
                .build())
        .setLocalizedAddress(
            new RegistrarAddress.Builder()
                .setStreet(ImmutableList.of("123 Example B\u0151ulevard"))
                .setCity("Williamsburg")
                .setState("NY")
                .setZip("11211")
                .setCountryCode("US")
                .build())
        .setPhoneNumber("+1.3334445555")
        .setPhonePasscode("12345")
        .setBillingAccountMap(ImmutableMap.of(CurrencyUnit.USD, "abc123"))
        .setContactsRequireSyncing(true);
  }

  /** Public factory for first Registrar to allow comparison against stored value in unit tests. */
  public static Registrar makeRegistrar1() {
    return makeRegistrarCommon()
        .setRegistrarId("NewRegistrar")
        .setRegistrarName("New Registrar")
        .setEmailAddress("new.registrar@example.com")
        .setIanaIdentifier(8L)
        .setPassword("foo-BAR2")
        .setPhoneNumber("+1.3334445555")
        .setPhonePasscode("12345")
        .setRegistryLockAllowed(false)
        .build();
  }

  /** Public factory for second Registrar to allow comparison against stored value in unit tests. */
  public static Registrar makeRegistrar2() {
    return makeRegistrarCommon()
        .setRegistrarId("TheRegistrar")
        .setRegistrarName("The Registrar")
        .setEmailAddress("the.registrar@example.com")
        .setIanaIdentifier(1L)
        .setPassword("password2")
        .setPhoneNumber("+1.2223334444")
        .setPhonePasscode("22222")
        .setRegistryLockAllowed(true)
        .build();
  }

  /**
   * Public factory for first RegistrarContact to allow comparison against stored value in unit
   * tests.
   */
  public static RegistrarPoc makeRegistrarContact1() {
    return new RegistrarPoc.Builder()
        .setRegistrar(makeRegistrar1())
        .setName("Jane Doe")
        .setVisibleInWhoisAsAdmin(true)
        .setVisibleInWhoisAsTech(false)
        .setEmailAddress("janedoe@theregistrar.com")
        .setPhoneNumber("+1.1234567890")
        .setTypes(ImmutableSet.of(RegistrarPoc.Type.ADMIN))
        .build();
  }

  /**
   * Public factory for second RegistrarContact to allow comparison against stored value in unit
   * tests.
   */
  public static RegistrarPoc makeRegistrarContact2() {
    return new RegistrarPoc.Builder()
        .setRegistrar(makeRegistrar2())
        .setName("John Doe")
        .setEmailAddress("johndoe@theregistrar.com")
        .setPhoneNumber("+1.1234567890")
        .setTypes(ImmutableSet.of(RegistrarPoc.Type.ADMIN))
        .setLoginEmailAddress("johndoe@theregistrar.com")
        .build();
  }

  public static RegistrarPoc makeRegistrarContact3() {
    return new RegistrarPoc.Builder()
        .setRegistrar(makeRegistrar2())
        .setName("Marla Singer")
        .setEmailAddress("Marla.Singer@crr.com")
        .setRegistryLockEmailAddress("Marla.Singer.RegistryLock@crr.com")
        .setPhoneNumber("+1.2128675309")
        .setTypes(ImmutableSet.of(RegistrarPoc.Type.TECH))
        .setLoginEmailAddress("Marla.Singer@crr.com")
        .setAllowedToSetRegistryLockPassword(true)
        .setRegistryLockPassword("hi")
        .build();
  }

  /** Create some fake registrars. */
  public static void loadInitialData() {
    insertSimpleResources(
        ImmutableList.of(
            makeRegistrar1(),
            makeRegistrarContact1(),
            makeRegistrar2(),
            makeRegistrarContact2(),
            makeRegistrarContact3()));
  }

  /** Constructs the {@link EntityManagerFactory} instance. */
  private EntityManagerFactory createEntityManagerFactory(ImmutableMap<String, String> properties) {
    ParsedPersistenceXmlDescriptor descriptor =
        PersistenceXmlUtility.getParsedPersistenceXmlDescriptor();

    // If we don't include the nomulus schema, remove all entity classes in the descriptor but keep
    // other settings like the converter classes.
    if (!includeNomulusSchema) {
      List<String> nonEntityClasses =
          descriptor.getManagedClassNames().stream()
              .filter(
                  classString -> {
                    try {
                      return !Class.forName(classString).isAnnotationPresent(Entity.class);
                    } catch (ClassNotFoundException e) {
                      throw new IllegalArgumentException(e);
                    }
                  })
              .collect(toImmutableList());
      descriptor.getManagedClassNames().clear();
      descriptor.getManagedClassNames().addAll(nonEntityClasses);
    }

    extraEntityClasses.stream().map(Class::getName).forEach(descriptor::addClasses);
    return Bootstrap.getEntityManagerFactoryBuilder(descriptor, properties).build();
  }
}
