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


import static com.google.appengine.api.users.UserServiceFactory.getUserService;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.collect.ImmutableSet;
import google.registry.flows.GaeUserCredentials;
import google.registry.flows.GaeUserCredentials.BadGaeUserIdException;
import google.registry.flows.GaeUserCredentials.UserNotLoggedInException;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Unit tests for {@link LoginFlow} when accessed via a web frontend
 * transport, i.e. with GAIA ids.
 */
public class LoginFlowViaConsoleTest extends LoginFlowTestCase {

  private static final String GAE_USER_ID1 = "12345";
  private static final String GAE_USER_ID2 = "54321";

  Environment oldEnv = null;

  @Test
  public void testSuccess_withLoginAndLinkedAccount() throws Exception {
    persistLinkedAccount("person@example.com", GAE_USER_ID1);
    login("person", "example.com", GAE_USER_ID1);
    try {
      doSuccessfulTest("login_valid.xml");
    } finally {
      ApiProxy.setEnvironmentForCurrentThread(oldEnv);
    }
  }

  @Test
  public void testFailure_withoutLoginAndLinkedAccount() throws Exception {
    persistLinkedAccount("person@example.com", GAE_USER_ID1);
    noLogin();
    doFailingTest("login_valid.xml", UserNotLoggedInException.class);
  }

  @Test
  public void testFailure_withoutLoginAndWithoutLinkedAccount() throws Exception {
    noLogin();
    doFailingTest("login_valid.xml", UserNotLoggedInException.class);
  }

  @Test
  public void testFailure_withLoginAndWithoutLinkedAccount() throws Exception {
    login("person", "example.com", GAE_USER_ID1);
    try {
      doFailingTest("login_valid.xml", BadGaeUserIdException.class);
    } finally {
      ApiProxy.setEnvironmentForCurrentThread(oldEnv);
    }
  }

  @Test
  public void testFailure_withLoginAndNoMatchingLinkedAccount() throws Exception {
    persistLinkedAccount("joe@example.com", GAE_USER_ID2);
    login("person", "example.com", GAE_USER_ID1);
    try {
      doFailingTest("login_valid.xml", BadGaeUserIdException.class);
    } finally {
      ApiProxy.setEnvironmentForCurrentThread(oldEnv);
    }
  }

  Environment login(final String name, final String authDomain, final String gaeUserId) {
    // This envAttr thing is the only way to set userId.
    // see https://code.google.com/p/googleappengine/issues/detail?id=3579
    final HashMap<String, Object> envAttr = new HashMap<>();
    envAttr.put("com.google.appengine.api.users.UserService.user_id_key", gaeUserId);

    // And then.. this.
    oldEnv = ApiProxy.getCurrentEnvironment();
    final Environment e = oldEnv;
    ApiProxy.setEnvironmentForCurrentThread(new Environment() {
      @Override
      public String getAppId() {
        return e.getAppId();
      }

      @Override
      public String getModuleId() {
        return e.getModuleId();
      }

      @Override
      public String getVersionId() {
        return e.getVersionId();
      }

      @Override
      public String getEmail() {
        return name + "@" + authDomain;
      }

      @Override
      public boolean isLoggedIn() {
        return true;
      }

      @Override
      public boolean isAdmin() {
        return e.isAdmin();
      }

      @Override
      public String getAuthDomain() {
        return authDomain;
      }

      @Override
      @SuppressWarnings("deprecation")
      public String getRequestNamespace() {
        return e.getRequestNamespace();
      }

      @Override
      public long getRemainingMillis() {
        return e.getRemainingMillis();
      }

      @Override
      public Map<String, Object> getAttributes() {
        return envAttr;
      }
    });
    credentials = new GaeUserCredentials(getUserService().getCurrentUser());
    return oldEnv;
  }

  void noLogin() {
    oldEnv = ApiProxy.getCurrentEnvironment();
    credentials = new GaeUserCredentials(getUserService().getCurrentUser());
  }

  void persistLinkedAccount(String email, String gaeUserId) {
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
