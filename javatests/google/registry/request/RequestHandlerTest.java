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

package google.registry.request;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.POST;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.testing.NullPointerTester;
import google.registry.request.HttpException.ServiceUnavailableException;
import google.registry.request.auth.AppEngineInternalAuthenticationMechanism;
import google.registry.request.auth.Auth;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.AuthenticationMechanism;
import google.registry.request.auth.LegacyAuthenticationMechanism;
import google.registry.request.auth.OAuthAuthenticationMechanism;
import google.registry.request.auth.RequestAuthenticator;
import google.registry.security.XsrfTokenManager;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeUserService;
import google.registry.testing.Providers;
import google.registry.testing.UserInfo;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link RequestHandler}. */
@RunWith(MockitoJUnitRunner.class)
public final class RequestHandlerTest {

  @Rule
  public final AppEngineRule appEngine =
      AppEngineRule.builder()
          .withDatastore()
          .withUserService(UserInfo.create("test@example.com", "test@example.com"))
          .build();

  @Action(path = "/bumblebee", method = {GET, POST}, isPrefix = true)
  public static class BumblebeeTask implements Runnable {
    @Override
    public void run() {}
  }

  @Action(path = "/sloth", method = POST, automaticallyPrintOk = true)
  public static class SlothTask implements Runnable {
    @Override
    public void run() {}
  }

  @Action(path = "/safe-sloth", method = {GET, POST}, xsrfProtection = true, xsrfScope = "vampire")
  public static class SafeSlothTask implements Runnable {
    @Override
    public void run() {}
  }

  @Action(path = "/users-only", method = GET, requireLogin = true)
  public static class UsersOnlyAction implements Runnable {
    @Override
    public void run() {}
  }

  @Action(path = "/fail")
  public static final class FailTask implements Runnable {
    @Override
    public void run() {
      throw new ServiceUnavailableException("Set sail for fail");
    }
  }

  @Action(path = "/failAtConstruction")
  public static final class FailAtConstructionTask implements Runnable {
    public FailAtConstructionTask() {
      throw new ServiceUnavailableException("Fail at construction");
    }

    @Override
    public void run() {
      throw new AssertionError("should not get here");
    }
  }

  class AuthBase implements Runnable {
    private final AuthResult authResult;

    AuthBase(AuthResult authResult) {
      this.authResult = authResult;
    }

    @Override
    public void run() {
      providedAuthResult = authResult;
    }
  }

  @Action(
      path = "/auth/none",
      auth = @Auth(
          methods = {Auth.AuthMethod.INTERNAL},
          minimumLevel = AuthLevel.NONE,
          userPolicy = Auth.UserPolicy.IGNORED),
      method = Action.Method.GET)
  public class AuthNoneAction extends AuthBase {
    AuthNoneAction(AuthResult authResult) {
      super(authResult);
    }
  }

  @Action(
      path = "/auth/adminUserAnyMethod",
      auth = @Auth(
          methods = {Auth.AuthMethod.INTERNAL, Auth.AuthMethod.API, Auth.AuthMethod.LEGACY},
          minimumLevel = AuthLevel.USER,
          userPolicy = Auth.UserPolicy.ADMIN),
      method = Action.Method.GET)
  public class AuthAdminUserAnyMethodAction extends AuthBase {
    AuthAdminUserAnyMethodAction(AuthResult authResult) {
      super(authResult);
    }
  }

  public class Component {

    private RequestModule requestModule = null;

    public RequestModule getRequestModule() {
      return requestModule;
    }

    public void setRequestModule(RequestModule requestModule) {
      this.requestModule = requestModule;
    }

    public BumblebeeTask bumblebeeTask() {
      return bumblebeeTask;
    }

    public SlothTask slothTask() {
      return slothTask;
    }

    public SafeSlothTask safeSlothTask() {
      return safeSlothTask;
    }

    public UsersOnlyAction usersOnlyAction() {
      return usersOnlyAction;
    }

    public FailTask failTask() {
      return new FailTask();
    }

    public FailAtConstructionTask failAtConstructionTask() {
      return new FailAtConstructionTask();
    }

    public AuthNoneAction authNoneAction() {
      return new AuthNoneAction(component.getRequestModule().provideAuthResult());
    }

