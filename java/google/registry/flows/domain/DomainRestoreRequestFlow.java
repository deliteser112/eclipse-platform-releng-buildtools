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

import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.newAutorenewBillingEvent;
import static google.registry.flows.domain.DomainFlowUtils.newAutorenewPollMessage;
import static google.registry.flows.domain.DomainFlowUtils.validateFeeChallenge;
import static google.registry.flows.domain.DomainFlowUtils.verifyNotReserved;
import static google.registry.flows.domain.DomainFlowUtils.verifyPremiumNameIsNotBlocked;
import static google.registry.model.domain.fee.Fee.FEE_UPDATE_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER;
import static google.registry.model.eppoutput.Result.Code.Success;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.pricing.PricingEngineProxy.getDomainRenewCost;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Ref;
import google.registry.dns.DnsQueue;
import google.registry.flows.EppException;
import google.registry.flows.EppException.CommandUseErrorException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.flows.OwnedResourceMutateFlow;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainCommand.Update;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.fee.Fee;
import google.registry.model.domain.fee.FeeTransformCommandExtension;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.domain.rgp.RgpUpdateExtension;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * An EPP flow that requests that a deleted domain be restored.
 *
 * @error {@link google.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link DomainFlowUtils.CurrencyUnitMismatchException}
 * @error {@link DomainFlowUtils.CurrencyValueScaleException}
 * @error {@link DomainFlowUtils.DomainReservedException}
 * @error {@link DomainFlowUtils.FeesMismatchException}
 * @error {@link DomainFlowUtils.FeesRequiredForPremiumNameException}
 * @error {@link DomainFlowUtils.PremiumNameBlockedException}
 * @error {@link DomainFlowUtils.UnsupportedFeeAttributeException}
 * @error {@link DomainRestoreRequestFlow.DomainNotEligibleForRestoreException}
 * @error {@link DomainRestoreRequestFlow.RestoreCommandIncludesChangesException}
 */
public class DomainRestoreRequestFlow extends OwnedResourceMutateFlow<DomainResource, Update>  {

  protected FeeTransformCommandExtension feeUpdate;
  protected Money restoreCost;
  protected Money renewCost;

  @Inject DomainRestoreRequestFlow() {}

  @Override
  protected final void initResourceCreateOrMutateFlow() throws EppException {
    registerExtensions(RgpUpdateExtension.class);
    registerExtensions(FEE_UPDATE_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER);
  }

  @Override
  protected final void verifyMutationOnOwnedResourceAllowed() throws EppException {
    // No other changes can be specified on a restore request.
    if (!command.noChangesPresent()) {
      throw new RestoreCommandIncludesChangesException();
    }

    // Domain must be in pendingDelete and within the redemptionPeriod to be eligible for restore.
    if (!existingResource.getStatusValues().contains(StatusValue.PENDING_DELETE)
        || !existingResource.getGracePeriodStatuses().contains(GracePeriodStatus.REDEMPTION)) {
      throw new DomainNotEligibleForRestoreException();
    }

    String tld = existingResource.getTld();
    checkAllowedAccessToTld(getAllowedTlds(), tld);
    if (!isSuperuser) {
      verifyNotReserved(InternetDomainName.from(targetId), false);
      verifyPremiumNameIsNotBlocked(targetId, now, getClientId());
    }
    feeUpdate = eppInput.getFirstExtensionOfClasses(
        FEE_UPDATE_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER);
    restoreCost = Registry.get(tld).getStandardRestoreCost();
    renewCost = getDomainRenewCost(targetId, now, 1);
    validateFeeChallenge(targetId, tld, now, feeUpdate, restoreCost, renewCost);
  }

  @Override
  protected final DomainResource createOrMutateResource() throws EppException {
    // We don't preserve the original expiration time of the domain when we restore, since doing so
    // would require us to know if they received a grace period refund when they deleted the domain,
    // and to charge them for that again. Instead, we just say that all restores get a fresh year of
    // registration and bill them for that accordingly.
    DateTime newExpirationTime = now.plusYears(1);

    // Bill for the restore.
    BillingEvent.OneTime restoreEvent = new BillingEvent.OneTime.Builder()
        .setReason(Reason.RESTORE)
        .setTargetId(targetId)
        .setClientId(getClientId())
        .setCost(restoreCost)
        .setEventTime(now)
        .setBillingTime(now)
        .setParent(historyEntry)
        .build();
    // Create a new autorenew billing event and poll message starting at the new expiration time.
    BillingEvent.Recurring autorenewEvent = newAutorenewBillingEvent(existingResource)
        .setEventTime(newExpirationTime)
        .setRecurrenceEndTime(END_OF_TIME)
        .setParent(historyEntry)
        .build();
    PollMessage.Autorenew autorenewPollMessage = newAutorenewPollMessage(existingResource)
        .setEventTime(newExpirationTime)
        .setAutorenewEndTime(END_OF_TIME)
        .setParent(historyEntry)
        .build();

    // Also bill for the 1 year cost of a domain renew. This is to avoid registrants being able to
    // game the system for premium names by renewing, deleting, and then restoring to get a free
    // year. Note that this billing event has no grace period; it is effective immediately.
    BillingEvent.OneTime renewEvent = new BillingEvent.OneTime.Builder()
        .setReason(Reason.RENEW)
        .setTargetId(targetId)
        .setClientId(getClientId())
        .setPeriodYears(1)
        .setCost(renewCost)
        .setEventTime(now)
        .setBillingTime(now)
        .setParent(historyEntry)
        .build();

    ofy().save().<Object>entities(restoreEvent, autorenewEvent, autorenewPollMessage, renewEvent);
    return existingResource.asBuilder()
        .setRegistrationExpirationTime(newExpirationTime)
        .setDeletionTime(END_OF_TIME)
        .setStatusValues(null)
        .setGracePeriods(null)
        .setDeletePollMessage(null)
        .setAutorenewBillingEvent(Ref.create(autorenewEvent))
        .setAutorenewPollMessage(Ref.create(autorenewPollMessage))
        .build();
  }

  @Override
  protected void modifyRelatedResources() {
    // Update the relevant {@link ForeignKey} to cache the new deletion time.
    ofy().save().entity(ForeignKeyIndex.create(newResource, newResource.getDeletionTime()));
    ofy().delete().key(existingResource.getDeletePollMessage());
  }

  @Override
  protected void enqueueTasks() {
    DnsQueue.create().addDomainRefreshTask(existingResource.getFullyQualifiedDomainName());
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_RESTORE;
  }

  @Override
  protected final EppOutput getOutput() {
    return createOutput(
        Success,
        null,
        (feeUpdate == null) ? null : ImmutableList.of(
            feeUpdate.createResponseBuilder()
              .setCurrency(restoreCost.getCurrencyUnit())
              .setFees(ImmutableList.of(
                  Fee.create(restoreCost.getAmount(), "restore"),
                  Fee.create(renewCost.getAmount(), "renew")))
              .build()));
  }

  /** Restore command cannot have other changes specified. */
  static class RestoreCommandIncludesChangesException extends CommandUseErrorException {
    public RestoreCommandIncludesChangesException() {
      super("Restore command cannot have other changes specified");
    }
  }

  /** Domain is not eligible for restore. */
  static class DomainNotEligibleForRestoreException extends StatusProhibitsOperationException {
    public DomainNotEligibleForRestoreException() {
      super("Domain is not eligible for restore");
    }
  }
}
