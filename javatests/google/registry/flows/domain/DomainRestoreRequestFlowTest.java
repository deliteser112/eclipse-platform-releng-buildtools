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
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.getOnlyHistoryEntryOfType;
import static com.google.domain.registry.testing.DatastoreHelper.getPollMessages;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistReservedList;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.DomainResourceSubject.assertAboutDomains;
import static com.google.domain.registry.testing.TaskQueueHelper.assertDnsTasksEnqueued;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.EUR;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.domain.registry.flows.EppException.UnimplementedExtensionException;
import com.google.domain.registry.flows.FlowRunner.CommitMode;
import com.google.domain.registry.flows.FlowRunner.UserPrivileges;
import com.google.domain.registry.flows.ResourceFlowTestCase;
import com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.CurrencyUnitMismatchException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.CurrencyValueScaleException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.DomainReservedException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.FeesMismatchException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.FeesRequiredForPremiumNameException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.PremiumNameBlockedException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.UnsupportedFeeAttributeException;
import com.google.domain.registry.flows.domain.DomainRestoreRequestFlow.DomainNotEligibleForRestoreException;
import com.google.domain.registry.flows.domain.DomainRestoreRequestFlow.RestoreCommandIncludesChangesException;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.billing.BillingEvent.Flag;
import com.google.domain.registry.model.billing.BillingEvent.Reason;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.GracePeriod;
import com.google.domain.registry.model.domain.rgp.GracePeriodStatus;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.reporting.HistoryEntry;

import com.googlecode.objectify.Key;

