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

package com.google.domain.registry.flows.domain;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.assertBillingEvents;
import static com.google.domain.registry.testing.DatastoreHelper.deleteResource;
import static com.google.domain.registry.testing.DatastoreHelper.getOnlyHistoryEntryOfType;
import static com.google.domain.registry.testing.DatastoreHelper.getOnlyPollMessage;
import static com.google.domain.registry.testing.DatastoreHelper.getPollMessages;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.DomainResourceSubject.assertAboutDomains;
import static com.google.domain.registry.testing.GenericEppResourceSubject.assertAboutEppResources;
import static com.google.domain.registry.testing.HistoryEntrySubject.assertAboutHistoryEntries;
import static com.google.domain.registry.testing.HostResourceSubject.assertAboutHosts;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static java.util.Arrays.asList;
import static org.joda.money.CurrencyUnit.EUR;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.domain.registry.flows.FlowRunner.CommitMode;
import com.google.domain.registry.flows.FlowRunner.UserPrivileges;
import com.google.domain.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException;
import com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException;
import com.google.domain.registry.flows.ResourceTransferRequestFlow.AlreadyPendingTransferException;
import com.google.domain.registry.flows.ResourceTransferRequestFlow.MissingTransferRequestAuthInfoException;
import com.google.domain.registry.flows.ResourceTransferRequestFlow.ObjectAlreadySponsoredException;
import com.google.domain.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.BadPeriodUnitException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.CurrencyUnitMismatchException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.CurrencyValueScaleException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.FeesMismatchException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.FeesRequiredForPremiumNameException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.PremiumNameBlockedException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.UnsupportedFeeAttributeException;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.billing.BillingEvent.Cancellation.Builder;
import com.google.domain.registry.model.billing.BillingEvent.Flag;
import com.google.domain.registry.model.billing.BillingEvent.Reason;
import com.google.domain.registry.model.contact.ContactAuthInfo;
import com.google.domain.registry.model.domain.DomainAuthInfo;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.GracePeriod;
import com.google.domain.registry.model.domain.rgp.GracePeriodStatus;
import com.google.domain.registry.model.eppcommon.AuthInfo.PasswordAuth;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.poll.PendingActionNotificationResponse;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.model.transfer.TransferResponse;
import com.google.domain.registry.model.transfer.TransferStatus;

