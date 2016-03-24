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

package com.google.domain.registry.flows;


import static com.google.domain.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.UserInfo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Tests for {@link EppConsoleServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class EppConsoleServletTest extends EppServletXmlLoginTestCase<EppConsoleServlet> {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .withUserService(UserInfo.create(GAE_USER_EMAIL, GAE_USER_ID))
      .build();

  private static final String GAE_USER_ID = "12345";
  private static final String GAE_USER_EMAIL = "person@example.com";

  @Before
  public void initTest() throws Exception {
    Registrar registrar = Registrar.loadByClientId("NewRegistrar");
    RegistrarContact contact = new RegistrarContact.Builder()
        .setParent(registrar)
        .setEmailAddress(GAE_USER_EMAIL)
        .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
        .setGaeUserId(GAE_USER_ID)
        .build();
    persistResource(contact);
  }

  @Test
  public void testNonAuthedLogin() throws Exception {
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
}
