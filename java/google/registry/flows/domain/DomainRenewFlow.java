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

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.newAutorenewBillingEvent;
import static google.registry.flows.domain.DomainFlowUtils.newAutorenewPollMessage;
import static google.registry.flows.domain.DomainFlowUtils.updateAutorenewRecurrenceEndTime;
import static google.registry.flows.domain.DomainFlowUtils.validateFeeChallenge;
import static google.registry.flows.domain.DomainFlowUtils.verifyUnitIsYears;
import static google.registry.model.domain.DomainResource.MAX_REGISTRATION_YEARS;
import static google.registry.model.domain.fee.Fee.FEE_RENEW_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.pricing.PricingEngineProxy.getDomainRenewCost;
import static google.registry.util.DateTimeUtils.leapSafeAddYears;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.ObjectPendingTransferException;
import google.registry.flows.EppException.ParameterValueRangeErrorException;
import google.registry.flows.OwnedResourceMutateFlow;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainCommand.Renew;
import google.registry.model.domain.DomainRenewData;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.Period;
import google.registry.model.domain.fee.BaseFee.FeeType;
import google.registry.model.domain.fee.Fee;
import google.registry.model.domain.fee.FeeTransformCommandExtension;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferStatus;
import java.util.Set;
import javax.inject.Inject;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * An EPP flow that updates a domain resource.
 *
 * @error {@link google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link google.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException}
 * @error {@link DomainFlowUtils.BadPeriodUnitException}
 * @error {@link DomainFlowUtils.CurrencyUnitMismatchException}
 * @error {@link DomainFlowUtils.CurrencyValueScaleException}
 * @error {@link DomainFlowUtils.FeesMismatchException}
 * @error {@link DomainFlowUtils.FeesRequiredForPremiumNameException}
 * @error {@link DomainFlowUtils.UnsupportedFeeAttributeException}
 * @error {@link DomainRenewFlow.DomainHasPendingTransferException}
 * @error {@link DomainRenewFlow.ExceedsMaxRegistrationYearsException}
 * @error {@link DomainRenewFlow.IncorrectCurrentExpirationDateException}
 */
public class DomainRenewFlow extends OwnedResourceMutateFlow<DomainResource, Renew> {

