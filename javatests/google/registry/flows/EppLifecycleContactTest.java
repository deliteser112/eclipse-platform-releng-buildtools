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

package google.registry.flows;

import com.google.common.collect.ImmutableMap;
import google.registry.testing.AppEngineRule;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for contact lifecycle. */
@RunWith(JUnit4.class)
public class EppLifecycleContactTest extends EppTestCase {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Test
  public void testContactLifecycle() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        null,
         "contact_create_response_sh8013.xml",
         ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
         DateTime.parse("2000-06-01T00:00:00Z"));
    assertCommandAndResponse(
        "contact_info.xml",
        "contact_info_from_create_response.xml",
        DateTime.parse("2000-06-01T00:01:00Z"));
    assertCommandAndResponse("contact_delete_sh8013.xml", "contact_delete_response_sh8013.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testContactTransferPollMessage() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
    "contact_create_sh8013.xml",
    ImmutableMap.<String, String>of(),
    "contact_create_response_sh8013.xml",
    ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
    DateTime.parse("2000-06-01T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Initiate a transfer of the newly created contact.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_transfer_request.xml",
        "contact_transfer_request_response_alternate.xml",
        DateTime.parse("2000-06-08T22:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in with the losing registrar, read the poll message, and then ack it.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "poll.xml",
        "poll_response_contact_transfer.xml",
        DateTime.parse("2000-06-08T22:01:00Z"));
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "2-1-ROID-3-4"),
        "poll_ack_response_empty.xml",
        null,
        DateTime.parse("2000-06-08T22:02:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
