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
import static google.registry.testing.FullFieldsTestEntityHelper.makeDomainBase;
import static google.registry.testing.FullFieldsTestEntityHelper.makeHistoryEntry;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static google.registry.testing.TestDataHelper.loadFile;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.Period;
import google.registry.model.host.HostResource;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.rdap.RdapMetrics.EndpointType;
import google.registry.rdap.RdapMetrics.SearchType;
import google.registry.rdap.RdapMetrics.WildcardType;
import google.registry.rdap.RdapSearchResults.IncompletenessWarningType;
import google.registry.request.Action;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapDomainAction}. */
@RunWith(JUnit4.class)
public class RdapDomainActionTest extends RdapActionBaseTestCase<RdapDomainAction> {

  public RdapDomainActionTest() {
    super(RdapDomainAction.class, RdapDomainAction.PATH);
  }

  @Before
  public void setUp() {
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
        persistResource(makeDomainBase("cat.lol",
            registrantLol, adminContactLol, techContactLol, host1, host2, registrarLol));

    // deleted domain in lol
    HostResource hostDodo2 = makeAndPersistHostResource(
        "ns2.dodo.lol", "bad:f00d:cafe:0:0:0:15:beef", clock.nowUtc().minusYears(2));
    DomainBase domainDeleted = persistResource(makeDomainBase("dodo.lol",
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
    DomainBase domainCatIdn = persistResource(makeDomainBase("cat.みんな",
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
    DomainBase domainCat1Tld = persistResource(makeDomainBase("cat.1.tld",
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
      builder.put("rdapConformance", ImmutableList.of("icann_rdap_response_profile_0"));
      RdapTestHelper.addDomainBoilerplateNotices(
          builder,
          false,
          RdapTestHelper.createNotices(
              "https://example.tld/rdap/",
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
  public void testInvalidDomain_returns400() {
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
  public void testUnknownDomain_returns400() {
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
  public void testValidDomain_works() {
    login("evilregistrar");
    assertProperResponseForCatLol("cat.lol", "rdap_domain.json");
  }

  @Test
  public void testValidDomain_works_sameRegistrarRequested() {
    action.registrarParam = Optional.of("evilregistrar");
    login("evilregistrar");
    assertProperResponseForCatLol("cat.lol", "rdap_domain.json");
  }

  @Test
  public void testValidDomain_notFound_differentRegistrarRequested() {
    action.registrarParam = Optional.of("idnregistrar");
    generateActualJson("cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testValidDomain_asAdministrator_works() {
    loginAsAdmin();
    assertProperResponseForCatLol("cat.lol", "rdap_domain.json");
  }

  @Test
  public void testValidDomain_notLoggedIn_noContacts() {
    assertProperResponseForCatLol("cat.lol", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testValidDomain_loggedInAsOtherRegistrar_noContacts() {
    login("idnregistrar");
    assertProperResponseForCatLol("cat.lol", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testUpperCase_ignored() {
    assertProperResponseForCatLol("CaT.lOl", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testTrailingDot_ignored() {
    assertProperResponseForCatLol("cat.lol.", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testQueryParameter_ignored() {
    assertProperResponseForCatLol("cat.lol?key=value", "rdap_domain_no_contacts.json");
  }

  @Test
  public void testIdnDomain_works() {
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
  public void testIdnDomainWithPercentEncoding_works() {
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
  public void testPunycodeDomain_works() {
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
  public void testMultilevelDomain_works() {
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
  public void testDomainInTestTld_notFound() {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    generateActualJson("cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_notFound() {
    assertJsonEqual(
        generateActualJson("dodo.lol"),
        generateExpectedJson("dodo.lol not found", null, "1", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_notFound_includeDeletedSetFalse() {
    action.includeDeletedParam = Optional.of(true);
    generateActualJson("dodo.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_notFound_notLoggedIn() {
    action.includeDeletedParam = Optional.of(true);
    generateActualJson("dodo.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_notFound_loggedInAsDifferentRegistrar() {
    login("1tldregistrar");
    action.includeDeletedParam = Optional.of(true);
    generateActualJson("dodo.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_works_loggedInAsCorrectRegistrar() {
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
  public void testDeletedDomain_works_loggedInAsAdmin() {
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
  public void testMetrics() {
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
