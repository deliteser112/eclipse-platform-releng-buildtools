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
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static org.mockito.Mockito.mock;
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
    DomainBase domainDeleted = persistResource(makeDomainResource("dodo.lol",
        registrantLol,
        adminContactLol,
        techContactLol,
        host1,
        host2,
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
            clock.nowUtc()));
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

    action = new RdapDomainAction();
    action.clock = clock;
    action.request = request;
    action.response = response;
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapLinkBase = "https://example.com/rdap/";
    action.rdapWhoisServer = null;
    action.sessionUtils = sessionUtils;
    action.authResult = AuthResult.create(AuthLevel.USER, userAuthInfo);
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("evilregistrar");
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
    return generateExpectedJson(name, punycodeName, handle, null, expectedOutputFile);
  }

  private Object generateExpectedJson(
      String name,
      String punycodeName,
      String handle,
      @Nullable List<String> contactRoids,
      String expectedOutputFile) {
    ImmutableMap.Builder<String, String> substitutionsBuilder = new ImmutableMap.Builder<>();
    substitutionsBuilder.put("NAME", name);
    substitutionsBuilder.put("PUNYCODENAME", (punycodeName == null) ? name : punycodeName);
    substitutionsBuilder.put("HANDLE", handle);
    substitutionsBuilder.put("TYPE", "domain name");
    substitutionsBuilder.put("NAMESERVER1ROID", "8-ROID");
    substitutionsBuilder.put("NAMESERVER1NAME", "ns1.cat.lol");
    substitutionsBuilder.put("NAMESERVER1PUNYCODENAME", "ns1.cat.lol");
    substitutionsBuilder.put("NAMESERVER1ADDRESS", "1.2.3.4");
    substitutionsBuilder.put("NAMESERVER2ROID", "A-ROID");
    substitutionsBuilder.put("NAMESERVER2NAME", "ns2.cat.lol");
    substitutionsBuilder.put("NAMESERVER2PUNYCODENAME", "ns2.cat.lol");
    substitutionsBuilder.put("NAMESERVER2ADDRESS", "bad:f00d:cafe::15:beef");
    if (contactRoids != null) {
      for (int i = 0; i < contactRoids.size(); i++) {
        substitutionsBuilder.put("CONTACT" + (i + 1) + "ROID", contactRoids.get(i));
      }
    }
    return JSONValue.parse(
        loadFileWithSubstitutions(
            this.getClass(), expectedOutputFile, substitutionsBuilder.build()));
  }

  private Object generateExpectedJsonWithTopLevelEntries(
      String name,
      String punycodeName,
      String handle,
      @Nullable List<String> contactRoids,
      String expectedOutputFile) {
    Object obj = generateExpectedJson(
        name, punycodeName, handle, contactRoids, expectedOutputFile);
    if (obj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) obj;
      ImmutableMap.Builder<String, Object> builder =
          RdapTestHelper.getBuilderExcluding(
              map, ImmutableSet.of("rdapConformance", "notices", "remarks"));
      builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
      RdapTestHelper.addNotices(
          builder,
          "https://example.com/rdap/",
          (contactRoids == null)
              ? RdapTestHelper.ContactNoticeType.DOMAIN
              : RdapTestHelper.ContactNoticeType.NONE,
          map.get("notices"));
      RdapTestHelper.addDomainBoilerplateRemarks(builder, false, map.get("remarks"));
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

  @Test
  public void testInvalidDomain_returns400() throws Exception {
    assertJsonEqual(
        generateActualJson("invalid/domain/name"),
        generateExpectedJson(
            "invalid/domain/name is not a valid domain name", null, "1", "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testUnknownDomain_returns404() throws Exception {
    assertJsonEqual(
        generateActualJson("missingdomain.com"),
        generateExpectedJson("missingdomain.com not found", null, "1", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_returns404() throws Exception {
    assertJsonEqual(
        generateActualJson("dodo.lol"),
        generateExpectedJson("dodo.lol not found", null, "1", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testValidDomain_works() throws Exception {
    assertJsonEqual(
        generateActualJson("cat.lol"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.lol",
            null,
            "C-LOL",
            ImmutableList.of("4-ROID", "6-ROID", "2-ROID"),
            "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testValidDomain_asAdministrator_works() throws Exception {
    UserAuthInfo adminUserAuthInfo = UserAuthInfo.create(user, true);
    action.authResult = AuthResult.create(AuthLevel.USER, adminUserAuthInfo);
    when(sessionUtils.checkRegistrarConsoleLogin(request, adminUserAuthInfo)).thenReturn(false);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("noregistrar");
    assertJsonEqual(
        generateActualJson("cat.lol"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.lol",
            null,
            "C-LOL",
            ImmutableList.of("4-ROID", "6-ROID", "2-ROID"),
            "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testValidDomain_notLoggedIn_noContacts() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(false);
    assertJsonEqual(
        generateActualJson("cat.lol"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.lol",
            null,
            "C-LOL",
            null,
            "rdap_domain_no_contacts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testValidDomain_loggedInAsOtherRegistrar_noContacts() throws Exception {
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("otherregistrar");
    assertJsonEqual(
        generateActualJson("cat.lol"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.lol",
            null,
            "C-LOL",
            null,
            "rdap_domain_no_contacts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testTrailingDot_ignored() throws Exception {
    assertJsonEqual(
        generateActualJson("cat.lol."),
        generateExpectedJsonWithTopLevelEntries(
            "cat.lol",
            null,
            "C-LOL",
            ImmutableList.of("4-ROID", "6-ROID", "2-ROID"),
            "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testQueryParameter_ignored() throws Exception {
    assertJsonEqual(
        generateActualJson("cat.lol?key=value"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.lol",
            null,
            "C-LOL",
            ImmutableList.of("4-ROID", "6-ROID", "2-ROID"),
            "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testIdnDomain_works() throws Exception {
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("idnregistrar");
    assertJsonEqual(
        generateActualJson("cat.みんな"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.みんな",
            "cat.xn--q9jyb4c",
            "15-Q9JYB4C",
            ImmutableList.of("11-ROID", "13-ROID", "F-ROID"),
            "rdap_domain_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testIdnDomainWithPercentEncoding_works() throws Exception {
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("idnregistrar");
    assertJsonEqual(
        generateActualJson("cat.%E3%81%BF%E3%82%93%E3%81%AA"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.みんな",
            "cat.xn--q9jyb4c",
            "15-Q9JYB4C",
            ImmutableList.of("11-ROID", "13-ROID", "F-ROID"),
            "rdap_domain_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testPunycodeDomain_works() throws Exception {
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("idnregistrar");
    assertJsonEqual(
        generateActualJson("cat.xn--q9jyb4c"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.みんな",
            "cat.xn--q9jyb4c",
            "15-Q9JYB4C",
            ImmutableList.of("11-ROID", "13-ROID", "F-ROID"),
            "rdap_domain_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testMultilevelDomain_works() throws Exception {
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("1tldregistrar");
    assertJsonEqual(
        generateActualJson("cat.1.tld"),
        generateExpectedJsonWithTopLevelEntries(
            "cat.1.tld",
            null,
            "1D-1_TLD",
            ImmutableList.of("19-ROID", "1B-ROID", "17-ROID"),
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
}
