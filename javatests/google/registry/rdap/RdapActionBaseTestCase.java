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

import static google.registry.rdap.RdapAuthorization.Role.ADMINISTRATOR;
import static google.registry.rdap.RdapAuthorization.Role.PUBLIC;
import static google.registry.rdap.RdapAuthorization.Role.REGISTRAR;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;
import static google.registry.request.auth.AuthenticatedRegistrarAccessor.Role.OWNER;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableSetMultimap;
import google.registry.model.ofy.Ofy;
import google.registry.request.Action;
import google.registry.request.Actions;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.AuthenticatedRegistrarAccessor;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.util.TypeUtils;
import java.util.Optional;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
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

  protected final AuthenticatedRegistrarAccessor registrarAccessor =
      mock(AuthenticatedRegistrarAccessor.class);

  protected FakeResponse response = new FakeResponse();
  protected final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));
  protected final RdapMetrics rdapMetrics = mock(RdapMetrics.class);

  protected RdapAuthorization.Role metricRole = PUBLIC;
  protected A action;

  protected final String actionPath;
  protected final Class<A> rdapActionClass;

  protected RdapActionBaseTestCase(Class<A> rdapActionClass) {
    this.rdapActionClass = rdapActionClass;
    this.actionPath = Actions.getPathForAction(rdapActionClass);
  }

  @Before
  public void baseSetUp() {
    inject.setStaticField(Ofy.class, "clock", clock);
    action = TypeUtils.instantiate(rdapActionClass);
    action.registrarAccessor = registrarAccessor;
    action.clock = clock;
    action.authResult = AUTH_RESULT;
    action.includeDeletedParam = Optional.empty();
    action.registrarParam = Optional.empty();
    action.formatOutputParam = Optional.empty();
    action.response = response;
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapMetrics = rdapMetrics;
    action.requestMethod = Action.Method.GET;
    action.fullServletPath = "https://example.tld/rdap";
    action.rdapWhoisServer = null;
    logout();
  }

  protected void login(String clientId) {
    when(registrarAccessor.getAllClientIdWithRoles())
        .thenReturn(ImmutableSetMultimap.of(clientId, OWNER));
    action.authResult = AUTH_RESULT;
    metricRole = REGISTRAR;
  }

  protected void logout() {
    when(registrarAccessor.getAllClientIdWithRoles()).thenReturn(ImmutableSetMultimap.of());
    action.authResult = AUTH_RESULT;
    metricRole = PUBLIC;
  }

  protected void loginAsAdmin() {
    // when admin, we don't actually check what they have access to - so it doesn't matter what we
    // return.
    // null isn't actually a legal value, we just want to make sure it's never actually used.
    when(registrarAccessor.getAllClientIdWithRoles()).thenReturn(null);
    action.authResult = AUTH_RESULT_ADMIN;
    metricRole = ADMINISTRATOR;
  }

  protected Object generateActualJson(String domainName) {
    action.requestPath = actionPath + domainName;
    action.requestMethod = GET;
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  protected String generateHeadPayload(String domainName) {
    action.requestPath = actionPath + domainName;
    action.fullServletPath = "http://myserver.example.com" + actionPath;
    action.requestMethod = HEAD;
    action.run();
    return response.getPayload();
  }
}
