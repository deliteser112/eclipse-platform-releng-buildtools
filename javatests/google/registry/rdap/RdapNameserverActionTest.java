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
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistHostResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.TestDataHelper.loadFile;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
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
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapNameserverAction}. */
@RunWith(JUnit4.class)
public class RdapNameserverActionTest {

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

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    // normal
    createTld("lol");
    makeAndPersistHostResource(
        "ns1.cat.lol", "1.2.3.4", clock.nowUtc().minusYears(1));
    // idn
    createTld("xn--q9jyb4c");
    makeAndPersistHostResource(
        "ns1.cat.xn--q9jyb4c", "bad:f00d:cafe:0:0:0:15:beef", clock.nowUtc().minusYears(1));
    // multilevel
    createTld("1.tld");
    makeAndPersistHostResource(
        "ns1.domain.1.tld", "5.6.7.8", clock.nowUtc().minusYears(1));
    // deleted
    persistResource(
        makeAndPersistHostResource("nsdeleted.cat.lol", "1.2.3.4", clock.nowUtc().minusYears(1))
            .asBuilder()
            .setDeletionTime(clock.nowUtc().minusMonths(1))
            .build());
    // other registrar
    persistResource(
        makeRegistrar("otherregistrar", "Yes Virginia <script>", Registrar.State.ACTIVE, 102L));
    // external
    makeAndPersistHostResource("ns1.domain.external", "9.10.11.12", clock.nowUtc().minusYears(1));
  }

  private RdapNameserverAction newRdapNameserverAction(
      String input, Optional<String> desiredRegistrar, Optional<Boolean> includeDeleted) {
    return newRdapNameserverAction(
        input, desiredRegistrar, includeDeleted, AuthResult.create(AuthLevel.USER, userAuthInfo));
  }

  private RdapNameserverAction newRdapNameserverAction(
      String input,
      Optional<String> desiredRegistrar,
      Optional<Boolean> includeDeleted,
      AuthResult authResult) {
    RdapNameserverAction action = new RdapNameserverAction();
    action.clock = clock;
    action.request = request;
    action.requestMethod = Action.Method.GET;
    action.fullServletPath = "https://example.tld/rdap";
    action.response = response;
    action.requestPath = RdapNameserverAction.PATH.concat(input);
    action.registrarParam = desiredRegistrar;
    action.includeDeletedParam = includeDeleted;
    action.formatOutputParam = Optional.empty();
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapWhoisServer = null;
    action.authResult = authResult;
    action.sessionUtils = sessionUtils;
    action.rdapMetrics = rdapMetrics;
    return action;
  }

  private Object generateActualJson(String name) {
    return generateActualJson(name, Optional.empty(), Optional.empty());
  }

  private Object generateActualJson(
      String name, Optional<String> desiredRegistrar, Optional<Boolean> includeDeleted) {
    newRdapNameserverAction(name, desiredRegistrar, includeDeleted).run();
    return JSONValue.parse(response.getPayload());
  }

  private Object generateExpectedJson(
      String name,
      @Nullable ImmutableMap<String, String> otherSubstitutions,
      String expectedOutputFile) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    builder.put("TYPE", "nameserver");
    builder.put("NAME", name);
    boolean punycodeSet = false;
    if (otherSubstitutions != null) {
      builder.putAll(otherSubstitutions);
      if (otherSubstitutions.containsKey("PUNYCODENAME")) {
        punycodeSet = true;
      }
    }
    if (!punycodeSet) {
      builder.put("PUNYCODENAME", name);
    }
    return JSONValue.parse(loadFile(
        this.getClass(),
        expectedOutputFile,
        builder.build()));
  }

  private Object generateExpectedJsonWithTopLevelEntries(
      String name,
      @Nullable ImmutableMap<String, String> otherSubstitutions,
      String expectedOutputFile) {
    Object obj = generateExpectedJson(name, otherSubstitutions, expectedOutputFile);
    if (obj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) obj;
      ImmutableMap.Builder<String, Object> builder =
          RdapTestHelper.getBuilderExcluding(
              map, ImmutableSet.of("rdapConformance", "notices", "remarks"));
      builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
      RdapTestHelper.addNonDomainBoilerplateNotices(
          builder,
          RdapTestHelper.createNotices(
              "https://example.tld/rdap/",
              RdapTestHelper.ContactNoticeType.NONE,
              map.get("notices")));
      obj = builder.build();
    }
    return obj;
  }

  @Test
  public void testInvalidNameserver_returns400() throws Exception {
    assertThat(generateActualJson("invalid/host/name"))
        .isEqualTo(
            generateExpectedJson(
                "invalid/host/name is not a valid nameserver: Invalid host name",
                null,
                "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testUnknownNameserver_returns404() throws Exception {
    assertThat(generateActualJson("ns1.missing.com")).isEqualTo(
        generateExpectedJson("ns1.missing.com not found", null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testValidNameserver_works() throws Exception {
    assertThat(generateActualJson("ns1.cat.lol"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.lol",
            ImmutableMap.of(
                "HANDLE", "2-ROID",
                "ADDRESSTYPE", "v4",
                "ADDRESS", "1.2.3.4",
                "STATUS", "active"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testTrailingDot_getsIgnored() throws Exception {
    assertThat(generateActualJson("ns1.cat.lol."))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.lol",
            ImmutableMap.of(
                "HANDLE", "2-ROID",
                "ADDRESSTYPE", "v4",
                "ADDRESS", "1.2.3.4",
                "STATUS", "active"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testUpperCase_getsCanonicalized() throws Exception {
    assertThat(generateActualJson("Ns1.CaT.lOl."))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.lol",
            ImmutableMap.of(
                "HANDLE", "2-ROID",
                "ADDRESSTYPE", "v4",
                "ADDRESS", "1.2.3.4",
                "STATUS", "active"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testQueryParameter_getsIgnored() throws Exception {
    assertThat(generateActualJson("ns1.cat.lol?key=value"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.lol",
            ImmutableMap.of(
                "HANDLE", "2-ROID",
                "ADDRESSTYPE", "v4",
                "ADDRESS", "1.2.3.4",
                "STATUS", "active"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testIdnNameserver_works() throws Exception {
    assertThat(generateActualJson("ns1.cat.みんな"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.みんな",
            ImmutableMap.of(
                "PUNYCODENAME", "ns1.cat.xn--q9jyb4c",
                "HANDLE", "5-ROID",
                "ADDRESSTYPE", "v6",
                "ADDRESS", "bad:f00d:cafe::15:beef",
                "STATUS", "active"),
            "rdap_host_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testPunycodeNameserver_works() throws Exception {
    assertThat(generateActualJson("ns1.cat.xn--q9jyb4c"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.みんな",
            ImmutableMap.of(
                "PUNYCODENAME", "ns1.cat.xn--q9jyb4c",
                "HANDLE", "5-ROID",
                "ADDRESSTYPE", "v6",
                "ADDRESS", "bad:f00d:cafe::15:beef",
                "STATUS", "active"),
            "rdap_host_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testMultilevelNameserver_works() throws Exception {
    assertThat(generateActualJson("ns1.domain.1.tld"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.domain.1.tld",
            ImmutableMap.of(
                "HANDLE", "8-ROID",
                "ADDRESSTYPE", "v4",
                "ADDRESS", "5.6.7.8",
                "STATUS", "active"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testExternalNameserver_works() throws Exception {
    assertThat(generateActualJson("ns1.domain.external"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.domain.external",
            ImmutableMap.of(
                "HANDLE", "C-ROID",
                "ADDRESSTYPE", "v4",
                "ADDRESS", "9.10.11.12",
                "STATUS", "active"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserver_found_sameRegistrarRequested() throws Exception {
    assertThat(
            generateActualJson(
                "ns1.cat.lol", Optional.of("TheRegistrar"), Optional.empty()))
        .isEqualTo(
            generateExpectedJsonWithTopLevelEntries(
                "ns1.cat.lol",
                ImmutableMap.of(
                    "HANDLE", "2-ROID",
                    "ADDRESSTYPE", "v4",
                    "ADDRESS", "1.2.3.4",
                    "STATUS", "active"),
                "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserver_notFound_differentRegistrarRequested() throws Exception {
    generateActualJson("ns1.cat.lol", Optional.of("otherregistrar"), Optional.of(false));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedNameserver_notFound_includeDeletedNotSpecified() throws Exception {
    generateActualJson("nsdeleted.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedNameserver_notFound_includeDeletedSetFalse() throws Exception {
    generateActualJson("nsdeleted.cat.lol", Optional.empty(), Optional.of(false));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedNameserver_notFound_notLoggedIn() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(false);
    generateActualJson("nsdeleted.cat.lol", Optional.empty(), Optional.of(true));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedNameserver_notFound_loggedInAsDifferentRegistrar() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("otherregistrar");
    generateActualJson("nsdeleted.cat.lol", Optional.empty(), Optional.of(true));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedNameserver_found_loggedInAsCorrectRegistrar() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("TheRegistrar");
    assertThat(
            generateActualJson("nsdeleted.cat.lol", Optional.empty(), Optional.of(true)))
        .isEqualTo(
            generateExpectedJsonWithTopLevelEntries(
                "nsdeleted.cat.lol",
                ImmutableMap.of(
                    "HANDLE", "A-ROID",
                    "ADDRESSTYPE", "v4",
                    "ADDRESS", "1.2.3.4",
                    "STATUS", "inactive"),
                "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDeletedNameserver_found_loggedInAsAdmin() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(request, adminUserAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("irrelevant");
    newRdapNameserverAction(
            "nsdeleted.cat.lol",
            Optional.empty(),
            Optional.of(true),
            AuthResult.create(AuthLevel.USER, adminUserAuthInfo))
        .run();
    assertThat(JSONValue.parse(response.getPayload()))
        .isEqualTo(
            generateExpectedJsonWithTopLevelEntries(
                "nsdeleted.cat.lol",
                ImmutableMap.of(
                    "HANDLE", "A-ROID",
                    "ADDRESSTYPE", "v4",
                    "ADDRESS", "1.2.3.4",
                    "STATUS", "inactive"),
                "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDeletedNameserver_found_sameRegistrarRequested() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("TheRegistrar");
    assertThat(
            generateActualJson("nsdeleted.cat.lol", Optional.of("TheRegistrar"), Optional.of(true)))
        .isEqualTo(
            generateExpectedJsonWithTopLevelEntries(
                "nsdeleted.cat.lol",
                ImmutableMap.of(
                    "HANDLE", "A-ROID",
                    "ADDRESSTYPE", "v4",
                    "ADDRESS", "1.2.3.4",
                    "STATUS", "inactive"),
                "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDeletedNameserver_notFound_differentRegistrarRequested() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("TheRegistrar");
    generateActualJson("ns1.cat.lol", Optional.of("otherregistrar"), Optional.of(false));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testMetrics() throws Exception {
    generateActualJson("ns1.cat.lol");
    verify(rdapMetrics)
        .updateMetrics(
            RdapMetrics.RdapMetricInformation.builder()
                .setEndpointType(EndpointType.NAMESERVER)
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
