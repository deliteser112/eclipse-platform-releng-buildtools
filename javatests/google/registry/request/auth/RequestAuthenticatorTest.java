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

package google.registry.request.auth;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.request.Action;
import google.registry.security.XsrfTokenManager;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeOAuthService;
import google.registry.testing.FakeUserService;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RequestAuthenticator}. */
@RunWith(JUnit4.class)
public class RequestAuthenticatorTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Action(
      path = "/auth/none",
      auth = @Auth(
          methods = {Auth.AuthMethod.INTERNAL},
          minimumLevel = AuthLevel.NONE,
          userPolicy = Auth.UserPolicy.IGNORED),
      method = Action.Method.GET)
  public static class AuthNone {}

  @Action(
      path = "/auth/internalOnly",
      auth = @Auth(minimumLevel = AuthLevel.APP),
      method = Action.Method.GET)
  public static class AuthInternalOnly {}

  @Action(
      path = "/auth/anyUserAnyMethod",
      auth = @Auth(
          methods = {Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API, Auth.AuthMethod.LEGACY},
          minimumLevel = AuthLevel.USER,
          userPolicy = Auth.UserPolicy.PUBLIC),
      method = Action.Method.GET)
  public static class AuthAnyUserAnyMethod {}

  @Action(
      path = "/auth/anyUserNoLegacy",
      auth = @Auth(
          methods = {Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API},
          minimumLevel = AuthLevel.USER,
          userPolicy = Auth.UserPolicy.PUBLIC),
      method = Action.Method.GET)
  public static class AuthAnyUserNoLegacy {}

  @Action(
      path = "/auth/adminUserAnyMethod",
      auth = @Auth(
          methods = {Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API, Auth.AuthMethod.LEGACY},
          minimumLevel = AuthLevel.USER,
          userPolicy = Auth.UserPolicy.ADMIN),
      method = Action.Method.GET)
  public static class AuthAdminUserAnyMethod {}

  @Action(
      path = "/auth/noMethods",
      auth = @Auth(methods = {}))
  public static class AuthNoMethods {}

  @Action(
      path = "/auth/missingInternal",
      auth = @Auth(methods = {Auth.AuthMethod.API, Auth.AuthMethod.LEGACY}))
  public static class AuthMissingInternal {}

  @Action(
      path = "/auth/wrongMethodOrdering",
      auth = @Auth(methods = {Auth.AuthMethod.API, Auth.AuthMethod.INTERNAL}))
  public static class AuthWrongMethodOrdering {}

  @Action(
      path = "/auth/duplicateMethods",
      auth = @Auth(methods = {Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API, Auth.AuthMethod.API}))
  public static class AuthDuplicateMethods {}

  @Action(
      path = "/auth/internalWithUser",
      auth = @Auth(methods = {Auth.AuthMethod.INTERNAL}, minimumLevel = AuthLevel.USER))
  public static class AuthInternalWithUser {}

  @Action(
      path = "/auth/wronglyIgnoringUser",
      auth = @Auth(
          methods = {Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API},
          userPolicy = Auth.UserPolicy.IGNORED))
  public static class AuthWronglyIgnoringUser {}

  private final UserService mockUserService = mock(UserService.class);
  private final HttpServletRequest req = mock(HttpServletRequest.class);

  private final User testUser = new User("test@google.com", "test@google.com");
  private final FakeUserService fakeUserService = new FakeUserService();
  private final XsrfTokenManager xsrfTokenManager =
      new XsrfTokenManager(new FakeClock(), fakeUserService);
  private final FakeOAuthService fakeOAuthService = new FakeOAuthService(
      false /* isOAuthEnabled */,
      testUser,
      false /* isUserAdmin */,
      "test-client-id",
      ImmutableList.of("test-scope1", "test-scope2", "nontest-scope"));

  @Before
  public void before() throws Exception {
    when(req.getMethod()).thenReturn("POST");
  }

  private RequestAuthenticator createRequestAuthenticator(UserService userService) {
    return new RequestAuthenticator(
        new AppEngineInternalAuthenticationMechanism(),
        ImmutableList.<AuthenticationMechanism>of(
            new OAuthAuthenticationMechanism(
                fakeOAuthService,
                ImmutableSet.of("test-scope1", "test-scope2", "test-scope3"),
                ImmutableSet.of("test-scope1", "test-scope2"),
                ImmutableSet.of("test-client-id", "other-test-client-id"))),
        new LegacyAuthenticationMechanism(userService, xsrfTokenManager));
  }

  private Optional<AuthResult> runTest(UserService userService, Class<?> clazz) {
    return createRequestAuthenticator(userService)
        .authorize(clazz.getAnnotation(Action.class).auth(), req);
  }

  @Test
  public void testNoAuthNeeded_noneFound() throws Exception {
    Optional<AuthResult> authResult = runTest(mockUserService, AuthNone.class);

    verifyZeroInteractions(mockUserService);
    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.NONE);
  }

  @Test
  public void testNoAuthNeeded_internalFound() throws Exception {
    when(req.getHeader("X-AppEngine-QueueName")).thenReturn("__cron");

    Optional<AuthResult> authResult = runTest(mockUserService, AuthNone.class);

    verifyZeroInteractions(mockUserService);
    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.APP);
    assertThat(authResult.get().userAuthInfo()).isAbsent();
  }

  @Test
  public void testInternalAuth_notInvokedInternally() throws Exception {
    Optional<AuthResult> authResult = runTest(mockUserService, AuthInternalOnly.class);

    verifyZeroInteractions(mockUserService);
    assertThat(authResult).isAbsent();
  }

  @Test
  public void testInternalAuth_success() throws Exception {
    when(req.getHeader("X-AppEngine-QueueName")).thenReturn("__cron");

    Optional<AuthResult> authResult = runTest(mockUserService, AuthInternalOnly.class);

    verifyZeroInteractions(mockUserService);
    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.APP);
    assertThat(authResult.get().userAuthInfo()).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_notLoggedIn() throws Exception {
    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserAnyMethod.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_xsrfFailure() throws Exception {
    fakeUserService.setUser(testUser, false);

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserAnyMethod.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_success() throws Exception {
    fakeUserService.setUser(testUser, false /* isAdmin */);
    when(req.getHeader(XsrfTokenManager.X_CSRF_TOKEN))
        .thenReturn(xsrfTokenManager.generateLegacyToken("console", testUser.getEmail()));

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserAnyMethod.class);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isFalse();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_xsrfNotRequiredForGet() throws Exception {
    fakeUserService.setUser(testUser, false);
    when(req.getMethod()).thenReturn("GET");

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserAnyMethod.class);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isAbsent();
  }

  @Test
  public void testAdminUserAnyMethod_notLoggedIn() throws Exception {
    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAdminUserAnyMethod.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAdminUserAnyMethod_notAdminUser() throws Exception {
    fakeUserService.setUser(testUser, false /* isAdmin */);

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAdminUserAnyMethod.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAdminUserAnyMethod_xsrfFailure() throws Exception {
    fakeUserService.setUser(testUser, true);

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAdminUserAnyMethod.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAdminUserAnyMethod_success() throws Exception {
    fakeUserService.setUser(testUser, true /* isAdmin */);
    when(req.getHeader(XsrfTokenManager.X_CSRF_TOKEN))
        .thenReturn(xsrfTokenManager.generateLegacyToken("console", testUser.getEmail()));

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAdminUserAnyMethod.class);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isTrue();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isAbsent();
  }

  @Test
  public void testOAuth_success() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserNoLegacy.class);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isFalse();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().authorizedScopes())
      .containsAllOf("test-scope1", "test-scope2");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().oauthClientId())
      .isEqualTo("test-client-id");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().rawAccessToken())
      .isEqualTo("TOKEN");
  }

  @Test
  public void testOAuthAdmin_success() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setUserAdmin(true);
    fakeOAuthService.setOAuthEnabled(true);
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserNoLegacy.class);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isTrue();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().authorizedScopes())
      .containsAllOf("test-scope1", "test-scope2");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().oauthClientId())
      .isEqualTo("test-client-id");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().rawAccessToken())
      .isEqualTo("TOKEN");
  }

  @Test
  public void testOAuthMissingAuthenticationToken_failure() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserNoLegacy.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testOAuthClientIdMismatch_failure() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    fakeOAuthService.setClientId("wrong-client-id");
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserNoLegacy.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testOAuthNoScopes_failure() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    fakeOAuthService.setAuthorizedScopes();
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserNoLegacy.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testOAuthMissingScope_failure() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    fakeOAuthService.setAuthorizedScopes("test-scope1", "test-scope3");
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserNoLegacy.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testOAuthExtraScope_success() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    fakeOAuthService.setAuthorizedScopes("test-scope1", "test-scope2", "test-scope3");
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserNoLegacy.class);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isFalse();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().authorizedScopes())
      .containsAllOf("test-scope1", "test-scope2", "test-scope3");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().oauthClientId())
      .isEqualTo("test-client-id");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().rawAccessToken())
      .isEqualTo("TOKEN");
  }

  @Test
  public void testAnyUserNoLegacy_failureWithLegacyUser() throws Exception {
    fakeUserService.setUser(testUser, false /* isAdmin */);

    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserNoLegacy.class);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testNoMethods_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Must specify at least one auth method");

    runTest(fakeUserService, AuthNoMethods.class);
  }

  @Test
  public void testMissingInternal_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Auth method INTERNAL must always be specified, and as the first auth method");

    runTest(fakeUserService, AuthMissingInternal.class);
  }

  @Test
  public void testWrongMethodOrdering_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Auth methods must be unique and strictly in order - INTERNAL, API, LEGACY");

    runTest(fakeUserService, AuthWrongMethodOrdering.class);
  }

  @Test
  public void testDuplicateMethods_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Auth methods must be unique and strictly in order - INTERNAL, API, LEGACY");

    runTest(fakeUserService, AuthDuplicateMethods.class);
  }

  @Test
  public void testInternalWithUser_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Actions with only INTERNAL auth may not require USER auth level");

    runTest(fakeUserService, AuthInternalWithUser.class);
  }

  @Test
  public void testWronglyIgnoringUser_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Actions with auth methods beyond INTERNAL must not specify the IGNORED user policy");

    runTest(fakeUserService, AuthWronglyIgnoringUser.class);
  }
}
