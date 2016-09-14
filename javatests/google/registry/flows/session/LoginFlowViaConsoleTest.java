// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.flows.session;


import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableSet;
import google.registry.flows.GaeUserCredentials;
import google.registry.flows.GaeUserCredentials.BadGaeUserIdException;
import google.registry.flows.GaeUserCredentials.UserNotLoggedInException;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import org.junit.Test;

/**
 * Unit tests for {@link LoginFlow} when accessed via a web frontend
 * transport, i.e. with GAIA ids.
 */
public class LoginFlowViaConsoleTest extends LoginFlowTestCase {

  private static final String GAE_USER_ID1 = "12345";
  private static final String GAE_USER_ID2 = "54321";

  @Test
  public void testSuccess_withLoginAndLinkedAccount() throws Exception {
    persistLinkedAccount("person@example.com", GAE_USER_ID1);
    credentials =
        GaeUserCredentials.forTestingUser(new User("person", "example.com", GAE_USER_ID1), false);
    doSuccessfulTest("login_valid.xml");
  }

  @Test
  public void testFailure_withoutLoginAndLinkedAccount() throws Exception {
    persistLinkedAccount("person@example.com", GAE_USER_ID1);
    credentials = GaeUserCredentials.forLoggedOutUser();
    doFailingTest("login_valid.xml", UserNotLoggedInException.class);
  }

  @Test
  public void testFailure_withoutLoginAndWithoutLinkedAccount() throws Exception {
    credentials = GaeUserCredentials.forLoggedOutUser();
    doFailingTest("login_valid.xml", UserNotLoggedInException.class);
  }

  @Test
  public void testFailure_withLoginAndWithoutLinkedAccount() throws Exception {
    credentials =
        GaeUserCredentials.forTestingUser(new User("person", "example.com", GAE_USER_ID1), false);
    doFailingTest("login_valid.xml", BadGaeUserIdException.class);
  }

  @Test
  public void testFailure_withLoginAndNoMatchingLinkedAccount() throws Exception {
    persistLinkedAccount("joe@example.com", GAE_USER_ID2);
    credentials =
        GaeUserCredentials.forTestingUser(new User("person", "example.com", GAE_USER_ID1), false);
    doFailingTest("login_valid.xml", BadGaeUserIdException.class);
  }

  private void persistLinkedAccount(String email, String gaeUserId) {
    Registrar registrar = Registrar.loadByClientId("NewRegistrar");
    RegistrarContact c = new RegistrarContact.Builder()
        .setParent(registrar)
        .setEmailAddress(email)
        .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
        .setGaeUserId(gaeUserId)
        .build();
    persistResource(c);
  }
}
