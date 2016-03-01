// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.rdap;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.request.Action.Method.GET;
import static com.google.domain.registry.request.Action.Method.HEAD;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;

import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.InjectRule;

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
  class RdapTestAction extends RdapActionBase {

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
    public ImmutableMap<String, Object> getJsonObjectForResource(String searchString) {
      if (searchString.equals("IllegalArgumentException")) {
        throw new IllegalArgumentException();
      }
      if (searchString.equals("RuntimeException")) {
        throw new RuntimeException();
      }
      return ImmutableMap.<String, Object>of("key", "value");
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
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private String generateHeadPayload(String domainName) {
    action.requestPath = RdapTestAction.PATH + domainName;
    action.requestMethod = HEAD;
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
        "{\"rdapConformance\":[\"rdap_level_0\"], \"key\":\"value\"}"));
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