  private static final Set<StatusValue> RENEW_DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.CLIENT_RENEW_PROHIBITED,
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_RENEW_PROHIBITED);

  protected FeeTransformCommandExtension feeRenew;
  protected Money renewCost;

  protected Optional<RegistryExtraFlowLogic> extraFlowLogic;

  @Inject DomainRenewFlow() {}

  @Override
  protected Set<StatusValue> getDisallowedStatuses() {
    return RENEW_DISALLOWED_STATUSES;
  }

  @Override
  public final void initResourceCreateOrMutateFlow() throws EppException {
    registerExtensions(FEE_RENEW_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER);
    feeRenew =
        eppInput.getFirstExtensionOfClasses(FEE_RENEW_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER);
    extraFlowLogic = RegistryExtraFlowLogicProxy.newInstanceForDomain(existingResource);
  }

  @Override
  protected void verifyMutationOnOwnedResourceAllowed() throws EppException {
    checkAllowedAccessToTld(getAllowedTlds(), existingResource.getTld());
    // Verify that the resource does not have a pending transfer on it.
    if (existingResource.getTransferData().getTransferStatus() == TransferStatus.PENDING) {
      throw new DomainHasPendingTransferException(targetId);
    }
    verifyUnitIsYears(command.getPeriod());
    // If the date they specify doesn't match the expiration, fail. (This is an idempotence check).
    if (!command.getCurrentExpirationDate().equals(
        existingResource.getRegistrationExpirationTime().toLocalDate())) {
      throw new IncorrectCurrentExpirationDateException();
    }
    renewCost = getDomainRenewCost(targetId, now, command.getPeriod().getValue());
    validateFeeChallenge(
        targetId, existingResource.getTld(), now, feeRenew, renewCost);
  }

  @Override
  protected DomainResource createOrMutateResource() throws EppException {
    DateTime newExpirationTime = leapSafeAddYears(
        existingResource.getRegistrationExpirationTime(), command.getPeriod().getValue());
    // Bill for this explicit renew itself.
    BillingEvent.OneTime explicitRenewEvent = new BillingEvent.OneTime.Builder()
        .setReason(Reason.RENEW)
        .setTargetId(targetId)
        .setClientId(getClientId())
        .setPeriodYears(command.getPeriod().getValue())
        .setCost(checkNotNull(renewCost))
        .setEventTime(now)
        .setBillingTime(
            now.plus(Registry.get(existingResource.getTld()).getRenewGracePeriodLength()))
        .setParent(historyEntry)
        .build();
    // End the old autorenew billing event and poll message now. This may delete the poll message.
    updateAutorenewRecurrenceEndTime(existingResource, now);
    // Create a new autorenew billing event and poll message starting at the new expiration time.
    BillingEvent.Recurring newAutorenewEvent = newAutorenewBillingEvent(existingResource)
        .setEventTime(newExpirationTime)
        .setParent(historyEntry)
        .build();
    PollMessage.Autorenew newAutorenewPollMessage = newAutorenewPollMessage(existingResource)
        .setEventTime(newExpirationTime)
        .setParent(historyEntry)
        .build();

    // Handle extra flow logic, if any.
    if (extraFlowLogic.isPresent()) {
      extraFlowLogic.get().performAdditionalDomainRenewLogic(
          existingResource,
          getClientId(),
          now,
          command.getPeriod().getValue(),
          eppInput,
          historyEntry);
    }

    ofy().save().<Object>entities(explicitRenewEvent, newAutorenewEvent, newAutorenewPollMessage);
    return existingResource.asBuilder()
        .setRegistrationExpirationTime(newExpirationTime)
        .setAutorenewBillingEvent(Key.create(newAutorenewEvent))
        .setAutorenewPollMessage(Key.create(newAutorenewPollMessage))
        .addGracePeriod(GracePeriod.forBillingEvent(GracePeriodStatus.RENEW, explicitRenewEvent))
        .build();
  }

  @Override
  protected void verifyNewStateIsAllowed() throws EppException {
    if (leapSafeAddYears(now, MAX_REGISTRATION_YEARS)
        .isBefore(newResource.getRegistrationExpirationTime())) {
      throw new ExceedsMaxRegistrationYearsException();
    }
  }

  /** Commit any extra flow logic. */
  @Override
  protected final void modifyRelatedResources() {
    if (extraFlowLogic.isPresent()) {
      extraFlowLogic.get().commitAdditionalLogicChanges();
    }
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_RENEW;
  }

  @Override
  protected final Period getCommandPeriod() {
    return command.getPeriod();
  }

  @Override
  protected final EppOutput getOutput() {
    return createOutput(
        SUCCESS,
        DomainRenewData.create(
            newResource.getFullyQualifiedDomainName(), newResource.getRegistrationExpirationTime()),
        (feeRenew == null)
            ? null
            : ImmutableList.of(
                feeRenew
                    .createResponseBuilder()
                    .setCurrency(renewCost.getCurrencyUnit())
                    .setFees(
                        ImmutableList.of(Fee.create(renewCost.getAmount(), FeeType.RENEW)))
                    .build()));
  }

  /** The domain has a pending transfer on it and so can't be explicitly renewed. */
  public static class DomainHasPendingTransferException extends ObjectPendingTransferException {
    public DomainHasPendingTransferException(String targetId) {
      super(targetId);
    }
  }

  /** The current expiration date is incorrect. */
  static class IncorrectCurrentExpirationDateException extends ParameterValueRangeErrorException {
    public IncorrectCurrentExpirationDateException() {
      super("The current expiration date is incorrect");
    }
  }

  /** New registration period exceeds maximum number of years. */
  static class ExceedsMaxRegistrationYearsException extends ParameterValueRangeErrorException {
    public ExceedsMaxRegistrationYearsException() {
      super(String.format(
          "Registrations cannot extend for more than %d years into the future",
          MAX_REGISTRATION_YEARS));
    }
  }
}
