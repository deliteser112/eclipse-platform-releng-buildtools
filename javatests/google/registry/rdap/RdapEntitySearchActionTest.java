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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistResources;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.model.ImmutableObject;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
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
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapEntitySearchAction}. */
@RunWith(JUnit4.class)
public class RdapEntitySearchActionTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();
  @Rule public final InjectRule inject = new InjectRule();

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01T00:00:00Z"));
  private final SessionUtils sessionUtils = mock(SessionUtils.class);
  private final User user = new User("rdap.user@example.com", "gmail.com", "12345");
  private final UserAuthInfo userAuthInfo = UserAuthInfo.create(user, false);
  private final UserAuthInfo adminUserAuthInfo = UserAuthInfo.create(user, true);
  private final RdapEntitySearchAction action = new RdapEntitySearchAction();

  private Registrar registrarDeleted;
  private Registrar registrarInactive;
  private Registrar registrarTest;

  private Object generateActualJsonWithFullName(String fn) {
    action.fnParam = Optional.of(fn);
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private Object generateActualJsonWithHandle(String handle) {
    action.handleParam = Optional.of(handle);
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);

    createTld("tld");

    // deleted
    registrarDeleted =
        persistResource(
            makeRegistrar("2-Registrar", "Yes Virginia <script>", Registrar.State.ACTIVE, 20L));
    persistSimpleResources(makeRegistrarContacts(registrarDeleted));

    // inactive
    registrarInactive =
        persistResource(makeRegistrar("2-RegistrarInact", "No Way", Registrar.State.PENDING, 21L));
    persistSimpleResources(makeRegistrarContacts(registrarInactive));

    // test
    registrarTest =
        persistResource(
            makeRegistrar("2-RegistrarTest", "Da Test Registrar", Registrar.State.ACTIVE)
                .asBuilder()
                .setType(Registrar.Type.TEST)
                .setIanaIdentifier(null)
                .build());
    persistSimpleResources(makeRegistrarContacts(registrarTest));

    makeAndPersistContactResource(
        "blinky",
        "Blinky (赤ベイ)",
        "blinky@b.tld",
        ImmutableList.of("123 Blinky St", "Blinkyland"),
        clock.nowUtc(),
        registrarTest);

    makeAndPersistContactResource(
        "blindly",
        "Blindly",
        "blindly@b.tld",
        ImmutableList.of("123 Blindly St", "Blindlyland"),
        clock.nowUtc(),
        registrarTest);

    makeAndPersistContactResource(
        "clyde",
        "Clyde (愚図た)",
        "clyde@c.tld",
        ImmutableList.of("123 Example Blvd <script>"),
        clock.nowUtc().minusYears(1),
        registrarDeleted,
        clock.nowUtc().minusMonths(6));

    action.clock = clock;
    action.request = request;
    action.requestPath = RdapEntitySearchAction.PATH;
    action.response = response;
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapResultSetMaxSize = 4;
    action.rdapLinkBase = "https://example.com/rdap/";
    action.rdapWhoisServer = null;
    action.fnParam = Optional.absent();
    action.handleParam = Optional.absent();
    action.registrarParam = Optional.absent();
    action.includeDeletedParam = Optional.absent();
    action.sessionUtils = sessionUtils;
    action.authResult = AuthResult.create(AuthLevel.USER, userAuthInfo);
  }

  private void login(String registrar) {
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn(registrar);
  }

  private void loginAsAdmin() {
    action.authResult = AuthResult.create(AuthLevel.USER, adminUserAuthInfo);
    when(sessionUtils.checkRegistrarConsoleLogin(request, adminUserAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("noregistrar");
  }

  private Object generateExpectedJson(String expectedOutputFile) {
    return JSONValue.parse(loadFileWithSubstitutions(
        this.getClass(),
        expectedOutputFile,
        ImmutableMap.of("TYPE", "entity")));
  }

  private Object generateExpectedJson(
      String handle,
      String expectedOutputFile) {
        return generateExpectedJson(handle, null, "active", null, null, expectedOutputFile);
  }

  private Object generateExpectedJson(
      String handle,
      @Nullable String fullName,
      String status,
      @Nullable String email,
      @Nullable String address,
      String expectedOutputFile) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    builder.put("NAME", handle);
    if (fullName != null) {
      builder.put("FULLNAME", fullName);
    }
    if (email != null) {
      builder.put("EMAIL", email);
    }
    if (address != null) {
      builder.put("ADDRESS", address);
    }
    builder.put("TYPE", "entity");
    builder.put("STATUS", status);
    String substitutedFile =
        loadFileWithSubstitutions(this.getClass(), expectedOutputFile, builder.build());
    Object jsonObject = JSONValue.parse(substitutedFile);
    checkNotNull(jsonObject, "substituted file is not valid JSON: %s", substitutedFile);
    return jsonObject;
  }

  private Object generateExpectedJsonForEntity(
      String handle,
      String fullName,
      String status,
      @Nullable String email,
      @Nullable String address,
      String expectedOutputFile) {
    Object obj = generateExpectedJson(handle, fullName, status, email, address, expectedOutputFile);
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("entitySearchResults", ImmutableList.of(obj));
    builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
    RdapTestHelper.addNotices(builder, "https://example.com/rdap/");
    RdapTestHelper.addNonDomainBoilerplateRemarks(builder);
    return builder.build();
  }

  private void createManyContactsAndRegistrars(
      int numContacts, int numRegistrars, Registrar contactRegistrar) {
    ImmutableList.Builder<ImmutableObject> resourcesBuilder = new ImmutableList.Builder<>();
    for (int i = 1; i <= numContacts; i++) {
      resourcesBuilder.add(makeContactResource(
          String.format("contact%d", i),
          String.format("Entity %d", i),
          String.format("contact%d@gmail.com", i),
          contactRegistrar));
    }
    persistResources(resourcesBuilder.build());
    for (int i = 1; i <= numRegistrars; i++) {
      resourcesBuilder.add(
          makeRegistrar(
              String.format("registrar%d", i),
              String.format("Entity %d", i + numContacts),
              Registrar.State.ACTIVE,
              300L + i));
    }
    persistResources(resourcesBuilder.build());
  }

  private void checkNumberOfEntitiesInResult(Object obj, int expected) {
    assertThat(obj).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) obj;

    @SuppressWarnings("unchecked")
    List<Object> domains = (List<Object>) map.get("entitySearchResults");

    assertThat(domains).hasSize(expected);
  }

  private void runSuccessfulNameTestWithBlinky(String queryString, String fileName) {
    runSuccessfulNameTest(
        queryString,
        "2-ROID",
        "Blinky (赤ベイ)",
        "active",
        "blinky@b.tld",
        "\"123 Blinky St\", \"Blinkyland\"",
        fileName);
  }

  private void runSuccessfulNameTest(
      String queryString,
      String handle,
      @Nullable String fullName,
      String fileName) {
    runSuccessfulNameTest(queryString, handle, fullName, "active", null, null, fileName);
  }

  private void runSuccessfulNameTest(
      String queryString,
      String handle,
      @Nullable String fullName,
      String status,
      @Nullable String email,
      @Nullable String address,
      String fileName) {
    assertThat(generateActualJsonWithFullName(queryString))
        .isEqualTo(
            generateExpectedJsonForEntity(handle, fullName, status, email, address, fileName));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private void runNotFoundNameTest(String fullName) {
    assertThat(generateActualJsonWithFullName(fullName))
        .isEqualTo(generateExpectedJson("No entities found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  private void runSuccessfulHandleTestWithBlinky(String queryString, String fileName) {
    runSuccessfulHandleTest(
        queryString,
        "2-ROID",
        "Blinky (赤ベイ)",
        "active",
        "blinky@b.tld",
        "\"123 Blinky St\", \"Blinkyland\"",
        fileName);
  }

  private void runSuccessfulHandleTest(
      String queryString,
      String handle,
      @Nullable String fullName,
      String fileName) {
    runSuccessfulHandleTest(queryString, handle, fullName, "active", null, null, fileName);
  }

  private void runSuccessfulHandleTest(
      String queryString,
      String handle,
      @Nullable String fullName,
      String status,
      @Nullable String email,
      @Nullable String address,
      String fileName) {
    assertThat(generateActualJsonWithHandle(queryString))
        .isEqualTo(
            generateExpectedJsonForEntity(handle, fullName, status, email, address, fileName));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private void runNotFoundHandleTest(String handle) {
    assertThat(generateActualJsonWithHandle(handle))
        .isEqualTo(generateExpectedJson("No entities found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testInvalidPath_rejected() throws Exception {
    action.requestPath = RdapEntitySearchAction.PATH + "/path";
    action.run();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testInvalidRequest_rejected() throws Exception {
    action.run();
    assertThat(JSONValue.parse(response.getPayload()))
        .isEqualTo(
            generateExpectedJson(
                "You must specify either fn=XXXX or handle=YYYY", "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testNameMatch_suffixRejected() throws Exception {
    assertThat(generateActualJsonWithFullName("exam*ple"))
        .isEqualTo(
            generateExpectedJson("Suffix not allowed after wildcard", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testHandleMatch_suffixRejected() throws Exception {
    assertThat(generateActualJsonWithHandle("exam*ple"))
        .isEqualTo(
            generateExpectedJson("Suffix not allowed after wildcard", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testMultipleWildcards_rejected() throws Exception {
    assertThat(generateActualJsonWithHandle("*.*"))
        .isEqualTo(generateExpectedJson("Only one wildcard allowed", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNoCharactersToMatch_rejected() throws Exception {
    assertThat(generateActualJsonWithHandle("*"))
        .isEqualTo(
            generateExpectedJson(
                "Initial search string must be at least 2 characters",
                "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testFewerThanTwoCharactersToMatch_rejected() throws Exception {
    assertThat(generateActualJsonWithHandle("a*"))
        .isEqualTo(
            generateExpectedJson(
                "Initial search string must be at least 2 characters",
                "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNameMatchContact_found() throws Exception {
    login("2-RegistrarTest");
    runSuccessfulNameTestWithBlinky("Blinky (赤ベイ)", "rdap_contact.json");
  }

  @Test
  public void testNameMatchContact_found_specifyingSameRegistrar() throws Exception {
    login("2-RegistrarTest");
    action.registrarParam = Optional.of("2-RegistrarTest");
    runSuccessfulNameTestWithBlinky("Blinky (赤ベイ)", "rdap_contact.json");
  }

  @Test
  public void testNameMatchContact_notFound_specifyingOtherRegistrar() throws Exception {
    login("2-RegistrarTest");
    action.registrarParam = Optional.of("2-RegistrarInact");
    runNotFoundNameTest("Blinky (赤ベイ)");
  }

  @Test
  public void testNameMatchContact_found_asAdministrator() throws Exception {
    loginAsAdmin();
    runSuccessfulNameTestWithBlinky("Blinky (赤ベイ)", "rdap_contact.json");
  }

  @Test
  public void testNameMatchContact_found_notLoggedIn() throws Exception {
    runSuccessfulNameTestWithBlinky(
        "Blinky (赤ベイ)", "rdap_contact_no_personal_data_with_remark.json");
  }

  @Test
  public void testNameMatchContact_found_loggedInAsOtherRegistrar() throws Exception {
    login("2-Registrar");
    runSuccessfulNameTestWithBlinky(
        "Blinky (赤ベイ)", "rdap_contact_no_personal_data_with_remark.json");
  }

  @Test
  public void testNameMatchContact_found_wildcard() throws Exception {
    login("2-RegistrarTest");
    runSuccessfulNameTestWithBlinky("Blinky*", "rdap_contact.json");
  }

  @Test
  public void testNameMatchContact_found_wildcardBoth() throws Exception {
    login("2-RegistrarTest");
    assertThat(generateActualJsonWithFullName("Blin*"))
        .isEqualTo(generateExpectedJson("rdap_multiple_contacts2.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatchContact_notFound_deleted() throws Exception {
    login("2-RegistrarTest");
    runNotFoundNameTest("Cl*");
  }

  @Test
  public void testNameMatchContact_notFound_deletedWhenLoggedInAsOtherRegistrar() throws Exception {
    login("2-RegistrarTest");
    action.includeDeletedParam = Optional.of(true);
    runNotFoundNameTest("Cl*");
  }

  @Test
  public void testNameMatchContact_found_deletedWhenLoggedInAsSameRegistrar() throws Exception {
    login("2-Registrar");
    action.includeDeletedParam = Optional.of(true);
    runSuccessfulNameTest(
        "Cl*",
        "6-ROID",
        "Clyde (愚図た)",
        "removed",
        "clyde@c.tld",
        "\"123 Example Blvd <script>\"",
        "rdap_contact_deleted.json");
  }

  @Test
  public void testNameMatchRegistrar_found() throws Exception {
    login("2-RegistrarTest");
    runSuccessfulNameTest(
        "Yes Virginia <script>", "20", "Yes Virginia <script>", "rdap_registrar.json");
  }

  @Test
  public void testNameMatchRegistrar_found_specifyingSameRegistrar() throws Exception {
    action.registrarParam = Optional.of("2-Registrar");
    runSuccessfulNameTest(
        "Yes Virginia <script>", "20", "Yes Virginia <script>", "rdap_registrar.json");
  }

  @Test
  public void testNameMatchRegistrar_notFound_specifyingDifferentRegistrar() throws Exception {
    action.registrarParam = Optional.of("2-RegistrarTest");
    runNotFoundNameTest("Yes Virginia <script>");
  }

  @Test
  public void testNameMatchContacts_nonTruncated() throws Exception {
    login("2-RegistrarTest");
    createManyContactsAndRegistrars(4, 0, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_nontruncated_contacts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatchContacts_truncated() throws Exception {
    login("2-RegistrarTest");
    createManyContactsAndRegistrars(5, 0, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_contacts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatchContacts_reallyTruncated() throws Exception {
    login("2-RegistrarTest");
    createManyContactsAndRegistrars(9, 0, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_contacts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatchRegistrars_nonTruncated() throws Exception {
    createManyContactsAndRegistrars(0, 4, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_nontruncated_registrars.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatchRegistrars_truncated() throws Exception {
    createManyContactsAndRegistrars(0, 5, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_registrars.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatchRegistrars_reallyTruncated() throws Exception {
    createManyContactsAndRegistrars(0, 9, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_registrars.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatchMix_truncated() throws Exception {
    login("2-RegistrarTest");
    createManyContactsAndRegistrars(3, 3, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_mixed_entities.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatchRegistrar_notFound_inactive() throws Exception {
    runNotFoundNameTest("No Way");
  }

  @Test
  public void testNameMatchRegistrar_notFound_inactiveAsDifferentRegistrar() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    login("2-Registrar");
    runNotFoundNameTest("No Way");
  }

  @Test
  public void testNameMatchRegistrar_found_inactiveAsSameRegistrar() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    login("2-RegistrarInact");
    runSuccessfulNameTest("No Way", "21", "No Way", "removed", null, null, "rdap_registrar.json");
  }

  @Test
  public void testNameMatchRegistrar_found_inactiveAsAdmin() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    loginAsAdmin();
    runSuccessfulNameTest("No Way", "21", "No Way", "removed", null, null, "rdap_registrar.json");
  }

  @Test
  public void testNameMatchRegistrar_notFound_test() throws Exception {
    runNotFoundNameTest("Da Test Registrar");
  }

  @Test
  public void testNameMatchRegistrar_notFound_testAsDifferentRegistrar() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    login("2-Registrar");
    runNotFoundNameTest("Da Test Registrar");
  }

  @Test
  public void testNameMatchRegistrar_found_testAsSameRegistrar() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    login("2-RegistrarTest");
    runSuccessfulNameTest(
        "Da Test Registrar", "(none)", "Da Test Registrar", "rdap_registrar_test.json");
  }

  @Test
  public void testNameMatchRegistrar_found_testAsAdmin() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    loginAsAdmin();
    runSuccessfulNameTest(
        "Da Test Registrar", "(none)", "Da Test Registrar", "rdap_registrar_test.json");
  }

  @Test
  public void testHandleMatchContact_found() throws Exception {
    login("2-RegistrarTest");
    runSuccessfulHandleTestWithBlinky("2-ROID", "rdap_contact.json");
  }

  @Test
  public void testHandleMatchContact_found_specifyingSameRegistrar() throws Exception {
    action.registrarParam = Optional.of("2-RegistrarTest");
    runSuccessfulHandleTestWithBlinky("2-ROID", "rdap_contact_no_personal_data_with_remark.json");
  }

  @Test
  public void testHandleMatchContact_notFound_specifyingDifferentRegistrar() throws Exception {
    action.registrarParam = Optional.of("2-Registrar");
    runNotFoundHandleTest("2-ROID");
  }

  @Test
  public void testHandleMatchRegistrar_found() throws Exception {
    runSuccessfulHandleTest("20", "20", "Yes Virginia <script>", "rdap_registrar.json");
  }

  @Test
  public void testHandleMatchRegistrar_found_specifyingSameRegistrar() throws Exception {
    action.registrarParam = Optional.of("2-Registrar");
    runSuccessfulHandleTest("20", "20", "Yes Virginia <script>", "rdap_registrar.json");
  }

  @Test
  public void testHandleMatchRegistrar_notFound_specifyingDifferentRegistrar() throws Exception {
    action.registrarParam = Optional.of("2-RegistrarTest");
    runNotFoundHandleTest("20");
  }

  @Test
  public void testHandleMatchContact_found_wildcardWithResultSetSizeOne() throws Exception {
    login("2-RegistrarTest");
    action.rdapResultSetMaxSize = 1;
    runSuccessfulHandleTestWithBlinky("2-R*", "rdap_contact.json");
  }

  @Test
  public void testHandleMatchContact_found_wildcard() throws Exception {
    login("2-RegistrarTest");
    runSuccessfulHandleTestWithBlinky("2-RO*", "rdap_contact.json");
  }

  @Test
  public void testHandleMatchContact_notFound_wildcard() throws Exception {
    runNotFoundHandleTest("20*");
  }

  @Test
  public void testHandleMatchRegistrar_notFound_wildcard() throws Exception {
    runNotFoundHandleTest("3test*");
  }

  @Test
  public void testHandleMatchMix_found_truncated() throws Exception {
    createManyContactsAndRegistrars(300, 0, registrarTest);
    Object obj = generateActualJsonWithHandle("10*");
    assertThat(response.getStatus()).isEqualTo(200);
    checkNumberOfEntitiesInResult(obj, 4);
  }

  @Test
  public void testHandleMatchRegistrar_notFound_inactive() throws Exception {
    runNotFoundHandleTest("21");
  }

  @Test
  public void testHandleMatchRegistrar_notFound_inactiveAsDifferentRegistrar() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    login("2-Registrar");
    runNotFoundHandleTest("21");
  }

  @Test
  public void testHandleMatchRegistrar_found_inactiveAsSameRegistrar() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    login("2-RegistrarInact");
    runSuccessfulHandleTest("21", "21", "No Way", "removed", null, null, "rdap_registrar.json");
  }

  @Test
  public void testHandleMatchRegistrar_found_inactiveAsAdmin() throws Exception {
    action.includeDeletedParam = Optional.of(true);
    loginAsAdmin();
    runSuccessfulHandleTest("21", "21", "No Way", "removed", null, null, "rdap_registrar.json");
  }
}