import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainTransferRequestFlow}. */
public class DomainTransferRequestFlowTest
    extends DomainTransferFlowTestCase<DomainTransferRequestFlow, DomainResource> {

  @Before
  public void setUp() throws Exception {
    setEppInput("domain_transfer_request.xml");
    setClientIdForFlow("NewRegistrar");
    setupDomain("tld");
  }

  private void assertTransferRequested(EppResource resource) throws Exception {
    assertAboutEppResources().that(resource)
        .hasTransferStatus(TransferStatus.PENDING).and()
        .hasTransferGainingClientId("NewRegistrar").and()
        .hasTransferLosingClientId("TheRegistrar").and()
        .hasTransferRequestTrid(getTrid()).and()
        .hasCurrentSponsorClientId("TheRegistrar").and()
        .hasPendingTransferExpirationTime(
            clock.nowUtc().plus(Registry.get("tld").getAutomaticTransferLength())).and()
        .hasStatusValue(StatusValue.PENDING_TRANSFER);
  }

  private void assertTransferApproved(EppResource resource) {
    DateTime afterAutoAck = clock.nowUtc().plus(Registry.get("tld").getAutomaticTransferLength());
    assertAboutEppResources().that(resource)
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
      BillingEvent.Cancellation.Builder... extraExpectedBillingEvents) throws Exception {
    setEppInput(commandFilename);
    ImmutableSet<GracePeriod> originalGracePeriods = domain.getGracePeriods();
    // Replace the ROID in the xml file with the one generated in our test.
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // For all of the other transfer flow tests, 'now' corresponds to day 3 of the transfer, but
    // for the request test we want that same 'now' to be the initial request time, so we shift
    // the transfer timeline 3 days later by adjusting the implicit transfer time here.
    DateTime implicitTransferTime =
        clock.nowUtc().plus(Registry.get("tld").getAutomaticTransferLength());
    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile(expectedXmlFilename));
    // Transfer should have been requested. Verify correct fields were set.
    domain = reloadResourceByUniqueId();
    final HistoryEntry historyEntryTransferRequest =
        getOnlyHistoryEntryOfType(domain, HistoryEntry.Type.DOMAIN_TRANSFER_REQUEST);
    int registrationYears = domain.getTransferData().getExtendedRegistrationYears();
    subordinateHost = reloadResourceAndCloneAtTime(subordinateHost, clock.nowUtc());
    assertTransferRequested(domain);
    assertTransferRequested(subordinateHost);
    assertAboutDomains().that(domain)
        .hasPendingTransferExpirationTime(implicitTransferTime).and()
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.DOMAIN_CREATE,
            HistoryEntry.Type.DOMAIN_TRANSFER_REQUEST);
    assertAboutHistoryEntries().that(historyEntryTransferRequest).hasPeriodYears(registrationYears);
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
        .setBillingTime(
            implicitTransferTime.plus(Registry.get("tld").getTransferGracePeriodLength()))
        .setClientId("NewRegistrar")
        .setCost(Money.of(USD, 11).multipliedBy(registrationYears))
        .setPeriodYears(registrationYears)
        .setParent(historyEntryTransferRequest)
        .build();
    assertBillingEvents(FluentIterable.from(asList(extraExpectedBillingEvents))
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
    BillingEvent.Recurring domainAutorenewEvent = domain.getAutorenewBillingEvent().get();
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
                  .plus(Registry.get("tld").getAutomaticTransferLength())
                  .plus(Registry.get("tld").getTransferGracePeriodLength()),
                "NewRegistrar",
                null),
            transferBillingEvent));
    assertTransferApproved(domainAfterAutomaticTransfer);
    assertTransferApproved(
        subordinateHost.cloneProjectedAtTime(implicitTransferTime));

    // Two poll messages on the gaining registrar's side at the expected expiration time: a
    // (OneTime) transfer approved message, and an Autorenew poll message.
    assertThat(getPollMessages("NewRegistrar", expectedExpirationTime))
        .hasSize(2);
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
    assertThat(panData.getTrid())
        .isEqualTo(Trid.create("ABC-12345", "server-trid"));
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
    assertThat(domainAfterAutomaticTransfer.getAutorenewBillingEvent().get().getEventTime())
        .isEqualTo(expectedExpirationTime);
    // And after the expected grace time, the grace period should be gone.
    DomainResource afterGracePeriod = domain.cloneProjectedAtTime(
        clock.nowUtc().plus(Registry.get("tld").getAutomaticTransferLength()).plus(
            Registry.get("tld").getTransferGracePeriodLength()));
    assertThat(afterGracePeriod.getGracePeriods()).isEmpty();
  }

  private void doSuccessfulTest(String commandFilename, String expectedXmlFilename)
      throws Exception {
    clock.advanceOneMilli();
    doSuccessfulTest(
        commandFilename, expectedXmlFilename, domain.getRegistrationExpirationTime().plusYears(1));
  }

  private void runTest(String commandFilename, UserPrivileges userPrivileges) throws Exception {
    setEppInput(commandFilename);
    // Replace the ROID in the xml file with the one generated in our test.
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlow(CommitMode.LIVE, userPrivileges);
  }

  private void doFailingTest(String commandFilename) throws Exception {
    runTest(commandFilename, UserPrivileges.NORMAL);
  }

  @Test
  public void testDryRun() throws Exception {
    setEppInput("domain_transfer_request.xml");
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    dryRunFlowAssertResponse(readFile("domain_transfer_request_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_fee() throws Exception {
    doSuccessfulTest("domain_transfer_request_fee.xml", "domain_transfer_request_response_fee.xml");
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes() throws Exception {
    doSuccessfulTest(
        "domain_transfer_request_fee_defaults.xml", "domain_transfer_request_response_fee.xml");
  }

  @Test
  public void testFailure_refundableFee() throws Exception {
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_refundable.xml");
  }


  @Test
  public void testFailure_gracePeriodFee() throws Exception {
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_grace_period.xml");
  }


  @Test
  public void testFailure_appliedFee() throws Exception {
    thrown.expect(UnsupportedFeeAttributeException.class);
    doFailingTest("domain_transfer_request_fee_applied.xml");
  }

  @Test
  public void testSuccess_nonDefaultAutomaticTransferLength() throws Exception {
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
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setTransferGracePeriodLength(Duration.standardMinutes(5))
            .build());
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_missingPeriod() throws Exception {
    doSuccessfulTest("domain_transfer_request_missing_period.xml",
        "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_cappedExpiration() throws Exception {
    // The current expiration is in 15 months, so requesting 10 years would give 11 years 3 months,
    // were it not that we cap at 10 years. (MAX_REGISTRATION_YEARS == 10 and is unlikely to ever
    // change; we just use a constant for readability.)
    clock.advanceOneMilli();
    doSuccessfulTest(
        "domain_transfer_request_10_years.xml",
        "domain_transfer_request_response_10_years.xml",
        clock.nowUtc().plus(Registry.get("tld").getAutomaticTransferLength()).plusYears(10));
  }

  @Test
  public void testSuccess_domainAuthInfo() throws Exception {
    doSuccessfulTest(
        "domain_transfer_request_domain_authinfo.xml",
        "domain_transfer_request_response.xml");
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    thrown.expect(NotAuthorizedForTldException.class);
    persistResource(
        Registrar.loadByClientId("NewRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_autorenewBeforeAutomaticTransfer() throws Exception {
    DomainResource oldResource = persistResource(reloadResourceByUniqueId().asBuilder()
        .setRegistrationExpirationTime(clock.nowUtc().plusDays(1).plus(1))
        .build());
    clock.advanceOneMilli();
    // The autorenew should be subsumed into the transfer resulting in 2 years of renewal in total.
    doSuccessfulTest(
        "domain_transfer_request_2_years.xml",
        "domain_transfer_request_response_2_years.xml",
        clock.nowUtc().plusDays(1).plusYears(2),
        new BillingEvent.Cancellation.Builder()
            .setReason(Reason.RENEW)
            .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
            .setTargetId("example.tld")
            .setClientId("TheRegistrar")
            // The cancellation happens at the moment of transfer.
            .setEventTime(clock.nowUtc().plus(Registry.get("tld").getAutomaticTransferLength()))
            .setBillingTime(oldResource.getRegistrationExpirationTime().plus(
                Registry.get("tld").getAutoRenewGracePeriodLength()))
            // The cancellation should refer to the old autorenew billing event.
            .setRecurringEventRef(oldResource.getAutorenewBillingEvent()));
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

  @Test
  public void testFailure_wrongCurrency() throws Exception {
    thrown.expect(CurrencyUnitMismatchException.class);
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setCurrency(EUR)
            .setCreateBillingCost(Money.of(EUR, 13))
            .setRestoreBillingCost(Money.of(EUR, 11))
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
            .build());
    doFailingTest("domain_transfer_request_fee.xml");
  }

  @Test
  public void testFailure_feeGivenInWrongScale() throws Exception {
    thrown.expect(CurrencyValueScaleException.class);
    doFailingTest("domain_transfer_request_fee_bad_scale.xml");
  }

  @Test
  public void testFailure_wrongFeeAmount() throws Exception {
    thrown.expect(FeesMismatchException.class);
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(USD, 20)))
            .build());
    doFailingTest("domain_transfer_request_fee.xml");
  }

  @Test
  public void testFailure_premiumBlocked() throws Exception {
    thrown.expect(PremiumNameBlockedException.class);
    setupDomain("rich", "example");
    // Modify the Registrar to block premium names.
    persistResource(
        Registrar.loadByClientId("NewRegistrar").asBuilder().setBlockPremiumNames(true).build());
    doFailingTest("domain_transfer_request_premium.xml");
  }


  @Test
  public void testFailure_feeNotProvidedOnPremiumName() throws Exception {
    thrown.expect(FeesRequiredForPremiumNameException.class);
    setupDomain("rich", "example");
    doFailingTest("domain_transfer_request_premium.xml");
  }

  @Test
  public void testFailure_noAuthInfo() throws Exception {
    thrown.expect(MissingTransferRequestAuthInfoException.class);
    doFailingTest("domain_transfer_request_no_authinfo.xml");
  }

  @Test
  public void testFailure_badContactPassword() throws Exception {
    thrown.expect(BadAuthInfoForResourceException.class);
    // Change the contact's password so it does not match the password in the file.
    contact = persistResource(
        contact.asBuilder()
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("badpassword")))
            .build());
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_badContactRepoId() throws Exception {
    thrown.expect(BadAuthInfoForResourceException.class);
    // Set the contact to a different ROID, but don't persist it; this is just so the substitution
    // code above will write the wrong ROID into the file.
    contact = contact.asBuilder().setRepoId("DEADBEEF_TLD-ROID").build();
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testSuccess_clientApproved() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_APPROVED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_clientRejected() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_REJECTED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_clientCancelled() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_CANCELLED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_serverApproved() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_APPROVED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_serverCancelled() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    doSuccessfulTest("domain_transfer_request.xml", "domain_transfer_request_response.xml");
  }

  @Test
  public void testFailure_pending() throws Exception {
    thrown.expect(AlreadyPendingTransferException.class);
    domain = persistResource(domain.asBuilder()
        .setTransferData(domain.getTransferData().asBuilder()
            .setTransferStatus(TransferStatus.PENDING)
            .setPendingTransferExpirationTime(clock.nowUtc().plusDays(1))
            .build())
        .build());
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_badDomainPassword() throws Exception {
    thrown.expect(BadAuthInfoForResourceException.class);
    // Change the domain's password so it does not match the password in the file.
    domain = persistResource(domain.asBuilder()
        .setAuthInfo(DomainAuthInfo.create(PasswordAuth.create("badpassword")))
        .build());
    doFailingTest("domain_transfer_request_domain_authinfo.xml");
  }

  @Test
  public void testFailure_sponsoringClient() throws Exception {
    thrown.expect(ObjectAlreadySponsoredException.class);
    setClientIdForFlow("TheRegistrar");
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_deletedDomain() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    domain = persistResource(
        domain.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_invalidDomain() throws Exception {
    setEppInput("domain_transfer_request_wildcard.xml", ImmutableMap.of("DOMAIN", "--invalid"));
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    assertTransactionalFlow(true);
    thrown.expect(ResourceToMutateDoesNotExistException.class, "(--invalid)");
    runFlow(CommitMode.LIVE, UserPrivileges.NORMAL);
  }

  @Test
  public void testFailure_nonexistentDomain() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    deleteResource(domain);
    doFailingTest("domain_transfer_request.xml");
  }

  @Test
  public void testFailure_periodInMonths() throws Exception {
    thrown.expect(BadPeriodUnitException.class);
    doFailingTest("domain_transfer_request_months.xml");
  }

  @Test
  public void testFailure_pendingDelete() throws Exception {
    thrown.expect(ResourceStatusProhibitsOperationException.class);
    domain = persistResource(
        domain.asBuilder().addStatusValue(StatusValue.PENDING_DELETE).build());
    doFailingTest("domain_transfer_request.xml");
  }
}
