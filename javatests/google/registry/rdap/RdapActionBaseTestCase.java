// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.rdap.RdapAuthorization.Role.ADMINISTRATOR;
import static google.registry.rdap.RdapAuthorization.Role.PUBLIC;
import static google.registry.rdap.RdapAuthorization.Role.REGISTRAR;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;
import static google.registry.testing.TestDataHelper.loadFile;
import static org.mockito.Mockito.mock;

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import google.registry.model.ofy.Ofy;
import google.registry.request.Action;
import google.registry.request.Actions;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.util.TypeUtils;
import java.util.Map;
import java.util.Optional;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Common unit test code for actions inheriting {@link RdapActionBase}. */
@RunWith(JUnit4.class)
public class RdapActionBaseTestCase<A extends RdapActionBase> {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  protected static final AuthResult AUTH_RESULT =
      AuthResult.create(
          AuthLevel.USER,
          UserAuthInfo.create(new User("rdap.user@user.com", "gmail.com", "12345"), false));

  protected static final AuthResult AUTH_RESULT_ADMIN =
      AuthResult.create(
          AuthLevel.USER,
          UserAuthInfo.create(new User("rdap.admin@google.com", "gmail.com", "12345"), true));

  protected FakeResponse response = new FakeResponse();
  protected final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));
  protected final RdapMetrics rdapMetrics = mock(RdapMetrics.class);

  protected RdapAuthorization.Role metricRole = PUBLIC;
  protected A action;

  protected final String actionPath;
  protected final Class<A> rdapActionClass;

  private static final Gson GSON = new Gson();

  protected RdapActionBaseTestCase(Class<A> rdapActionClass) {
    this.rdapActionClass = rdapActionClass;
    this.actionPath = Actions.getPathForAction(rdapActionClass);
  }

  @Before
  public void baseSetUp() {
    inject.setStaticField(Ofy.class, "clock", clock);
    action = TypeUtils.instantiate(rdapActionClass);
    action.clock = clock;
    action.includeDeletedParam = Optional.empty();
    action.registrarParam = Optional.empty();
    action.formatOutputParam = Optional.empty();
    action.response = response;
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapMetrics = rdapMetrics;
    action.requestMethod = Action.Method.GET;
    action.rdapWhoisServer = null;
    logout();
  }

  protected void login(String clientId) {
    action.rdapAuthorization = RdapAuthorization.create(REGISTRAR, clientId);
    action.rdapJsonFormatter.rdapAuthorization = action.rdapAuthorization;
    metricRole = REGISTRAR;
  }

  protected void logout() {
    action.rdapAuthorization = RdapAuthorization.PUBLIC_AUTHORIZATION;
    action.rdapJsonFormatter.rdapAuthorization = action.rdapAuthorization;
    metricRole = PUBLIC;
  }

  protected void loginAsAdmin() {
    action.rdapAuthorization = RdapAuthorization.ADMINISTRATOR_AUTHORIZATION;
    action.rdapJsonFormatter.rdapAuthorization = action.rdapAuthorization;
    metricRole = ADMINISTRATOR;
  }

  protected static JsonObject parseJsonObject(String jsonString) {
    return GSON.fromJson(jsonString, JsonObject.class);
  }

  protected JsonObject generateActualJson(String domainName) {
    action.requestPath = actionPath + domainName;
    action.requestMethod = GET;
    action.run();
    return parseJsonObject(response.getPayload());
  }

  protected String generateHeadPayload(String domainName) {
    action.requestPath = actionPath + domainName;
    action.requestMethod = HEAD;
    action.run();
    return response.getPayload();
  }

  /**
   * Loads a resource testdata JSON file, and applies substitutions.
   *
   * <p>{@code loadJsonFile("filename.json", "NANE", "something", "ID", "other")} is the same as
   * {@code loadJsonFile("filename.json", ImmutableMap.of("NANE", "something", "ID", "other"))}.
   *
   * @param filename the name of the file from the testdata directory
   * @param keysAndValues alternating substitution key and value. The substitutions are applied to
   *     the file before parsing it to JSON.
   */
  protected static JsonObject loadJsonFile(String filename, String... keysAndValues) {
    checkArgument(keysAndValues.length % 2 == 0);
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      if (keysAndValues[i + 1] != null) {
        builder.put(keysAndValues[i], keysAndValues[i + 1]);
      }
    }
    return loadJsonFile(filename, builder.build());
  }

  /**
   * Loads a resource testdata JSON file, and applies substitutions.
   *
   * @param filename the name of the file from the testdata directory
   * @param substitutions map of substitutions to apply to the file. The substitutions are applied
   *     to the file before parsing it to JSON.
   */
  protected static JsonObject loadJsonFile(String filename, Map<String, String> substitutions) {
    return parseJsonObject(loadFile(RdapActionBaseTestCase.class, filename, substitutions));
  }

  protected JsonObject generateExpectedJsonError(
      String description,
      int code) {
    String title;
    switch (code) {
      case 404:
        title = "Not Found";
        break;
      case 500:
        title = "Internal Server Error";
        break;
      case 501:
        title = "Not Implemented";
        break;
      case 400:
        title = "Bad Request";
        break;
      case 422:
        title = "Unprocessable Entity";
        break;
      default:
        title = "ERR";
        break;
    }
    return loadJsonFile(
        "rdap_error.json",
        "DESCRIPTION", description,
        "TITLE", title,
        "CODE", String.valueOf(code));
  }
}
