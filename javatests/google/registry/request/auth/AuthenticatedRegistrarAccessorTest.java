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

package google.registry.request.auth;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.request.auth.AuthenticatedRegistrarAccessor.Role.ADMIN;
import static google.registry.request.auth.AuthenticatedRegistrarAccessor.Role.OWNER;
import static google.registry.testing.AppEngineRule.THE_REGISTRAR_GAE_USER_ID;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.testing.LogsSubject.assertAboutLogs;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.flogger.LoggerConfig;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.TestLogHandler;
import google.registry.groups.GroupsConnection;
import google.registry.request.auth.AuthenticatedRegistrarAccessor.RegistrarAccessDeniedException;
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
  private final GroupsConnection groupsConnection = mock(GroupsConnection.class);
  private final TestLogHandler testLogHandler = new TestLogHandler();

  private static final AuthResult AUTHORIZED_USER = createAuthResult(true, false);
  private static final AuthResult UNAUTHORIZED_USER = createAuthResult(false, false);
  private static final AuthResult AUTHORIZED_ADMIN = createAuthResult(true, true);
  private static final AuthResult UNAUTHORIZED_ADMIN = createAuthResult(false, true);
  private static final AuthResult NO_USER = AuthResult.create(AuthLevel.NONE);
  private static final String SUPPORT_GROUP = "support@registry.example";
  private static final String DEFAULT_CLIENT_ID = "TheRegistrar";
  private static final String ADMIN_CLIENT_ID = "NewRegistrar";

  private static AuthResult createAuthResult(boolean isAuthorized, boolean isAdmin) {
    return AuthResult.create(
        AuthLevel.USER,
        UserAuthInfo.create(
            new User(
                String.format(
                    "%s_%s@gmail.com",
                    isAuthorized ? "auth" : "unauth", isAdmin ? "admin" : "user"),
                "gmail.com",
                isAuthorized ? THE_REGISTRAR_GAE_USER_ID : "badGaeUserId"),
            isAdmin));
  }

  @Before
  public void before() {
    LoggerConfig.getConfig(AuthenticatedRegistrarAccessor.class).addHandler(testLogHandler);
    persistResource(loadRegistrar(ADMIN_CLIENT_ID));
    when(groupsConnection.isMemberOfGroup(any(), any())).thenReturn(false);
  }

  @After
  public void after() {
    LoggerConfig.getConfig(AuthenticatedRegistrarAccessor.class).removeHandler(testLogHandler);
  }

  /** Users only have access to the registrars they are a contact for. */
  @Test
  public void getAllClientIdWithAccess_authorizedUser() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            AUTHORIZED_USER, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.getAllClientIdWithRoles())
        .containsExactly(DEFAULT_CLIENT_ID, OWNER);
  }

  /** Users in support group have admin access to everything. */
  @Test
  public void getAllClientIdWithAccess_authorizedUser_isSupportGroup() {
    when(groupsConnection.isMemberOfGroup("auth_user@gmail.com", SUPPORT_GROUP)).thenReturn(true);
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            AUTHORIZED_USER, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.getAllClientIdWithRoles())
        .containsExactly(
            DEFAULT_CLIENT_ID, OWNER,
            DEFAULT_CLIENT_ID, ADMIN,
            ADMIN_CLIENT_ID, ADMIN);
  }

  /** Logged out users don't have access to anything. */
  @Test
  public void getAllClientIdWithAccess_loggedOutUser() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            NO_USER, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.getAllClientIdWithRoles()).isEmpty();
  }

  /** Unauthorized users don't have access to anything. */
  @Test
  public void getAllClientIdWithAccess_unauthorizedUser() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            UNAUTHORIZED_USER, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.getAllClientIdWithRoles()).isEmpty();
  }

  /** Unauthorized users who are in support group have admin access. */
  @Test
  public void getAllClientIdWithAccess_unauthorizedUser_inSupportGroup() {
    when(groupsConnection.isMemberOfGroup("unauth_user@gmail.com", SUPPORT_GROUP)).thenReturn(true);
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            UNAUTHORIZED_USER, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.getAllClientIdWithRoles())
        .containsExactly(
            DEFAULT_CLIENT_ID, ADMIN,
            ADMIN_CLIENT_ID, ADMIN);
  }

  /** Empty Support group email - skips check. */
  @Test
  public void getAllClientIdWithAccess_emptySupportEmail_works() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(AUTHORIZED_USER, ADMIN_CLIENT_ID, "", groupsConnection);

    verifyNoMoreInteractions(groupsConnection);
    assertThat(registrarAccessor.getAllClientIdWithRoles())
        .containsExactly(DEFAULT_CLIENT_ID, OWNER);
  }

  /** Support group check throws - continue anyway. */
  @Test
  public void getAllClientIdWithAccess_throwingGroupCheck_stillWorks() {
    when(groupsConnection.isMemberOfGroup(any(), any())).thenThrow(new RuntimeException("blah"));
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            AUTHORIZED_USER, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    verify(groupsConnection).isMemberOfGroup("auth_user@gmail.com", SUPPORT_GROUP);
    assertThat(registrarAccessor.getAllClientIdWithRoles())
        .containsExactly(DEFAULT_CLIENT_ID, OWNER);
  }

  /** Admins have read/write access to the authorized registrars, AND the admin registrar. */
  @Test
  public void getAllClientIdWithAccess_authorizedAdmin() {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            AUTHORIZED_ADMIN, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

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
        new AuthenticatedRegistrarAccessor(
            UNAUTHORIZED_ADMIN, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

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
        "user unauth_user@gmail.com doesn't have access to registrar TheRegistrar");
  }

  /** Fail loading registrar if there's no user associated with the request. */
  @Test
  public void testGetRegistrarForUser_noUser() {
    expectGetRegistrarFailure(
        DEFAULT_CLIENT_ID,
        NO_USER,
        "<logged-out user> doesn't have access to registrar TheRegistrar");
  }

  /** Succeed loading registrar if user has access to it. */
  @Test
  public void testGetRegistrarForUser_hasAccess_isNotAdmin() throws Exception {
    expectGetRegistrarSuccess(
        AUTHORIZED_USER, "user auth_user@gmail.com has [OWNER] access to registrar TheRegistrar");
  }

  /** Succeed loading registrar if admin with access. */
  @Test
  public void testGetRegistrarForUser_hasAccess_isAdmin() throws Exception {
    expectGetRegistrarSuccess(
        AUTHORIZED_ADMIN,
        "admin auth_admin@gmail.com has [OWNER, ADMIN] access to registrar TheRegistrar");
  }

  /** Succeed loading registrar for admin even if they aren't on the approved contacts list. */
  @Test
  public void testGetRegistrarForUser_noAccess_isAdmin() throws Exception {
    expectGetRegistrarSuccess(
        UNAUTHORIZED_ADMIN,
        "admin unauth_admin@gmail.com has [ADMIN] access to registrar TheRegistrar.");
  }

  /** Fail loading registrar even if admin, if registrar doesn't exist. */
  @Test
  public void testGetRegistrarForUser_doesntExist_isAdmin() {
    expectGetRegistrarFailure(
        "BadClientId",
        AUTHORIZED_ADMIN,
        "admin auth_admin@gmail.com doesn't have access to registrar BadClientId");
  }

  private void expectGetRegistrarSuccess(AuthResult authResult, String message) throws Exception {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            authResult, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    // make sure loading the registrar succeeds and returns a value
    assertThat(registrarAccessor.getRegistrar(DEFAULT_CLIENT_ID)).isNotNull();
    assertAboutLogs().that(testLogHandler).hasLogAtLevelWithMessage(Level.INFO, message);
  }

  private void expectGetRegistrarFailure(
      String clientId, AuthResult authResult, String message) {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            authResult, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    // make sure getRegistrar fails
    RegistrarAccessDeniedException exception =
        assertThrows(
            RegistrarAccessDeniedException.class, () -> registrarAccessor.getRegistrar(clientId));

    assertThat(exception).hasMessageThat().contains(message);
  }

  /** If a user has access to a registrar, we should guess that registrar. */
  @Test
  public void testGuessClientIdForUser_hasAccess_isNotAdmin() throws Exception {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            AUTHORIZED_USER, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.guessClientId()).isEqualTo(DEFAULT_CLIENT_ID);
  }

  /** If a user doesn't have access to any registrars, guess returns nothing. */
  @Test
  public void testGuessClientIdForUser_noAccess_isNotAdmin() {
    expectGuessRegistrarFailure(
        UNAUTHORIZED_USER, "user unauth_user@gmail.com isn't associated with any registrar");
  }

  /**
   * If an admin has access to a registrar, we should guess that registrar (rather than the
   * ADMIN_CLIENT_ID).
   */
  @Test
  public void testGuessClientIdForUser_hasAccess_isAdmin() throws Exception {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            AUTHORIZED_ADMIN, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.guessClientId()).isEqualTo(DEFAULT_CLIENT_ID);
  }

  /** If an admin doesn't have access to a registrar, we should guess the ADMIN_CLIENT_ID. */
  @Test
  public void testGuessClientIdForUser_noAccess_isAdmin() throws Exception {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            UNAUTHORIZED_ADMIN, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.guessClientId()).isEqualTo(ADMIN_CLIENT_ID);
  }

  /**
   * If an admin is not associated with a registrar and there is no configured adminClientId, but
   * since it's an admin - we have read-only access to everything - return one of the existing
   * registrars.
   */
  @Test
  public void testGuessClientIdForUser_noAccess_isAdmin_adminClientIdEmpty() throws Exception {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(UNAUTHORIZED_ADMIN, "", SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.guessClientId()).isAnyOf(ADMIN_CLIENT_ID, DEFAULT_CLIENT_ID);
  }

  /**
   * If an admin is not associated with a registrar and the configured adminClientId points to a
   * non-existent registrar, we still guess it (we will later fail loading the registrar).
   */
  @Test
  public void testGuessClientIdForUser_noAccess_isAdmin_adminClientIdInvalid() throws Exception {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            UNAUTHORIZED_ADMIN, "NonexistentRegistrar", SUPPORT_GROUP, groupsConnection);

    assertThat(registrarAccessor.guessClientId()).isEqualTo("NonexistentRegistrar");
  }

  private void expectGuessRegistrarFailure(AuthResult authResult, String message) {
    AuthenticatedRegistrarAccessor registrarAccessor =
        new AuthenticatedRegistrarAccessor(
            authResult, ADMIN_CLIENT_ID, SUPPORT_GROUP, groupsConnection);

    RegistrarAccessDeniedException exception =
        assertThrows(RegistrarAccessDeniedException.class, () -> registrarAccessor.guessClientId());
    assertThat(exception).hasMessageThat().contains(message);
  }

  @Test
  public void testNullness() {
    new NullPointerTester()
        .setDefault(HttpServletRequest.class, req)
        .setDefault(HttpServletResponse.class, rsp)
        .testAllPublicStaticMethods(AuthenticatedRegistrarAccessor.class);
  }
}
