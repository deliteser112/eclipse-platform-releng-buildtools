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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.CertificateSamples;
import com.google.domain.registry.testing.FakeServletInputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test setup for EppServletTest subclasses. */
@RunWith(MockitoJUnitRunner.class)
public class EppTlsServletTest extends EppServletXmlLoginTestCase<EppTlsServlet> {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  String ipAddressAndPort = "192.168.1.100:54321";
  String clientCert = CertificateSamples.SAMPLE_CERT_HASH;
  String clientCert2 = CertificateSamples.SAMPLE_CERT2_HASH;
  String requestedServername = "test.example";

  private String gfeRequestClientCertificateHashField;

  @Before
  public void initTest() throws Exception {
    persistResource(Registrar.loadByClientId("NewRegistrar")
        .asBuilder()
        .setClientCertificateHash(clientCert)
        .build());

    persistResource(Registrar.loadByClientId("TheRegistrar")
        .asBuilder()
        .setClientCertificateHash(clientCert2)
        .build());
  }

  @Test
  public void testSetTldViaSni() throws Exception {
    requestedServername = "epp.nic.xn--q9jyb4c";
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.<String, String>of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        "2000-06-01T00:00:00Z");
    assertCommandAndResponse(
        "domain_create_minna.xml",
        "domain_create_response_minna.xml",
        "2000-06-01T01:02:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  /** This test requires multiple registrars, which EppConsoleServlet doesn't allow. */
  @Test
  public void testContactTransferPollMessage() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.<String, String>of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        "2000-06-01T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Initiate a transfer of the newly created contact.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_transfer_request.xml",
        "contact_transfer_request_response_alternate.xml",
        "2000-06-08T22:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in with the losing registrar, read the poll message, and then ack it.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "poll.xml",
        "poll_response_contact_transfer.xml",
        "2000-06-08T22:01:00Z");
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "2-4-ROID-6-7"),
        "poll_ack_response_empty.xml",
        null,
        "2000-06-08T22:02:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  /** This test requires multiple registrars, which EppConsoleServlet doesn't allow. */
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
        "2001-01-01T00:00:00Z");
    Matcher matcher = Pattern.compile("<svTRID>(.*)</svTRID>").matcher(response);
    matcher.find();
    String transferRequestTrid = matcher.group(1);
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // As the losing registrar, read the request poll message, and then ack it.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "poll.xml",
        "poll_response_domain_transfer_request.xml",
        "2001-01-01T00:01:00Z");
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-C-EXAMPLE-18-22"),
        "poll_ack_response_empty.xml",
        null,
        "2001-01-01T00:01:00Z");

    // Five days in the future, expect a server approval poll message to the loser, and ack it.
    assertCommandAndResponse(
        "poll.xml",
        "poll_response_domain_transfer_server_approve_loser.xml",
        "2001-01-06T00:01:00Z");
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-C-EXAMPLE-18-24"),
        "poll_ack_response_empty.xml",
        null,
        "2001-01-06T00:01:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Also expect a server approval poll message to the winner, with the transfer request trid.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "poll.xml",
        null,
        "poll_response_domain_transfer_server_approve_winner.xml",
        ImmutableMap.of("SERVER_TRID", transferRequestTrid),
        "2001-01-06T00:02:00Z");
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-C-EXAMPLE-18-23"),
        "poll_ack_response_empty.xml",
        null,
        "2001-01-06T00:02:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Override
  protected void extendedSessionConfig(String inputFile) throws Exception {
    when(req.getHeader(EppTlsServlet.REQUESTED_SERVERNAME_VIA_SNI_FIELD))
        .thenReturn(requestedServername);
    when(req.getHeader(EppTlsServlet.FORWARDED_FOR_FIELD))
        .thenReturn(ipAddressAndPort);
    if (gfeRequestClientCertificateHashField != null) {
      when(req.getHeader(EppTlsServlet.SSL_CLIENT_CERTIFICATE_HASH_FIELD))
          .thenReturn(gfeRequestClientCertificateHashField);
    } else {
      when(req.getHeader(EppTlsServlet.SSL_CLIENT_CERTIFICATE_HASH_FIELD))
          .thenReturn(inputFile.contains("TheRegistrar") ? clientCert2 : clientCert);
    }
    when(req.getInputStream()).thenReturn(new FakeServletInputStream(inputFile.getBytes(UTF_8)));
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
        "2002-05-30T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the first registrar and verify things.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_pending_transfer.xml",
        "2002-05-30T01:00:00Z");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_pending_transfer_autorenew.xml",
        "2002-06-02T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the second registrar and verify transfer details.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    // Verify that domain is in the transfer period now with expiration date two years out.
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_transfer_period.xml",
        "2002-06-06T00:00:00Z");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_transfer_complete.xml",
        "2002-06-12T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testBadCertificate_failsBadCertificate2200() throws Exception {
    gfeRequestClientCertificateHashField = "laffo";
    assertCommandAndResponse("login_valid.xml", "login_response_bad_certificate.xml");
  }

  @Test
  public void testGfeDidntProvideClientCertificate_failsMissingCertificate2200() throws Exception {
    gfeRequestClientCertificateHashField = "";
    assertCommandAndResponse("login_valid.xml", "login_response_missing_certificate.xml");
  }

  @Test
  public void testGoodPrimaryCertificate() throws Exception {
    gfeRequestClientCertificateHashField = CertificateSamples.SAMPLE_CERT_HASH;
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setClientCertificate(CertificateSamples.SAMPLE_CERT, clock.nowUtc())
        .setFailoverClientCertificate(CertificateSamples.SAMPLE_CERT2, clock.nowUtc())
        .build());
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
  }

  @Test
  public void testGoodFailoverCertificate() throws Exception {
    gfeRequestClientCertificateHashField = CertificateSamples.SAMPLE_CERT2_HASH;
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setClientCertificate(CertificateSamples.SAMPLE_CERT, clock.nowUtc())
        .setFailoverClientCertificate(CertificateSamples.SAMPLE_CERT2, clock.nowUtc())
        .build());
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
  }

  @Test
  public void testMissingPrimaryCertificateButHasFailover_usesFailover() throws Exception {
    gfeRequestClientCertificateHashField = CertificateSamples.SAMPLE_CERT2_HASH;
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setClientCertificate(null, clock.nowUtc())
        .setFailoverClientCertificate(CertificateSamples.SAMPLE_CERT2, clock.nowUtc())
        .build());
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
  }

  @Test
  public void testRegistrarHasNoCertificatesOnFile_disablesCertChecking() throws Exception {
    gfeRequestClientCertificateHashField = "laffo";
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setClientCertificate(null, clock.nowUtc())
        .setFailoverClientCertificate(null, clock.nowUtc())
        .build());
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
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
        "2002-05-30T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the first registrar and verify domain is pending transfer.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_3_nameservers_pending_transfer.xml",
        "2002-05-30T01:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as second registrar and verify transfer was successful.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    // Expect transfer complete with all three nameservers on it.
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_3_nameservers_transfer_successful.xml",
        "2002-06-09T00:00:00Z");
    // Verify that host's client ID was set to the new registrar and has the transfer date set.
    assertCommandAndResponse(
        "host_info_fakesite.xml",
        null,
        "host_info_response_fakesite_post_transfer.xml",
        ImmutableMap.of("trDate", "2002-06-04T00:00:00Z"),
        "2002-06-09T00:01:00Z");
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
        "2002-05-30T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the first registrar and delete then restore the domain while the transfer
    // is still pending.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_pending_transfer.xml",
        "2002-05-30T01:00:00Z");
    assertCommandAndResponse(
        "domain_delete_fakesite.xml",
        "generic_success_action_pending_response.xml",
        "2002-05-30T01:01:00Z");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_pending_delete.xml",
        "2002-05-30T01:02:00Z");
    assertCommandAndResponse(
        "domain_update_restore_fakesite.xml",
        "domain_update_restore_request_response.xml",
        "2002-05-30T01:03:00Z");

    // Expect domain is ok now, not pending delete or transfer, and has been extended by a year from
    // the date of the restore.  (Not from the original expiration date.)
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_restored_ok.xml",
        "2002-05-30T01:04:00Z");
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
        "2000-09-02T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_1_year.xml",
        "domain_transfer_response_1_year.xml",
        "2001-01-01T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Verify that reID is set correctly.
    assertCommandAndResponse(
        "domain_transfer_query_fakesite.xml",
        "domain_transfer_query_response_fakesite.xml",
        "2001-01-02T00:00:00Z");
    // Verify that status went from 'pending' to 'serverApproved'.
    assertCommandAndResponse(
        "domain_transfer_query_fakesite.xml",
        "domain_transfer_query_response_completed_fakesite.xml",
        "2001-01-08T00:00:00Z");
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
        "2000-09-02T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_1_year.xml",
        "domain_transfer_response_1_year.xml",
        "2001-01-01T00:00:00Z");
    // Verify that the lastTransferTime now reflects the superordinate domain's transfer.
    assertCommandAndResponse(
        "host_info.xml",
        ImmutableMap.of("hostname", "ns3.fakesite.example"),
        "host_info_response_fakesite_post_transfer.xml",
        ImmutableMap.of("trDate", "2001-01-06T00:00:00.000Z"),
        "2001-01-07T00:00:00Z");
    assertCommandAndResponse(
        "domain_create_secondsite.xml",
        "domain_create_response_secondsite.xml",
        "2001-01-08T00:00:00Z");
    // Update the host to be subordinate to a different domain by renaming it to
    // ns3.secondsite.example
    assertCommandAndResponse(
        "host_update_rename_only.xml",
        ImmutableMap.of("oldName", "ns3.fakesite.example", "newName", "ns3.secondsite.example"),
        "generic_success_response.xml",
        null,
        "2002-05-30T01:01:00Z");
    // The last transfer time on the host should still be what it was from the transfer.
    assertCommandAndResponse(
        "host_info.xml",
        ImmutableMap.of("hostname", "ns3.secondsite.example"),
        "host_info_response_fakesite_post_transfer_and_update.xml",
        ImmutableMap.of(
            "hostname", "ns3.secondsite.example",
            "trDate", "2001-01-06T00:00:00.000Z"),
        "2003-01-07T00:00:00Z");
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
        "2000-09-02T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_1_year.xml",
        "domain_transfer_response_1_year.xml",
        "2001-01-01T00:00:00Z");
    // Verify that the lastTransferTime now reflects the superordinate domain's transfer.
    assertCommandAndResponse(
        "host_info_fakesite.xml",
        null,
        "host_info_response_fakesite_post_transfer.xml",
        ImmutableMap.of("trDate", "2001-01-06T00:00:00.000Z"),
        "2001-01-07T00:00:00Z");
    // Update the host to be external by renaming it to ns3.notarealsite.external
    assertCommandAndResponse(
        "host_update_rename_and_remove_addresses.xml",
        ImmutableMap.of(
            "oldName", "ns3.fakesite.example",
            "newName", "ns3.notarealsite.external"),
        "generic_success_response.xml",
        null,
        "2002-05-30T01:01:00Z");
    // The last transfer time on the host should still be what it was from the transfer.
    assertCommandAndResponse(
        "host_info.xml",
        ImmutableMap.of("hostname", "ns3.notarealsite.external"),
        "host_info_response_fakesite_post_transfer_and_update_no_addresses.xml",
        ImmutableMap.of(
            "hostname", "ns3.notarealsite.external",
            "trDate", "2001-01-06T00:00:00.000Z"),
        "2001-01-07T00:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
