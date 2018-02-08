// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.proxy;

import static com.google.common.base.Suppliers.memoizeWithExpiration;
import static google.registry.proxy.ProxyConfig.getProxyConfig;
import static google.registry.util.ResourceUtils.readResourceBytes;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.cloudkms.v1.CloudKMS;
import com.google.api.services.cloudkms.v1.model.DecryptRequest;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import google.registry.proxy.EppProtocolModule.EppProtocol;
import google.registry.proxy.HealthCheckProtocolModule.HealthCheckProtocol;
import google.registry.proxy.Protocol.FrontendProtocol;
import google.registry.proxy.ProxyConfig.Environment;
import google.registry.proxy.WhoisProtocolModule.WhoisProtocol;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.SystemClock;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * A module that provides the port-to-protocol map and other configs that are used to bootstrap the
 * server.
 */
@Module
public class ProxyModule {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Parameter(names = "--whois", description = "Port for WHOIS")
  private Integer whoisPort;

  @Parameter(names = "--epp", description = "Port for EPP")
  private Integer eppPort;

  @Parameter(names = "--health_check", description = "Port for health check protocol")
  private Integer healthCheckPort;

  @Parameter(names = "--env", description = "Environment to run the proxy in")
  private Environment env = Environment.LOCAL;

  @Parameter(names = "--log", description = "Whether to log activities for debugging")
  boolean log;

  /**
   * Enable FINE level logging.
   *
   * <p>Set the loggers log level to {@code FINE}, and also add a console handler that actually
   * output {@code FINE} level messages to stdout. The handler filters out all non FINE level log
   * record to avoid double logging. Log records at level higher than FINE (e. g. INFO) will be
   * handled at parent loggers. Note that {@code FINE} level corresponds to {@code DEBUG} level in
   * Netty.
   */
  private static void enableDebugLevelLogging() {
    ImmutableList<Logger> parentLoggers =
        ImmutableList.of(
            Logger.getLogger("io.netty.handler.logging.LoggingHandler"),
            // Parent of all FormattingLoggers, so that we do not have to configure each
            // FormattingLogger individually.
            Logger.getLogger("google.registry.proxy"));
    for (Logger logger : parentLoggers) {
      logger.setLevel(Level.FINE);
      Handler handler = new ConsoleHandler();
      handler.setFilter(record -> Objects.equals(record.getLevel(), Level.FINE));
      handler.setLevel(Level.FINE);
      logger.addHandler(handler);
    }
  }

  /**
   * Parses command line arguments. Show usage if wrong arguments are given.
   *
   * @param args list of {@code String} arguments
   * @return this {@code ProxyModule} object
   */
  ProxyModule parse(String[] args) {
    JCommander jCommander = new JCommander(this);
    jCommander.setProgramName("proxy_server");
    try {
      jCommander.parse(args);
    } catch (ParameterException e) {
      jCommander.usage();
      throw e;
    }
    if (log) {
      logger.info("DEBUG LOGGING: ENABLED");
      enableDebugLevelLogging();
    }
    return this;
  }

  @Provides
  @WhoisProtocol
  int provideWhoisPort(ProxyConfig config) {
    return Optional.fromNullable(whoisPort).or(config.whois.port);
  }

  @Provides
  @EppProtocol
  int provideEppPort(ProxyConfig config) {
    return Optional.fromNullable(eppPort).or(config.epp.port);
  }

  @Provides
  @HealthCheckProtocol
  int provideHealthCheckPort(ProxyConfig config) {
    return Optional.fromNullable(healthCheckPort).or(config.healthCheck.port);
  }

  @Provides
  ImmutableMap<Integer, FrontendProtocol> providePortToProtocolMap(
      Set<FrontendProtocol> protocolSet) {
    return Maps.uniqueIndex(protocolSet, Protocol::port);
  }

  @Provides
  Environment provideEnvironment() {
    return env;
  }

  /** Shared logging handler, set to default DEBUG(FINE) level. */
  @Singleton
  @Provides
  static LoggingHandler provideLoggingHandler() {
    return new LoggingHandler();
  }

  @Singleton
  @Provides
  static GoogleCredential provideCredential(ProxyConfig config) {
    try {
      GoogleCredential credential = GoogleCredential.getApplicationDefault();
      if (credential.createScopedRequired()) {
        credential = credential.createScoped(config.gcpScopes);
      }
      return credential;
    } catch (IOException e) {
      logger.severe(e, "Unable to obtain OAuth2 credential.");
      throw new RuntimeException(e);
    }
  }

