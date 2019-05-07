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
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistDeletedContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeDomainBase;
import static google.registry.testing.FullFieldsTestEntityHelper.makeHostResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.model.contact.ContactResource;
import google.registry.model.host.HostResource;
import google.registry.model.registrar.Registrar;
import google.registry.rdap.RdapMetrics.EndpointType;
import google.registry.rdap.RdapMetrics.SearchType;
import google.registry.rdap.RdapMetrics.WildcardType;
import google.registry.rdap.RdapSearchResults.IncompletenessWarningType;
import google.registry.request.Action;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapEntityAction}. */
@RunWith(JUnit4.class)
public class RdapEntityActionTest extends RdapActionBaseTestCase<RdapEntityAction> {

  public RdapEntityActionTest() {
    super(RdapEntityAction.class);
  }

  private Registrar registrarLol;
  private ContactResource registrant;
  private ContactResource adminContact;
  private ContactResource techContact;
  private ContactResource disconnectedContact;
  private ContactResource deletedContact;

  @Before
  public void setUp() {
    // lol
    createTld("lol");
    registrarLol = persistResource(makeRegistrar(
        "evilregistrar", "Yes Virginia <script>", Registrar.State.ACTIVE, 101L));
    persistSimpleResources(makeRegistrarContacts(registrarLol));
    registrant = makeAndPersistContactResource(
        "8372808-REG",
        "(◕‿◕)",
        "lol@cat.みんな",
        ImmutableList.of("1 Smiley Row", "Suite みんな"),
        clock.nowUtc(),
        registrarLol);
    adminContact = makeAndPersistContactResource(
        "8372808-ADM",
        "(◕‿◕)",
        "lol@cat.みんな",
        ImmutableList.of("1 Smiley Row", "Suite みんな"),
        clock.nowUtc(),
        registrarLol);
    techContact = makeAndPersistContactResource(
        "8372808-TEC",
        "(◕‿◕)",
        "lol@cat.みんな",
        ImmutableList.of("1 Smiley Row", "Suite みんな"),
        clock.nowUtc(),
        registrarLol);
    HostResource host1 =
        persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    HostResource host2 =
        persistResource(makeHostResource("ns2.cat.lol", "bad:f00d:cafe:0:0:0:15:beef"));
    persistResource(makeDomainBase("cat.lol",
        registrant,
        adminContact,
        techContact,
        host1,
        host2,
        registrarLol));
    // xn--q9jyb4c
    createTld("xn--q9jyb4c");
    Registrar registrarIdn = persistResource(
        makeRegistrar("idnregistrar", "IDN Registrar", Registrar.State.ACTIVE, 102L));
    persistSimpleResources(makeRegistrarContacts(registrarIdn));
    // 1.tld
    createTld("1.tld");
    Registrar registrar1tld = persistResource(
        makeRegistrar("1tldregistrar", "Multilevel Registrar", Registrar.State.ACTIVE, 103L));
    persistSimpleResources(makeRegistrarContacts(registrar1tld));
    // deleted registrar
    Registrar registrarDeleted = persistResource(
        makeRegistrar("deletedregistrar", "Yes Virginia <script>", Registrar.State.PENDING, 104L));
    persistSimpleResources(makeRegistrarContacts(registrarDeleted));
    // other contacts
    disconnectedContact =
        makeAndPersistContactResource(
            "8372808-DIS",
            "(◕‿◕)",
            "lol@cat.みんな",
            ImmutableList.of("1 Smiley Row", "Suite みんな"),
            clock.nowUtc(),
            registrarLol);
    deletedContact =
        makeAndPersistDeletedContactResource(
            "8372808-DEL",
            clock.nowUtc().minusYears(1),
            registrarLol,
            clock.nowUtc().minusMonths(6));
  }

  private Object generateExpectedJson(
      String handle,
      String fullName,
      String status,
      @Nullable String address,
      String expectedOutputFile) {
    return loadJsonFile(
        expectedOutputFile,
        "NAME", handle,
        "FULLNAME", fullName,
        "ADDRESS", (address == null) ? "\"1 Smiley Row\", \"Suite みんな\"" : address,
        "EMAIL", "lol@cat.みんな",
        "TYPE", "entity",
        "STATUS", status);
  }

