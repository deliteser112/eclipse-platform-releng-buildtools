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

import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_WITH_ACTION_PENDING;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.EppMetricSubject.assertThat;

import com.google.common.collect.ImmutableMap;
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
      .withTaskQueue()
      .build();

  @Test
  public void testLifecycle() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "hello.xml",
        ImmutableMap.<String, String>of(),
        "greeting.xml",
        ImmutableMap.of("DATE", "2000-06-02T00:00:00Z"),
        DateTime.parse("2000-06-02T00:00:00Z"));
    // Note that Hello commands don't set a status code on the response.
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasCommandName("Hello")
        .and()
        .hasNoStatus();
    assertCommandAndResponse(
        "host_create.xml",
        ImmutableMap.of("HOSTNAME", "ns1.example.tld"),
        "host_create_response.xml",
        ImmutableMap.of("HOSTNAME", "ns1.example.tld", "CRDATE", "2000-06-02T00:01:00Z"),
        DateTime.parse("2000-06-02T00:01:00Z"));
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasCommandName("HostCreate")
        .and()
        .hasEppTarget("ns1.example.tld")
        .and()
        .hasStatus(SUCCESS);
    assertCommandAndResponse(
        "host_info.xml",
        ImmutableMap.of("HOSTNAME", "ns1.example.tld"),
        "host_info_response.xml",
        ImmutableMap.<String, String>of(
            "HOSTNAME", "ns1.example.tld", "ROID", "1-ROID", "CRDATE", "2000-06-02T00:01:00Z"),
        DateTime.parse("2000-06-02T00:02:00Z"));
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasCommandName("HostInfo")
        .and()
        .hasEppTarget("ns1.example.tld")
        .and()
        .hasStatus(SUCCESS);
    assertCommandAndResponse(
        "host_delete.xml",
        ImmutableMap.of("HOSTNAME", "ns1.example.tld"),
        "generic_success_action_pending_response.xml",
        ImmutableMap.<String, String>of(),
        DateTime.parse("2000-06-02T00:03:00Z"));
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasCommandName("HostDelete")
        .and()
        .hasEppTarget("ns1.example.tld")
        .and()
        .hasStatus(SUCCESS_WITH_ACTION_PENDING);
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testRenamingHostToExistingHost_fails() throws Exception {
    createTld("example");
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Create the fakesite domain.
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.<String, String>of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        DateTime.parse("2000-06-01T00:00:00Z"));
    assertCommandAndResponse(
        "contact_create_jd1234.xml",
        "contact_create_response_jd1234.xml",
        DateTime.parse("2000-06-01T00:01:00Z"));
    assertCommandAndResponse(
        "domain_create_fakesite_no_nameservers.xml",
        "domain_create_response_fakesite.xml",
        DateTime.parse("2000-06-01T00:04:00Z"));
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_inactive.xml",
        DateTime.parse("2000-06-05T00:02:00Z"));
    // Add the fakesite subordinate host (requires that domain is already created).
    assertCommandAndResponse(
        "host_create_fakesite.xml",
        "host_create_response_fakesite.xml",
        DateTime.parse("2000-06-06T00:01:00Z"));
    // Add the 2nd fakesite subordinate host.
    assertCommandAndResponse(
        "host_create_fakesite2.xml",
        "host_create_response_fakesite2.xml",
        DateTime.parse("2000-06-09T00:01:00Z"));
    // Attempt overwriting of 2nd fakesite subordinate host with the 1st.
    assertCommandAndResponse(
        "host_update_fakesite1_to_fakesite2.xml",
        "host_update_failed_response.xml",
        DateTime.parse("2000-06-10T00:01:00Z"));
    // Verify that fakesite hosts still exist in their unmodified states.
    assertCommandAndResponse(
        "host_info_fakesite.xml",
        "host_info_response_fakesite_ok.xml",
        DateTime.parse("2000-06-11T00:07:00Z"));
    assertCommandAndResponse(
        "host_info_fakesite2.xml",
        "host_info_response_fakesite2.xml",
        DateTime.parse("2000-06-11T00:08:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