    public AuthAdminUserAnyMethodAction authAdminUserAnyMethodAction() {
      return new AuthAdminUserAnyMethodAction(component.getRequestModule().provideAuthResult());
    }
  }

  /** Fake Builder for the fake component above to satisfy RequestHandler expectations. */
  public abstract class Builder implements RequestComponentBuilder<Component> {
    @Override
    public Builder requestModule(RequestModule requestModule) {
      component.setRequestModule(requestModule);
      return this;
    }
  }

  @Mock
  private HttpServletRequest req;

  @Mock
  private HttpServletResponse rsp;

  @Mock
  private BumblebeeTask bumblebeeTask;

  @Mock
  private SlothTask slothTask;

  @Mock
  private UsersOnlyAction usersOnlyAction;

  @Mock
  private SafeSlothTask safeSlothTask;

  private final Component component = new Component();
  private final StringWriter httpOutput = new StringWriter();
  private RequestHandler<Component> handler;
  private AuthResult providedAuthResult = null;
  private final User testUser = new User("test@example.com", "test@example.com");
  private RequestAuthenticator requestAuthenticator;
  private XsrfTokenManager xsrfTokenManager;
  private FakeUserService userService;

  @Before
  public void before() throws Exception {
    userService = new FakeUserService();
    xsrfTokenManager = new XsrfTokenManager(new FakeClock(), userService);
    requestAuthenticator = new RequestAuthenticator(
        new AppEngineInternalAuthenticationMechanism(),
        ImmutableList.<AuthenticationMechanism>of(
            new OAuthAuthenticationMechanism(
                OAuthServiceFactory.getOAuthService(),
                ImmutableSet.of("https://www.googleapis.com/auth/userinfo.email"),
                ImmutableSet.of("https://www.googleapis.com/auth/userinfo.email"),
                ImmutableSet.of("proxy-client-id", "regtool-client-id"))),
        new LegacyAuthenticationMechanism(userService, xsrfTokenManager));

    // Initialize here, not inline, so that we pick up the mocked UserService.
    handler = RequestHandler.<Component>createForTest(
        Component.class,
        Providers.<Builder>of(new Builder() {
          @Override
          public Component build() {
            // Use a fake Builder that returns the single component instance that uses the mocks.
            return component;
          }
        }),
        userService,
        requestAuthenticator,
        xsrfTokenManager);
    when(rsp.getWriter()).thenReturn(new PrintWriter(httpOutput));
  }

  @After
  public void after() throws Exception {
    verifyNoMoreInteractions(rsp, bumblebeeTask, slothTask, safeSlothTask);
  }

