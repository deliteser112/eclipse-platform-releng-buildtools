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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.AppEngineRule.THE_REGISTRAR_GAE_USER_ID;
import static google.registry.testing.DatastoreHelper.deleteResource;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.testing.LogsSubject.assertAboutLogs;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.flogger.LoggerConfig;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.TestLogHandler;
import google.registry.model.registrar.RegistrarContact;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.InjectRule;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SessionUtils}. */
@RunWith(JUnit4.class)
public class SessionUtilsTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();
  @Rule public final InjectRule inject = new InjectRule();

  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse rsp = mock(HttpServletResponse.class);
  private final HttpSession session = mock(HttpSession.class);
  private final TestLogHandler testLogHandler = new TestLogHandler();

  private SessionUtils sessionUtils;

  private static final UserAuthInfo AUTHORIZED_USER = createAuthInfo(true, false);
  private static final UserAuthInfo UNAUTHORIZED_USER = createAuthInfo(false, false);
  private static final UserAuthInfo AUTHORIZED_ADMIN = createAuthInfo(true, true);
  private static final UserAuthInfo UNAUTHORIZED_ADMIN = createAuthInfo(false, true);
  private static final String DEFAULT_CLIENT_ID = "TheRegistrar";
  private static final String ADMIN_CLIENT_ID = "NewRegistrar";

  private static UserAuthInfo createAuthInfo(boolean isAuthorized, boolean isAdmin) {
    return UserAuthInfo.create(
        new User(
            "user1@google.com",
            "google.com",
            isAuthorized ? THE_REGISTRAR_GAE_USER_ID : "badGaeUserId"),
        isAdmin);
  }

  @Before
  public void before() throws Exception {
    LoggerConfig.getConfig(SessionUtils.class).addHandler(testLogHandler);
    sessionUtils = new SessionUtils();
    sessionUtils.registryAdminClientId = ADMIN_CLIENT_ID;
    persistResource(loadRegistrar(ADMIN_CLIENT_ID));
    when(req.getSession()).thenReturn(session);
  }

  @After
  public void after() throws Exception {
    LoggerConfig.getConfig(SessionUtils.class).removeHandler(testLogHandler);
  }

  /** User needs to be logged in before calling checkRegistrarConsoleLogin */
  @Test
  public void testCheckRegistrarConsoleLogin_notLoggedIn_throwsIllegalStateException()
      throws Exception {
    assertThrows(
        IllegalStateException.class,
        () -> {
          @SuppressWarnings("unused")
          boolean unused = sessionUtils.checkRegistrarConsoleLogin(req, null);
        });
  }

  /**
   * If clientId exists in the session and the user does not have access to that registrar, then no
   * access should be granted.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_hasSession_noAccess_isNotAdmin() throws Exception {
    when(session.getAttribute("clientId")).thenReturn(DEFAULT_CLIENT_ID);
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, UNAUTHORIZED_USER)).isFalse();
    verify(session).invalidate();
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(Level.INFO, "Registrar Console access revoked");
  }

  /**
   * If clientId exists in the session and the user does not have access to that registrar, then
   * access should be revoked. The admin flag should be ignored.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_hasSession_noAccess_isAdmin() throws Exception {
    when(session.getAttribute("clientId")).thenReturn(DEFAULT_CLIENT_ID);
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, UNAUTHORIZED_ADMIN)).isFalse();
    verify(session).invalidate();
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(Level.INFO, "Registrar Console access revoked");
  }

  /**
   * If clientId exists in the session and the user does have access to that registrar, then access
   * should be allowed.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_hasSession_hasAccess_isNotAdmin() throws Exception {
    when(session.getAttribute("clientId")).thenReturn(DEFAULT_CLIENT_ID);
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, AUTHORIZED_USER)).isTrue();
    verify(session).getAttribute("clientId");
    verifyNoMoreInteractions(session);
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO,
            String.format(
                "Associating user %s with given registrar %s.",
                AUTHORIZED_USER.user().getUserId(), DEFAULT_CLIENT_ID));
  }

  /**
   * If clientId exists in the session and the user does have access to that registrar, then access
   * should be allowed. The admin flag should be ignored.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_hasSession_hasAccess_isAdmin() throws Exception {
    when(session.getAttribute("clientId")).thenReturn(DEFAULT_CLIENT_ID);
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, AUTHORIZED_ADMIN)).isTrue();
    verify(session).getAttribute("clientId");
    verifyNoMoreInteractions(session);
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO,
            String.format(
                "Associating user %s with given registrar %s.",
                AUTHORIZED_ADMIN.user().getUserId(), DEFAULT_CLIENT_ID));
  }

  /**
   * If clientId does not exist in the session and the user has access to a registrar, then access
   * should be granted to that registrar.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_noSession_hasAccess_isNotAdmin() throws Exception {
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, AUTHORIZED_USER)).isTrue();
    verify(session).setAttribute(eq("clientId"), eq(DEFAULT_CLIENT_ID));
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO,
            String.format(
                "Associating user %s with found registrar %s.",
                AUTHORIZED_USER.user().getUserId(), DEFAULT_CLIENT_ID));
  }

  /**
   * If clientId does not exist in the session and the user has access to a registrar, then access
   * should be granted to that registrar. The admin flag should be ignored.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_noSession_hasAccess_isAdmin() throws Exception {
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, AUTHORIZED_ADMIN)).isTrue();
    verify(session).setAttribute(eq("clientId"), eq(DEFAULT_CLIENT_ID));
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO,
            String.format(
                "Associating user %s with found registrar %s.",
                AUTHORIZED_ADMIN.user().getUserId(), DEFAULT_CLIENT_ID));
  }

  /**
   * If clientId does not exist in the session, the user is not associated with a registrar and the
   * user is an admin, then access could be granted to the configured adminClientId. But if the
   * configured adminClientId is empty or null, no access is granted.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_noSession_noAccess_isAdmin_adminClientIdEmpty()
      throws Exception {
    sessionUtils.registryAdminClientId = "";
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, UNAUTHORIZED_ADMIN)).isFalse();
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO,
            String.format(
                "Cannot associate admin user %s with configured client Id."
                    + " ClientId is null or empty.",
                UNAUTHORIZED_ADMIN.user().getUserId()));
  }

  /**
   * If clientId does not exist in the session, the user is not associated with a registrar and the
   * user is an admin, then access could be granted to the configured adminClientId. But if the
   * configured adminClientId does not reference a registry, then no access is granted.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_noSession_noAccess_isAdmin_adminClientIdInvalid()
      throws Exception {
    sessionUtils.registryAdminClientId = "NonexistentRegistry";
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, UNAUTHORIZED_ADMIN)).isFalse();
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO,
            String.format(
                "Cannot associate admin user %s with configured client Id %s."
                    + " Registrar does not exist.",
                UNAUTHORIZED_ADMIN.user().getUserId(), "NonexistentRegistry"));
  }

  /**
   * If clientId does not exist in the session, the user does not have access to a registrar and the
   * user is an admin, then grant the user access to the validated configured adminClientId.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_noSession_noAccess_isAdmin() throws Exception {
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, UNAUTHORIZED_ADMIN)).isTrue();
    verify(session).setAttribute(eq("clientId"), eq(ADMIN_CLIENT_ID));
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO,
            String.format(
                "User %s is an admin with no associated registrar."
                    + " Automatically associating the user with configured client Id %s.",
                UNAUTHORIZED_ADMIN.user().getUserId(), ADMIN_CLIENT_ID));
  }

  /**
   * If clientId does not exist in the session and the user is not associated with a registrar, then
   * access should not be granted.
   */
  @Test
  public void testCheckRegistrarConsoleLogin_noSession_noAccess_isNotAdmin() throws Exception {
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, UNAUTHORIZED_USER)).isFalse();
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO,
            String.format(
                "User not associated with any Registrar: %s",
                UNAUTHORIZED_USER.user().getUserId()));
  }

  @Test
  public void testHasAccessToRegistrar_orphanedContact_returnsFalse() throws Exception {
    deleteResource(loadRegistrar(DEFAULT_CLIENT_ID));
    assertThat(SessionUtils.hasAccessToRegistrar(DEFAULT_CLIENT_ID, THE_REGISTRAR_GAE_USER_ID))
        .isFalse();
  }

  @Test
  public void testHasAccessToRegistrar_accessRevoked_returnsFalse() throws Exception {
    RegistrarContact.updateContacts(loadRegistrar(DEFAULT_CLIENT_ID), new java.util.HashSet<>());
    assertThat(SessionUtils.hasAccessToRegistrar(DEFAULT_CLIENT_ID, THE_REGISTRAR_GAE_USER_ID))
        .isFalse();
  }

  @Test
  public void testNullness() throws Exception {
    new NullPointerTester()
        .setDefault(HttpServletRequest.class, req)
        .setDefault(HttpServletResponse.class, rsp)
        .testAllPublicStaticMethods(SessionUtils.class);
  }
}
