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

import google.registry.testing.AppEngineRule;
import google.registry.testing.UserInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test logging in with appengine admin user credentials. */
@RunWith(JUnit4.class)
public class EppLoginAdminUserTest extends EppTestCase {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withUserService(UserInfo.createAdmin("someone@example.com", "12345"))
      .build();

  @Before
  public void initTransportCredentials() {
    setTransportCredentials(GaeUserCredentials.forCurrentUser(getUserService()));
  }

  @Test
  public void testNonAuthedLogin_succeedsAsAdmin() throws Exception {
    // Login succeeds even though this user isn't listed on the registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
  }

  @Test
  public void testLoginLogout_wrongPasswordStillWorks() throws Exception {
    // For user-based logins the password in the epp xml is ignored.
    assertCommandAndResponse("login_invalid_wrong_password.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testNonAuthedMultiLogin_succeedsAsAdmin() throws Exception {
    // The admin can log in as different registrars.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
  }
}
