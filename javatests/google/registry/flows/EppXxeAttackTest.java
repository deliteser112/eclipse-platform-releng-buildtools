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

import google.registry.testing.AppEngineRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for XXE attacks.
 *
 */
@RunWith(JUnit4.class)
public class EppXxeAttackTest extends EppTestCase {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Test
  public void testRemoteXmlExternalEntity() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_remote_xxe.xml",
        "contact_create_remote_response_xxe.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testLocalXmlExtrernalEntity() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_local_xxe.xml",
        "contact_create_local_response_xxe.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testBillionLaughsAttack() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_billion_laughs.xml",
        "contact_create_response_billion_laughs.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
