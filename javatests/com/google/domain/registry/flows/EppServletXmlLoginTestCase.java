// Copyright 2016 Google Inc. All Rights Reserved.
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

import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static com.google.domain.registry.util.ResourceUtils.readResourceUtf8;
import static com.google.domain.registry.xml.XmlTestUtils.assertXmlEquals;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.util.DateTimeUtils;

import org.joda.time.DateTime;
import org.junit.Test;

import javax.servlet.http.HttpServlet;

/**
 * Test setup for EppServletTest subclasses which use XML-based authentication.
 *
 * @param <S> The EppXXXServlet class to test.
 */
public abstract class EppServletXmlLoginTestCase<S extends HttpServlet> extends
    EppServletTestCase<S> {

  @Test
  public void testHello() throws Exception {
    assertXmlEquals(
        readResourceUtf8(getClass(), "testdata/greeting_crr.xml"),
        expectXmlCommand(readResourceUtf8(getClass(), "testdata/hello.xml"), DateTime.now(UTC)),
        "epp.greeting.svDate");
  }

  @Test
  public void testLoginLogout() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testPdtLogin() throws Exception {
    assertCommandAndResponse("pdt_login.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testSyntaxError() throws Exception {
    assertCommandAndResponse("syntax_error.xml", "syntax_error_response.xml");
  }

  @Test
  public void testContactLifecycle() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.<String, String>of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        "2000-06-01T00:00:00Z");
    assertCommandAndResponse(
        "contact_info.xml",
        "contact_info_from_create_response.xml",
        "2000-06-01T00:01:00Z");
    assertCommandAndResponse("contact_delete_sh8013.xml", "contact_delete_response_sh8013.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainDeleteRestore() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Create contacts sh8013 and jd1234.
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.<String, String>of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        "2000-06-01T00:00:00Z");

    assertCommandAndResponse(
        "contact_create_jd1234.xml",
        "contact_create_response_jd1234.xml",
        "2000-06-01T00:01:00Z");

    // Create domain example.tld.
    assertCommandAndResponse(
        "domain_create_no_hosts_or_dsdata.xml",
        "domain_create_response.xml",
        "2000-06-01T00:02:00Z");

    // Delete domain example.com after its add grace period has expired.
    assertCommandAndResponse(
        "domain_delete.xml",
        "generic_success_action_pending_response.xml",
        "2000-07-01T00:02:00Z");

    // Restore the domain.
    assertCommandAndResponse(
        "domain_update_restore_request.xml",
        "domain_update_restore_request_response.xml",
        "2000-07-01T00:03:00Z");

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainDeletion_withinAddGracePeriod() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");

    // Create contacts sh8013 and jd1234.
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.<String, String>of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", "2000-06-01T00:00:00Z"),
        "2000-06-01T00:00:00Z");

    assertCommandAndResponse(
        "contact_create_jd1234.xml",
        "contact_create_response_jd1234.xml",
        "2000-06-01T00:01:00Z");

    // Create domain example.tld.
    assertCommandAndResponse(
        "domain_create_no_hosts_or_dsdata.xml",
        "domain_create_response.xml",
        "2000-06-01T00:02:00Z");

    // Delete domain example.tld after its add grace period has expired.
    assertCommandAndResponse(
        "domain_delete.xml",
        "generic_success_action_pending_response.xml",
        "2000-07-01T00:02:00Z");

    // Poke the domain a little at various times to see its status
    assertCommandAndResponse(
        "domain_info.xml",
        "domain_info_response_pendingdelete.xml",
        "2000-08-01T00:02:00Z");  // 1 day out.

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
        "2002-05-30T01:01:00Z");
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
        "2002-05-30T01:01:00Z");
    // Delete the fakesite.example domain (which should succeed since it no longer has subords).
    assertCommandAndResponse(
        "domain_delete_fakesite.xml",
        "generic_success_action_pending_response.xml",
        "2002-05-30T01:02:00Z");
    // Check info on the renamed host and verify that it's still around and wasn't deleted.
    assertCommandAndResponse(
        "host_info_ns9000_example.xml",
        "host_info_response_ns9000_example.xml",
        "2002-06-30T01:03:00Z");
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
        "2002-05-30T01:01:00Z");
    // Attempt to delete the fakesite.example domain (which should fail since it now has a
    // subordinate host).
    assertCommandAndResponse(
        "domain_delete_fakesite.xml",
        "domain_delete_response_prohibited.xml",
        "2002-05-30T01:02:00Z");
    // Check info on the renamed host and verify that it's still around and wasn't deleted.
    assertCommandAndResponse(
        "host_info_fakesite.xml",
        "host_info_response_fakesite_post_update.xml",
        "2002-06-30T01:03:00Z");
    // Verify that fakesite.example domain is still around and wasn't deleted.
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
        "domain_info_response_fakesite_ok_post_host_update.xml",
        "2002-05-30T01:00:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testRenamingHostToExistingHost_fails() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Create the two hosts.
    assertCommandAndResponse(
        "host_create.xml",
        "host_create_response.xml",
        "2000-06-01T00:02:00Z");
    assertCommandAndResponse(
        "host_create2.xml",
        "host_create2_response.xml",
        "2000-06-01T00:03:00Z");
    // Verify that host1 and host2 were created as we expect them.
    assertCommandAndResponse(
        "host_info_ns1.xml",
        "host_info_response_ns1.xml",
        "2000-06-01T00:04:00Z");
    assertCommandAndResponse(
        "host_info_ns2.xml",
        "host_info_response_ns2.xml",
        "2000-06-01T00:05:00Z");
    // Attempt overwriting of host1 on top of host2 (and verify that it fails).
    assertCommandAndResponse(
        "host_update_ns1_to_ns2.xml",
        "host_update_failed_response.xml",
        "2000-06-01T00:06:00Z");
    // Verify that host1 and host2 still exist in their unmodified states.
    assertCommandAndResponse(
        "host_info_ns1.xml",
        "host_info_response_ns1.xml",
        "2000-06-01T00:07:00Z");
    assertCommandAndResponse(
        "host_info_ns2.xml",
        "host_info_response_ns2.xml",
        "2000-06-01T00:08:00Z");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testApplicationDuringSunrise_doesntCreateDomainWithoutAllocation() throws Exception {
    ImmutableSortedMap<DateTime, TldState> transitions = ImmutableSortedMap.of(
        DateTimeUtils.START_OF_TIME, TldState.SUNRISE,
        START_OF_GA, TldState.GENERAL_AVAILABILITY);
    createTld("example", transitions);

    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createContactsAndHosts();
    // Note that the trademark is valid from 2013-08-09 to 2017-07-23, hence the domain creation
    // in 2014.
    assertCommandAndResponse(
        "domain_create_sunrise_encoded_mark.xml",
        "domain_create_sunrise_encoded_signed_mark_response.xml",
        "2014-01-01T00:00:00Z");
    assertCommandAndResponse(
        "domain_info_testvalidate.xml",
        "domain_info_response_testvalidate_doesnt_exist.xml",
        "2014-01-01T00:01:00Z");

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainCreation_failsBeforeSunrise() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");

    DateTime sunriseDate = DateTime.parse("2000-05-30T00:00:00Z");
    ImmutableSortedMap<DateTime, TldState> transitions = ImmutableSortedMap.of(
        START_OF_TIME, TldState.PREDELEGATION,
        sunriseDate, TldState.SUNRISE,
        sunriseDate.plusMonths(2), TldState.GENERAL_AVAILABILITY);
    createTld("example", transitions);

    setClientIdentifier("NewRegistrar");
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
    assertCommandAndResponse("login_valid_fee_extension.xml", "login_response.xml");

    DateTime gaDate = DateTime.parse("2000-05-30T00:00:00Z");
    ImmutableSortedMap<DateTime, TldState> transitions = ImmutableSortedMap.of(
        START_OF_TIME, TldState.PREDELEGATION,
        gaDate, TldState.GENERAL_AVAILABILITY);
    createTld("example", transitions);

    assertCommandAndResponse(
        "domain_check_fee_premium.xml",
        "domain_check_fee_premium_response.xml",
        gaDate.plusDays(1));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testRemoteXmlExternalEntity() throws Exception {
    // Check go/XXE
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_remote_xxe.xml",
        "contact_create_remote_response_xxe.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testLocalXmlExternalEntity() throws Exception {
    // Check go/XXE
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_local_xxe.xml",
        "contact_create_local_response_xxe.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testBillionLaughsAttack() throws Exception {
    // Check go/XXE
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "contact_create_billion_laughs.xml",
        "contact_create_response_billion_laughs.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
