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

package google.registry.flows.domain;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.assertBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.getOnlyHistoryEntryOfType;
import static google.registry.testing.DatastoreHelper.getOnlyPollMessage;
import static google.registry.testing.DatastoreHelper.getPollMessages;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainResourceSubject.assertAboutDomains;
import static google.registry.testing.HistoryEntrySubject.assertAboutHistoryEntries;
import static google.registry.testing.HostResourceSubject.assertAboutHosts;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.EUR;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.googlecode.objectify.Key;
import google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.domain.DomainFlowUtils.BadPeriodUnitException;
import google.registry.flows.domain.DomainFlowUtils.CurrencyUnitMismatchException;
import google.registry.flows.domain.DomainFlowUtils.CurrencyValueScaleException;
import google.registry.flows.domain.DomainFlowUtils.FeesMismatchException;
import google.registry.flows.domain.DomainFlowUtils.FeesRequiredForPremiumNameException;
import google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import google.registry.flows.domain.DomainFlowUtils.PremiumNameBlockedException;
import google.registry.flows.domain.DomainFlowUtils.UnsupportedFeeAttributeException;
import google.registry.flows.exceptions.AlreadyPendingTransferException;
import google.registry.flows.exceptions.MissingTransferRequestAuthInfoException;
import google.registry.flows.exceptions.ObjectAlreadySponsoredException;
import google.registry.flows.exceptions.ResourceStatusProhibitsOperationException;
import google.registry.flows.exceptions.TransferPeriodMustBeOneYearException;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Cancellation.Builder;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.contact.ContactAuthInfo;
import google.registry.model.domain.DomainAuthInfo;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.eppcommon.AuthInfo.PasswordAuth;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.poll.PendingActionNotificationResponse;
import google.registry.model.poll.PollMessage;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferResponse;
import google.registry.model.transfer.TransferStatus;
import java.util.Map;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainTransferRequestFlow}. */
public class DomainTransferRequestFlowTest
    extends DomainTransferFlowTestCase<DomainTransferRequestFlow, DomainResource> {

  private static final ImmutableMap<String, String> BASE_FEE_MAP =
      new ImmutableMap.Builder<String, String>()
          .put("DOMAIN", "example.tld")
          .put("YEARS", "1")
          .put("AMOUNT", "11.00")
          .build();
  private static final ImmutableMap<String, String> FEE_06_MAP =
      new ImmutableMap.Builder<String, String>()
          .putAll(BASE_FEE_MAP)
          .put("FEE_VERSION", "0.6")
          .put("FEE_NS", "fee")
          .build();
  private static final ImmutableMap<String, String> FEE_11_MAP =
      new ImmutableMap.Builder<String, String>()
          .putAll(BASE_FEE_MAP)
          .put("FEE_VERSION", "0.11")
          .put("FEE_NS", "fee11")
          .build();
  private static final ImmutableMap<String, String> FEE_12_MAP =
      new ImmutableMap.Builder<String, String>()
          .putAll(BASE_FEE_MAP)
          .put("FEE_VERSION", "0.12")
          .put("FEE_NS", "fee12")
          .build();

  @Before
  public void setUp() throws Exception {
    setEppInput("domain_transfer_request.xml");
    setClientIdForFlow("NewRegistrar");
  }

  private void assertTransferRequested(DomainResource domain) throws Exception {
    assertAboutDomains().that(domain)
        .hasTransferStatus(TransferStatus.PENDING).and()
        .hasTransferGainingClientId("NewRegistrar").and()
        .hasTransferLosingClientId("TheRegistrar").and()
        .hasTransferRequestClientTrid(getClientTrid()).and()
        .hasCurrentSponsorClientId("TheRegistrar").and()
        .hasPendingTransferExpirationTime(
            clock.nowUtc().plus(Registry.get(domain.getTld()).getAutomaticTransferLength())).and()
        .hasStatusValue(StatusValue.PENDING_TRANSFER);
  }

  private void assertTransferApproved(DomainResource domain) {
    DateTime afterAutoAck =
        clock.nowUtc().plus(Registry.get(domain.getTld()).getAutomaticTransferLength());
    assertAboutDomains().that(domain)
        .hasTransferStatus(TransferStatus.SERVER_APPROVED).and()
        .hasCurrentSponsorClientId("NewRegistrar").and()
        .hasLastTransferTime(afterAutoAck).and()
        .hasPendingTransferExpirationTime(afterAutoAck).and()
        .doesNotHaveStatusValue(StatusValue.PENDING_TRANSFER);
  }

  /**
   * Runs a successful test.  The extraExpectedBillingEvents parameter consists of cancellation
   * billing event builders that have had all of their attributes set except for the parent history
   * entry, which is filled in during the execution of this method.
   */
  private void doSuccessfulTest(
      String commandFilename,
      String expectedXmlFilename,
      DateTime expectedExpirationTime,
      Map<String, String> substitutions,
      Optional<Money> transferCost,
      BillingEvent.Cancellation.Builder... extraExpectedBillingEvents) throws Exception {
    setEppInput(commandFilename, substitutions);
    ImmutableSet<GracePeriod> originalGracePeriods = domain.getGracePeriods();
    // Replace the ROID in the xml file with the one generated in our test.
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // For all of the other transfer flow tests, 'now' corresponds to day 3 of the transfer, but
    // for the request test we want that same 'now' to be the initial request time, so we shift
    // the transfer timeline 3 days later by adjusting the implicit transfer time here.
    Registry registry = Registry.get(domain.getTld());
    DateTime implicitTransferTime = clock.nowUtc().plus(registry.getAutomaticTransferLength());
    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile(expectedXmlFilename, substitutions));
    // Transfer should have been requested. Verify correct fields were set.
    domain = reloadResourceByForeignKey();
    final HistoryEntry historyEntryTransferRequest =
        getOnlyHistoryEntryOfType(domain, HistoryEntry.Type.DOMAIN_TRANSFER_REQUEST);
    subordinateHost = reloadResourceAndCloneAtTime(subordinateHost, clock.nowUtc());
    assertTransferRequested(domain);
    assertAboutDomains().that(domain)
        .hasPendingTransferExpirationTime(implicitTransferTime).and()
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.DOMAIN_CREATE,
            HistoryEntry.Type.DOMAIN_TRANSFER_REQUEST);
    assertAboutHistoryEntries()
        .that(historyEntryTransferRequest)
        .hasPeriodYears(1)
        .and()
        .hasOtherClientId("TheRegistrar");
    assertAboutHosts().that(subordinateHost).hasNoHistoryEntries();
    assertThat(getPollMessages("TheRegistrar", clock.nowUtc())).hasSize(1);

    // A BillingEvent should be created AUTOMATIC_TRANSFER_DAYS in the future, for the case when the
    // transfer is implicitly acked, but there should be no grace period yet. There should also be
    // two autorenew billing events, one for the losing client that ends at the transfer time, and
    // one for the gaining client that starts at that time.
    BillingEvent.OneTime transferBillingEvent = new BillingEvent.OneTime.Builder()
        .setReason(Reason.TRANSFER)
        .setTargetId(domain.getFullyQualifiedDomainName())
        .setEventTime(implicitTransferTime)
        .setBillingTime(implicitTransferTime.plus(registry.getTransferGracePeriodLength()))
        .setClientId("NewRegistrar")
        .setCost(transferCost.or(Money.of(USD, 11)))
        .setPeriodYears(1)
        .setParent(historyEntryTransferRequest)
        .build();
    assertBillingEvents(FluentIterable.from(extraExpectedBillingEvents)
        .transform(new Function<BillingEvent.Cancellation.Builder, BillingEvent>() {
          @Override
          public BillingEvent apply(Builder builder) {
            return builder.setParent(historyEntryTransferRequest).build();
          }})
        .append(
            transferBillingEvent,
            // All of the other transfer flow tests happen on day 3 of the transfer, but the initial
            // request by definition takes place on day 1, so we need to edit the times in the
            // autorenew events from the base test case.
            getLosingClientAutorenewEvent().asBuilder()
                .setRecurrenceEndTime(implicitTransferTime)
                .build(),
            getGainingClientAutorenewEvent().asBuilder()
                .setEventTime(expectedExpirationTime)
                .build())
        .toArray(BillingEvent.class));
    // The domain's autorenew billing event should still point to the losing client's event.
    BillingEvent.Recurring domainAutorenewEvent =
        ofy().load().key(domain.getAutorenewBillingEvent()).now();
    assertThat(domainAutorenewEvent.getClientId()).isEqualTo("TheRegistrar");
    assertThat(domainAutorenewEvent.getRecurrenceEndTime()).isEqualTo(implicitTransferTime);
    // The original grace periods should remain untouched.
    assertThat(domain.getGracePeriods()).containsExactlyElementsIn(originalGracePeriods);
    // If we fast forward AUTOMATIC_TRANSFER_DAYS, we should see the grace period appear, the
    // transfer should have happened, and all other grace periods should be gone. Also, both the
    // gaining and losing registrars should have a new poll message.
    DomainResource domainAfterAutomaticTransfer = domain.cloneProjectedAtTime(implicitTransferTime);
    assertGracePeriods(
        domainAfterAutomaticTransfer.getGracePeriods(),
        ImmutableMap.of(
            GracePeriod.create(
                GracePeriodStatus.TRANSFER,
                clock.nowUtc()
                  .plus(registry.getAutomaticTransferLength())
                  .plus(registry.getTransferGracePeriodLength()),
                "NewRegistrar",
                null),
            transferBillingEvent));
    assertTransferApproved(domainAfterAutomaticTransfer);

    // Two poll messages on the gaining registrar's side at the expected expiration time: a
    // (OneTime) transfer approved message, and an Autorenew poll message.
    assertThat(getPollMessages("NewRegistrar", expectedExpirationTime)).hasSize(2);
    PollMessage transferApprovedPollMessage = getOnlyPollMessage(
        "NewRegistrar", implicitTransferTime, PollMessage.OneTime.class);
    PollMessage autorenewPollMessage = getOnlyPollMessage(
        "NewRegistrar", expectedExpirationTime, PollMessage.Autorenew.class);
    assertThat(transferApprovedPollMessage.getEventTime()).isEqualTo(implicitTransferTime);
    assertThat(autorenewPollMessage.getEventTime()).isEqualTo(expectedExpirationTime);
    assertThat(
        Iterables.getOnlyElement(FluentIterable
            .from(transferApprovedPollMessage.getResponseData())
            .filter(TransferResponse.class))
                .getTransferStatus())
                .isEqualTo(TransferStatus.SERVER_APPROVED);
    PendingActionNotificationResponse panData = Iterables.getOnlyElement(FluentIterable
        .from(transferApprovedPollMessage.getResponseData())
        .filter(PendingActionNotificationResponse.class));
    assertThat(panData.getTrid().getClientTransactionId()).isEqualTo("ABC-12345");
    assertThat(panData.getActionResult()).isTrue();

    // Two poll messages on the losing registrar's side at the implicit transfer time: a
    // transfer pending message, and a transfer approved message (both OneTime messages).
    assertThat(getPollMessages("TheRegistrar", implicitTransferTime)).hasSize(2);
    PollMessage losingTransferPendingPollMessage =
        getOnlyPollMessage("TheRegistrar", clock.nowUtc());
    PollMessage losingTransferApprovedPollMessage = Iterables.getOnlyElement(FluentIterable
        .from(getPollMessages("TheRegistrar", implicitTransferTime))
        .filter(Predicates.not(Predicates.equalTo(losingTransferPendingPollMessage))));
    assertThat(losingTransferPendingPollMessage.getEventTime()).isEqualTo(clock.nowUtc());
    assertThat(losingTransferApprovedPollMessage.getEventTime()).isEqualTo(implicitTransferTime);
    assertThat(
        Iterables.getOnlyElement(FluentIterable
            .from(losingTransferPendingPollMessage.getResponseData())
            .filter(TransferResponse.class))
                .getTransferStatus())
                .isEqualTo(TransferStatus.PENDING);
    assertThat(
        Iterables.getOnlyElement(FluentIterable
            .from(losingTransferApprovedPollMessage.getResponseData())
            .filter(TransferResponse.class))
                .getTransferStatus())
                .isEqualTo(TransferStatus.SERVER_APPROVED);

    assertAboutDomains().that(domainAfterAutomaticTransfer)
        .hasRegistrationExpirationTime(expectedExpirationTime);
    assertThat(ofy().load().key(domainAfterAutomaticTransfer.getAutorenewBillingEvent()).now()
        .getEventTime())
            .isEqualTo(expectedExpirationTime);
    // And after the expected grace time, the grace period should be gone.
    DomainResource afterGracePeriod = domain.cloneProjectedAtTime(
        clock.nowUtc().plus(registry.getAutomaticTransferLength()).plus(
            registry.getTransferGracePeriodLength()));
    assertThat(afterGracePeriod.getGracePeriods()).isEmpty();
  }

  private void doSuccessfulTest(
      String commandFilename,
      String expectedXmlFilename,
      DateTime expectedExpirationTime,
      BillingEvent.Cancellation.Builder... extraExpectedBillingEvents) throws Exception {
    doSuccessfulTest(
        commandFilename,
        expectedXmlFilename,
        expectedExpirationTime,
        ImmutableMap.<String, String>of(),
        Optional.<Money>absent(),
        extraExpectedBillingEvents);
  }

  private void doSuccessfulTest(
      String commandFilename,
      String expectedXmlFilename,
      Map<String, String> substitutions) throws Exception {
    clock.advanceOneMilli();
    doSuccessfulTest(
        commandFilename,
        expectedXmlFilename,
        domain.getRegistrationExpirationTime().plusYears(1),
        substitutions,
        Optional.<Money>absent());
  }

  private void doSuccessfulTest(String commandFilename, String expectedXmlFilename)
      throws Exception {
    clock.advanceOneMilli();
    doSuccessfulTest(
        commandFilename, expectedXmlFilename, domain.getRegistrationExpirationTime().plusYears(1));
  }

  private void runTest(
      String commandFilename,
      UserPrivileges userPrivileges,
      Map<String, String> substitutions) throws Exception {
    setEppInput(commandFilename, substitutions);
    // Replace the ROID in the xml file with the one generated in our test.
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlow(CommitMode.LIVE, userPrivileges);
  }

  private void runTest(String commandFilename, UserPrivileges userPrivileges) throws Exception {
    runTest(commandFilename, userPrivileges, ImmutableMap.<String, String>of());
  }

  private void doFailingTest(
      String commandFilename, Map<String, String> substitutions) throws Exception {
    runTest(commandFilename, UserPrivileges.NORMAL, substitutions);
  }

  private void doFailingTest(String commandFilename) throws Exception {
    runTest(commandFilename, UserPrivileges.NORMAL, ImmutableMap.<String, String> of());
  }

  @Test
  public void testDryRun() throws Exception {
    setupDomain("example", "tld");
    setEppInput("domain_transfer_request.xml");
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    dryRunFlowAssertResponse(readFile("domain_transfer_request_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    setupDomain("example", "tld");
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_fee_v06() throws Exception {
    setupDomain("example", "tld");
    doSuccessfulTest(
        "domain_transfer_request_fee.xml", "domain_transfer_request_response_fee.xml", FEE_06_MAP);
  }

  @Test
  public void testSuccess_fee_v11() throws Exception {
    setupDomain("example", "tld");
    doSuccessfulTest(
        "domain_transfer_request_fee.xml", "domain_transfer_request_response_fee.xml", FEE_11_MAP);
  }

  @Test
  public void testSuccess_fee_v12() throws Exception {
    setupDomain("example", "tld");
    doSuccessfulTest(
        "domain_transfer_request_fee.xml", "domain_transfer_request_response_fee.xml", FEE_12_MAP);
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes_v06() throws Exception {
    setupDomain("example", "tld");
    doSuccessfulTest(
        "domain_transfer_request_fee_defaults.xml",
        "domain_transfer_request_response_fee.xml",
        FEE_06_MAP);
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes_v11() throws Exception {
    setupDomain("example", "tld");
    doSuccessfulTest(
        "domain_transfer_request_fee_defaults.xml",
        "domain_transfer_request_response_fee.xml",
        FEE_11_MAP);
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes_v12() throws Exception {
    setupDomain("example", "tld");
    doSuccessfulTest(
        "domain_transfer_request_fee_defaults.xml",
        "domain_transfer_request_response_fee.xml",
        FEE_12_MAP);
  }

  @Test
  public void testFailure_refundableFee_v06() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_refundable.xml", FEE_06_MAP);
  }

  @Test
  public void testFailure_refundableFee_v11() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_refundable.xml", FEE_11_MAP);
  }

  @Test
  public void testFailure_refundableFee_v12() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_refundable.xml", FEE_12_MAP);
  }

  @Test
  public void testFailure_gracePeriodFee_v06() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_grace_period.xml", FEE_06_MAP);
  }

  @Test
  public void testFailure_gracePeriodFee_v11() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_grace_period.xml", FEE_11_MAP);
  }

  @Test
  public void testFailure_gracePeriodFee_v12() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_grace_period.xml", FEE_12_MAP);
  }

  @Test
  public void testFailure_appliedFee_v06() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_applied.xml", FEE_06_MAP);
  }

  @Test
  public void testFailure_appliedFee_v11() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_applied.xml", FEE_11_MAP);
  }

  @Test
  public void testFailure_appliedFee_v12() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_applied.xml", FEE_12_MAP);
  }

  @Test
  public void testSuccess_nonDefaultAutomaticTransferLength() throws Exception {
    setupDomain("example", "tld");
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAutomaticTransferLength(Duration.standardMinutes(15))
            .build());
    doSuccessfulTest(
        "domain_transfer_request.xml",
        "domain_transfer_request_response_15_minutes.xml");
  }

  @Test
  public void testSuccess_nonDefaultTransferGracePeriod() throws Exception {
    setupDomain("example", "tld");
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setTransferGracePeriodLength(Duration.standardMinutes(5))
            .build());
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_missingPeriod_defaultsToOneYear() throws Exception {
    setupDomain("example", "tld");
    doSuccessfulTest(
        "domain_transfer_request_missing_period.xml",
        "domain_transfer_request_response.xml");
  }

  @Test
  public void testFailure_multiYearPeriod() throws Exception {
    setupDomain("example", "tld");
    clock.advanceOneMilli();
    thrown.expect(TransferPeriodMustBeOneYearException.class);
    doFailingTest("domain_transfer_request_2_years.xml");
  }

  @Test
  public void testSuccess_cappedExpiration() throws Exception {
    setupDomain("example", "tld");
    // Set the domain to expire 10 years from now (as if it were just created with a 10-year term).
    domain = persistResource(domain.asBuilder()
        .setRegistrationExpirationTime(clock.nowUtc().plusYears(10))
        .build());
    // New expiration time should be capped at exactly 10 years from the transfer server-approve
    // time, so the domain only ends up gaining the 5-day transfer window's worth of extra
    // registration time.
    clock.advanceOneMilli();
    doSuccessfulTest(
        "domain_transfer_request.xml",
        "domain_transfer_request_response_10_year_cap.xml",
        clock.nowUtc().plus(Registry.get("tld").getAutomaticTransferLength()).plusYears(10));
  }

  @Test
  public void testSuccess_domainAuthInfo() throws Exception {
    setupDomain("example", "tld");
    doSuccessfulTest(
        "domain_transfer_request_domain_authinfo.xml",
        "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_customLogicFee() throws Exception {
    setupDomain("expensive-domain", "foo");
    clock.advanceOneMilli();
    doSuccessfulTest(
        "domain_transfer_request_fee.xml",
        "domain_transfer_request_response_fees.xml",
        domain.getRegistrationExpirationTime().plusYears(1),
        new ImmutableMap.Builder<String, String>()
            .put("DOMAIN", "expensive-domain.foo")
            .put("YEARS", "1")
            .put("AMOUNT", "111.00")
            .put("EXDATE", "2002-09-08T22:00:00.0Z")
            .put("FEE_VERSION", "0.6")
            .put("FEE_NS", "fee")
            .build(),
        Optional.of(Money.of(USD, 111)));
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    setupDomain("example", "tld");
    persistResource(
        Registrar.loadByClientId("NewRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    thrown.expect(NotAuthorizedForTldException.class);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_autorenewGraceActive_onlyAtTransferRequestTime() throws Exception {
    setupDomain("example", "tld");
    // Set the domain to have auto-renewed long enough ago that it is still in the autorenew grace
    // period at the transfer request time, but will have exited it by the automatic transfer time.
    DateTime autorenewTime =
        clock.nowUtc().minus(Registry.get("tld").getAutoRenewGracePeriodLength()).plusDays(1);
    DateTime expirationTime = autorenewTime.plusYears(1);
    domain = persistResource(domain.asBuilder()
        .setRegistrationExpirationTime(expirationTime)
        .addGracePeriod(GracePeriod.createForRecurring(
            GracePeriodStatus.AUTO_RENEW,
            autorenewTime.plus(Registry.get("tld").getAutoRenewGracePeriodLength()),
            "TheRegistrar",
            domain.getAutorenewBillingEvent()))
        .build());
    clock.advanceOneMilli();
    // Since the autorenew grace period will have ended by the automatic transfer time, subsuming
    // will not occur in the server-approve case, so the transfer will add 1 year to the current
    // expiration time as usual, and no Cancellation will be issued.  Note however that if the
    // transfer were to be manually approved before the autorenew grace period ends, then the
    // DomainTransferApproveFlow will still issue a Cancellation.
    doSuccessfulTest(
        "domain_transfer_request.xml",
        "domain_transfer_request_response_autorenew_grace_at_request_only.xml",
        expirationTime.plusYears(1));
  }

  @Test
  public void testSuccess_autorenewGraceActive_throughoutTransferWindow() throws Exception {
    setupDomain("example", "tld");
    Key<BillingEvent.Recurring> existingAutorenewEvent =
        domain.getAutorenewBillingEvent();
    // Set domain to have auto-renewed just before the transfer request, so that it will have an
    // active autorenew grace period spanning the entire transfer window.
    DateTime autorenewTime = clock.nowUtc().minusDays(1);
    DateTime expirationTime = autorenewTime.plusYears(1);
    domain = persistResource(domain.asBuilder()
        .setRegistrationExpirationTime(expirationTime)
        .addGracePeriod(GracePeriod.createForRecurring(
            GracePeriodStatus.AUTO_RENEW,
            autorenewTime.plus(Registry.get("tld").getAutoRenewGracePeriodLength()),
            "TheRegistrar",
            existingAutorenewEvent))
        .build());
    clock.advanceOneMilli();
    // The transfer will subsume the recent autorenew, so there will be no net change in expiration
    // time caused by the transfer, but we must write a Cancellation.
    doSuccessfulTest(
        "domain_transfer_request.xml",
        "domain_transfer_request_response_autorenew_grace_throughout_transfer_window.xml",
        expirationTime,
        new BillingEvent.Cancellation.Builder()
            .setReason(Reason.RENEW)
            .setTargetId("example.tld")
            .setClientId("TheRegistrar")
            // The cancellation happens at the moment of transfer.
            .setEventTime(clock.nowUtc().plus(Registry.get("tld").getAutomaticTransferLength()))
            .setBillingTime(autorenewTime.plus(
                Registry.get("tld").getAutoRenewGracePeriodLength()))
            // The cancellation should refer to the old autorenew billing event.
            .setRecurringEventKey(existingAutorenewEvent));
  }

  @Test
  public void testSuccess_autorenewGraceActive_onlyAtAutomaticTransferTime() throws Exception {
    setupDomain("example", "tld");
    Key<BillingEvent.Recurring> existingAutorenewEvent =
        domain.getAutorenewBillingEvent();
    // Set domain to expire in 1 day, so that it will be in the autorenew grace period by the
    // automatic transfer time, even though it isn't yet.
    DateTime expirationTime = clock.nowUtc().plusDays(1);
    domain = persistResource(domain.asBuilder()
        .setRegistrationExpirationTime(expirationTime)
        .build());
    clock.advanceOneMilli();
    // The transfer will subsume the future autorenew, meaning that the expected server-approve
    // expiration time will be 1 year beyond the current one, and we must write a Cancellation.
    doSuccessfulTest(
        "domain_transfer_request.xml",
        "domain_transfer_request_response_autorenew_grace_at_transfer_only.xml",
        expirationTime.plusYears(1),
        new BillingEvent.Cancellation.Builder()
            .setReason(Reason.RENEW)
            .setTargetId("example.tld")
            .setClientId("TheRegistrar")
            // The cancellation happens at the moment of transfer.
            .setEventTime(clock.nowUtc().plus(Registry.get("tld").getAutomaticTransferLength()))
            .setBillingTime(
                expirationTime.plus(Registry.get("tld").getAutoRenewGracePeriodLength()))
            // The cancellation should refer to the old autorenew billing event.
            .setRecurringEventKey(existingAutorenewEvent));
  }

  @Test
  public void testSuccess_premiumNotBlocked() throws Exception {
    setupDomain("rich", "example");
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    clock.advanceOneMilli();
    // We don't verify the results; just check that the flow doesn't fail.
    runTest("domain_transfer_request_premium.xml", UserPrivileges.NORMAL);
  }

  @Test
  public void testSuccess_premiumNotBlockedInSuperuserMode() throws Exception {
    setupDomain("rich", "example");
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    clock.advanceOneMilli();
    // Modify the Registrar to block premium names.
    persistResource(
        Registrar.loadByClientId("NewRegistrar").asBuilder().setBlockPremiumNames(true).build());
    // We don't verify the results; just check that the flow doesn't fail.
    runTest("domain_transfer_request_premium.xml", UserPrivileges.SUPERUSER);
  }

  private void runWrongCurrencyTest(Map<String, String> substitutions) throws Exception {
    setupDomain("example", "tld");
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setCurrency(EUR)
            .setCreateBillingCost(Money.of(EUR, 13))
            .setRestoreBillingCost(Money.of(EUR, 11))
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
            .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
            .setServerStatusChangeBillingCost(Money.of(EUR, 19))
            .build());
    thrown.expect(CurrencyUnitMismatchException.class);
    doFailingTest("domain_transfer_request_fee.xml", substitutions);
  }

  @Test
  public void testFailure_wrongCurrency_v06() throws Exception {
    setupDomain("example", "tld");
    runWrongCurrencyTest(FEE_06_MAP);
  }

  @Test
  public void testFailure_wrongCurrency_v11() throws Exception {
    setupDomain("example", "tld");
    runWrongCurrencyTest(FEE_11_MAP);
  }

  @Test
  public void testFailure_wrongCurrency_v12() throws Exception {
    setupDomain("example", "tld");
    runWrongCurrencyTest(FEE_12_MAP);
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v06() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(CurrencyValueScaleException.class);
    doFailingTest("domain_transfer_request_fee_bad_scale.xml", FEE_06_MAP);
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v11() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(CurrencyValueScaleException.class);
    doFailingTest("domain_transfer_request_fee_bad_scale.xml", FEE_11_MAP);
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v12() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(CurrencyValueScaleException.class);
    doFailingTest("domain_transfer_request_fee_bad_scale.xml", FEE_12_MAP);
  }


  private void runWrongFeeAmountTest(Map<String, String> substitutions) throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(USD, 20)))
            .build());
    thrown.expect(FeesMismatchException.class);
    doFailingTest("domain_transfer_request_fee.xml", substitutions);
  }

  @Test
  public void testFailure_wrongFeeAmount_v06() throws Exception {
    setupDomain("example", "tld");
    runWrongFeeAmountTest(FEE_06_MAP);
  }

  @Test
  public void testFailure_wrongFeeAmount_v11() throws Exception {
    setupDomain("example", "tld");
    runWrongFeeAmountTest(FEE_11_MAP);
  }

  @Test
  public void testFailure_wrongFeeAmount_v12() throws Exception {
    setupDomain("example", "tld");
    runWrongFeeAmountTest(FEE_12_MAP);
  }

  @Test
  public void testFailure_premiumBlocked() throws Exception {
    setupDomain("rich", "example");
    // Modify the Registrar to block premium names.
    persistResource(
        Registrar.loadByClientId("NewRegistrar").asBuilder().setBlockPremiumNames(true).build());
    thrown.expect(PremiumNameBlockedException.class);
    doFailingTest("domain_transfer_request_premium.xml");
  }

  @Test
  public void testFailure_feeNotProvidedOnPremiumName() throws Exception {
    setupDomain("rich", "example");
    thrown.expect(FeesRequiredForPremiumNameException.class);
    doFailingTest("domain_transfer_request_premium.xml");
  }

  @Test
  public void testFailure_noAuthInfo() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(MissingTransferRequestAuthInfoException.class);
    doFailingTest("domain_transfer_request_no_authinfo.xml");
  }

  @Test
  public void testFailure_badContactPassword() throws Exception {
    setupDomain("example", "tld");
    // Change the contact's password so it does not match the password in the file.
    contact = persistResource(
        contact.asBuilder()
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("badpassword")))
            .build());
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_badContactRepoId() throws Exception {
    setupDomain("example", "tld");
    // Set the contact to a different ROID, but don't persist it; this is just so the substitution
    // code above will write the wrong ROID into the file.
    contact = contact.asBuilder().setRepoId("DEADBEEF_TLD-ROID").build();
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testSuccess_clientApproved() throws Exception {
    setupDomain("example", "tld");
    changeTransferStatus(TransferStatus.CLIENT_APPROVED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_clientRejected() throws Exception {
    setupDomain("example", "tld");
    changeTransferStatus(TransferStatus.CLIENT_REJECTED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_clientCancelled() throws Exception {
    setupDomain("example", "tld");
    changeTransferStatus(TransferStatus.CLIENT_CANCELLED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_serverApproved() throws Exception {
    setupDomain("example", "tld");
    changeTransferStatus(TransferStatus.SERVER_APPROVED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_serverCancelled() throws Exception {
    setupDomain("example", "tld");
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testFailure_pending() throws Exception {
    setupDomain("example", "tld");
    domain = persistResource(domain.asBuilder()
        .setTransferData(domain.getTransferData().asBuilder()
            .setTransferStatus(TransferStatus.PENDING)
            .setPendingTransferExpirationTime(clock.nowUtc().plusDays(1))
            .build())
        .build());
    thrown.expect(AlreadyPendingTransferException.class);
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_badDomainPassword() throws Exception {
    setupDomain("example", "tld");
    // Change the domain's password so it does not match the password in the file.
    domain = persistResource(domain.asBuilder()
        .setAuthInfo(DomainAuthInfo.create(PasswordAuth.create("badpassword")))
        .build());
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("domain_transfer_request_domain_authinfo.xml");
  }

  @Test
  public void testFailure_sponsoringClient() throws Exception {
    setupDomain("example", "tld");
    setClientIdForFlow("TheRegistrar");
    thrown.expect(ObjectAlreadySponsoredException.class);
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_deletedDomain() throws Exception {
    setupDomain("example", "tld");
    domain = persistResource(
        domain.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_invalidDomain() throws Exception {
    setupDomain("example", "tld");
    setEppInput(
        "domain_transfer_request_wildcard.xml",
        ImmutableMap.of("YEARS", "1", "DOMAIN", "--invalid", "EXDATE", "2002-09-08T22:00:00.0Z"));
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    assertTransactionalFlow(true);
    thrown.expect(ResourceDoesNotExistException.class, "(--invalid)");
    runFlow(CommitMode.LIVE, UserPrivileges.NORMAL);
  }

  @Test
  public void testFailure_nonexistentDomain() throws Exception {
    createTld("tld");
    contact = persistActiveContact("jd1234");
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", "example.tld"));
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_periodInMonths() throws Exception {
    setupDomain("example", "tld");
    thrown.expect(BadPeriodUnitException.class);
    doFailingTest("domain_transfer_request_months.xml");
  }

  @Test
  public void testFailure_clientTransferProhibited() throws Exception {
    setupDomain("example", "tld");
    domain = persistResource(
        domain.asBuilder().addStatusValue(StatusValue.CLIENT_TRANSFER_PROHIBITED).build());
    thrown.expect(ResourceStatusProhibitsOperationException.class, "clientTransferProhibited");
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_serverTransferProhibited() throws Exception {
    setupDomain("example", "tld");
    domain = persistResource(
        domain.asBuilder().addStatusValue(StatusValue.SERVER_TRANSFER_PROHIBITED).build());
    thrown.expect(ResourceStatusProhibitsOperationException.class, "serverTransferProhibited");
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_pendingDelete() throws Exception {
    setupDomain("example", "tld");
    domain = persistResource(
        domain.asBuilder().addStatusValue(StatusValue.PENDING_DELETE).build());
    thrown.expect(ResourceStatusProhibitsOperationException.class, "pendingDelete");
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    setupDomain("example", "tld");
    clock.advanceOneMilli();
    runTest("domain_transfer_request.xml", UserPrivileges.NORMAL);
    assertIcannReportingActivityFieldLogged("srs-dom-transfer-request");
    assertTldsFieldLogged("tld");
  }
}
