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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.request.Action;
import google.registry.testing.FakeUserService;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link RequestAuthenticator}. */
@RunWith(MockitoJUnitRunner.class)
public class RequestAuthenticatorTest {

  private FakeUserService fakeUserService;

  @Mock
  private UserService mockUserService;

  @Mock
  private HttpServletRequest req;

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
      path = "/auth/adminUserAnyMethod",
      auth = @Auth(
          methods = {Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API, Auth.AuthMethod.LEGACY},
          minimumLevel = AuthLevel.USER,
          userPolicy = Auth.UserPolicy.ADMIN),
      method = Action.Method.GET)
  public static class AuthAdminUserAnyMethod {
  }

  private final User testUser = new User("test@example.com", "test@example.com");

  @Before
  public void before() throws Exception {
    fakeUserService = new FakeUserService();
  }

  private static RequestAuthenticator createRequestAuthenticator(UserService userService) {
    return new RequestAuthenticator(
        new AppEngineInternalAuthenticationMechanism(),
        ImmutableList.<AuthenticationMechanism>of(
            new OAuthAuthenticationMechanism(
                OAuthServiceFactory.getOAuthService(),
                ImmutableSet.of("https://www.googleapis.com/auth/userinfo.email"),
                ImmutableSet.of("https://www.googleapis.com/auth/userinfo.email"),
                ImmutableSet.of("proxy-client-id", "regtool-client-id"))),
        new LegacyAuthenticationMechanism(userService));
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
    assertThat(authResult.get()).isNotNull();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.NONE);
  }

  @Test
  public void testNoAuthNeeded_internalFound() throws Exception {
    when(req.getHeader("X-AppEngine-QueueName")).thenReturn("__cron");
    Optional<AuthResult> authResult = runTest(mockUserService, AuthNone.class);
    verifyZeroInteractions(mockUserService);
    assertThat(authResult).isPresent();
    assertThat(authResult.get()).isNotNull();
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
    assertThat(authResult.get()).isNotNull();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.APP);
    assertThat(authResult.get().userAuthInfo()).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_notLoggedIn() throws Exception {
    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserAnyMethod.class);
    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_success() throws Exception {
    fakeUserService.setUser(testUser, false);
    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAnyUserAnyMethod.class);
    assertThat(authResult).isPresent();
    assertThat(authResult.get()).isNotNull();
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
    fakeUserService.setUser(testUser, false);
    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAdminUserAnyMethod.class);
    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAdminUserAnyMethod_success() throws Exception {
    fakeUserService.setUser(testUser, true);
    Optional<AuthResult> authResult = runTest(fakeUserService, AuthAdminUserAnyMethod.class);
    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isAbsent();
  }

  // TODO(mountford) Add more tests for OAuth, misconfiguration, etc., either here or separately
}
