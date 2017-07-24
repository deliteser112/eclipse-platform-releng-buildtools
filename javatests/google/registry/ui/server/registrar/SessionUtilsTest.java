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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.testing.NullPointerTester;
import google.registry.model.registrar.RegistrarContact;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.InjectRule;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SessionUtils}. */
@RunWith(JUnit4.class)
public class SessionUtilsTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse rsp = mock(HttpServletResponse.class);
  private final HttpSession session = mock(HttpSession.class);

  private SessionUtils sessionUtils;
  private final User jart = new User("jart@google.com", "google.com", THE_REGISTRAR_GAE_USER_ID);
  private final User bozo = new User("bozo@bing.com", "bing.com", "badGaeUserId");

  @Before
  public void before() throws Exception {
    sessionUtils = new SessionUtils();
    when(req.getSession()).thenReturn(session);
  }

  @Test
  public void testCheckRegistrarConsoleLogin_authedButNoSession_createsSession() throws Exception {
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, jart)).isTrue();
    verify(session).setAttribute(eq("clientId"), eq("TheRegistrar"));
  }

  @Test
  public void testCheckRegistrarConsoleLogin_authedWithValidSession_doesNothing() throws Exception {
    when(session.getAttribute("clientId")).thenReturn("TheRegistrar");
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, jart)).isTrue();
    verify(session).getAttribute("clientId");
    verifyNoMoreInteractions(session);
  }

  @Test
  public void testCheckRegistrarConsoleLogin_sessionRevoked_invalidates() throws Exception {
    RegistrarContact.updateContacts(
        loadRegistrar("TheRegistrar"), new java.util.HashSet<RegistrarContact>());
    when(session.getAttribute("clientId")).thenReturn("TheRegistrar");
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, jart)).isFalse();
    verify(session).invalidate();
  }

  @Test
  public void testCheckRegistrarConsoleLogin_orphanedContactIsDenied() throws Exception {
    deleteResource(loadRegistrar("TheRegistrar"));
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, jart)).isFalse();
  }

  @Test
  public void testCheckRegistrarConsoleLogin_notLoggedIn_throwsIllegalStateException()
      throws Exception {
    thrown.expect(IllegalStateException.class);
    @SuppressWarnings("unused")
    boolean unused = sessionUtils.checkRegistrarConsoleLogin(req, null);
  }

  @Test
  public void testCheckRegistrarConsoleLogin_notAllowed_returnsFalse() throws Exception {
    assertThat(sessionUtils.checkRegistrarConsoleLogin(req, bozo)).isFalse();
  }

  @Test
  public void testNullness() throws Exception {
    new NullPointerTester()
        .setDefault(HttpServletRequest.class, req)
        .setDefault(HttpServletResponse.class, rsp)
        .testAllPublicStaticMethods(SessionUtils.class);
  }
}
