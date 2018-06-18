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

package google.registry.flows;

import static com.google.appengine.api.users.UserServiceFactory.getUserService;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registrar.RegistrarContact;
import google.registry.testing.AppEngineRule;
import google.registry.testing.UserInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test logging in with appengine user credentials, such as via the console. */
@RunWith(JUnit4.class)
public class EppLoginUserTest extends EppTestCase {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withUserService(UserInfo.create("user@example.com", "12345"))
      .build();

  @Before
  public void initTest() {
    User user = getUserService().getCurrentUser();
    persistResource(
        new RegistrarContact.Builder()
            .setParent(loadRegistrar("NewRegistrar"))
            .setEmailAddress(user.getEmail())
            .setGaeUserId(user.getUserId())
            .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
            .build());
    setTransportCredentials(GaeUserCredentials.forCurrentUser(getUserService()));
  }

  @Test
  public void testLoginLogout() throws Exception {
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    assertThatLogoutSucceeds();
  }

  @Test
  public void testNonAuthedLogin_fails() throws Exception {
    assertThatLogin("TheRegistrar", "password2")
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of(
                "CODE", "2200",
                "MSG", "User id is not allowed to login as requested registrar: user@example.com"));
  }

  @Test
  public void testMultiLogin() throws Exception {
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    assertThatLogoutSucceeds();
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    assertThatLogoutSucceeds();
    assertThatLogin("TheRegistrar", "password2")
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of(
                "CODE", "2200",
                "MSG", "User id is not allowed to login as requested registrar: user@example.com"));
  }

  @Test
  public void testLoginLogout_wrongPasswordStillWorks() throws Exception {
    // For user-based logins the password in the epp xml is ignored.
    assertThatLoginSucceeds("NewRegistrar", "incorrect");
    assertThatLogoutSucceeds();
  }
}
