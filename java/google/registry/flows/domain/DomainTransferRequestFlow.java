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

package google.registry.flows.domain;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Sets.union;
import static google.registry.flows.ResourceFlowUtils.loadAndVerifyExistence;
import static google.registry.flows.ResourceFlowUtils.verifyNoDisallowedStatuses;
import static google.registry.flows.ResourceFlowUtils.verifyRequiredAuthInfoForResourceTransfer;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.createGainingTransferPollMessage;
import static google.registry.flows.domain.DomainFlowUtils.createLosingTransferPollMessage;
import static google.registry.flows.domain.DomainFlowUtils.createTransferResponse;
import static google.registry.flows.domain.DomainFlowUtils.updateAutorenewRecurrenceEndTime;
import static google.registry.flows.domain.DomainFlowUtils.validateFeeChallenge;
import static google.registry.flows.domain.DomainFlowUtils.verifyPremiumNameIsNotBlocked;
import static google.registry.flows.domain.DomainFlowUtils.verifyUnitIsYears;
import static google.registry.model.domain.DomainResource.extendRegistrationWithCap;
import static google.registry.model.domain.fee.Fee.FEE_TRANSFER_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_WITH_ACTION_PENDING;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.pricing.PricingEngineProxy.getDomainRenewCost;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.exceptions.AlreadyPendingTransferException;
import google.registry.flows.exceptions.ObjectAlreadySponsoredException;
import google.registry.model.ImmutableObject;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainCommand.Transfer;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.Period;
import google.registry.model.domain.fee.BaseFee.FeeType;
import google.registry.model.domain.fee.Fee;
import google.registry.model.domain.fee.FeeTransformCommandExtension;
import google.registry.model.domain.fee.FeeTransformResponseExtension;
import google.registry.model.domain.flags.FlagsTransferCommandExtension;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferData.Builder;
import google.registry.model.transfer.TransferData.TransferServerApproveEntity;
import google.registry.model.transfer.TransferResponse.DomainTransferResponse;
import google.registry.model.transfer.TransferStatus;
import javax.inject.Inject;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * An EPP flow that requests a transfer on a domain.
 *
 * <p>The "gaining" registrar requests a transfer from the "losing" (aka current) registrar. The
 * losing registrar has a "transfer" time period to respond (by default five days) after which the
 * transfer is automatically approved. Within that window, the transfer might be approved explicitly
 * by the losing registrar or rejected, and the gaining registrar can also cancel the transfer
 * request.
 *
 * <p>When a transfer is requested, poll messages and billing events are saved to Datastore with
 * timestamps such that they only become active when the server-approval period passes. Keys to
 * these speculative objects are saved in the domain's transfer data, and on explicit approval,
 * rejection or cancellation of the request, they will be deleted (and in the approval case,
 * replaced with new ones with the correct approval time).
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException}
 * @error {@link google.registry.flows.exceptions.AlreadyPendingTransferException}
 * @error {@link google.registry.flows.exceptions.MissingTransferRequestAuthInfoException}
 * @error {@link google.registry.flows.exceptions.ObjectAlreadySponsoredException}
 * @error {@link google.registry.flows.exceptions.ResourceStatusProhibitsOperationException}
 * @error {@link DomainFlowUtils.BadPeriodUnitException}
 * @error {@link DomainFlowUtils.CurrencyUnitMismatchException}
 * @error {@link DomainFlowUtils.CurrencyValueScaleException}
 * @error {@link DomainFlowUtils.FeesMismatchException}
 * @error {@link DomainFlowUtils.FeesRequiredForPremiumNameException}
 * @error {@link DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link DomainFlowUtils.PremiumNameBlockedException}
 * @error {@link DomainFlowUtils.UnsupportedFeeAttributeException}
 */
public final class DomainTransferRequestFlow extends LoggedInFlow implements TransactionalFlow {