  @Test
  public void testHandleRequest_normalRequest_works() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/bumblebee");
    handler.handleRequest(req, rsp);
    verifyZeroInteractions(rsp);
    verify(bumblebeeTask).run();
  }

  @Test
  public void testHandleRequest_multipleMethodMappings_works() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/bumblebee");
    handler.handleRequest(req, rsp);
    verify(bumblebeeTask).run();
  }

  @Test
  public void testHandleRequest_prefixEnabled_subpathsWork() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/bumblebee/hive");
    handler.handleRequest(req, rsp);
    verify(bumblebeeTask).run();
  }

  @Test
  public void testHandleRequest_taskHasAutoPrintOk_printsOk() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/sloth");
    handler.handleRequest(req, rsp);
    verify(slothTask).run();
    verify(rsp).setContentType("text/plain; charset=utf-8");
    verify(rsp).getWriter();
    assertThat(httpOutput.toString()).isEqualTo("OK\n");
  }

  @Test
  public void testHandleRequest_prefixDisabled_subpathsReturn404NotFound() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/sloth/nest");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(404);
  }

  @Test
  public void testHandleRequest_taskThrowsHttpException_getsHandledByHandler() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/fail");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(503, "Set sail for fail");
  }

  /** Test for a regression of the issue in b/21377705. */
  @Test
  public void testHandleRequest_taskThrowsHttpException_atConstructionTime_getsHandledByHandler()
      throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/failAtConstruction");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(503, "Fail at construction");
  }

  @Test
  public void testHandleRequest_notFound_returns404NotFound() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/bogus");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(404);
  }

  @Test
  public void testHandleRequest_methodNotAllowed_returns405MethodNotAllowed() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/fail");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(405);
  }

  @Test
  public void testHandleRequest_insaneMethod_returns405MethodNotAllowed() throws Exception {
    when(req.getMethod()).thenReturn("FIREAWAY");
    when(req.getRequestURI()).thenReturn("/fail");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(405);
  }

  /** @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html#sec5.1.1">
   *     RFC2616 - HTTP/1.1 - Method</a> */
  @Test
  public void testHandleRequest_lowercaseMethod_notRecognized() throws Exception {
    when(req.getMethod()).thenReturn("get");
    when(req.getRequestURI()).thenReturn("/bumblebee");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(405);
  }

  @Test
  public void testNullness() {
    NullPointerTester tester = new NullPointerTester();
    tester.setDefault(Class.class, Component.class);
    tester.setDefault(RequestAuthenticator.class, requestAuthenticator);
    tester.setDefault(XsrfTokenManager.class, xsrfTokenManager);
    tester.testAllPublicStaticMethods(RequestHandler.class);
    tester.testAllPublicInstanceMethods(handler);
  }

  @Test
  public void testXsrfProtection_noTokenProvided_returns403Forbidden() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/safe-sloth");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(403, "Invalid X-CSRF-Token");
  }

  @Test
  public void testXsrfProtection_validTokenProvided_runsAction() throws Exception {
    userService.setUser(testUser,  false);
    when(req.getMethod()).thenReturn("POST");
    when(req.getHeader("X-CSRF-Token"))
        .thenReturn(xsrfTokenManager.generateLegacyToken("admin", testUser.getEmail()));
    when(req.getRequestURI()).thenReturn("/safe-sloth");
    handler.handleRequest(req, rsp);
    verify(safeSlothTask).run();
  }

  @Test
  public void testXsrfProtection_tokenWithInvalidUserProvided_returns403() throws Exception {
    userService.setUser(testUser,  false);
    when(req.getMethod()).thenReturn("POST");
    when(req.getHeader("X-CSRF-Token"))
        .thenReturn(xsrfTokenManager.generateLegacyToken("admin", "wrong@example.com"));
    when(req.getRequestURI()).thenReturn("/safe-sloth");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(403, "Invalid X-CSRF-Token");
  }

  @Test
  public void testXsrfProtection_GETMethodWithoutToken_doesntCheckToken() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/safe-sloth");
    handler.handleRequest(req, rsp);
    verify(safeSlothTask).run();
  }

  @Test
  public void testMustBeLoggedIn_notLoggedIn_redirectsToLoginPage() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/users-only");
    handler.handleRequest(req, rsp);
    verify(rsp).setStatus(302);
    verify(rsp).setHeader("Location", "/login?dest=/users-only");
  }

  @Test
  public void testMustBeLoggedIn_loggedIn_runsAction() throws Exception {
    userService.setUser(testUser,  false);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/users-only");
    handler.handleRequest(req, rsp);
    verify(usersOnlyAction).run();
  }

  @Test
  public void testNoAuthNeeded_success() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/auth/none");
    handler.handleRequest(req, rsp);
    assertThat(providedAuthResult).isNotNull();
    assertThat(providedAuthResult.authLevel()).isEqualTo(AuthLevel.NONE);
    assertThat(providedAuthResult.userAuthInfo()).isAbsent();
  }

  @Test
  public void testAuthNeeded_notLoggedIn() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/auth/adminUserAnyMethod");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(403);
    assertThat(providedAuthResult).isNull();
  }

  @Test
  public void testAuthNeeded_notAuthorized() throws Exception {
    userService.setUser(testUser,  false);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/auth/adminUserAnyMethod");
    handler.handleRequest(req, rsp);
    verify(rsp).sendError(403);
    assertThat(providedAuthResult).isNull();
  }

  @Test
  public void testAuthNeeded_success() throws Exception {
    userService.setUser(testUser,  true);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/auth/adminUserAnyMethod");
    handler.handleRequest(req, rsp);
    assertThat(providedAuthResult).isNotNull();
    assertThat(providedAuthResult.authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(providedAuthResult.userAuthInfo()).isPresent();
    assertThat(providedAuthResult.userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(providedAuthResult.userAuthInfo().get().oauthTokenInfo()).isAbsent();
  }
}
