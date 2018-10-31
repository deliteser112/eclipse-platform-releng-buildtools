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

package google.registry.ui.server.registrar;

import static com.google.monitoring.metrics.contrib.LongMetricSubject.assertThat;
import static google.registry.config.RegistryConfig.getGSuiteOutgoingEmailAddress;
import static google.registry.config.RegistryConfig.getGSuiteOutgoingEmailDisplayName;
import static google.registry.security.JsonHttpTestUtils.createJsonPayload;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.disallowRegistrarAccess;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.ui.server.registrar.AuthenticatedRegistrarAccessor.Role.ADMIN;
import static google.registry.ui.server.registrar.AuthenticatedRegistrarAccessor.Role.OWNER;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import google.registry.config.RegistryEnvironment;
import google.registry.model.ofy.Ofy;
import google.registry.request.HttpException.ForbiddenException;
import google.registry.request.JsonActionRunner;
import google.registry.request.JsonResponse;
import google.registry.request.ResponseImpl;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.testing.MockitoJUnitRule;
import google.registry.util.AppEngineServiceUtils;
import google.registry.util.SendEmailService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

/** Base class for tests using {@link RegistrarSettingsAction}. */
@RunWith(JUnit4.class)
public class RegistrarSettingsActionTestCase {

  static final String CLIENT_ID = "TheRegistrar";

  @Rule
  public final AppEngineRule appEngine =
      AppEngineRule.builder().withDatastore().withTaskQueue().build();

  @Rule public final InjectRule inject = new InjectRule();
  @Rule public final MockitoJUnitRule mocks = MockitoJUnitRule.create();

  @Mock AppEngineServiceUtils appEngineServiceUtils;
  @Mock HttpServletRequest req;
  @Mock HttpServletResponse rsp;
  @Mock SendEmailService emailService;
  final User user = new User("user", "gmail.com");

  Message message;

  final RegistrarSettingsAction action = new RegistrarSettingsAction();
  final StringWriter writer = new StringWriter();
  final FakeClock clock = new FakeClock(DateTime.parse("2014-01-01T00:00:00Z"));

  @Before
  public void setUp() throws Exception {
    // Create a tld and give access to registrar "TheRegistrar" for most common test case.
    createTlds("currenttld");
    // Create another tld but remove access for registrar "TheRegistrar". This is for the test case
    // of updating allowed tld for registrar
    createTlds("newtld");
    disallowRegistrarAccess(CLIENT_ID, "newtld");
    action.registrarAccessor = null;
    action.appEngineServiceUtils = appEngineServiceUtils;
    when(appEngineServiceUtils.getCurrentVersionHostname("backend")).thenReturn("backend.hostname");
    action.jsonActionRunner = new JsonActionRunner(
        ImmutableMap.of(), new JsonResponse(new ResponseImpl(rsp)));
    action.registrarChangesNotificationEmailAddresses = ImmutableList.of(
        "notification@test.example", "notification2@test.example");
    action.sendEmailUtils =
        new SendEmailUtils(
            getGSuiteOutgoingEmailAddress(), getGSuiteOutgoingEmailDisplayName(), emailService);
    action.registryEnvironment = RegistryEnvironment.get();
    action.registrarConsoleMetrics = new RegistrarConsoleMetrics();
    action.authResult =
        AuthResult.create(
            AuthLevel.USER,
            UserAuthInfo.create(new User("user@email.com", "email.com", "12345"), false));
    inject.setStaticField(Ofy.class, "clock", clock);
    message = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
    when(emailService.createMessage()).thenReturn(message);
    when(req.getMethod()).thenReturn("POST");
    when(rsp.getWriter()).thenReturn(new PrintWriter(writer));
    when(req.getContentType()).thenReturn("application/json");
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of("op", "read")));
    // We set the default to a user with access, as that's the most common test case. When we want
    // to specifically check a user without access, we can switch user for that specific test.
    setUserWithAccess();
    RegistrarConsoleMetrics.settingsRequestMetric.reset();
  }

  @After
  public void tearDown() throws Exception {
    assertThat(RegistrarConsoleMetrics.settingsRequestMetric).hasNoOtherValues();
  }

  public void assertMetric(String clientId, String op, String roles, String status) {
    assertThat(RegistrarConsoleMetrics.settingsRequestMetric)
        .hasValueForLabels(1, clientId, op, roles, status);
    RegistrarConsoleMetrics.settingsRequestMetric.reset(clientId, op, roles, status);
  }

  /** Sets registrarAccessor.getRegistrar to succeed for all AccessTypes. */
  protected void setUserWithAccess() {
    action.registrarAccessor = mock(AuthenticatedRegistrarAccessor.class);

    when(action.registrarAccessor.getAllClientIdWithRoles())
        .thenReturn(ImmutableSetMultimap.of(CLIENT_ID, OWNER));
    when(action.registrarAccessor.getRegistrar(CLIENT_ID))
        .thenAnswer(x -> loadRegistrar(CLIENT_ID));
  }

  /** Sets registrarAccessor.getRegistrar to always fail. */
  protected void setUserWithoutAccess() {
    action.registrarAccessor = mock(AuthenticatedRegistrarAccessor.class);

    when(action.registrarAccessor.getAllClientIdWithRoles()).thenReturn(ImmutableSetMultimap.of());
    when(action.registrarAccessor.getRegistrar(CLIENT_ID))
        .thenThrow(new ForbiddenException("forbidden test error"));
  }

  /**
   * Sets registrarAccessor.getAllClientIdWithRoles to return a map with admin role for CLIENT_ID
   */
  protected void setUserAdmin() {
    action.registrarAccessor = mock(AuthenticatedRegistrarAccessor.class);

    when(action.registrarAccessor.getAllClientIdWithRoles())
        .thenReturn(ImmutableSetMultimap.of(CLIENT_ID, ADMIN));
    when(action.registrarAccessor.getRegistrar(CLIENT_ID))
        .thenAnswer(x -> loadRegistrar(CLIENT_ID));
  }
}
