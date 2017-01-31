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

package google.registry.flows.session;

import static google.registry.testing.DatastoreHelper.createTld;

import google.registry.flows.FlowTestCase;
import google.registry.flows.FlowUtils.NotLoggedInException;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link LogoutFlow}. */
public class LogoutFlowTest extends FlowTestCase<LogoutFlow> {

  public LogoutFlowTest() {
    setEppInput("logout.xml");
  }

  @Before
  public void setupTld() {
    createTld("example");
  }

  @Test
  public void testSuccess() throws Exception {
    assertTransactionalFlow(false);
    // All flow tests are implicitly logged in, so logout should work.
    runFlowAssertResponse(readFile("logout_response.xml"));
  }

  @Test
  public void testFailure() throws Exception {
    sessionMetadata.setClientId(null);  // Turn off the implicit login
    thrown.expect(NotLoggedInException.class);
    runFlow();
  }
}
