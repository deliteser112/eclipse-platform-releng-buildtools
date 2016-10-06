// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.model.ofy.Ofy;
import google.registry.rdap.RdapJsonFormatter.BoilerplateType;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
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
    public String getActionPath() {
      return PATH;
    }

    @Override
    public ImmutableMap<String, Object> getJsonObjectForResource(
        String pathSearchString, boolean isHeadRequest, String linkBase) {
      if (pathSearchString.equals("IllegalArgumentException")) {
        throw new IllegalArgumentException();
      }
      if (pathSearchString.equals("RuntimeException")) {
        throw new RuntimeException();
      }
      ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
      builder.put("key", "value");
      RdapJsonFormatter.addTopLevelEntries(
          builder,
          BoilerplateType.OTHER,
          ImmutableList.<ImmutableMap<String, Object>>of(),
          ImmutableList.<ImmutableMap<String, Object>>of(),
          "http://myserver.google.com/");
      return builder.build();
    }
  }

  private RdapTestAction action;

  @Before
  public void setUp() throws Exception {
    createTld("thing");
    inject.setStaticField(Ofy.class, "clock", clock);
    action = new RdapTestAction();
    action.response = response;
  }

  private Object generateActualJson(String domainName) {
    action.requestPath = RdapTestAction.PATH + domainName;
    action.requestMethod = GET;
    action.rdapLinkBase = "http://myserver.google.com/";
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private String generateHeadPayload(String domainName) {
    action.requestPath = RdapTestAction.PATH + domainName;
    action.requestMethod = HEAD;
    action.rdapLinkBase = "http://myserver.google.com/";
    action.run();
    return response.getPayload();
  }

  @Test
  public void testIllegalValue_showsReadableTypeName() throws Exception {
    assertThat(generateActualJson("IllegalArgumentException")).isEqualTo(JSONValue.parse(
        "{\"lang\":\"en\", \"errorCode\":400, \"title\":\"Bad Request\","
        + "\"rdapConformance\":[\"rdap_level_0\"],"
        + "\"description\":[\"Not a valid human-readable string\"]}"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testRuntimeException_returns500Error() throws Exception {
    assertThat(generateActualJson("RuntimeException")).isEqualTo(JSONValue.parse(
        "{\"lang\":\"en\", \"errorCode\":500, \"title\":\"Internal Server Error\","
        + "\"rdapConformance\":[\"rdap_level_0\"],"
        + "\"description\":[\"An error was encountered\"]}"));
    assertThat(response.getStatus()).isEqualTo(500);
  }

  @Test
  public void testValidName_works() throws Exception {
    assertThat(generateActualJson("no.thing")).isEqualTo(JSONValue.parse(
        loadFileWithSubstitutions(this.getClass(), "rdapjson_toplevel.json", null)));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHeadRequest_returnsNoContent() throws Exception {
    assertThat(generateHeadPayload("no.thing")).isEmpty();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHeadRequestIllegalValue_returnsNoContent() throws Exception {
    assertThat(generateHeadPayload("IllegalArgumentException")).isEmpty();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testRdapServer_allowsAllCrossOriginRequests() throws Exception {
    generateActualJson("no.thing");
    assertThat(response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("*");
  }
}
