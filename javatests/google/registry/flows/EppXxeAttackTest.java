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

import com.google.common.collect.ImmutableMap;
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
        ImmutableMap.of(),
        "response_error_no_cltrid.xml",
        ImmutableMap.of(
            "MSG",
            "Syntax error at line 11, column 34: "
                + "The entity &quot;remote&quot; was referenced, but not declared.",
            "CODE",
            "2001"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testLocalXmlExtrernalEntity() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_local_xxe.xml",
        ImmutableMap.of(),
        "response_error_no_cltrid.xml",
        ImmutableMap.of(
            "MSG",
            "Syntax error at line 11, column 31: "
                + "The entity &quot;ent&quot; was referenced, but not declared.",
            "CODE",
            "2001"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testBillionLaughsAttack() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_billion_laughs.xml",
        ImmutableMap.of(),
        "response_error_no_cltrid.xml",
        ImmutableMap.of(
            "MSG",
            "Syntax error at line 20, column 32: "
                + "The entity &quot;lol9&quot; was referenced, but not declared.",
            "CODE",
            "2001"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