  private Object generateExpectedJsonWithTopLevelEntries(
      String handle,
      String expectedOutputFile) {
    return generateExpectedJsonWithTopLevelEntries(
        handle, "(◕‿◕)", "active", null, expectedOutputFile);
  }

  private Object generateExpectedJsonWithTopLevelEntries(
      String handle,
      String fullName,
      String status,
      String address,
      String expectedOutputFile) {
    Object obj = generateExpectedJson(handle, fullName, status, address, expectedOutputFile);
    if (obj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) obj;
      ImmutableMap.Builder<String, Object> builder =
          RdapTestHelper.getBuilderExcluding(map, ImmutableSet.of("notices"));
      RdapTestHelper.addNonDomainBoilerplateNotices(
          builder, RdapTestHelper.createNotices("https://example.tld/rdap/", map.get("notices")));
      obj = new JSONObject(builder.build());
    }
    return obj;
  }

  private void runSuccessfulTest(String queryString, String fileName) {
    runSuccessfulTest(queryString, "(◕‿◕)", "active", null, fileName);
  }

  private void runSuccessfulTest(String queryString, String fullName, String fileName) {
    runSuccessfulTest(queryString, fullName, "active", null, fileName);
  }

  private void runSuccessfulTest(
      String queryString,
      String fullName,
      String rdapStatus,
      String address,
      String fileName) {
    assertThat(generateActualJson(queryString))
        .isEqualTo(
            generateExpectedJsonWithTopLevelEntries(
                queryString, fullName, rdapStatus, address, fileName));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private void runNotFoundTest(String queryString) {
    assertThat(generateActualJson(queryString))
        .isEqualTo(generateExpectedJsonError(queryString + " not found", 404));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testInvalidEntity_returns400() {
    assertThat(generateActualJson("invalid/entity/handle")).isEqualTo(
        generateExpectedJsonError(
            "invalid/entity/handle is not a valid entity handle",
            400));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testUnknownEntity_notFound() {
    runNotFoundTest("_MISSING-ENTITY_");
  }

  @Test
  public void testValidRegistrantContact_works() {
    login("evilregistrar");
    runSuccessfulTest(registrant.getRepoId(), "rdap_associated_contact.json");
  }

  @Test
  public void testValidRegistrantContact_found_sameRegistrarRequested() {
    login("evilregistrar");
    action.registrarParam = Optional.of("evilregistrar");
    runSuccessfulTest(registrant.getRepoId(), "rdap_associated_contact.json");
  }

  @Test
  public void testValidRegistrantContact_notFound_differentRegistrarRequested() {
    login("evilregistrar");
    action.registrarParam = Optional.of("idnregistrar");
    runNotFoundTest(registrant.getRepoId());
  }

  @Test
  public void testValidRegistrantContact_found_asAdministrator() {
    loginAsAdmin();
    runSuccessfulTest(registrant.getRepoId(), "rdap_associated_contact.json");
  }

  @Test
  public void testValidRegistrantContact_found_notLoggedIn() {
    runSuccessfulTest(
        registrant.getRepoId(),
        "(◕‿◕)",
        "active",
        null,
        "rdap_associated_contact_no_personal_data.json");
  }

  @Test
  public void testValidRegistrantContact_found_loggedInAsOtherRegistrar() {
    login("otherregistrar");
    runSuccessfulTest(
        registrant.getRepoId(),
        "(◕‿◕)",
        "active",
        null,
        "rdap_associated_contact_no_personal_data.json");
  }

  @Test
  public void testValidAdminContact_works() {
    login("evilregistrar");
    runSuccessfulTest(adminContact.getRepoId(), "rdap_associated_contact.json");
  }

  @Test
  public void testValidTechContact_works() {
    login("evilregistrar");
    runSuccessfulTest(techContact.getRepoId(), "rdap_associated_contact.json");
  }

  @Test
  public void testValidDisconnectedContact_works() {
    login("evilregistrar");
    runSuccessfulTest(disconnectedContact.getRepoId(), "rdap_contact.json");
  }

  @Test
  public void testDeletedContact_notFound() {
    runNotFoundTest(deletedContact.getRepoId());
  }

  @Test
  public void testDeletedContact_notFound_includeDeletedSetFalse() {
    action.includeDeletedParam = Optional.of(false);
    runNotFoundTest(deletedContact.getRepoId());
  }

  @Test
  public void testDeletedContact_notFound_notLoggedIn() {
    action.includeDeletedParam = Optional.of(true);
    runNotFoundTest(deletedContact.getRepoId());
  }

  @Test
  public void testDeletedContact_notFound_loggedInAsDifferentRegistrar() {
    login("idnregistrar");
    action.includeDeletedParam = Optional.of(true);
    runNotFoundTest(deletedContact.getRepoId());
  }

  @Test
  public void testDeletedContact_found_loggedInAsCorrectRegistrar() {
    login("evilregistrar");
    action.includeDeletedParam = Optional.of(true);
    runSuccessfulTest(
        deletedContact.getRepoId(),
        "",
        "inactive",
        "",
        "rdap_contact_deleted.json");
  }

  @Test
  public void testDeletedContact_found_loggedInAsAdmin() {
    loginAsAdmin();
    action.includeDeletedParam = Optional.of(true);
    runSuccessfulTest(
        deletedContact.getRepoId(),
        "",
        "inactive",
        "",
        "rdap_contact_deleted.json");
  }

  @Test
  public void testRegistrar_found() {
    runSuccessfulTest("101", "Yes Virginia <script>", "rdap_registrar.json");
  }

  @Test
  public void testRegistrar102_works() {
    runSuccessfulTest("102", "IDN Registrar", "rdap_registrar.json");
  }

  @Test
  public void testRegistrar102_found_requestingSameRegistrar() {
    action.registrarParam = Optional.of("idnregistrar");
    runSuccessfulTest("102", "IDN Registrar", "rdap_registrar.json");
  }

  @Test
  public void testRegistrar102_notFound_requestingOtherRegistrar() {
    action.registrarParam = Optional.of("1tldregistrar");
    runNotFoundTest("102");
  }

  @Test
  public void testRegistrar103_works() {
    runSuccessfulTest("103", "Multilevel Registrar", "rdap_registrar.json");
  }

  @Test
  public void testRegistrar104_notFound() {
    runNotFoundTest("104");
  }

  @Test
  public void testRegistrar104_notFound_deletedFlagWhenNotLoggedIn() {
    action.includeDeletedParam = Optional.of(true);
    runNotFoundTest("104");
  }

  @Test
  public void testRegistrar104_found_deletedFlagWhenLoggedIn() {
    login("deletedregistrar");
    action.includeDeletedParam = Optional.of(true);
    runSuccessfulTest(
        "104", "Yes Virginia <script>", "inactive", null, "rdap_registrar.json");
  }

  @Test
  public void testRegistrar104_notFound_deletedFlagWhenLoggedInAsOther() {
    login("1tldregistrar");
    action.includeDeletedParam = Optional.of(true);
    runNotFoundTest("104");
  }

  @Test
  public void testRegistrar104_found_deletedFlagWhenLoggedInAsAdmin() {
    loginAsAdmin();
    action.includeDeletedParam = Optional.of(true);
    runSuccessfulTest(
        "104", "Yes Virginia <script>", "inactive", null, "rdap_registrar.json");
  }

  @Test
  public void testRegistrar105_doesNotExist() {
    runNotFoundTest("105");
  }

  @Test
  public void testQueryParameter_ignored() {
    login("evilregistrar");
    assertThat(generateActualJson(techContact.getRepoId() + "?key=value")).isEqualTo(
        generateExpectedJsonWithTopLevelEntries(
            techContact.getRepoId(), "rdap_associated_contact.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testMetrics() {
    generateActualJson(registrant.getRepoId());
    verify(rdapMetrics)
        .updateMetrics(
            RdapMetrics.RdapMetricInformation.builder()
                .setEndpointType(EndpointType.ENTITY)
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
