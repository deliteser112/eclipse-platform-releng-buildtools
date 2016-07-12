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

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import google.registry.model.registry.Registry.TldState;
import google.registry.testing.AppEngineRule;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for domain lifecycle. */
@RunWith(JUnit4.class)
public class EppLifecycleDomainTest extends EppTestCase {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Before
  public void initTld() {
    createTlds("example", "tld");
  }

  /** Create the two administrative contacts and two hosts. */
  void createContactsAndHosts() throws Exception {
    DateTime startTime = DateTime.parse("2000-06-01T00:00:00Z");
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.<String, String>of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        startTime);
    assertCommandAndResponse(
        "contact_create_jd1234.xml",
        "contact_create_response_jd1234.xml",
        startTime.plusMinutes(1));
    assertCommandAndResponse(
        "host_create.xml",
        "host_create_response.xml",
        startTime.plusMinutes(2));
    assertCommandAndResponse(
        "host_create2.xml",
        "host_create2_response.xml",
        startTime.plusMinutes(3));
  }

  /** Creates the domain fakesite.example with two nameservers on it. */
  void createFakesite() throws Exception {
    createContactsAndHosts();
    assertCommandAndResponse(
        "domain_create_fakesite.xml",
        "domain_create_response_fakesite.xml",
        DateTime.parse("2000-06-01T00:04:00Z"));
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_ok.xml",
        DateTime.parse("2000-06-06T00:00:00Z"));
  }

  /** Creates ns3.fakesite.example as a host, then adds it to fakesite. */
  void createSubordinateHost() throws Exception {
    // Add the fakesite nameserver (requires that domain is already created).
    assertCommandAndResponse(
        "host_create_fakesite.xml",
        "host_create_response_fakesite.xml",
        DateTime.parse("2000-06-06T00:01:00Z"));
    // Add new nameserver to domain.
    assertCommandAndResponse(
        "domain_update_add_nameserver_fakesite.xml",
        "domain_update_add_nameserver_response_fakesite.xml",
        DateTime.parse("2000-06-08T00:00:00Z"));
    // Verify new nameserver was added.
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_3_nameservers.xml",
        DateTime.parse("2000-06-08T00:01:00Z"));
    // Verify that nameserver's data was set correctly.
    assertCommandAndResponse(
        "host_info_fakesite.xml",
        "host_info_response_fakesite.xml",
        DateTime.parse("2000-06-08T00:02:00Z"));
  }

  @Test
  public void testDomainDeleteRestore() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");

    // Create contacts sh8013 and jd1234.
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        null,
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        DateTime.parse("2000-06-01T00:00:00Z"));
    assertCommandAndResponse(
        "contact_create_jd1234.xml",
        "contact_create_response_jd1234.xml",
        DateTime.parse("2000-06-01T00:01:00Z"));

    // Create domain example.tld.
    assertCommandAndResponse(
        "domain_create_no_hosts_or_dsdata.xml",
        "domain_create_response.xml",
        DateTime.parse("2000-06-01T00:02:00Z"));

    // Delete domain example.tld after its add grace period has expired.
    assertCommandAndResponse(
        "domain_delete.xml",
        "generic_success_action_pending_response.xml",
        DateTime.parse("2000-07-01T00:02:00Z"));

    // Restore the domain.
    assertCommandAndResponse(
        "domain_update_restore_request.xml",
        "domain_update_restore_request_response.xml",
        DateTime.parse("2000-07-01T00:03:00Z"));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainDeletion_withinAddGracePeriod() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");

    // Create contacts sh8013 and jd1234.
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        null,
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        DateTime.parse("2000-06-01T00:00:00Z"));
    assertCommandAndResponse(
        "contact_create_jd1234.xml",
        "contact_create_response_jd1234.xml",
        DateTime.parse("2000-06-01T00:01:00Z"));

    // Create domain example.tld.
    assertCommandAndResponse(
        "domain_create_no_hosts_or_dsdata.xml",
        "domain_create_response.xml",
        DateTime.parse("2000-06-01T00:02:00Z"));

    // Delete domain example.tld after its add grace period has expired.
    assertCommandAndResponse(
        "domain_delete.xml",
        "generic_success_action_pending_response.xml",
        DateTime.parse("2000-07-01T00:02:00Z"));

    // Poke the domain a little at various times to see its status
    assertCommandAndResponse(
        "domain_info.xml",
        "domain_info_response_pendingdelete.xml",
        DateTime.parse("2000-08-01T00:02:00Z"));  // 1 day out.

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainDeletionWithSubordinateHost_fails() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    createSubordinateHost();
    assertCommandAndResponse(
        "domain_delete_fakesite.xml",
        "domain_delete_response_prohibited.xml",
        DateTime.parse("2002-05-30T01:01:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDeletionOfDomain_afterRenameOfSubordinateHost_succeeds() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    createSubordinateHost();
    // Update the ns3 host to no longer be on fakesite.example domain.
    assertCommandAndResponse(
        "host_update_fakesite.xml",
        "generic_success_response.xml",
        DateTime.parse("2002-05-30T01:01:00Z"));
    // Delete the fakesite.example domain (which should succeed since it no longer has subords).
    assertCommandAndResponse(
        "domain_delete_fakesite.xml",
        "generic_success_action_pending_response.xml",
        DateTime.parse("2002-05-30T01:02:00Z"));
    // Check info on the renamed host and verify that it's still around and wasn't deleted.
    assertCommandAndResponse(
        "host_info_ns9000_example.xml",
        "host_info_response_ns9000_example.xml",
        DateTime.parse("2002-06-30T01:03:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDeletionOfDomain_afterUpdateThatCreatesSubordinateHost_fails() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    // Update the ns1 host to be on the fakesite.example domain.
    assertCommandAndResponse(
        "host_update_ns1_to_fakesite.xml",
        "generic_success_response.xml",
        DateTime.parse("2002-05-30T01:01:00Z"));
    // Attempt to delete the fakesite.example domain (which should fail since it now has a
    // subordinate host).
    assertCommandAndResponse(
        "domain_delete_fakesite.xml",
        "domain_delete_response_prohibited.xml",
        DateTime.parse("2002-05-30T01:02:00Z"));
    // Check info on the renamed host and verify that it's still around and wasn't deleted.
    assertCommandAndResponse(
        "host_info_fakesite.xml",
        "host_info_response_fakesite_post_update.xml",
        DateTime.parse("2002-06-30T01:03:00Z"));
    // Verify that fakesite.example domain is still around and wasn't deleted.
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_ok_post_host_update.xml",
        DateTime.parse("2002-05-30T01:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testRenamingHostToExistingHost_fails() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Create the two hosts.
    assertCommandAndResponse(
        "host_create.xml", "host_create_response.xml", DateTime.parse("2000-06-01T00:02:00Z"));
    assertCommandAndResponse(
        "host_create2.xml", "host_create2_response.xml", DateTime.parse("2000-06-01T00:03:00Z"));
    // Verify that host1 and host2 were created as we expect them.
    assertCommandAndResponse(
        "host_info_ns1.xml", "host_info_response_ns1.xml", DateTime.parse("2000-06-01T00:04:00Z"));
    assertCommandAndResponse(
        "host_info_ns2.xml", "host_info_response_ns2.xml", DateTime.parse("2000-06-01T00:05:00Z"));
    // Attempt overwriting of host1 on top of host2 (and verify that it fails).
    assertCommandAndResponse(
        "host_update_ns1_to_ns2.xml",
        "host_update_failed_response.xml",
        DateTime.parse("2000-06-01T00:06:00Z"));
    // Verify that host1 and host2 still exist in their unmodified states.
    assertCommandAndResponse(
        "host_info_ns1.xml", "host_info_response_ns1.xml", DateTime.parse("2000-06-01T00:07:00Z"));
    assertCommandAndResponse(
        "host_info_ns2.xml", "host_info_response_ns2.xml", DateTime.parse("2000-06-01T00:08:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainCreation_failsBeforeSunrise() throws Exception {
    DateTime sunriseDate = DateTime.parse("2000-05-30T00:00:00Z");
    createTld("example", ImmutableSortedMap.of(
        START_OF_TIME, TldState.PREDELEGATION,
        sunriseDate, TldState.SUNRISE,
        sunriseDate.plusMonths(2), TldState.GENERAL_AVAILABILITY));

    assertCommandAndResponse("login_valid.xml", "login_response.xml");

    createContactsAndHosts();

    assertCommandAndResponse(
        "domain_create_sunrise_encoded_mark.xml",
        "domain_create_testvalidate_invalid_phase.xml",
        sunriseDate.minusDays(1));

    assertCommandAndResponse(
        "domain_info_testvalidate.xml",
        "domain_info_response_testvalidate_doesnt_exist.xml",
        sunriseDate.plusDays(1));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainCheckFee_succeeds() throws Exception {
    DateTime gaDate = DateTime.parse("2000-05-30T00:00:00Z");
    createTld("example", ImmutableSortedMap.of(
        START_OF_TIME, TldState.PREDELEGATION,
        gaDate, TldState.GENERAL_AVAILABILITY));

    assertCommandAndResponse("login_valid_fee_extension.xml", "login_response.xml");

    assertCommandAndResponse(
        "domain_check_fee_premium.xml",
        "domain_check_fee_premium_response.xml",
        gaDate.plusDays(1));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainTransferPollMessage_serverApproved() throws Exception {
    // As the losing registrar, create the domain.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // As the winning registrar, request a transfer. Capture the server trid; we'll need it later.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    String response = assertCommandAndResponse(
        "domain_transfer_request_1_year.xml",
        "domain_transfer_response_1_year.xml",
        DateTime.parse("2001-01-01T00:00:00Z"));
    Matcher matcher = Pattern.compile("<svTRID>(.*)</svTRID>").matcher(response);
    matcher.find();
    String transferRequestTrid = matcher.group(1);
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // As the losing registrar, read the request poll message, and then ack it.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "poll.xml",
        "poll_response_domain_transfer_request.xml",
        DateTime.parse("2001-01-01T00:01:00Z"));
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-B-EXAMPLE-17-21"),
        "poll_ack_response_empty.xml",
        null,
    DateTime.parse("2001-01-01T00:01:00Z"));

    // Five days in the future, expect a server approval poll message to the loser, and ack it.
    assertCommandAndResponse(
        "poll.xml",
        "poll_response_domain_transfer_server_approve_loser.xml",
        DateTime.parse("2001-01-06T00:01:00Z"));
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-B-EXAMPLE-17-23"),
        "poll_ack_response_empty.xml",
        null,
        DateTime.parse("2001-01-06T00:01:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Also expect a server approval poll message to the winner, with the transfer request trid.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "poll.xml",
        null,
        "poll_response_domain_transfer_server_approve_winner.xml",
        ImmutableMap.of("SERVER_TRID", transferRequestTrid),
        DateTime.parse("2001-01-06T00:02:00Z"));
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-B-EXAMPLE-17-22"),
        "poll_ack_response_empty.xml",
        null,
        DateTime.parse("2001-01-06T00:02:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testIgnoredTransferDuringAutoRenewPeriod_succeeds() throws Exception {
    // Register the domain as the first registrar.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_2_years.xml",
        "domain_transfer_response_2_years.xml",
        DateTime.parse("2002-05-30T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the first registrar and verify things.
    assertCommandAndResponse(
        "login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_pending_transfer.xml",
        DateTime.parse("2002-05-30T01:00:00Z"));
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_pending_transfer_autorenew.xml",
        DateTime.parse("2002-06-02T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the second registrar and verify transfer details.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    // Verify that domain is in the transfer period now with expiration date two years out.
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_transfer_period.xml",
        DateTime.parse("2002-06-06T00:00:00Z"));
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_transfer_complete.xml",
        DateTime.parse("2002-06-12T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testNameserversTransferWithDomain_successfully() throws Exception {
    // Log in as the first registrar and set up domains with hosts.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    createSubordinateHost();
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_2_years.xml",
        "domain_transfer_response_2_years.xml",
        DateTime.parse("2002-05-30T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the first registrar and verify domain is pending transfer.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("domain_info_fakesite.xml",
        "domain_info_response_fakesite_3_nameservers_pending_transfer.xml",
        DateTime.parse("2002-05-30T01:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as second registrar and verify transfer was successful.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    // Expect transfer complete with all three nameservers on it.
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_3_nameservers_transfer_successful.xml",
        DateTime.parse("2002-06-09T00:00:00Z"));
    // Verify that host's client ID was set to the new registrar and has the transfer date set.
    assertCommandAndResponse(
        "host_info_fakesite.xml",
        null,
        "host_info_response_fakesite_post_transfer.xml",
        ImmutableMap.of("trDate", "2002-06-04T00:00:00Z"),
        DateTime.parse("2002-06-09T00:01:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainDeletionCancelsPendingTransfer() throws Exception {
    // Register the domain as the first registrar.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_2_years.xml",
        "domain_transfer_response_2_years.xml",
        DateTime.parse("2002-05-30T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the first registrar and delete then restore the domain while the transfer
    // is still pending.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_pending_transfer.xml",
        DateTime.parse("2002-05-30T01:00:00Z"));
    assertCommandAndResponse(
        "domain_delete_fakesite.xml",
        "generic_success_action_pending_response.xml",
        DateTime.parse("2002-05-30T01:01:00Z"));
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_pending_delete.xml",
        DateTime.parse("2002-05-30T01:02:00Z"));
    assertCommandAndResponse(
        "domain_update_restore_fakesite.xml",
        "domain_update_restore_request_response.xml",
        DateTime.parse("2002-05-30T01:03:00Z"));

    // Expect domain is ok now, not pending delete or transfer, and has been extended by a year from
    // the date of the restore.  (Not from the original expiration date.)
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_restored_ok.xml",
        DateTime.parse("2002-05-30T01:04:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainTransfer_subordinateHost_showsChangeInTransferQuery() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    createSubordinateHost();
    assertCommandAndResponse(
        "domain_transfer_query_fakesite.xml",
        "domain_transfer_query_response_no_transfer_history.xml",
        DateTime.parse("2000-09-02T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_1_year.xml",
        "domain_transfer_response_1_year.xml", DateTime.parse("2001-01-01T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Verify that reID is set correctly.
    assertCommandAndResponse(
        "domain_transfer_query_fakesite.xml",
        "domain_transfer_query_response_fakesite.xml",
        DateTime.parse("2001-01-02T00:00:00Z"));
    // Verify that status went from 'pending' to 'serverApproved'.
    assertCommandAndResponse(
        "domain_transfer_query_fakesite.xml",
        "domain_transfer_query_response_completed_fakesite.xml",
        DateTime.parse("2001-01-08T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  /**
   * Tests that when a superordinate domain of a host is transferred, and then the host is updated
   * to be subordinate to a different domain, that the host retains the transfer time of the first
   * superordinate domain, not whatever the transfer time from the second domain is.
   */
  @Test
  public void testSuccess_lastTransferTime_superordinateDomainTransferFollowedByHostUpdate()
      throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Create fakesite.example with subordinate host ns3.fakesite.example
    createFakesite();
    createSubordinateHost();
    assertCommandAndResponse(
        "domain_transfer_query_fakesite.xml",
        "domain_transfer_query_response_no_transfer_history.xml",
        DateTime.parse("2000-09-02T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_1_year.xml",
        "domain_transfer_response_1_year.xml",
        DateTime.parse("2001-01-01T00:00:00Z"));
    // Verify that the lastTransferTime now reflects the superordinate domain's transfer.
    assertCommandAndResponse(
        "host_info.xml",
        ImmutableMap.of("hostname", "ns3.fakesite.example"),
        "host_info_response_fakesite_post_transfer.xml",
        ImmutableMap.of("trDate", "2001-01-06T00:00:00.000Z"),
        DateTime.parse("2001-01-07T00:00:00Z"));
    assertCommandAndResponse(
        "domain_create_secondsite.xml",
        "domain_create_response_secondsite.xml",
        DateTime.parse("2001-01-08T00:00:00Z"));
    // Update the host to be subordinate to a different domain by renaming it to
    // ns3.secondsite.example
    assertCommandAndResponse(
        "host_update_rename_only.xml",
        ImmutableMap.of("oldName", "ns3.fakesite.example", "newName", "ns3.secondsite.example"),
        "generic_success_response.xml",
        null,
        DateTime.parse("2002-05-30T01:01:00Z"));
    // The last transfer time on the host should still be what it was from the transfer.
    assertCommandAndResponse(
        "host_info.xml",
        ImmutableMap.of("hostname", "ns3.secondsite.example"),
        "host_info_response_fakesite_post_transfer_and_update.xml",
        ImmutableMap.of(
            "hostname", "ns3.secondsite.example",
            "trDate", "2001-01-06T00:00:00.000Z"),
        DateTime.parse("2003-01-07T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  /**
   * Tests that when a superordinate domain of a host is transferred, and then the host is updated
   * to be external, that the host retains the transfer time of the first superordinate domain.
   */
  @Test
  public void testSuccess_lastTransferTime_superordinateDomainTransferThenHostUpdateToExternal()
      throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Create fakesite.example with subordinate host ns3.fakesite.example
    createFakesite();
    createSubordinateHost();
    assertCommandAndResponse(
        "domain_transfer_query_fakesite.xml",
        "domain_transfer_query_response_no_transfer_history.xml",
         DateTime.parse("2000-09-02T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_1_year.xml",
        "domain_transfer_response_1_year.xml",
        DateTime.parse("2001-01-01T00:00:00Z"));
    // Verify that the lastTransferTime now reflects the superordinate domain's transfer.
    assertCommandAndResponse(
        "host_info_fakesite.xml",
        null,
        "host_info_response_fakesite_post_transfer.xml",
        ImmutableMap.of("trDate", "2001-01-06T00:00:00.000Z"),
        DateTime.parse("2001-01-07T00:00:00Z"));
    // Update the host to be external by renaming it to ns3.notarealsite.external
    assertCommandAndResponse(
        "host_update_rename_and_remove_addresses.xml",
        ImmutableMap.of(
            "oldName", "ns3.fakesite.example",
            "newName", "ns3.notarealsite.external"),
        "generic_success_response.xml",
        null,
        DateTime.parse("2002-05-30T01:01:00Z"));
    // The last transfer time on the host should still be what it was from the transfer.
    assertCommandAndResponse(
        "host_info.xml",
        ImmutableMap.of("hostname", "ns3.notarealsite.external"),
        "host_info_response_fakesite_post_transfer_and_update_no_addresses.xml",
        ImmutableMap.of(
            "hostname", "ns3.notarealsite.external",
            "trDate", "2001-01-06T00:00:00.000Z"),
        DateTime.parse("2001-01-07T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
