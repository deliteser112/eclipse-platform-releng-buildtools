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

import static com.google.common.truth.Truth8.assertThat;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_AND_CLOSE;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_WITH_ACTION_PENDING;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.assertBillingEventsForResource;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.getOnlyHistoryEntryOfType;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.stripBillingEventId;
import static google.registry.testing.EppMetricSubject.assertThat;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.googlecode.objectify.Key;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.OneTime;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainResource;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.reporting.HistoryEntry.Type;
import google.registry.testing.AppEngineRule;
import java.util.Objects;
import java.util.Optional;
import org.joda.money.Money;
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
  public final AppEngineRule appEngine =
      AppEngineRule.builder().withDatastore().withTaskQueue().build();

  @Before
  public void initTld() {
    createTlds("example", "tld");
  }

  /** Create the two administrative contacts and two hosts. */
  void createContactsAndHosts() throws Exception {
    DateTime createTime = DateTime.parse("2000-06-01T00:00:00Z");
    createContacts(createTime);
    assertCommandAndResponse(
        "host_create.xml",
        ImmutableMap.of("HOSTNAME", "ns1.example.external"),
        "host_create_response.xml",
        ImmutableMap.of(
            "HOSTNAME", "ns1.example.external", "CRDATE", createTime.plusMinutes(2).toString()),
        createTime.plusMinutes(2));
    assertCommandAndResponse(
        "host_create.xml",
        ImmutableMap.of("HOSTNAME", "ns2.example.external"),
        "host_create_response.xml",
        ImmutableMap.of(
            "HOSTNAME", "ns2.example.external", "CRDATE", createTime.plusMinutes(3).toString()),
        createTime.plusMinutes(3));
  }

  private void createContacts(DateTime createTime) throws Exception {
    assertCommandAndResponse(
        "contact_create_sh8013.xml",
        ImmutableMap.of(),
        "contact_create_response_sh8013.xml",
        ImmutableMap.of("CRDATE", createTime.toString()),
        createTime);
    assertCommandAndResponse(
        "contact_create_jd1234.xml",
        ImmutableMap.of(),
        "contact_create_response_jd1234.xml",
        ImmutableMap.of("CRDATE", createTime.plusMinutes(1).toString()),
        createTime.plusMinutes(1));
  }

  /** Creates the domain fakesite.example with two nameservers on it. */
  void createFakesite() throws Exception {
    createContactsAndHosts();
    assertCommandAndResponse(
        "domain_create_fakesite.xml",
        ImmutableMap.of(),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "fakesite.example",
            "CRDATE", "2000-06-01T00:04:00.0Z",
            "EXDATE", "2002-06-01T00:04:00.0Z"),
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
        "host_info_response_fakesite_linked.xml",
        DateTime.parse("2000-06-08T00:02:00Z"));
  }

  @Test
  public void testDomainDeleteRestore() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createContacts(DateTime.parse("2000-06-01T00:00:00Z"));

    // Create domain example.tld
    assertCommandAndResponse(
        "domain_create_no_hosts_or_dsdata.xml",
        ImmutableMap.of(),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "example.tld",
            "CRDATE", "2000-06-01T00:02:00.0Z",
            "EXDATE", "2002-06-01T00:02:00.0Z"),
        DateTime.parse("2000-06-01T00:02:00Z"));

    // Delete domain example.tld after its add grace period has expired.
    assertCommandAndResponse(
        "domain_delete.xml", ImmutableMap.of("NAME", "example.tld"),
        "generic_success_action_pending_response.xml", ImmutableMap.of(),
        DateTime.parse("2000-07-01T00:02:00Z"));

    // Restore the domain.
    assertCommandAndResponse(
        "domain_update_restore_request.xml",
        "domain_update_restore_request_response.xml",
        DateTime.parse("2000-07-01T00:03:00Z"));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainDeletion_withinAddGracePeriod_deletesImmediately() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createContacts(DateTime.parse("2000-06-01T00:00:00Z"));

    // Create domain example.tld
    DateTime createTime = DateTime.parse("2000-06-01T00:02:00Z");
    assertCommandAndResponse(
        "domain_create_no_hosts_or_dsdata.xml",
        ImmutableMap.of(),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "example.tld",
            "CRDATE", "2000-06-01T00:02:00.0Z",
            "EXDATE", "2002-06-01T00:02:00.0Z"),
        createTime);

    DomainResource domain =
        loadByForeignKey(DomainResource.class, "example.tld", createTime.plusHours(1));

    // Delete domain example.tld within the add grace period.
    DateTime deleteTime = createTime.plusDays(1);
    assertCommandAndResponse(
        "domain_delete.xml", ImmutableMap.of("NAME", "example.tld"),
        "generic_success_response.xml", ImmutableMap.of(),
        deleteTime);

    // Verify that it is immediately non-existent.
    assertCommandAndResponse(
        "domain_info.xml",
        ImmutableMap.of(),
        "response_error.xml",
        ImmutableMap.of(
            "MSG", "The domain with given ID (example.tld) doesn't exist.", "CODE", "2303"),
        deleteTime.plusSeconds(1));

    // The expected one-time billing event, that should have an associated Cancellation.
    OneTime oneTimeCreateBillingEvent = makeOneTimeCreateBillingEvent(domain, createTime);
    // Verify that the OneTime billing event associated with the domain creation is canceled.
    assertBillingEventsForResource(
        domain,
        // Check the existence of the expected create one-time billing event.
        oneTimeCreateBillingEvent,
        makeRecurringCreateBillingEvent(domain, createTime, deleteTime),
        // Check for the existence of a cancellation for the given one-time billing event.
        makeCancellationBillingEventFor(
            domain, oneTimeCreateBillingEvent, createTime, deleteTime));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainDeletion_outsideAddGracePeriod_showsRedemptionPeriod() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createContacts(DateTime.parse("2000-06-01T00:00:00Z"));

    DateTime createTime = DateTime.parse("2000-06-01T00:02:00Z");
    // Create domain example.tld
    assertCommandAndResponse(
        "domain_create_no_hosts_or_dsdata.xml",
        ImmutableMap.of(),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "example.tld",
            "CRDATE", "2000-06-01T00:02:00.0Z",
            "EXDATE", "2002-06-01T00:02:00.0Z"),
        createTime);

    DateTime deleteTime = DateTime.parse("2000-07-07T00:02:00Z"); // 1 month and 6 days after
    // Delete domain example.tld after its add grace period has expired.
    assertCommandAndResponse(
        "domain_delete.xml", ImmutableMap.of("NAME", "example.tld"),
        "generic_success_action_pending_response.xml", ImmutableMap.of(),
        deleteTime);

    // Verify that domain shows redemptionPeriod soon after deletion.
    assertCommandAndResponse(
        "domain_info.xml",
        ImmutableMap.of(),
        "domain_info_response_wildcard.xml",
        ImmutableMap.of("STATUS", "redemptionPeriod"),
        DateTime.parse("2000-07-08T00:00:00Z"));

    // Verify that the domain shows pendingDelete next.
    assertCommandAndResponse(
        "domain_info.xml",
        ImmutableMap.of(),
        "domain_info_response_wildcard.xml",
        ImmutableMap.of("STATUS", "pendingDelete"),
        DateTime.parse("2000-08-08T00:00:00Z"));

    // Verify that the domain is non-existent (available for registration) later.
    assertCommandAndResponse(
        "domain_info.xml",
        ImmutableMap.of(),
        "response_error.xml",
        ImmutableMap.of(
            "MSG", "The domain with given ID (example.tld) doesn't exist.", "CODE", "2303"),
        DateTime.parse("2000-09-01T00:00:00Z"));

    DomainResource domain =
        loadByForeignKey(
            DomainResource.class, "example.tld", DateTime.parse("2000-08-01T00:02:00Z"));
    // Verify that the autorenew was ended and that the one-time billing event is not canceled.
    assertBillingEventsForResource(
        domain,
        makeOneTimeCreateBillingEvent(domain, createTime),
        makeRecurringCreateBillingEvent(domain, createTime, deleteTime));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testEapDomainDeletion_withinAddGracePeriod_eapFeeIsNotRefunded() throws Exception {
    assertCommandAndResponse("login_valid_fee_extension.xml", "login_response.xml");
    createContacts(DateTime.parse("2000-06-01T00:00:00Z"));

    // Set the EAP schedule.
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setEapFeeSchedule(
                ImmutableSortedMap.of(
                    START_OF_TIME, Money.of(USD, 0),
                    DateTime.parse("2000-06-01T00:00:00Z"), Money.of(USD, 100),
                    DateTime.parse("2000-06-02T00:00:00Z"), Money.of(USD, 0)))
            .build());

    // Create domain example.tld, which should have an EAP fee of USD 100.
    DateTime createTime = DateTime.parse("2000-06-01T00:02:00Z");
    assertCommandAndResponse(
        "domain_create_eap_fee.xml",
        "domain_create_response_eap_fee.xml",
        createTime);

    DomainResource domain =
        loadByForeignKey(
            DomainResource.class, "example.tld", DateTime.parse("2000-06-01T00:03:00Z"));

    // Delete domain example.tld within the add grade period.
    DateTime deleteTime = createTime.plusDays(1);
    assertCommandAndResponse(
        "domain_delete.xml", ImmutableMap.of("NAME", "example.tld"),
        "domain_delete_response_fee.xml", ImmutableMap.of(),
        deleteTime);

    // Verify that the OneTime billing event associated with the base fee of domain registration and
    // is canceled and the autorenew is ended, but that the EAP fee is not canceled.
    OneTime expectedCreateEapBillingEvent =
        new BillingEvent.OneTime.Builder()
            .setReason(Reason.FEE_EARLY_ACCESS)
            .setTargetId("example.tld")
            .setClientId("NewRegistrar")
            .setCost(Money.parse("USD 100.00"))
            .setEventTime(createTime)
            .setBillingTime(createTime.plus(Registry.get("tld").getRenewGracePeriodLength()))
            .setParent(getOnlyHistoryEntryOfType(domain, Type.DOMAIN_CREATE))
            .build();

    // The expected one-time billing event, that should have an associated Cancellation.
    OneTime expectedOneTimeCreateBillingEvent = makeOneTimeCreateBillingEvent(domain, createTime);
    assertBillingEventsForResource(
        domain,
        // Check for the expected create one-time billing event ...
        expectedOneTimeCreateBillingEvent,
        // ... and the expected one-time EAP fee billing event ...
        expectedCreateEapBillingEvent,
        makeRecurringCreateBillingEvent(domain, createTime, deleteTime),
        // ... and verify that the create one-time billing event was canceled ...
        makeCancellationBillingEventFor(
            domain, expectedOneTimeCreateBillingEvent, createTime, deleteTime));
    // ... but there was NOT a Cancellation for the EAP fee, as this would fail if additional
    // billing events were present.

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  /** Makes a one-time billing event corresponding to the given domain's creation. */
  private static BillingEvent.OneTime makeOneTimeCreateBillingEvent(
      DomainResource domain, DateTime createTime) {
    return new BillingEvent.OneTime.Builder()
        .setReason(Reason.CREATE)
        .setTargetId(domain.getFullyQualifiedDomainName())
        .setClientId(domain.getCurrentSponsorClientId())
        .setCost(Money.parse("USD 26.00"))
        .setPeriodYears(2)
        .setEventTime(createTime)
        .setBillingTime(createTime.plus(Registry.get(domain.getTld()).getRenewGracePeriodLength()))
        .setParent(getOnlyHistoryEntryOfType(domain, Type.DOMAIN_CREATE))
        .build();
  }

  /** Makes a recurring billing event corresponding to the given domain's creation. */
  private static BillingEvent.Recurring makeRecurringCreateBillingEvent(
      DomainResource domain, DateTime createTime, DateTime endTime) {
    return new BillingEvent.Recurring.Builder()
        .setReason(Reason.RENEW)
        .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
        .setTargetId(domain.getFullyQualifiedDomainName())
        .setClientId(domain.getCurrentSponsorClientId())
        .setEventTime(createTime.plusYears(2))
        .setRecurrenceEndTime(endTime)
        .setParent(getOnlyHistoryEntryOfType(domain, Type.DOMAIN_CREATE))
        .build();
  }

  /** Makes a cancellation billing event cancelling out the given domain create billing event. */
  private static BillingEvent.Cancellation makeCancellationBillingEventFor(
      DomainResource domain,
      OneTime billingEventToCancel,
      DateTime createTime,
      DateTime deleteTime) {
    return new BillingEvent.Cancellation.Builder()
        .setTargetId(domain.getFullyQualifiedDomainName())
        .setClientId(domain.getCurrentSponsorClientId())
        .setEventTime(deleteTime)
        .setOneTimeEventKey(findKeyToActualOneTimeBillingEvent(billingEventToCancel))
        .setBillingTime(createTime.plus(Registry.get(domain.getTld()).getRenewGracePeriodLength()))
        .setReason(Reason.CREATE)
        .setParent(getOnlyHistoryEntryOfType(domain, Type.DOMAIN_DELETE))
        .build();
  }

  /**
   * Finds the Key to the actual one-time create billing event associated with a domain's creation.
   *
   * <p>This is used in the situation where we have created an expected billing event associated
   * with the domain's creation (which is passed as the parameter here), then need to locate the key
   * to the actual billing event in Datastore that would be seen on a Cancellation billing event.
   * This is necessary because the ID will be different even though all the rest of the fields are
   * the same.
   */
  private static Key<OneTime> findKeyToActualOneTimeBillingEvent(OneTime expectedBillingEvent) {
    Optional<OneTime> actualCreateBillingEvent =
        ofy()
            .load()
            .type(BillingEvent.OneTime.class)
            .list()
            .stream()
            .filter(
                b ->
                    Objects.equals(
                        stripBillingEventId(b), stripBillingEventId(expectedBillingEvent)))
            .findFirst();
    assertThat(actualCreateBillingEvent).isPresent();
    return Key.create(actualCreateBillingEvent.get());
  }

  @Test
  public void testDomainDeletionWithSubordinateHost_fails() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    createSubordinateHost();
    assertCommandAndResponse(
        "domain_delete.xml",
        ImmutableMap.of("NAME", "fakesite.example"),
        "response_error.xml",
        ImmutableMap.of("MSG", "Domain to be deleted has subordinate hosts", "CODE", "2305"),
        DateTime.parse("2002-05-30T01:01:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDeletionOfDomain_afterRenameOfSubordinateHost_succeeds() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasNoTld()
        .and()
        .hasCommandName("Login")
        .and()
        .hasStatus(SUCCESS);
    createFakesite();
    createSubordinateHost();
    // Update the ns3 host to no longer be on fakesite.example domain.
    assertCommandAndResponse(
        "host_update_fakesite.xml",
        "generic_success_response.xml",
        DateTime.parse("2002-05-30T01:01:00Z"));
    // Add assert about EppMetric
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasCommandName("HostUpdate")
        .and()
        .hasEppTarget("ns3.fakesite.example")
        .and()
        .hasStatus(SUCCESS);
    // Delete the fakesite.example domain (which should succeed since it no longer has subords).
    assertCommandAndResponse(
        "domain_delete.xml", ImmutableMap.of("NAME", "fakesite.example"),
        "generic_success_action_pending_response.xml", ImmutableMap.of(),
        DateTime.parse("2002-05-30T01:02:00Z"));
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasTld("example")
        .and()
        .hasCommandName("DomainDelete")
        .and()
        .hasEppTarget("fakesite.example")
        .and()
        .hasStatus(SUCCESS_WITH_ACTION_PENDING);
    // Check info on the renamed host and verify that it's still around and wasn't deleted.
    assertCommandAndResponse(
        "host_info_ns9000_example.xml",
        "host_info_response_ns9000_example.xml",
        DateTime.parse("2002-06-30T01:03:00Z"));
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasCommandName("HostInfo")
        .and()
        .hasEppTarget("ns9000.example.external")
        .and()
        .hasStatus(SUCCESS);
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasCommandName("Logout")
        .and()
        .hasStatus(SUCCESS_AND_CLOSE);
  }

  @Test
  public void testDeletionOfDomain_afterUpdateThatCreatesSubordinateHost_fails() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();

    // Create domain example.tld
    assertCommandAndResponse(
        "domain_create_no_hosts_or_dsdata.xml",
        ImmutableMap.of(),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "example.tld",
            "CRDATE", "2000-06-02T00:00:00.0Z",
            "EXDATE", "2002-06-02T00:00:00.0Z"),
        DateTime.parse("2000-06-02T00:00:00Z"));

    // Create nameserver ns1.example.tld
    assertCommandAndResponse(
        "host_create_example.xml",
        "host_create_response_example.xml",
        DateTime.parse("2000-06-02T00:01:00Z"));

    // Update the ns1 host to be on the fakesite.example domain.
    assertCommandAndResponse(
        "host_update_ns1_to_fakesite.xml",
        "generic_success_response.xml",
        DateTime.parse("2002-05-30T01:01:00Z"));
    // Attempt to delete the fakesite.example domain (which should fail since it now has a
    // subordinate host).
    assertCommandAndResponse(
        "domain_delete.xml",
        ImmutableMap.of("NAME", "fakesite.example"),
        "response_error.xml",
        ImmutableMap.of("MSG", "Domain to be deleted has subordinate hosts", "CODE", "2305"),
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
  public void testDomainCreation_failsBeforeSunrise() throws Exception {
    DateTime sunriseDate = DateTime.parse("2000-05-30T00:00:00Z");
    createTld(
        "example",
        ImmutableSortedMap.of(
            START_OF_TIME,
            TldState.PREDELEGATION,
            sunriseDate,
            TldState.SUNRISE,
            sunriseDate.plusMonths(2),
            TldState.GENERAL_AVAILABILITY));

    assertCommandAndResponse("login_valid.xml", "login_response.xml");

    createContactsAndHosts();

    assertCommandAndResponse(
        "domain_create_sunrise_encoded_mark.xml",
        ImmutableMap.of(),
        "response_error.xml",
        ImmutableMap.of(
            "MSG", "Command is not allowed in the current registry phase", "CODE", "2002"),
        sunriseDate.minusDays(1));

    assertCommandAndResponse(
        "domain_info_testvalidate.xml",
        ImmutableMap.of(),
        "response_error.xml",
        ImmutableMap.of(
            "MSG", "The domain with given ID (test-validate.example) doesn't exist.",
            "CODE", "2303"),
        sunriseDate.plusDays(1));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainCheckFee_succeeds() throws Exception {
    DateTime gaDate = DateTime.parse("2000-05-30T00:00:00Z");
    createTld(
        "example",
        ImmutableSortedMap.of(
            START_OF_TIME, TldState.PREDELEGATION,
            gaDate, TldState.GENERAL_AVAILABILITY));

    assertCommandAndResponse("login_valid_fee_extension.xml", "login_response.xml");

    assertCommandAndResponse(
        "domain_check_fee_premium.xml",
        "domain_check_fee_premium_response.xml",
        gaDate.plusDays(1));
    assertThat(getRecordedEppMetric())
        .hasClientId("NewRegistrar")
        .and()
        .hasCommandName("DomainCheck")
        .and()
        .hasEppTarget("rich.example")
        .and()
        .hasTld("example")
        .and()
        .hasStatus(SUCCESS);

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testDomainCreate_annualAutoRenewPollMessages_haveUniqueIds() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Create the domain.
    createFakesite();

    // The first autorenew poll message isn't seen until after the initial two years of registration
    // are up.
    assertCommandAndResponse(
        "poll.xml", "poll_response_empty.xml", DateTime.parse("2001-01-01T00:01:00Z"));
    assertCommandAndResponse(
        "poll.xml",
        ImmutableMap.of(),
        "poll_response_autorenew.xml",
        ImmutableMap.of(
            "ID", "1-C-EXAMPLE-13-16-2002",
            "QDATE", "2002-06-01T00:04:00Z",
            "DOMAIN", "fakesite.example",
            "EXDATE", "2003-06-01T00:04:00Z"),
        DateTime.parse("2002-07-01T00:01:00Z"));
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-C-EXAMPLE-13-16-2002"),
        "poll_ack_response_empty.xml",
        ImmutableMap.of(),
        DateTime.parse("2002-07-01T00:02:00Z"));

    // The second autorenew poll message isn't seen until after another year, and it should have a
    // different ID.
    assertCommandAndResponse(
        "poll.xml", "poll_response_empty.xml", DateTime.parse("2002-07-01T00:05:00Z"));
    assertCommandAndResponse(
        "poll.xml",
        ImmutableMap.of(),
        "poll_response_autorenew.xml",
        ImmutableMap.of(
            "ID", "1-C-EXAMPLE-13-16-2003", // Note -- Year is different from previous ID.
            "QDATE", "2003-06-01T00:04:00Z",
            "DOMAIN", "fakesite.example",
            "EXDATE", "2004-06-01T00:04:00Z"),
        DateTime.parse("2003-07-01T00:05:00Z"));

    // Ack the second poll message and verify that none remain.
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-C-EXAMPLE-13-16-2003"),
        "poll_ack_response_empty.xml",
        ImmutableMap.of(),
        DateTime.parse("2003-07-01T00:05:05Z"));
    assertCommandAndResponse(
        "poll.xml", "poll_response_empty.xml", DateTime.parse("2003-07-01T00:05:10Z"));

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
    String response =
        assertCommandAndResponse(
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
        ImmutableMap.of("ID", "1-C-EXAMPLE-17-23-2001"),
        "poll_ack_response_empty.xml",
        ImmutableMap.of(),
        DateTime.parse("2001-01-01T00:01:00Z"));

    // Five days in the future, expect a server approval poll message to the loser, and ack it.
    assertCommandAndResponse(
        "poll.xml",
        "poll_response_domain_transfer_server_approve_loser.xml",
        DateTime.parse("2001-01-06T00:01:00Z"));
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-C-EXAMPLE-17-22-2001"),
        "poll_ack_response_empty.xml",
        ImmutableMap.of(),
        DateTime.parse("2001-01-06T00:01:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Also expect a server approval poll message to the winner, with the transfer request trid.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "poll.xml",
        ImmutableMap.of(),
        "poll_response_domain_transfer_server_approve_winner.xml",
        ImmutableMap.of("SERVER_TRID", transferRequestTrid),
        DateTime.parse("2001-01-06T00:02:00Z"));
    assertCommandAndResponse(
        "poll_ack.xml",
        ImmutableMap.of("ID", "1-C-EXAMPLE-17-21-2001"),
        "poll_ack_response_empty.xml",
        ImmutableMap.of(),
        DateTime.parse("2001-01-06T00:02:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testTransfer_autoRenewGraceActive_onlyAtAutomaticTransferTime_getsSubsumed()
      throws Exception {
    // Register the domain as the first registrar.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createFakesite();
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request.xml",
        "domain_transfer_response.xml",
        DateTime.parse("2002-05-30T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the first registrar and verify things.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
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
    // Verify that domain is in the transfer period now with expiration date still one year out,
    // since the transfer should subsume the autorenew that happened during the transfer window.
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
        "domain_transfer_request.xml",
        "domain_transfer_response.xml",
        DateTime.parse("2002-05-30T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Log back in as the first registrar and verify domain is pending transfer.
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_info_fakesite.xml",
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
        ImmutableMap.of(),
        "host_info_response_fakesite_post_transfer.xml",
        ImmutableMap.of("TRDATE", "2002-06-04T00:00:00Z"),
        DateTime.parse("2002-06-09T00:01:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testRenewalFails_whenTotalTermExceeds10Years() throws Exception {
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    // Creates domain with 2 year expiration.
    createFakesite();
    // Attempt to renew for 9 years, adding up to a total greater than the allowed max of 10 years.
    assertCommandAndResponse(
        "domain_renew.xml",
        ImmutableMap.of("DOMAIN", "fakesite.example", "EXPDATE", "2002-06-01", "YEARS", "9"),
        "domain_renew_response_exceeds_max_years.xml",
        ImmutableMap.of(),
        DateTime.parse("2000-06-07T00:00:00Z"));
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
        "domain_transfer_request.xml",
        "domain_transfer_response.xml",
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
        "domain_delete.xml", ImmutableMap.of("NAME", "fakesite.example"),
        "generic_success_action_pending_response.xml", ImmutableMap.of(),
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
        ImmutableMap.of(),
        "response_error.xml",
        ImmutableMap.of("MSG", "Object has no transfer history", "CODE", "2002"),
        DateTime.parse("2000-09-02T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");

    // Request a transfer of the domain to the second registrar.
    assertCommandAndResponse("login2_valid.xml", "login_response.xml");
    assertCommandAndResponse(
        "domain_transfer_request_1_year.xml",
        "domain_transfer_response_1_year.xml",
        DateTime.parse("2001-01-01T00:00:00Z"));
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
        ImmutableMap.of(),
        "response_error.xml",
        ImmutableMap.of("MSG", "Object has no transfer history", "CODE", "2002"),
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
        ImmutableMap.of("HOSTNAME", "ns3.fakesite.example"),
        "host_info_response_fakesite_post_transfer.xml",
        ImmutableMap.of("TRDATE", "2001-01-06T00:00:00.000Z"),
        DateTime.parse("2001-01-07T00:00:00Z"));
    assertCommandAndResponse(
        "domain_create_secondsite.xml",
        ImmutableMap.of(),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "secondsite.example",
            "CRDATE", "2001-01-08T00:00:00.0Z",
            "EXDATE", "2003-01-08T00:00:00.0Z"),
        DateTime.parse("2001-01-08T00:00:00Z"));
    // Update the host to be subordinate to a different domain by renaming it to
    // ns3.secondsite.example
    assertCommandAndResponse(
        "host_update_rename_only.xml",
        ImmutableMap.of("oldName", "ns3.fakesite.example", "newName", "ns3.secondsite.example"),
        "generic_success_response.xml",
        ImmutableMap.of(),
        DateTime.parse("2002-05-30T01:01:00Z"));
    // The last transfer time on the host should still be what it was from the transfer.
    assertCommandAndResponse(
        "host_info.xml",
        ImmutableMap.of("HOSTNAME", "ns3.secondsite.example"),
        "host_info_response_fakesite_post_transfer_and_update.xml",
        ImmutableMap.of(
            "HOSTNAME", "ns3.secondsite.example",
            "TRDATE", "2001-01-06T00:00:00.000Z"),
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
        ImmutableMap.of(),
        "response_error.xml",
        ImmutableMap.of("MSG", "Object has no transfer history", "CODE", "2002"),
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
        ImmutableMap.of(),
        "host_info_response_fakesite_post_transfer.xml",
        ImmutableMap.of("TRDATE", "2001-01-06T00:00:00.000Z"),
        DateTime.parse("2001-01-07T00:00:00Z"));
    // Update the host to be external by renaming it to ns3.notarealsite.external
    assertCommandAndResponse(
        "host_update_rename_and_remove_addresses.xml",
        ImmutableMap.of(
            "oldName", "ns3.fakesite.example",
            "newName", "ns3.notarealsite.external"),
        "generic_success_response.xml",
        ImmutableMap.of(),
        DateTime.parse("2002-05-30T01:01:00Z"));
    // The last transfer time on the host should still be what it was from the transfer.
    assertCommandAndResponse(
        "host_info.xml",
        ImmutableMap.of("HOSTNAME", "ns3.notarealsite.external"),
        "host_info_response_fakesite_post_transfer_and_update_no_addresses.xml",
        ImmutableMap.of(
            "HOSTNAME", "ns3.notarealsite.external",
            "TRDATE", "2001-01-06T00:00:00.000Z"),
        DateTime.parse("2001-01-07T00:00:00Z"));
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testSuccess_multipartTldsWithSharedSuffixes() throws Exception {
    createTlds("bar.foo.tld", "foo.tld");

    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createContacts(DateTime.parse("2000-06-01T00:00:00.000Z"));

    // Create domain example.bar.foo.tld
    assertCommandAndResponse(
        "domain_create_wildcard.xml",
        ImmutableMap.of("HOSTNAME", "example.bar.foo.tld"),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "example.bar.foo.tld",
            "CRDATE", "2000-06-01T00:02:00Z",
            "EXDATE", "2002-06-01T00:02:00Z"),
        DateTime.parse("2000-06-01T00:02:00.001Z"));

    // Create domain example.foo.tld
    assertCommandAndResponse(
        "domain_create_wildcard.xml",
        ImmutableMap.of("HOSTNAME", "example.foo.tld"),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "example.foo.tld",
            "CRDATE", "2000-06-01T00:02:00Z",
            "EXDATE", "2002-06-01T00:02:00Z"),
        DateTime.parse("2000-06-01T00:02:00.002Z"));

    // Create domain example.tld
    assertCommandAndResponse(
        "domain_create_wildcard.xml",
        ImmutableMap.of("HOSTNAME", "example.tld"),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "example.tld",
            "CRDATE", "2000-06-01T00:02:00Z",
            "EXDATE", "2002-06-01T00:02:00Z"),
        DateTime.parse("2000-06-01T00:02:00.003Z"));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testSuccess_multipartTldsWithSharedPrefixes() throws Exception {
    createTld("tld.foo");

    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    createContacts(DateTime.parse("2000-06-01T00:00:00.000Z"));

    // Create domain example.tld.foo
    assertCommandAndResponse(
        "domain_create_wildcard.xml",
        ImmutableMap.of("HOSTNAME", "example.tld.foo"),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "example.tld.foo",
            "CRDATE", "2000-06-01T00:02:00Z",
            "EXDATE", "2002-06-01T00:02:00Z"),
        DateTime.parse("2000-06-01T00:02:00.001Z"));

    // Create domain example.tld
    assertCommandAndResponse(
        "domain_create_wildcard.xml",
        ImmutableMap.of("HOSTNAME", "example.tld"),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "example.tld",
            "CRDATE", "2000-06-01T00:02:00Z",
            "EXDATE", "2002-06-01T00:02:00Z"),
        DateTime.parse("2000-06-01T00:02:00.002Z"));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  /**
   * Test a full launch of start-date sunrise.
   *
   * We show that we can't create during pre-delegation, can only create with an encoded mark during
   * start-date sunrise - which we can then delete "as normal" (no need for a signed mark or
   * anything for delete), and then use "regular" create during general-availability.
   */
  @Test
  public void testDomainCreation_startDateSunriseFull() throws Exception {
    // The signed mark is valid between 2013 and 2017
    DateTime sunriseDate = DateTime.parse("2014-09-08T09:09:09Z");
    DateTime gaDate = sunriseDate.plusDays(30);
    createTld(
        "example",
        ImmutableSortedMap.of(
            START_OF_TIME, TldState.PREDELEGATION,
            sunriseDate, TldState.START_DATE_SUNRISE,
            gaDate, TldState.GENERAL_AVAILABILITY));

    assertCommandAndResponse("login_valid.xml", "login_response.xml", sunriseDate.minusDays(3));

    createContactsAndHosts();

    // During pre-delegation, any create should fail both with and without mark
    assertCommandAndResponse(
        "domain_create_start_date_sunrise_encoded_mark.xml",
        ImmutableMap.of(),
        "response_error.xml",
        ImmutableMap.of(
            "MSG", "Declared launch extension phase does not match the current registry phase",
            "CODE", "2306"),
        sunriseDate.minusDays(2));

    assertCommandAndResponse(
        "domain_create_wildcard.xml",
        ImmutableMap.of("HOSTNAME", "general.example"),
        "response_error.xml",
        ImmutableMap.of(
            "MSG", "The current registry phase does not allow for general registrations",
            "CODE", "2002"),
        sunriseDate.minusDays(1));

    // During start-date sunrise, create with mark will succeed but without will fail.
    // We also test we can delete without a mark.
    assertCommandAndResponse(
        "domain_create_start_date_sunrise_encoded_mark.xml",
        ImmutableMap.of(),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "test-validate.example",
            "CRDATE", "2014-09-09T09:09:09Z",
            "EXDATE", "2015-09-09T09:09:09Z"),
        sunriseDate.plusDays(1));

    assertCommandAndResponse(
        "domain_delete.xml", ImmutableMap.of("NAME", "test-validate.example"),
        "generic_success_response.xml", ImmutableMap.of(),
        sunriseDate.plusDays(1).plusMinutes(1));

    assertCommandAndResponse(
        "domain_create_wildcard.xml",
        ImmutableMap.of("HOSTNAME", "general.example"),
        "response_error.xml",
        ImmutableMap.of(
            "MSG", "The current registry phase requires a signed mark for registrations",
            "CODE", "2002"),
        sunriseDate.plusDays(2));

    // During general availability, sunrise creates will fail but regular creates succeed
    assertCommandAndResponse(
        "domain_create_start_date_sunrise_encoded_mark.xml",
        ImmutableMap.of(),
        "response_error.xml",
        ImmutableMap.of(
            "MSG", "Declared launch extension phase does not match the current registry phase",
            "CODE", "2306"),
        gaDate.plusDays(1));

    assertCommandAndResponse(
        "domain_create_wildcard.xml",
        ImmutableMap.of("HOSTNAME", "general.example"),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "general.example",
            "CRDATE", "2014-10-10T09:09:09Z",
            "EXDATE", "2016-10-10T09:09:09Z"),
        gaDate.plusDays(2));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  /**
   * Test that missing type= argument on launch create works in start-date sunrise.
   *
   * <p>TODO(b/76095570):have the same exact test on end-date sunrise - using the same .xml file -
   * that checks that an application was created.
   */
  @Test
  public void testDomainCreation_startDateSunrise_noType() throws Exception {
    // The signed mark is valid between 2013 and 2017
    DateTime sunriseDate = DateTime.parse("2014-09-08T09:09:09Z");
    DateTime gaDate = sunriseDate.plusDays(30);
    createTld(
        "example",
        ImmutableSortedMap.of(
            START_OF_TIME, TldState.PREDELEGATION,
            sunriseDate, TldState.START_DATE_SUNRISE,
            gaDate, TldState.GENERAL_AVAILABILITY));

    assertCommandAndResponse("login_valid.xml", "login_response.xml", sunriseDate.minusDays(3));

    createContactsAndHosts();

    // During start-date sunrise, create with mark will succeed but without will fail.
    // We also test we can delete without a mark.
    assertCommandAndResponse(
        "domain_info.xml",
        ImmutableMap.of("NAME", "test-validate.example"),
        "response_error.xml",
        ImmutableMap.of(
            "MSG", "The domain with given ID (example.tld) doesn't exist.", "CODE", "2303"),
        sunriseDate.plusDays(1));

    assertCommandAndResponse(
        "domain_create_start_date_sunrise_encoded_mark_no_type.xml",
        ImmutableMap.of(),
        "domain_create_response.xml",
        ImmutableMap.of(
            "NAME", "test-validate.example",
            "CRDATE", "2014-09-09T09:10:09Z",
            "EXDATE", "2015-09-09T09:10:09Z"),
        sunriseDate.plusDays(1).plusMinutes(1));

    assertCommandAndResponse(
        "domain_info_wildcard.xml",
        ImmutableMap.of("NAME", "test-validate.example"),
        "domain_info_response_ok_wildcard.xml",
        ImmutableMap.of(
            "NAME", "test-validate.example",
            "CRDATE", "2014-09-09T09:10:09Z",
            "EXDATE", "2015-09-09T09:10:09Z"),
        sunriseDate.plusDays(1).plusMinutes(2));

    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }
}
