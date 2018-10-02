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
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.testing.LogsSubject.assertAboutLogs;
import static google.registry.ui.server.registrar.SessionUtils.AccessType.READ_ONLY;
import static google.registry.ui.server.registrar.SessionUtils.AccessType.READ_WRITE;
import static org.mockito.Mockito.mock;

import com.google.appengine.api.users.User;
import com.google.common.flogger.LoggerConfig;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.TestLogHandler;
import google.registry.request.HttpException.ForbiddenException;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.InjectRule;
import google.registry.ui.server.registrar.SessionUtils.AccessType;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
  private final TestLogHandler testLogHandler = new TestLogHandler();

  private SessionUtils sessionUtils;

  private static final AuthResult AUTHORIZED_USER = createAuthResult(true, false);
  private static final AuthResult UNAUTHORIZED_USER = createAuthResult(false, false);
  private static final AuthResult AUTHORIZED_ADMIN = createAuthResult(true, true);
  private static final AuthResult UNAUTHORIZED_ADMIN = createAuthResult(false, true);
  private static final AuthResult NO_USER = AuthResult.create(AuthLevel.NONE);
  private static final String DEFAULT_CLIENT_ID = "TheRegistrar";
  private static final String ADMIN_CLIENT_ID = "NewRegistrar";

  private static AuthResult createAuthResult(boolean isAuthorized, boolean isAdmin) {
    return AuthResult.create(
        AuthLevel.USER,
        UserAuthInfo.create(
            new User(
                String.format(
                    "%s_%s@gmail.com", isAuthorized ? "good" : "evil", isAdmin ? "admin" : "user"),
                "gmail.com",
                isAuthorized ? THE_REGISTRAR_GAE_USER_ID : "badGaeUserId"),
            isAdmin));
  }

  @Before
  public void before() {
    LoggerConfig.getConfig(SessionUtils.class).addHandler(testLogHandler);
    sessionUtils = new SessionUtils();
    sessionUtils.registryAdminClientId = ADMIN_CLIENT_ID;
    persistResource(loadRegistrar(ADMIN_CLIENT_ID));
  }

  @After
  public void after() {
    LoggerConfig.getConfig(SessionUtils.class).removeHandler(testLogHandler);
  }

  private String formatMessage(String message, AuthResult authResult, String clientId) {
    return message
        .replace("{user}", authResult.userIdForLogging())
        .replace("{clientId}", String.valueOf(clientId));
  }

  /** Fail loading registrar if user doesn't have access to it. */
  @Test
  public void testGetRegistrarForUser_readOnly_noAccess_isNotAdmin() {
    expectGetRegistrarFailure(
        DEFAULT_CLIENT_ID,
        READ_ONLY,
        UNAUTHORIZED_USER,
        "User {user} doesn't have READ_ONLY access to registrar {clientId}");
  }

  /** Fail loading registrar if user doesn't have access to it. */
  @Test
  public void testGetRegistrarForUser_readWrite_noAccess_isNotAdmin() {
    expectGetRegistrarFailure(
        DEFAULT_CLIENT_ID,
        READ_WRITE,
        UNAUTHORIZED_USER,
        "User {user} doesn't have READ_WRITE access to registrar {clientId}");
  }

  /** Fail loading registrar if there's no user associated with the request. */
  @Test
  public void testGetRegistrarForUser_readOnly_noUser() {
    expectGetRegistrarFailure(DEFAULT_CLIENT_ID, READ_ONLY, NO_USER, "Not logged in");
  }

  /** Fail loading registrar if there's no user associated with the request. */
  @Test
  public void testGetRegistrarForUser_readWrite_noUser() {
    expectGetRegistrarFailure(DEFAULT_CLIENT_ID, READ_WRITE, NO_USER, "Not logged in");

    assertAboutLogs().that(testLogHandler).hasNoLogsAtLevel(Level.INFO);
  }

  /** Succeed loading registrar in read-only mode if user has access to it. */
  @Test
  public void testGetRegistrarForUser_readOnly_hasAccess_isNotAdmin() {
    expectGetRegistrarSuccess(
        AUTHORIZED_USER, READ_ONLY, "User {user} has access to registrar {clientId}");
  }

  /** Succeed loading registrar in read-write mode if user has access to it. */
  @Test
  public void testGetRegistrarForUser_readWrite_hasAccess_isNotAdmin() {
    expectGetRegistrarSuccess(
        AUTHORIZED_USER, READ_WRITE, "User {user} has access to registrar {clientId}");
  }

  /** Succeed loading registrar in read-only mode if admin with access. */
  @Test
  public void testGetRegistrarForUser_readOnly_hasAccess_isAdmin() {
    expectGetRegistrarSuccess(
        AUTHORIZED_ADMIN, READ_ONLY, "User {user} has access to registrar {clientId}");
  }

  /** Succeed loading registrar in read-write mode if admin with access. */
  @Test
  public void testGetRegistrarForUser_readWrite_hasAccess_isAdmin() {
    expectGetRegistrarSuccess(
        AUTHORIZED_ADMIN, READ_WRITE, "User {user} has access to registrar {clientId}");
  }

  /** Succeed loading registrar for read-only when admin isn't on the approved contacts list. */
  @Test
  public void testGetRegistrarForUser_readOnly_noAccess_isAdmin() {
    expectGetRegistrarSuccess(
        UNAUTHORIZED_ADMIN,
        READ_ONLY,
        "Allowing admin {user} read-only access to registrar {clientId}.");
  }

  /** Fail loading registrar for read-write when admin isn't on the approved contacts list. */
  @Test
  public void testGetRegistrarForUser_readWrite_noAccess_isAdmin() {
    expectGetRegistrarFailure(
        DEFAULT_CLIENT_ID,
        READ_WRITE,
        UNAUTHORIZED_ADMIN,
        "User {user} doesn't have READ_WRITE access to registrar {clientId}");
  }

  /** Fail loading registrar even if admin, if registrar doesn't exist. */
  @Test
  public void testGetRegistrarForUser_readOnly_doesntExist_isAdmin() {
    expectGetRegistrarFailure(
        "BadClientId",
        READ_ONLY,
        AUTHORIZED_ADMIN,
        "Registrar {clientId} not found");
  }

  /** Fail loading registrar even if admin, if registrar doesn't exist. */
  @Test
  public void testGetRegistrarForUser_readWrite_doesntExist_isAdmin() {
    expectGetRegistrarFailure(
        "BadClientId",
        READ_WRITE,
        AUTHORIZED_ADMIN,
        "Registrar {clientId} not found");
  }

  private void expectGetRegistrarSuccess(
      AuthResult authResult, AccessType accessType, String message) {
    assertThat(sessionUtils.getRegistrarForUser(DEFAULT_CLIENT_ID, accessType, authResult))
        .isNotNull();
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO, formatMessage(message, authResult, DEFAULT_CLIENT_ID));
  }

  private void expectGetRegistrarFailure(
      String clientId, AccessType accessType, AuthResult authResult, String message) {
    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () -> sessionUtils.getRegistrarForUser(clientId, accessType, authResult));

    assertThat(exception).hasMessageThat().contains(formatMessage(message, authResult, clientId));
    assertAboutLogs().that(testLogHandler).hasNoLogsAtLevel(Level.INFO);
  }

  /** If a user has access to a registrar, we should guess that registrar. */
  @Test
  public void testGuessClientIdForUser_hasAccess_isNotAdmin() {
    expectGuessRegistrarSuccess(
        AUTHORIZED_USER,
        DEFAULT_CLIENT_ID,
        "Associating user {user} with found registrar {clientId}.");
  }

  /** If a user doesn't have access to any registrars, guess returns nothing. */
  @Test
  public void testGuessClientIdForUser_noAccess_isNotAdmin() {
    expectGuessRegistrarFailure(
        UNAUTHORIZED_USER, "User {user} isn't associated with any registrar");
  }

  /**
   * If an admin has access to a registrar, we should guess that registrar (rather than the
   * ADMIN_CLIENT_ID).
   */
  @Test
  public void testGuessClientIdForUser_hasAccess_isAdmin() {
    expectGuessRegistrarSuccess(
        AUTHORIZED_ADMIN,
        DEFAULT_CLIENT_ID,
        "Associating user {user} with found registrar {clientId}.");
  }

  /** If an admin doesn't have access to a registrar, we should guess the ADMIN_CLIENT_ID. */
  @Test
  public void testGuessClientIdForUser_noAccess_isAdmin() {
    expectGuessRegistrarSuccess(
        UNAUTHORIZED_ADMIN,
        ADMIN_CLIENT_ID,
        "User {user} is an admin with no associated registrar."
            + " Automatically associating the user with configured client Id {clientId}.");
  }

  /**
   * If an admin is not associated with a registrar and there is no configured adminClientId, we
   * can't guess the clientId.
   */
  @Test
  public void testGuessClientIdForUser_noAccess_isAdmin_adminClientIdEmpty() {
    sessionUtils.registryAdminClientId = "";
    expectGuessRegistrarFailure(
        UNAUTHORIZED_ADMIN, "User {user} isn't associated with any registrar");
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO,
            formatMessage(
                "Cannot associate admin user {user} with configured client Id."
                    + " ClientId is null or empty.",
                UNAUTHORIZED_ADMIN,
                null));
  }

  /**
   * If an admin is not associated with a registrar and the configured adminClientId points to a
   * non-existent registrar, we still guess it (we will later fail loading the registrar).
   */
  @Test
  public void testGuessClientIdForUser_noAccess_isAdmin_adminClientIdInvalid() {
    sessionUtils.registryAdminClientId = "NonexistentRegistrar";
    expectGuessRegistrarSuccess(
        UNAUTHORIZED_ADMIN,
        "NonexistentRegistrar",
        "User {user} is an admin with no associated registrar."
            + " Automatically associating the user with configured client Id {clientId}.");
  }

  private void expectGuessRegistrarSuccess(AuthResult authResult, String clientId, String message) {
    assertThat(sessionUtils.guessClientIdForUser(authResult)).isEqualTo(clientId);
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(Level.INFO, formatMessage(message, authResult, clientId));
  }

  private void expectGuessRegistrarFailure(AuthResult authResult, String message) {
    ForbiddenException exception =
        assertThrows(ForbiddenException.class, () -> sessionUtils.guessClientIdForUser(authResult));
    assertThat(exception)
        .hasMessageThat()
        .contains(formatMessage(message, authResult, null));
  }

  @Test
  public void testNullness() {
    new NullPointerTester()
        .setDefault(HttpServletRequest.class, req)
        .setDefault(HttpServletResponse.class, rsp)
        .testAllPublicStaticMethods(SessionUtils.class);
  }
}