  /** Access token supplier that auto refreshes 1 minute before expiry. */
  @Singleton
  @Provides
  @Named("accessToken")
  static Supplier<String> provideAccessTokenSupplier(
      GoogleCredential credential, ProxyConfig config) {
    return memoizeWithExpiration(
        () -> {
          try {
            credential.refreshToken();
          } catch (IOException e) {
            logger.severe(e, "Cannot refresh access token.");
            throw new RuntimeException(e);
          }
          return credential.getAccessToken();
        },
        config.accessTokenValidPeriodSeconds - config.accessTokenRefreshBeforeExpirySeconds,
        SECONDS);
  }

  @Singleton
  @Provides
  static CloudKMS provideCloudKms(GoogleCredential credential, ProxyConfig config) {
    return new CloudKMS.Builder(
            Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(), credential)
        .setApplicationName(config.projectId)
        .build();
  }

  @Singleton
  @Provides
  @Named("encryptedPemBytes")
  static byte[] provideEncryptedPemBytes(ProxyConfig config) {
    try {
      return readResourceBytes(ProxyModule.class, "resources/" + config.sslPemFilename).read();
    } catch (IOException e) {
      logger.severefmt(e, "Error reading encrypted PEM file: %s", config.sslPemFilename);
      throw new RuntimeException(e);
    }
  }

  @Singleton
  @Provides
  static PemBytes providePemBytes(
      CloudKMS cloudKms, @Named("encryptedPemBytes") byte[] encryptedPemBytes, ProxyConfig config) {
    String cryptoKeyUrl =
        String.format(
            "projects/%s/locations/%s/keyRings/%s/cryptoKeys/%s",
            config.projectId, config.kms.location, config.kms.keyRing, config.kms.cryptoKey);
    try {
      DecryptRequest decryptRequest = new DecryptRequest().encodeCiphertext(encryptedPemBytes);
      return PemBytes.create(
          cloudKms
              .projects()
              .locations()
              .keyRings()
              .cryptoKeys()
              .decrypt(cryptoKeyUrl, decryptRequest)
              .execute()
              .decodePlaintext());
    } catch (IOException e) {
      logger.severefmt(e, "PEM file decryption failed using CryptoKey: %s", cryptoKeyUrl);
      throw new RuntimeException(e);
    }
  }

  @Provides
  static SslProvider provideSslProvider() {
    // Prefer OpenSSL.
    return OpenSsl.isAvailable() ? SslProvider.OPENSSL : SslProvider.JDK;
  }

  @Provides
  @Singleton
  static Clock provideClock() {
    return new SystemClock();
  }

  @Provides
  static ExecutorService provideExecutorService() {
    return Executors.newWorkStealingPool();
  }

  @Provides
  static ScheduledExecutorService provideScheduledExecutorService() {
    return Executors.newSingleThreadScheduledExecutor();
  }

  @Singleton
  @Provides
  ProxyConfig provideProxyConfig(Environment env) {
    return getProxyConfig(env);
  }

  /**
   * A wrapper class for decrypted bytes of the PEM file.
   *
   * <p>Note that this should not be an @AutoValue class because we need a clone of the bytes to be
   * returned, otherwise the wrapper class becomes mutable.
   */
  // TODO: remove this class once FOSS build can use @BindsInstance to bind a byte[]
  // (https://github.com/bazelbuild/bazel/issues/4138)
  static class PemBytes {

    private final byte[] bytes;

    static final PemBytes create(byte[] bytes) {
      return new PemBytes(bytes);
    }

    private PemBytes(byte[] bytes) {
      this.bytes = bytes;
    }

    byte[] getBytes() {
      return bytes.clone();
    }
  }

  /** Root level component that exposes the port-to-protocol map. */
  @Singleton
  @Component(
    modules = {
      ProxyModule.class,
      CertificateModule.class,
      HttpsRelayProtocolModule.class,
      WhoisProtocolModule.class,
      EppProtocolModule.class,
      HealthCheckProtocolModule.class
    }
  )
  interface ProxyComponent {
    ImmutableMap<Integer, FrontendProtocol> portToProtocolMap();
  }
}
