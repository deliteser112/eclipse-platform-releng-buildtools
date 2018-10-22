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

package google.registry.ui.server.registrar;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.AppEngineRule.THE_REGISTRAR_GAE_USER_ID;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.testing.LogsSubject.assertAboutLogs;
import static google.registry.ui.server.registrar.AuthenticatedRegistrarAccessor.Role.ADMIN;
import static google.registry.ui.server.registrar.AuthenticatedRegistrarAccessor.Role.OWNER;
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
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AuthenticatedRegistrarAccessor}. */
@RunWith(JUnit4.class)
public class AuthenticatedRegistrarAccessorTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();
  @Rule public final InjectRule inject = new InjectRule();

  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse rsp = mock(HttpServletResponse.class);
  private final TestLogHandler testLogHandler = new TestLogHandler();

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
    LoggerConfig.getConfig(AuthenticatedRegistrarAccessor.class).addHandler(testLogHandler);
    persistResource(loadRegistrar(ADMIN_CLIENT_ID));
  }

  @After
  public void after() {
    LoggerConfig.getConfig(AuthenticatedRegistrarAccessor.class).removeHandler(testLogHandler);
  }

  private String formatMessage(String message, AuthResult authResult, String clientId) {
    return message
        .replace("{user}", authResult.userIdForLogging())
        .replace("{clientId}", String.valueOf(clientId));
  }

  /** Users only have access to the registrars they are a contact for. */
  @Test
  public void getAllClientIdWithAccess_authorizedUser() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(AUTHORIZED_USER, ADMIN_CLIENT_ID);

    assertThat(registrarAccessor.getAllClientIdWithRoles())
        .containsExactly(DEFAULT_CLIENT_ID, OWNER);
  }

  /** Logged out users don't have access to anything. */
  @Test
  public void getAllClientIdWithAccess_loggedOutUser() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(NO_USER, ADMIN_CLIENT_ID);

    assertThat(registrarAccessor.getAllClientIdWithRoles()).isEmpty();
  }

  /** Unauthorized users don't have access to anything. */
  @Test
  public void getAllClientIdWithAccess_unauthorizedUser() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(UNAUTHORIZED_USER, ADMIN_CLIENT_ID);

    assertThat(registrarAccessor.getAllClientIdWithRoles()).isEmpty();
  }

  /** Admins have read/write access to the authorized registrars, AND the admin registrar. */
  @Test
  public void getAllClientIdWithAccess_authorizedAdmin() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(AUTHORIZED_ADMIN, ADMIN_CLIENT_ID);

    assertThat(registrarAccessor.getAllClientIdWithRoles())
        .containsExactly(
            DEFAULT_CLIENT_ID, OWNER,
            DEFAULT_CLIENT_ID, ADMIN,
            ADMIN_CLIENT_ID, OWNER,
            ADMIN_CLIENT_ID, ADMIN)
        .inOrder();
  }

  /**
   * Unauthorized admins only have full access to the admin registrar, and read-only to the rest.
   */
  @Test
  public void getAllClientIdWithAccess_unauthorizedAdmin() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(UNAUTHORIZED_ADMIN, ADMIN_CLIENT_ID);

    assertThat(registrarAccessor.getAllClientIdWithRoles())
        .containsExactly(
            ADMIN_CLIENT_ID, OWNER,
            ADMIN_CLIENT_ID, ADMIN,
            DEFAULT_CLIENT_ID, ADMIN)
        .inOrder();
  }

  /** Fail loading registrar if user doesn't have access to it. */
  @Test
  public void testGetRegistrarForUser_noAccess_isNotAdmin() {
    expectGetRegistrarFailure(
        DEFAULT_CLIENT_ID,
        UNAUTHORIZED_USER,
        "{user} doesn't have access to registrar {clientId}");
  }

  /** Fail loading registrar if there's no user associated with the request. */
  @Test
  public void testGetRegistrarForUser_noUser() {
    expectGetRegistrarFailure(DEFAULT_CLIENT_ID, NO_USER, "Not logged in");
  }

  /** Succeed loading registrar if user has access to it. */
  @Test
  public void testGetRegistrarForUser_hasAccess_isNotAdmin() {
    expectGetRegistrarSuccess(
        AUTHORIZED_USER, "{user} has [OWNER] access to registrar {clientId}");
  }

  /** Succeed loading registrar if admin with access. */
  @Test
  public void testGetRegistrarForUser_hasAccess_isAdmin() {
    expectGetRegistrarSuccess(
        AUTHORIZED_ADMIN, "{user} has [OWNER, ADMIN] access to registrar {clientId}");
  }

  /** Succeed loading registrar for admin even if they aren't on the approved contacts list. */
  @Test
  public void testGetRegistrarForUser_noAccess_isAdmin() {
    expectGetRegistrarSuccess(
        UNAUTHORIZED_ADMIN, "{user} has [ADMIN] access to registrar {clientId}.");
  }

  /** Fail loading registrar even if admin, if registrar doesn't exist. */
  @Test
  public void testGetRegistrarForUser_doesntExist_isAdmin() {
    expectGetRegistrarFailure(
        "BadClientId",
        AUTHORIZED_ADMIN,
        "{user} doesn't have access to registrar {clientId}");
  }

  private void expectGetRegistrarSuccess(
      AuthResult authResult, String message) {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(authResult, ADMIN_CLIENT_ID);

    assertThat(registrarAccessor.getRegistrar(DEFAULT_CLIENT_ID)).isNotNull();
    assertAboutLogs()
        .that(testLogHandler)
        .hasLogAtLevelWithMessage(
            Level.INFO, formatMessage(message, authResult, DEFAULT_CLIENT_ID));
  }

  private void expectGetRegistrarFailure(
      String clientId, AuthResult authResult, String message) {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(authResult, ADMIN_CLIENT_ID);

    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class, () -> registrarAccessor.getRegistrar(clientId));

    assertThat(exception).hasMessageThat().contains(formatMessage(message, authResult, clientId));
  }

  /** If a user has access to a registrar, we should guess that registrar. */
  @Test
  public void testGuessClientIdForUser_hasAccess_isNotAdmin() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(AUTHORIZED_USER, ADMIN_CLIENT_ID);

    assertThat(registrarAccessor.guessClientId()).isEqualTo(DEFAULT_CLIENT_ID);
  }

  /** If a user doesn't have access to any registrars, guess returns nothing. */
  @Test
  public void testGuessClientIdForUser_noAccess_isNotAdmin() {
    expectGuessRegistrarFailure(UNAUTHORIZED_USER, "{user} isn't associated with any registrar");
  }

  /**
   * If an admin has access to a registrar, we should guess that registrar (rather than the
   * ADMIN_CLIENT_ID).
   */
  @Test
  public void testGuessClientIdForUser_hasAccess_isAdmin() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(AUTHORIZED_ADMIN, ADMIN_CLIENT_ID);

    assertThat(registrarAccessor.guessClientId()).isEqualTo(DEFAULT_CLIENT_ID);
  }

  /** If an admin doesn't have access to a registrar, we should guess the ADMIN_CLIENT_ID. */
  @Test
  public void testGuessClientIdForUser_noAccess_isAdmin() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(UNAUTHORIZED_ADMIN, ADMIN_CLIENT_ID);

    assertThat(registrarAccessor.guessClientId()).isEqualTo(ADMIN_CLIENT_ID);
  }

  /**
   * If an admin is not associated with a registrar and there is no configured adminClientId, but
   * since it's an admin - we have read-only access to everything - return one of the existing
   * registrars.
   */
  @Test
  public void testGuessClientIdForUser_noAccess_isAdmin_adminClientIdEmpty() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(UNAUTHORIZED_ADMIN, "");

    assertThat(registrarAccessor.guessClientId()).isAnyOf(ADMIN_CLIENT_ID, DEFAULT_CLIENT_ID);
  }

  /**
   * If an admin is not associated with a registrar and the configured adminClientId points to a
   * non-existent registrar, we still guess it (we will later fail loading the registrar).
   */
  @Test
  public void testGuessClientIdForUser_noAccess_isAdmin_adminClientIdInvalid() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(UNAUTHORIZED_ADMIN, "NonexistentRegistrar");

    assertThat(registrarAccessor.guessClientId()).isEqualTo("NonexistentRegistrar");
  }

  private void expectGuessRegistrarFailure(AuthResult authResult, String message) {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(authResult, ADMIN_CLIENT_ID);

    ForbiddenException exception =
        assertThrows(ForbiddenException.class, () -> registrarAccessor.guessClientId());
    assertThat(exception)
        .hasMessageThat()
        .contains(formatMessage(message, authResult, null));
  }

  @Test
  public void testNullness() {
    new NullPointerTester()
        .setDefault(HttpServletRequest.class, req)
        .setDefault(HttpServletResponse.class, rsp)
        .testAllPublicStaticMethods(AuthenticatedRegistrarAccessor.class);
  }
}