import org.joda.money.Money;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainRestoreRequestFlow}. */
public class DomainRestoreRequestFlowTest extends
    ResourceFlowTestCase<DomainRestoreRequestFlow, DomainResource> {

  public DomainRestoreRequestFlowTest() {
    setEppInput("domain_update_restore_request.xml");
  }

  @Before
  public void initDomainTest() {
    createTld("tld");
  }

  void persistPendingDeleteDomain() throws Exception {
    DomainResource domain = newDomainResource(getUniqueIdFromCommand());
    HistoryEntry historyEntry = persistResource(
        new HistoryEntry.Builder()
            .setType(HistoryEntry.Type.DOMAIN_DELETE)
            .setParent(domain)
            .build());
    domain = persistResource(domain.asBuilder()
        .setRegistrationExpirationTime(clock.nowUtc().plusYears(5).plusDays(45))
        .setDeletionTime(clock.nowUtc().plusDays(35))
        .addGracePeriod(GracePeriod.create(
            GracePeriodStatus.REDEMPTION, clock.nowUtc().plusDays(1), "foo", null))
        .setStatusValues(ImmutableSet.of(StatusValue.PENDING_DELETE))
        .setDeletePollMessage(Key.create(persistResource(
            new PollMessage.OneTime.Builder()
                .setClientId("TheRegistrar")
                .setEventTime(clock.nowUtc().plusDays(5))
                .setParent(historyEntry)
                .build())))
        .build());
    clock.advanceOneMilli();
  }

  @Test
  public void testDryRun() throws Exception {
    setEppInput("domain_update_restore_request.xml");
    persistPendingDeleteDomain();
    dryRunFlowAssertResponse(readFile("domain_update_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    setEppInput("domain_update_restore_request.xml");
    persistPendingDeleteDomain();
    assertTransactionalFlow(true);
    // Double check that we see a poll message in the future for when the delete happens.
    assertThat(getPollMessages("TheRegistrar", clock.nowUtc().plusMonths(1)))
        .hasSize(1);
    runFlowAssertResponse(readFile("domain_update_response.xml"));
    DomainResource domain = reloadResourceByUniqueId();
    HistoryEntry historyEntryDomainRestore =
        getOnlyHistoryEntryOfType(domain, HistoryEntry.Type.DOMAIN_RESTORE);
    assertThat(domain.getAutorenewBillingEvent().get().getEventTime())
        .isEqualTo(clock.nowUtc().plusYears(1));
    assertAboutDomains().that(domain)
        // New expiration time should be exactly a year from now.
        .hasRegistrationExpirationTime(clock.nowUtc().plusYears(1)).and()
        .doesNotHaveStatusValue(StatusValue.PENDING_DELETE).and()
        .hasDeletionTime(END_OF_TIME).and()
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.DOMAIN_DELETE,
            HistoryEntry.Type.DOMAIN_RESTORE);
    assertThat(domain.getGracePeriods()).isEmpty();
    assertDnsTasksEnqueued("example.tld");
    // The poll message for the delete should now be gone. The only poll message should be the new
    // autorenew poll message.
    assertPollMessages(
        "TheRegistrar",
        new PollMessage.Autorenew.Builder()
            .setTargetId("example.tld")
            .setClientId("TheRegistrar")
            .setEventTime(domain.getRegistrationExpirationTime())
            .setAutorenewEndTime(END_OF_TIME)
            .setMsg("Domain was auto-renewed.")
            .setParent(historyEntryDomainRestore)
            .build());
    // There should be a bill for the restore and an explicit renew, along with a new recurring
    // autorenew event.
    assertBillingEvents(
        new BillingEvent.Recurring.Builder()
            .setReason(Reason.RENEW)
            .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
            .setTargetId("example.tld")
            .setClientId("TheRegistrar")
            .setEventTime(domain.getRegistrationExpirationTime())
            .setRecurrenceEndTime(END_OF_TIME)
            .setParent(historyEntryDomainRestore)
            .build(),
        new BillingEvent.OneTime.Builder()
            .setReason(Reason.RESTORE)
            .setTargetId("example.tld")
            .setClientId("TheRegistrar")
            .setCost(Money.of(USD, 17))
            .setEventTime(clock.nowUtc())
            .setBillingTime(clock.nowUtc())
            .setParent(historyEntryDomainRestore)
            .build(),
        new BillingEvent.OneTime.Builder()
            .setReason(Reason.RENEW)
            .setTargetId("example.tld")
            .setClientId("TheRegistrar")
            .setCost(Money.of(USD, 11))
            .setPeriodYears(1)
            .setEventTime(clock.nowUtc())
            .setBillingTime(clock.nowUtc())
            .setParent(historyEntryDomainRestore)
            .build());
  }

  @Test
  public void testSuccess_fee() throws Exception {
    setEppInput("domain_update_restore_request_fee.xml");
    persistPendingDeleteDomain();
    runFlowAssertResponse(readFile("domain_update_restore_request_response_fee.xml"));
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes() throws Exception {
    setEppInput("domain_update_restore_request_fee_defaults.xml");
    persistPendingDeleteDomain();
    runFlowAssertResponse(readFile("domain_update_restore_request_response_fee.xml"));
  }

  @Test
  public void testFailure_refundableFee() throws Exception {
    thrown.expect(UnsupportedFeeAttributeException.class);
    setEppInput("domain_update_restore_request_fee_refundable.xml");
    persistPendingDeleteDomain();
    runFlow();
  }

  @Test
  public void testFailure_gracePeriodFee() throws Exception {
    thrown.expect(UnsupportedFeeAttributeException.class);
    setEppInput("domain_update_restore_request_fee_grace_period.xml");
    persistPendingDeleteDomain();
    runFlow();
  }

  @Test
  public void testFailure_appliedFee() throws Exception {
    thrown.expect(UnsupportedFeeAttributeException.class);
    setEppInput("domain_update_restore_request_fee_applied.xml");
    persistPendingDeleteDomain();
    runFlow();
  }

  @Test
  public void testSuccess_premiumNotBlocked() throws Exception {
    createTld("example");
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    setEppInput("domain_update_restore_request_premium.xml");
    persistPendingDeleteDomain();
    runFlow();
  }

  @Test
  public void testSuccess_superuserOverridesReservedList() throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(persistReservedList("tld-reserved", "example,FULLY_BLOCKED"))
            .build());
    persistPendingDeleteDomain();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_update_response.xml"));
  }

  @Test
  public void testSuccess_superuserOverridesPremiumNameBlock() throws Exception {
    createTld("example");
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    setEppInput("domain_update_restore_request_premium.xml");
    persistPendingDeleteDomain();
    // Modify the Registrar to block premium names.
    persistResource(
        Registrar.loadByClientId("TheRegistrar").asBuilder().setBlockPremiumNames(true).build());
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_update_response.xml"));
  }

  @Test
  public void testFailure_doesNotExist() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeAmount() throws Exception {
    thrown.expect(FeesMismatchException.class);
    setEppInput("domain_update_restore_request_fee.xml");
    persistPendingDeleteDomain();
    persistResource(
        Registry.get("tld").asBuilder().setRestoreBillingCost(Money.of(USD, 100)).build());
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency() throws Exception {
    thrown.expect(CurrencyUnitMismatchException.class);
    setEppInput("domain_update_restore_request_fee.xml");
    persistPendingDeleteDomain();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setCurrency(EUR)
            .setCreateBillingCost(Money.of(EUR, 13))
            .setRestoreBillingCost(Money.of(EUR, 11))
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
            .setServerStatusChangeBillingCost(Money.of(EUR, 19))
            .build());
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale() throws Exception {
    thrown.expect(CurrencyValueScaleException.class);
    setEppInput("domain_update_restore_request_fee_bad_scale.xml");
    persistPendingDeleteDomain();
    runFlow();
  }

  @Test
  public void testFailure_notInRedemptionPeriod() throws Exception {
    thrown.expect(DomainNotEligibleForRestoreException.class);
    persistResource(newDomainResource(getUniqueIdFromCommand()).asBuilder()
        .setDeletionTime(clock.nowUtc().plusDays(4))
        .setStatusValues(ImmutableSet.of(StatusValue.PENDING_DELETE))
        .build());
    runFlow();
  }

  @Test
  public void testFailure_notDeleted() throws Exception {
    thrown.expect(DomainNotEligibleForRestoreException.class);
    persistActiveDomain(getUniqueIdFromCommand());
    runFlow();
  }

  @Test
  public void testFailure_withChange() throws Exception {
    thrown.expect(RestoreCommandIncludesChangesException.class);
    persistPendingDeleteDomain();
    setEppInput("domain_update_restore_request_with_change.xml");
    runFlow();
  }

  @Test
  public void testFailure_withAdd() throws Exception {
    thrown.expect(RestoreCommandIncludesChangesException.class);
    persistPendingDeleteDomain();
    setEppInput("domain_update_restore_request_with_add.xml");
    runFlow();
  }

  @Test
  public void testFailure_withRemove() throws Exception {
    thrown.expect(RestoreCommandIncludesChangesException.class);
    persistPendingDeleteDomain();
    setEppInput("domain_update_restore_request_with_remove.xml");
    runFlow();
  }

  @Test
  public void testFailure_withSecDnsExtension() throws Exception {
    thrown.expect(UnimplementedExtensionException.class);
    persistPendingDeleteDomain();
    setEppInput("domain_update_restore_request_with_secdns.xml");
    runFlow();
  }

  @Test
  public void testFailure_unauthorizedClient() throws Exception {
    thrown.expect(ResourceNotOwnedException.class);
    sessionMetadata.setClientId("NewRegistrar");
    persistPendingDeleteDomain();
    runFlow();
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    thrown.expect(NotAuthorizedForTldException.class);
    persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    persistPendingDeleteDomain();
    runFlow();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    thrown.expect(ResourceNotOwnedException.class);
    sessionMetadata.setSuperuser(true);
    sessionMetadata.setClientId("NewRegistrar");
    persistPendingDeleteDomain();
    runFlowAssertResponse(readFile("domain_update_response.xml"));
  }

  @Test
  public void testFailure_premiumBlocked() throws Exception {
    thrown.expect(PremiumNameBlockedException.class);
    createTld("example");
    setEppInput("domain_update_restore_request_premium.xml");
    persistPendingDeleteDomain();
    // Modify the Registrar to block premium names.
    persistResource(
        Registrar.loadByClientId("TheRegistrar").asBuilder().setBlockPremiumNames(true).build());
    runFlow();
  }

  @Test
  public void testFailure_reservedBlocked() throws Exception {
    thrown.expect(DomainReservedException.class);
    createTld("tld");
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(persistReservedList("tld-reserved", "example,FULLY_BLOCKED"))
            .build());
    persistPendingDeleteDomain();
    runFlow();
  }

  @Test
  public void testFailure_feeNotProvidedOnPremiumName() throws Exception {
    thrown.expect(FeesRequiredForPremiumNameException.class);
    createTld("example");
    setEppInput("domain_update_restore_request_premium.xml");
    persistPendingDeleteDomain();
    runFlow();
  }
}
