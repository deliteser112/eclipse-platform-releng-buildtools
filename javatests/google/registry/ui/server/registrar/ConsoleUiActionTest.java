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

import static com.google.common.net.HttpHeaders.LOCATION;
import static com.google.common.truth.Truth.assertThat;
import static javax.servlet.http.HttpServletResponse.SC_MOVED_TEMPORARILY;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.net.MediaType;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.security.XsrfTokenManager;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.UserInfo;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ConsoleUiAction}. */
@RunWith(JUnit4.class)
public class ConsoleUiActionTest {

  @Rule
  public final AppEngineRule appEngineRule = AppEngineRule.builder()
      .withDatastore()
      .withUserService(UserInfo.create("marla.singer@example.com", "12345"))
      .build();

  private final SessionUtils sessionUtils = mock(SessionUtils.class);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final FakeResponse response = new FakeResponse();
  private final ConsoleUiAction action = new ConsoleUiAction();
  private final User user = new User("marla.singer@example.com", "gmail.com", "12345");

  @Before
  public void setUp() throws Exception {
    action.enabled = true;
    action.logoFilename = "logo.png";
    action.productName = "Nomulus";
    action.integrationEmail = "integration@example.com";
    action.supportEmail = "support@example.com";
    action.announcementsEmail = "announcements@example.com";
    action.supportPhoneNumber = "1 (888) 555 0123";
    action.technicalDocsUrl = "http://example.com/technical-docs";
    action.req = request;
    action.response = response;
    action.sessionUtils = sessionUtils;
    action.userService = UserServiceFactory.getUserService();
    action.xsrfTokenManager = new XsrfTokenManager(new FakeClock(), action.userService);
    action.authResult = AuthResult.create(AuthLevel.USER, UserAuthInfo.create(user, false));
    when(sessionUtils.checkRegistrarConsoleLogin(request, user)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("TheRegistrar");
  }

  @Test
  public void testWebPage_disallowsIframe() throws Exception {
    action.run();
    assertThat(response.getHeaders()).containsEntry("X-Frame-Options", "SAMEORIGIN");
  }

  @Test
  public void testWebPage_setsHtmlUtf8ContentType() throws Exception {
    action.run();
    assertThat(response.getContentType()).isEqualTo(MediaType.HTML_UTF_8);
  }

  @Test
  public void testWebPage_containsUserNickname() throws Exception {
    action.run();
    assertThat(response.getPayload()).contains("marla.singer");
  }

  @Test
  public void testUserHasAccessAsTheRegistrar_showsRegistrarConsole() throws Exception {
    action.run();
    assertThat(response.getPayload()).contains("Registrar Console");
    assertThat(response.getPayload()).contains("reg-content-and-footer");
  }

  @Test
  public void testConsoleDisabled_showsDisabledPage() throws Exception {
    action.enabled = false;
    action.run();
    assertThat(response.getPayload()).contains("<h1>Console is disabled</h1>");
  }

  @Test
  public void testUserDoesntHaveAccessToAnyRegistrar_showsWhoAreYouPage() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(any(HttpServletRequest.class), any(User.class)))
        .thenReturn(false);
    action.run();
    assertThat(response.getPayload()).contains("<h1>You need permission</h1>");
  }

  @Test
  public void testNoUser_redirect() throws Exception {
    when(request.getRequestURI()).thenReturn("/test");
    action.authResult = AuthResult.NOT_AUTHENTICATED;
    action.run();
    assertThat(response.getStatus()).isEqualTo(SC_MOVED_TEMPORARILY);
    assertThat(response.getHeaders().get(LOCATION)).isEqualTo("/_ah/login?continue=%2Ftest");
  }

  @Test
  public void testNoUserInformationAtAll_redirectToRoot() throws Exception {
    when(request.getRequestURI()).thenThrow(new IllegalArgumentException());
    action.authResult = AuthResult.NOT_AUTHENTICATED;
    action.run();
    assertThat(response.getStatus()).isEqualTo(SC_MOVED_TEMPORARILY);
    assertThat(response.getHeaders().get(LOCATION)).isEqualTo("/");
  }
}
