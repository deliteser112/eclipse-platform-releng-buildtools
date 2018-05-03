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

package google.registry.rdap;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistHostResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeDomainResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeHistoryEntry;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static google.registry.testing.TestDataHelper.loadFile;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.Period;
import google.registry.model.host.HostResource;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.rdap.RdapMetrics.EndpointType;
import google.registry.rdap.RdapMetrics.SearchType;
import google.registry.rdap.RdapMetrics.WildcardType;
import google.registry.rdap.RdapSearchResults.IncompletenessWarningType;
import google.registry.request.Action;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.ui.server.registrar.SessionUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.DateTime;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapDomainAction}. */
@RunWith(JUnit4.class)
public class RdapDomainActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));
  private final SessionUtils sessionUtils = mock(SessionUtils.class);
  private final User user = new User("rdap.user@example.com", "gmail.com", "12345");
  private final UserAuthInfo userAuthInfo = UserAuthInfo.create(user, false);
  private final UserAuthInfo adminUserAuthInfo = UserAuthInfo.create(user, true);
  private final RdapMetrics rdapMetrics = mock(RdapMetrics.class);

  private RdapDomainAction action;

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    // lol
    createTld("lol");
    Registrar registrarLol = persistResource(makeRegistrar(
        "evilregistrar", "Yes Virginia <script>", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrarLol));
    ContactResource registrantLol =
        makeAndPersistContactResource(
            "5372808-ERL",
            "Goblin Market",
            "lol@cat.lol",
            clock.nowUtc().minusYears(1),
            registrarLol);
    ContactResource adminContactLol =
        makeAndPersistContactResource(
            "5372808-IRL",
            "Santa Claus",
            "BOFH@cat.lol",
            clock.nowUtc().minusYears(2),
            registrarLol);
    ContactResource techContactLol =
        makeAndPersistContactResource(
            "5372808-TRL",
            "The Raven",
            "bog@cat.lol",
            clock.nowUtc().minusYears(3),
            registrarLol);
    HostResource host1 = makeAndPersistHostResource(
        "ns1.cat.lol", "1.2.3.4", null, clock.nowUtc().minusYears(1));
    HostResource host2 = makeAndPersistHostResource(
        "ns2.cat.lol", "bad:f00d:cafe:0:0:0:15:beef", clock.nowUtc().minusYears(2));
    DomainBase domainCatLol =
        persistResource(makeDomainResource("cat.lol",
            registrantLol, adminContactLol, techContactLol, host1, host2, registrarLol));

    // deleted domain in lol
    HostResource hostDodo2 = makeAndPersistHostResource(
        "ns2.dodo.lol", "bad:f00d:cafe:0:0:0:15:beef", clock.nowUtc().minusYears(2));
    DomainBase domainDeleted = persistResource(makeDomainResource("dodo.lol",
        makeAndPersistContactResource(
            "5372808-ERL",
            "Goblin Market",
            "lol@cat.lol",
            clock.nowUtc().minusYears(1),
            registrarLol),
        makeAndPersistContactResource(
            "5372808-IRL",
            "Santa Claus",
            "BOFH@cat.lol",
            clock.nowUtc().minusYears(2),
            registrarLol),
        makeAndPersistContactResource(
            "5372808-TRL",
            "The Raven",
            "bog@cat.lol",
            clock.nowUtc().minusYears(3),
            registrarLol),
        host1,
        hostDodo2,
        registrarLol).asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());

    // xn--q9jyb4c
    createTld("xn--q9jyb4c");
    Registrar registrarIdn =
        persistResource(makeRegistrar("idnregistrar", "IDN Registrar", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrarIdn));
    ContactResource registrantIdn =
        makeAndPersistContactResource(
            "5372808-ERL",
            "Goblin Market",
            "lol@cat.lol",
            clock.nowUtc().minusYears(1),
            registrarIdn);
    ContactResource adminContactIdn =
        makeAndPersistContactResource(
            "5372808-IRL",
            "Santa Claus",
            "BOFH@cat.lol",
            clock.nowUtc().minusYears(2),
            registrarIdn);
    ContactResource techContactIdn =
        makeAndPersistContactResource(
            "5372808-TRL",
            "The Raven",
            "bog@cat.lol",
            clock.nowUtc().minusYears(3),
            registrarIdn);
    DomainBase domainCatIdn = persistResource(makeDomainResource("cat.みんな",
        registrantIdn,
        adminContactIdn,
        techContactIdn,
        host1,
        host2,
        registrarIdn));

    // 1.tld
    createTld("1.tld");
    Registrar registrar1Tld = persistResource(
        makeRegistrar("1tldregistrar", "Multilevel Registrar", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrar1Tld));
    ContactResource registrant1Tld =
        makeAndPersistContactResource(
            "5372808-ERL",
            "Goblin Market",
            "lol@cat.lol",
            clock.nowUtc().minusYears(1),
            registrar1Tld);
    ContactResource adminContact1Tld =
        makeAndPersistContactResource(
            "5372808-IRL",
            "Santa Claus",
            "BOFH@cat.lol",
            clock.nowUtc().minusYears(2),
            registrar1Tld);
    ContactResource techContact1Tld =
        makeAndPersistContactResource(
            "5372808-TRL",
            "The Raven",
            "bog@cat.lol",
            clock.nowUtc().minusYears(3),
            registrar1Tld);
    DomainBase domainCat1Tld = persistResource(makeDomainResource("cat.1.tld",
        registrant1Tld,
        adminContact1Tld,
        techContact1Tld,
        host1,
        host2,
        registrar1Tld));

    // history entries
    persistResource(
        makeHistoryEntry(
            domainCatLol,
            HistoryEntry.Type.DOMAIN_CREATE,
            Period.create(1, Period.Unit.YEARS),
            "created",
            clock.nowUtc()));
    persistResource(
        makeHistoryEntry(
            domainDeleted,
            HistoryEntry.Type.DOMAIN_CREATE,
            Period.create(1, Period.Unit.YEARS),
            "created",
            clock.nowUtc().minusYears(1)));
    persistResource(
        makeHistoryEntry(
            domainCatIdn,
            HistoryEntry.Type.DOMAIN_CREATE,
            Period.create(1, Period.Unit.YEARS),
            "created",
            clock.nowUtc()));
    persistResource(
        makeHistoryEntry(
            domainCat1Tld,
            HistoryEntry.Type.DOMAIN_CREATE,
            Period.create(1, Period.Unit.YEARS),
            "created",
            clock.nowUtc()));
    persistResource(
        makeHistoryEntry(
            domainDeleted,
            HistoryEntry.Type.DOMAIN_DELETE,
            Period.create(1, Period.Unit.YEARS),
            "deleted",
            clock.nowUtc().minusMonths(6)));

    action = new RdapDomainAction();
    action.clock = clock;
    action.request = request;
    action.requestMethod = Action.Method.GET;
    action.fullServletPath = "https://example.com/rdap";
    action.response = response;
    action.registrarParam = Optional.empty();
    action.includeDeletedParam = Optional.empty();
    action.formatOutputParam = Optional.empty();
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapWhoisServer = null;
    action.sessionUtils = sessionUtils;
    action.authResult = AuthResult.create(AuthLevel.USER, userAuthInfo);
    action.rdapMetrics = rdapMetrics;
  }

  private void login(String clientId) {
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn(clientId);
  }

  private void loginAsAdmin() {
    when(sessionUtils.checkRegistrarConsoleLogin(request, adminUserAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("irrelevant");
    action.authResult = AuthResult.create(AuthLevel.USER, adminUserAuthInfo);
  }

  private Object generateActualJson(String domainName) {
    action.requestPath = RdapDomainAction.PATH + domainName;
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private Object generateExpectedJson(
      String name,
      String punycodeName,
      String handle,
      String expectedOutputFile) {
    return generateExpectedJson(
        name, punycodeName, handle, null, null, null, null, expectedOutputFile);
  }

  private Object generateExpectedJson(
      String name,
      String punycodeName,
      String handle,
      @Nullable List<String> contactRoids,
      @Nullable List<String> nameserverRoids,
      @Nullable List<String> nameserverNames,
      @Nullable String registrarName,
      String expectedOutputFile) {
    ImmutableMap.Builder<String, String> substitutionsBuilder = new ImmutableMap.Builder<>();
    substitutionsBuilder.put("NAME", name);
    substitutionsBuilder.put("PUNYCODENAME", (punycodeName == null) ? name : punycodeName);
    substitutionsBuilder.put("HANDLE", handle);
    substitutionsBuilder.put("TYPE", "domain name");
    substitutionsBuilder.put("NAMESERVER1ADDRESS", "1.2.3.4");
    substitutionsBuilder.put("NAMESERVER2ADDRESS", "bad:f00d:cafe::15:beef");
    if (registrarName != null) {
      substitutionsBuilder.put("REGISTRARNAME", registrarName);
    }
    if (contactRoids != null) {
      for (int i = 0; i < contactRoids.size(); i++) {
        substitutionsBuilder.put("CONTACT" + (i + 1) + "ROID", contactRoids.get(i));
      }
    }
    if (nameserverRoids != null) {
      for (int i = 0; i < nameserverRoids.size(); i++) {
        substitutionsBuilder.put("NAMESERVER" + (i + 1) + "ROID", nameserverRoids.get(i));
      }
    } else {
      substitutionsBuilder.put("NAMESERVER1ROID", "8-ROID");
      substitutionsBuilder.put("NAMESERVER2ROID", "A-ROID");
    }
    if (nameserverNames != null) {
      for (int i = 0; i < nameserverRoids.size(); i++) {
        substitutionsBuilder.put("NAMESERVER" + (i + 1) + "NAME", nameserverNames.get(i));
        substitutionsBuilder.put("NAMESERVER" + (i + 1) + "PUNYCODENAME", nameserverNames.get(i));
      }
    } else {
      substitutionsBuilder.put("NAMESERVER1NAME", "ns1.cat.lol");
      substitutionsBuilder.put("NAMESERVER1PUNYCODENAME", "ns1.cat.lol");
      substitutionsBuilder.put("NAMESERVER2NAME", "ns2.cat.lol");
      substitutionsBuilder.put("NAMESERVER2PUNYCODENAME", "ns2.cat.lol");
    }
    return JSONValue.parse(
        loadFile(this.getClass(), expectedOutputFile, substitutionsBuilder.build()));
  }

  private Object generateExpectedJsonWithTopLevelEntries(
      String name,
      String punycodeName,
      String handle,
      @Nullable List<String> contactRoids,
      @Nullable List<String> nameserverRoids,
      @Nullable String registrarName,
      String expectedOutputFile) {
    return generateExpectedJsonWithTopLevelEntries(
        name,
        punycodeName,
        handle,
        contactRoids,
        nameserverRoids,
        null,
        registrarName,
        expectedOutputFile);
  }
  private Object generateExpectedJsonWithTopLevelEntries(
      String name,
      String punycodeName,
      String handle,
      @Nullable List<String> contactRoids,
      @Nullable List<String> nameserverRoids,
      @Nullable List<String> nameserverNames,
      @Nullable String registrarName,
      String expectedOutputFile) {
    Object obj =
        generateExpectedJson(
            name,
            punycodeName,
            handle,
            contactRoids,
            nameserverRoids,
            nameserverNames,
            registrarName,
            expectedOutputFile);
    if (obj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) obj;
      ImmutableMap.Builder<String, Object> builder =
          RdapTestHelper.getBuilderExcluding(
              map, ImmutableSet.of("rdapConformance", "notices", "remarks"));
      builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
      RdapTestHelper.addDomainBoilerplateNotices(
          builder,
          false,
          RdapTestHelper.createNotices(
              "https://example.com/rdap/",
              (contactRoids == null)
                  ? RdapTestHelper.ContactNoticeType.DOMAIN
                  : RdapTestHelper.ContactNoticeType.NONE,
              map.get("notices")));
      obj = new JSONObject(builder.build());
    }
    return obj;
  }

  private void assertJsonEqual(Object json1, Object json2) {
    if (json1 instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) json1;
      assertThat(map).isEqualTo(json2);
    } else {
      assertThat(json1).isEqualTo(json2);
    }
  }

  private void assertProperResponseForCatLol(String queryString, String expectedOutputFile) {
    assertJsonEqual(
        generateActualJson(queryString),
        generateExpectedJsonWithTopLevelEntries(
            "cat.lol",
            null,
            "C-LOL",
            expectedOutputFile.equals("rdap_domain.json")
                ? ImmutableList.of("4-ROID", "6-ROID", "2-ROID")
                : null,
            ImmutableList.of("8-ROID", "A-ROID"),
            "Yes Virginia <script>",
            expectedOutputFile));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testInvalidDomain_returns400() throws Exception {
    assertJsonEqual(
        generateActualJson("invalid/domain/name"),
        generateExpectedJson(
            "invalid/domain/name is not a valid domain name: Domain names can only contain a-z,"
                + " 0-9, '.' and '-'",
            null,
            "1",
            "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testUnknownDomain_returns400() throws Exception {
    assertJsonEqual(
        generateActualJson("missingdomain.com"),
        generateExpectedJson(
            "missingdomain.com is not a valid domain name: Domain name is under tld com which"
                + " doesn't exist",
            null,
            "1",
            "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testValidDomain_works() throws Exception {
    login("evilregistrar");
    assertProperResponseForCatLol("cat.lol", "rdap_domain.json");
  }

  @Test
  public void testValidDomain_works_sameRegistrarRequested() throws Exception {
    action.registrarParam = Optional.of("evilregistrar");
    login("evilregistrar");
    assertProperResponseForCatLol("cat.lol", "rdap_domain.json");
  }

  @Test
  public void testValidDomain_notFound_differentRegistrarRequested() throws Exception {
    action.registrarParam = Optional.of("idnregistrar");
    generateActualJson("cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testValidDomain_asAdministrator_works() throws Exception {
    loginAsAdmin();
    assertProperResponseForCatLol("cat.lol", "rdap_domain.json");
  }

  @Test
  public void testValidDomain_notLoggedIn_noContacts() throws Exception {
    assertProperResponseForCatLol("cat.lol", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testValidDomain_loggedInAsOtherRegistrar_noContacts() throws Exception {
    login("idnregistrar");
    assertProperResponseForCatLol("cat.lol", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testUpperCase_ignored() throws Exception {
    assertProperResponseForCatLol("CaT.lOl", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testTrailingDot_ignored() throws Exception {
    assertProperResponseForCatLol("cat.lol.", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testQueryParameter_ignored() throws Exception {
    assertProperResponseForCatLol("cat.lol?key=value", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testIdnDomain_works() throws Exception {
    login("idnregistrar");
    assertJsonEqual(
        generateActualJson("cat.みんな"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.みんな",
            "cat.xn--q9jyb4c",
            "1D-Q9JYB4C",
            ImmutableList.of("19-ROID", "1B-ROID", "17-ROID"),
            ImmutableList.of("8-ROID", "A-ROID"),
            "IDN Registrar",
            "rdap_domain_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testIdnDomainWithPercentEncoding_works() throws Exception {
    login("idnregistrar");
    assertJsonEqual(
        generateActualJson("cat.%E3%81%BF%E3%82%93%E3%81%AA"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.みんな",
            "cat.xn--q9jyb4c",
            "1D-Q9JYB4C",
            ImmutableList.of("19-ROID", "1B-ROID", "17-ROID"),
            ImmutableList.of("8-ROID", "A-ROID"),
            "IDN Registrar",
            "rdap_domain_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testPunycodeDomain_works() throws Exception {
    login("idnregistrar");
    assertJsonEqual(
        generateActualJson("cat.xn--q9jyb4c"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.みんな",
            "cat.xn--q9jyb4c",
            "1D-Q9JYB4C",
            ImmutableList.of("19-ROID", "1B-ROID", "17-ROID"),
            ImmutableList.of("8-ROID", "A-ROID"),
            "IDN Registrar",
            "rdap_domain_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testMultilevelDomain_works() throws Exception {
    login("1tldregistrar");
    assertJsonEqual(
        generateActualJson("cat.1.tld"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.1.tld",
            null,
            "25-1_TLD",
            ImmutableList.of("21-ROID", "23-ROID", "1F-ROID"),
            ImmutableList.of("8-ROID", "A-ROID"),
            "Multilevel Registrar",
            "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  // todo (b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testDomainInTestTld_notFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    generateActualJson("cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_notFound() throws Exception {
    assertJsonEqual(
        generateActualJson("dodo.lol"),
        generateExpectedJson("dodo.lol not found", null, "1", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_notFound_includeDeletedSetFalse() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    generateActualJson("dodo.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_notFound_notLoggedIn() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    generateActualJson("dodo.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_notFound_loggedInAsDifferentRegistrar() throws Exception {
    login("1tldregistrar");
    action.includeDeletedParam = Optional.of(true);
    generateActualJson("dodo.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_works_loggedInAsCorrectRegistrar() throws Exception {
    login("evilregistrar");
    action.includeDeletedParam = Optional.of(true);
    assertJsonEqual(
        generateActualJson("dodo.lol"),
        generateExpectedJsonWithTopLevelEntries(
            "dodo.lol",
            null,
            "15-LOL",
            ImmutableList.of("11-ROID", "13-ROID", "F-ROID"),
            ImmutableList.of("8-ROID", "D-ROID"),
            ImmutableList.of("ns1.cat.lol", "ns2.dodo.lol"),
            "Yes Virginia <script>",
            "rdap_domain_deleted.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDeletedDomain_works_loggedInAsAdmin() throws Exception {
    loginAsAdmin();
    action.includeDeletedParam = Optional.of(true);
    assertJsonEqual(
        generateActualJson("dodo.lol"),
        generateExpectedJsonWithTopLevelEntries(
            "dodo.lol",
            null,
            "15-LOL",
            ImmutableList.of("11-ROID", "13-ROID", "F-ROID"),
            ImmutableList.of("8-ROID", "D-ROID"),
            ImmutableList.of("ns1.cat.lol", "ns2.dodo.lol"),
            "Yes Virginia <script>",
            "rdap_domain_deleted.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testMetrics() throws Exception {
    generateActualJson("cat.lol");
    verify(rdapMetrics)
        .updateMetrics(
            RdapMetrics.RdapMetricInformation.builder()
                .setEndpointType(EndpointType.DOMAIN)
                .setSearchType(SearchType.NONE)
                .setWildcardType(WildcardType.INVALID)
                .setPrefixLength(0)
                .setIncludeDeleted(false)
                .setRegistrarSpecified(false)
                .setRole(RdapAuthorization.Role.PUBLIC)
                .setRequestMethod(Action.Method.GET)
                .setStatusCode(200)
                .setIncompletenessWarningType(IncompletenessWarningType.COMPLETE)
                .build());
  }
}
