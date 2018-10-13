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

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.TestDataHelper.loadFile;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.model.ofy.Ofy;
import google.registry.rdap.RdapJsonFormatter.BoilerplateType;
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
import google.registry.ui.server.registrar.AuthenticatedRegistrarAccessor;
import java.util.Optional;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapActionBase}. */
@RunWith(JUnit4.class)
public class RdapActionBaseTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));
  private final AuthenticatedRegistrarAccessor registrarAccessor =
      mock(AuthenticatedRegistrarAccessor.class);
  private final User user = new User("rdap.user@example.com", "gmail.com", "12345");
  private final UserAuthInfo userAuthInfo = UserAuthInfo.create(user, false);
  private final RdapMetrics rdapMetrics = mock(RdapMetrics.class);

  /**
   * Dummy RdapActionBase subclass used for testing.
   */
  static class RdapTestAction extends RdapActionBase {

    public static final String PATH = "/rdap/test/";

    @Override
    public String getHumanReadableObjectTypeName() {
      return "human-readable string";
    }

    @Override
    public EndpointType getEndpointType() {
      return EndpointType.HELP;
    }

    @Override
    public String getActionPath() {
      return PATH;
    }

    @Override
    public ImmutableMap<String, Object> getJsonObjectForResource(
        String pathSearchString, boolean isHeadRequest) {
      if (pathSearchString.equals("IllegalArgumentException")) {
        throw new IllegalArgumentException();
      }
      if (pathSearchString.equals("RuntimeException")) {
        throw new RuntimeException();
      }
      ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
      builder.put("key", "value");
      rdapJsonFormatter.addTopLevelEntries(
          builder,
          BoilerplateType.OTHER,
          ImmutableList.of(),
          ImmutableList.of(),
          "http://myserver.example.com/");
      return builder.build();
    }
  }

  private RdapTestAction action;

  @Before
  public void setUp() {
    createTld("thing");
    inject.setStaticField(Ofy.class, "clock", clock);
    action = new RdapTestAction();
    action.registrarAccessor = registrarAccessor;
    action.authResult = AuthResult.create(AuthLevel.USER, userAuthInfo);
    action.includeDeletedParam = Optional.empty();
    action.registrarParam = Optional.empty();
    action.formatOutputParam = Optional.empty();
    action.response = response;
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapMetrics = rdapMetrics;
  }

  private Object generateActualJson(String domainName) {
    action.requestPath = RdapTestAction.PATH + domainName;
    action.fullServletPath = "http://myserver.example.com" + RdapTestAction.PATH;
    action.requestMethod = GET;
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private String generateHeadPayload(String domainName) {
    action.requestPath = RdapTestAction.PATH + domainName;
    action.fullServletPath = "http://myserver.example.com" + RdapTestAction.PATH;
    action.requestMethod = HEAD;
    action.run();
    return response.getPayload();
  }

  @Test
  public void testIllegalValue_showsReadableTypeName() {
    assertThat(generateActualJson("IllegalArgumentException")).isEqualTo(JSONValue.parse(
        "{\"lang\":\"en\", \"errorCode\":400, \"title\":\"Bad Request\","
        + "\"rdapConformance\":[\"rdap_level_0\"],"
        + "\"description\":[\"Not a valid human-readable string\"]}"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testRuntimeException_returns500Error() {
    assertThat(generateActualJson("RuntimeException")).isEqualTo(JSONValue.parse(
        "{\"lang\":\"en\", \"errorCode\":500, \"title\":\"Internal Server Error\","
        + "\"rdapConformance\":[\"rdap_level_0\"],"
        + "\"description\":[\"An error was encountered\"]}"));
    assertThat(response.getStatus()).isEqualTo(500);
  }

  @Test
  public void testValidName_works() {
    assertThat(generateActualJson("no.thing")).isEqualTo(JSONValue.parse(
        loadFile(this.getClass(), "rdapjson_toplevel.json")));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testContentType_rdapjson_utf8() {
    generateActualJson("no.thing");
    assertThat(response.getContentType().toString())
        .isEqualTo("application/rdap+json; charset=utf-8");
  }

  @Test
  public void testHeadRequest_returnsNoContent() {
    assertThat(generateHeadPayload("no.thing")).isEmpty();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHeadRequestIllegalValue_returnsNoContent() {
    assertThat(generateHeadPayload("IllegalArgumentException")).isEmpty();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testRdapServer_allowsAllCrossOriginRequests() {
    generateActualJson("no.thing");
    assertThat(response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("*");
  }

  @Test
  public void testMetrics_onSuccess() {
    generateActualJson("no.thing");
    verify(rdapMetrics)
        .updateMetrics(
            RdapMetrics.RdapMetricInformation.builder()
                .setEndpointType(EndpointType.HELP)
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

  @Test
  public void testMetrics_onError() {
    generateActualJson("IllegalArgumentException");
    verify(rdapMetrics)
        .updateMetrics(
            RdapMetrics.RdapMetricInformation.builder()
                .setEndpointType(EndpointType.HELP)
                .setSearchType(SearchType.NONE)
                .setWildcardType(WildcardType.INVALID)
                .setPrefixLength(0)
                .setIncludeDeleted(false)
                .setRegistrarSpecified(false)
                .setRole(RdapAuthorization.Role.PUBLIC)
                .setRequestMethod(Action.Method.GET)
                .setStatusCode(400)
                .setIncompletenessWarningType(IncompletenessWarningType.COMPLETE)
                .build());
  }

  private String loadFileWithoutTrailingNewline(String fileName) {
    String contents = loadFile(this.getClass(), fileName);
    return contents.endsWith("\n") ? contents.substring(0, contents.length() - 1) : contents;
  }

  @Test
  public void testUnformatted() {
    action.requestPath = RdapTestAction.PATH + "no.thing";
    action.fullServletPath = "http://myserver.example.com" + RdapTestAction.PATH;
    action.requestMethod = GET;
    action.run();
    assertThat(response.getPayload())
        .isEqualTo(loadFileWithoutTrailingNewline("rdap_unformatted_output.json"));
  }

  @Test
  public void testFormatted() {
    action.requestPath = RdapTestAction.PATH + "no.thing?formatOutput=true";
    action.fullServletPath = "http://myserver.example.com" + RdapTestAction.PATH;
    action.requestMethod = GET;
    action.formatOutputParam = Optional.of(true);
    action.run();
    assertThat(response.getPayload())
        .isEqualTo(loadFileWithoutTrailingNewline("rdap_formatted_output.json"));
  }
}
