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
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for host lifecycle. */
@RunWith(JUnit4.class)
public class EppLifecycleHostTest extends EppTestCase {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Test
  public void testRenamingHostToExistingHost_fails() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Create the two hosts.
    assertCommandAndResponse(
        "host_create.xml",
        "host_create_response.xml",
        DateTime.parse("2000-06-01T00:02:00Z"));
    assertCommandAndResponse(
        "host_create2.xml",
        "host_create2_response.xml",
        DateTime.parse("2000-06-01T00:03:00Z"));
    // Verify that host1 and host2 were created as we expect them.
    assertCommandAndResponse(
        "host_info_ns1.xml",
        "host_info_response_ns1.xml",
        DateTime.parse("2000-06-01T00:04:00Z"));
    assertCommandAndResponse(
        "host_info_ns2.xml",
        "host_info_response_ns2.xml",
        DateTime.parse("2000-06-01T00:05:00Z"));
    // Attempt overwriting of host1 on top of host2 (and verify that it fails).
    assertCommandAndResponse(
        "host_update_ns1_to_ns2.xml",
        "host_update_failed_response.xml",
        DateTime.parse("2000-06-01T00:06:00Z"));
    // Verify that host1 and host2 still exist in their unmodified states.
    assertCommandAndResponse(
        "host_info_ns1.xml",
        "host_info_response_ns1.xml",
        DateTime.parse("2000-06-01T00:07:00Z"));
    assertCommandAndResponse(
        "host_info_ns2.xml",
        "host_info_response_ns2.xml",
        DateTime.parse("2000-06-01T00:08:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
