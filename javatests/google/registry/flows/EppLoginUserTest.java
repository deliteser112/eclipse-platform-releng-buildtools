// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registrar.Registrar;
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
      .withUserService(UserInfo.create("person@example.com", "12345"))
      .build();

  @Before
  public void initTest() throws Exception {
    User user = getUserService().getCurrentUser();
    persistResource(new RegistrarContact.Builder()
        .setParent(Registrar.loadByClientId("NewRegistrar"))
        .setEmailAddress(user.getEmail())
        .setGaeUserId(user.getUserId())
        .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
        .build());
    setTransportCredentials(GaeUserCredentials.forCurrentUser(getUserService()));
  }

  @Test
  public void testLoginLogout() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testNonAuthedLogin_fails() throws Exception {
    assertCommandAndResponse("login2_valid.xml", "login_response_unauthorized_role.xml");
  }

  @Test
  public void testMultiLogin() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    assertCommandAndResponse("login2_valid.xml", "login_response_unauthorized_role.xml");
  }

  @Test
  public void testLoginLogout_wrongPasswordStillWorks() throws Exception {
    // For user-based logins the password in the epp xml is ignored.
    assertCommandAndResponse("login_invalid_wrong_password.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