  private static final ImmutableSet<StatusValue> DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.CLIENT_TRANSFER_PROHIBITED,
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_TRANSFER_PROHIBITED);

  @Inject ResourceCommand resourceCommand;
  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String gainingClientId;
  @Inject @TargetId String targetId;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject DomainTransferRequestFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class, FlagsTransferCommandExtension.class);
    registerExtensions(FEE_TRANSFER_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER);
  }

  @Override
  public final EppOutput run() throws EppException {
    Period period = ((Transfer) resourceCommand).getPeriod();
    int years = period.getValue();
    DomainResource existingDomain = loadAndVerifyExistence(DomainResource.class, targetId, now);
    verifyTransferAllowed(existingDomain, period);
    String tld = existingDomain.getTld();
    Registry registry = Registry.get(tld);
    // The cost of the renewal implied by a transfer.
    Money renewCost = getDomainRenewCost(targetId, now, years);
    // An optional extension from the client specifying what they think the transfer should cost.
    FeeTransformCommandExtension feeTransfer = eppInput.getFirstExtensionOfClasses(
        FEE_TRANSFER_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER);
    validateFeeChallenge(targetId, tld, now, feeTransfer, renewCost);
    ImmutableSet.Builder<ImmutableObject> entitiesToSave = new ImmutableSet.Builder<>();
    HistoryEntry historyEntry = buildHistory(period, existingDomain);
    entitiesToSave.add(historyEntry);
    DateTime automaticTransferTime = now.plus(registry.getAutomaticTransferLength());
    // The new expiration time if there is a server approval.
    DateTime serverApproveNewExpirationTime = extendRegistrationWithCap(
        automaticTransferTime, existingDomain.getRegistrationExpirationTime(), years);
    ImmutableSet<BillingEvent> billingEvents = createBillingEvents(
        renewCost,
        registry,
        existingDomain,
        historyEntry,
        automaticTransferTime,
        serverApproveNewExpirationTime,
        years);
    entitiesToSave.addAll(billingEvents);
    ImmutableSet<PollMessage> pollMessages = createPollMessages(
        existingDomain,
        historyEntry,
        automaticTransferTime,
        serverApproveNewExpirationTime,
        years);
    entitiesToSave.addAll(pollMessages);
    ImmutableSet.Builder<Key<? extends TransferServerApproveEntity>> serverApproveEntities =
        new ImmutableSet.Builder<>();
    for (TransferServerApproveEntity entity : union(billingEvents, pollMessages)) {
      serverApproveEntities.add(Key.create(entity));
    }
    // Create the transfer data that represents the pending transfer.
    TransferData pendingTransferData =  createTransferDataBuilder()
        .setTransferStatus(TransferStatus.PENDING)
        .setLosingClientId(existingDomain.getCurrentSponsorClientId())
        .setPendingTransferExpirationTime(automaticTransferTime)
        .setExtendedRegistrationYears(years)
        .setServerApproveBillingEvent(Key.create(
            getOnlyElement(filter(billingEvents, BillingEvent.OneTime.class))))
        .setServerApproveAutorenewEvent(Key.create(
            getOnlyElement(filter(billingEvents, BillingEvent.Recurring.class))))
        .setServerApproveAutorenewPollMessage(Key.create(
            getOnlyElement(filter(pollMessages, PollMessage.Autorenew.class))))
        .setServerApproveEntities(serverApproveEntities.build())
        .build();
    // When a transfer is requested, a poll message is created to notify the losing registrar.
    PollMessage requestPollMessage = createLosingTransferPollMessage(
        targetId, pendingTransferData, serverApproveNewExpirationTime, historyEntry)
            .asBuilder().setEventTime(now).build();
    entitiesToSave.add(requestPollMessage);
    // End the old autorenew event and poll message at the implicit transfer time. This may delete
    // the poll message if it has no events left. Note that this is still left on the domain as the
    // autorenewBillingEvent because it is still the current autorenew event until the transfer
    // happens. If you read the domain after the transfer occurs, then cloneProjectedAtTime() will
    // move the serverApproveAutoRenewEvent into the autoRenewEvent field.
    updateAutorenewRecurrenceEndTime(existingDomain, automaticTransferTime);
    handleExtraFlowLogic(years, existingDomain, historyEntry);
    DomainResource newDomain = existingDomain.asBuilder()
        .setTransferData(pendingTransferData)
        .addStatusValue(StatusValue.PENDING_TRANSFER)
        .build();
    ofy().save().entities(entitiesToSave.add(newDomain).build());
    return createOutput(
        SUCCESS_WITH_ACTION_PENDING,
        createResponse(period, existingDomain, newDomain),
        createResponseExtensions(renewCost, feeTransfer));
  }

  private void verifyTransferAllowed(DomainResource existingDomain, Period period)
      throws EppException {
    verifyNoDisallowedStatuses(existingDomain, DISALLOWED_STATUSES);
    verifyRequiredAuthInfoForResourceTransfer(authInfo, existingDomain);
    // Verify that the resource does not already have a pending transfer.
    if (TransferStatus.PENDING.equals(existingDomain.getTransferData().getTransferStatus())) {
      throw new AlreadyPendingTransferException(targetId);
    }
    // Verify that this client doesn't already sponsor this resource.
    if (gainingClientId.equals(existingDomain.getCurrentSponsorClientId())) {
      throw new ObjectAlreadySponsoredException();
    }
    checkAllowedAccessToTld(getAllowedTlds(), existingDomain.getTld());
    verifyUnitIsYears(period);
    if (!isSuperuser) {
      verifyPremiumNameIsNotBlocked(targetId, now, gainingClientId);
    }
  }

  private HistoryEntry buildHistory(Period period, DomainResource existingResource) {
    return historyBuilder
        .setType(HistoryEntry.Type.DOMAIN_TRANSFER_REQUEST)
        .setPeriod(period)
        .setModificationTime(now)
        .setParent(Key.create(existingResource))
        .build();
  }

  private ImmutableSet<BillingEvent> createBillingEvents(
      Money renewCost,
      Registry registry,
      DomainResource existingDomain,
      HistoryEntry historyEntry,
      DateTime automaticTransferTime,
      DateTime serverApproveNewExpirationTime,
      int years) {
    ImmutableSet.Builder<BillingEvent> billingEvents = new ImmutableSet.Builder<>();
    BillingEvent.OneTime transferBillingEvent =
        createTransferBillingEvent(years, renewCost, registry, historyEntry);
    BillingEvent.Recurring gainingClientAutorenewEvent = createGainingClientAutorenewEvent(
        historyEntry, serverApproveNewExpirationTime);
    billingEvents.add(transferBillingEvent, gainingClientAutorenewEvent);
    // If there will be an autorenew between now and the automatic transfer time, and if the
    // autorenew grace period length is long enough that the domain will still be within it at the
    // automatic transfer time, then the transfer will subsume the autorenew so we need to write out
    // a cancellation for it.
    DateTime oldExpirationTime = existingDomain.getRegistrationExpirationTime();
    if (automaticTransferTime.isAfter(oldExpirationTime) && automaticTransferTime.isBefore(
        oldExpirationTime.plus(registry.getAutoRenewGracePeriodLength()))) {
      BillingEvent.Cancellation autorenewCancellation =
          createAutorenewCancellation(
              existingDomain, historyEntry, automaticTransferTime, registry);
      billingEvents.add(autorenewCancellation);
    }
    return billingEvents.build();
  }

  private BillingEvent.OneTime createTransferBillingEvent(
      int years, Money renewCost, Registry registry, HistoryEntry historyEntry) {
    DateTime automaticTransferTime = now.plus(registry.getAutomaticTransferLength());
    return new BillingEvent.OneTime.Builder()
        .setReason(Reason.TRANSFER)
        .setTargetId(targetId)
        .setClientId(gainingClientId)
        .setCost(renewCost)
        .setPeriodYears(years)
        .setEventTime(automaticTransferTime)
        .setBillingTime(automaticTransferTime.plus(registry.getTransferGracePeriodLength()))
        .setParent(historyEntry)
        .build();
  }

  private BillingEvent.Recurring createGainingClientAutorenewEvent(
      HistoryEntry historyEntry, DateTime serverApproveNewExpirationTime) {
    return new BillingEvent.Recurring.Builder()
        .setReason(Reason.RENEW)
        .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
        .setTargetId(targetId)
        .setClientId(gainingClientId)
        .setEventTime(serverApproveNewExpirationTime)
        .setRecurrenceEndTime(END_OF_TIME)
        .setParent(historyEntry)
        .build();
  }

  /**
   * Creates an autorenew cancellation.
   *
   * <p>If there will be an autorenew between now and the automatic transfer time, and if the
   * autorenew grace period length is long enough that the domain will still be within it at the
   * automatic transfer time, then the transfer will subsume the autorenew and we need to write out
   * a cancellation for it.
   */
  private BillingEvent.Cancellation createAutorenewCancellation(
      DomainResource existingDomain,
      HistoryEntry historyEntry,
      DateTime automaticTransferTime,
      Registry registry) {
    return new BillingEvent.Cancellation.Builder()
        .setReason(Reason.RENEW)
        .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
        .setTargetId(targetId)
        .setClientId(existingDomain.getCurrentSponsorClientId())
        .setEventTime(automaticTransferTime)
        .setBillingTime(existingDomain.getRegistrationExpirationTime()
            .plus(registry.getAutoRenewGracePeriodLength()))
        .setRecurringEventKey(existingDomain.getAutorenewBillingEvent())
        .setParent(historyEntry)
        .build();
  }

  /** Create the message that will be sent to the gaining registrar on server approval. */
  private PollMessage createServerApproveGainingPollMessage(
      DomainResource existingDomain,
      HistoryEntry historyEntry,
      DateTime automaticTransferTime,
      DateTime serverApproveNewExpirationTime,
      int years) {
    return createGainingTransferPollMessage(
        targetId,
        createTransferDataBuilder()
            .setTransferStatus(TransferStatus.SERVER_APPROVED)
            .setLosingClientId(existingDomain.getCurrentSponsorClientId())
            .setPendingTransferExpirationTime(automaticTransferTime)
            .setExtendedRegistrationYears(years)
            .build(),
        serverApproveNewExpirationTime,
        historyEntry);
  }

  private ImmutableSet<PollMessage> createPollMessages(
      DomainResource existingDomain,
      HistoryEntry historyEntry,
      DateTime automaticTransferTime,
      DateTime serverApproveNewExpirationTime,
      int years) {
    PollMessage.Autorenew gainingClientAutorenewPollMessage =
        createGainingClientAutorenewPollMessage(historyEntry, serverApproveNewExpirationTime);
    PollMessage serverApproveGainingPollMessage = createServerApproveGainingPollMessage(
        existingDomain, historyEntry, automaticTransferTime, serverApproveNewExpirationTime, years);
    PollMessage serverApproveLosingPollMessage = createServerApproveLosingPollMessage(
        existingDomain, historyEntry, automaticTransferTime, serverApproveNewExpirationTime, years);
    return ImmutableSet.of(
        gainingClientAutorenewPollMessage,
        serverApproveGainingPollMessage,
        serverApproveLosingPollMessage);
  }

  private PollMessage.Autorenew createGainingClientAutorenewPollMessage(
      HistoryEntry historyEntry, DateTime serverApproveNewExpirationTime) {
    return new PollMessage.Autorenew.Builder()
        .setTargetId(targetId)
        .setClientId(gainingClientId)
        .setEventTime(serverApproveNewExpirationTime)
        .setAutorenewEndTime(END_OF_TIME)
        .setMsg("Domain was auto-renewed.")
        .setParent(historyEntry)
        .build();
  }

  /** Create the message that will be sent to the losing registrar on server approval. */
  private PollMessage createServerApproveLosingPollMessage(
      DomainResource existingDomain,
      HistoryEntry historyEntry,
      DateTime automaticTransferTime,
      DateTime serverApproveNewExpirationTime,
      int years) {
    return createLosingTransferPollMessage(
        targetId,
        createTransferDataBuilder()
            .setTransferStatus(TransferStatus.SERVER_APPROVED)
            .setLosingClientId(existingDomain.getCurrentSponsorClientId())
            .setPendingTransferExpirationTime(automaticTransferTime)
            .setExtendedRegistrationYears(years)
            .build(),
        serverApproveNewExpirationTime,
        historyEntry);
  }

  private Builder createTransferDataBuilder() {
    return new TransferData.Builder()
        .setTransferRequestTime(now)
        .setGainingClientId(gainingClientId)
        .setTransferRequestTrid(trid);
  }

  private void handleExtraFlowLogic(
      int years, DomainResource existingDomain, HistoryEntry historyEntry) throws EppException {
    Optional<RegistryExtraFlowLogic> extraFlowLogic =
        RegistryExtraFlowLogicProxy.newInstanceForDomain(existingDomain);
    if (extraFlowLogic.isPresent()) {
      extraFlowLogic.get().performAdditionalDomainTransferLogic(
          existingDomain, gainingClientId, now, years, eppInput, historyEntry);
      extraFlowLogic.get().commitAdditionalLogicChanges();
    }
  }

  private DomainTransferResponse createResponse(
      Period period, DomainResource existingDomain, DomainResource newDomain) {
    // If the registration were approved this instant, this is what the new expiration would be,
    // because we cap at 10 years from the moment of approval. This is different than the server
    // approval new expiration time, which is capped at 10 years from the server approve time.
    DateTime approveNowExtendedRegistrationTime = extendRegistrationWithCap(
        now,
        existingDomain.getRegistrationExpirationTime(),
        period.getValue());
    return createTransferResponse(
        targetId, newDomain.getTransferData(), approveNowExtendedRegistrationTime);
  }

  private ImmutableList<FeeTransformResponseExtension> createResponseExtensions(Money renewCost,
      FeeTransformCommandExtension feeTransfer) {
    return feeTransfer == null
        ? null
        : ImmutableList.of(feeTransfer.createResponseBuilder()
            .setCurrency(renewCost.getCurrencyUnit())
            .setFees(ImmutableList.of(Fee.create(renewCost.getAmount(), FeeType.RENEW)))
            .build());
  }
}
