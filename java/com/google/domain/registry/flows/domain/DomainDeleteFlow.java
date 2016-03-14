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

package com.google.domain.registry.flows.domain;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.domain.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static com.google.domain.registry.flows.domain.DomainFlowUtils.updateAutorenewRecurrenceEndTime;
import static com.google.domain.registry.model.eppoutput.Result.Code.Success;
import static com.google.domain.registry.model.eppoutput.Result.Code.SuccessWithActionPending;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.util.CollectionUtils.nullToEmpty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.dns.DnsQueue;
import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.EppException.AssociationProhibitsOperationException;
import com.google.domain.registry.flows.ResourceSyncDeleteFlow;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.common.TimeOfYear;
import com.google.domain.registry.model.domain.DomainCommand.Delete;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.DomainResource.Builder;
import com.google.domain.registry.model.domain.GracePeriod;
import com.google.domain.registry.model.domain.fee.Credit;
import com.google.domain.registry.model.domain.fee.FeeDeleteResponseExtension;
import com.google.domain.registry.model.domain.metadata.MetadataExtension;
import com.google.domain.registry.model.domain.rgp.GracePeriodStatus;
import com.google.domain.registry.model.domain.secdns.SecDnsUpdateExtension;
import com.google.domain.registry.model.eppcommon.ProtocolDefinition.ServiceExtension;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.eppoutput.Response.ResponseExtension;
import com.google.domain.registry.model.eppoutput.Result.Code;
import com.google.domain.registry.model.poll.PendingActionNotificationResponse.DomainPendingActionNotificationResponse;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.reporting.HistoryEntry;

import com.googlecode.objectify.Key;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * An EPP flow that deletes a domain resource.
 *
 * @error {@link com.google.domain.registry.flows.ResourceCreateOrMutateFlow.OnlyToolCanPassMetadataException}
 * @error {@link com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link com.google.domain.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException}
 * @error {@link DomainDeleteFlow.DomainToDeleteHasHostsException}
 */
public class DomainDeleteFlow extends ResourceSyncDeleteFlow<DomainResource, Builder, Delete> {

  PollMessage.OneTime deletePollMessage;

  CurrencyUnit creditsCurrencyUnit;

  ImmutableList<Credit> credits;

  @Override
  protected void initResourceCreateOrMutateFlow() throws EppException {
    registerExtensions(SecDnsUpdateExtension.class, MetadataExtension.class);
  }

  @Override
  protected final void verifyMutationOnOwnedResourceAllowed() throws EppException {
    checkRegistryStateForTld(existingResource.getTld());
    checkAllowedAccessToTld(getAllowedTlds(), existingResource.getTld());
    if (!existingResource.getSubordinateHosts().isEmpty()) {
      throw new DomainToDeleteHasHostsException();
    }
  }

  @Override
  protected final void setDeleteProperties(Builder builder) {
    // Only set to PENDING_DELETE if this domain is not in the Add Grace Period. If domain is in Add
    // Grace Period, we delete it immediately.
    // The base class code already handles the immediate delete case, so we only have to handle the
    // pending delete case here.
    if (!existingResource.getGracePeriodStatuses().contains(GracePeriodStatus.ADD)) {
      Registry registry = Registry.get(existingResource.getTld());
      // By default, this should be 30 days of grace, and 5 days of pending delete. */
      DateTime deletionTime = now
          .plus(registry.getRedemptionGracePeriodLength())
          .plus(registry.getPendingDeleteLength());
      deletePollMessage = new PollMessage.OneTime.Builder()
          .setClientId(existingResource.getCurrentSponsorClientId())
          .setEventTime(deletionTime)
          .setMsg("Domain deleted.")
          .setResponseData(ImmutableList.of(DomainPendingActionNotificationResponse.create(
              existingResource.getFullyQualifiedDomainName(), true, trid, deletionTime)))
          .setParent(historyEntry)
          .build();
      builder.setStatusValues(ImmutableSet.of(StatusValue.PENDING_DELETE))
          .setDeletionTime(deletionTime)
          // Clear out all old grace periods and add REDEMPTION, which does not include a ref
          // to a billing event because there isn't one for a domain delete.
          .setGracePeriods(ImmutableSet.of(GracePeriod.createWithoutBillingEvent(
              GracePeriodStatus.REDEMPTION,
              now.plus(registry.getRedemptionGracePeriodLength()),
              getClientId())))
          .setDeletePollMessage(Key.create(deletePollMessage));
    }
  }

  @Override
  protected final void enqueueTasks() {
    DnsQueue.create().addDomainRefreshTask(existingResource.getFullyQualifiedDomainName());
  }

  @Override
  protected final void modifySyncDeleteRelatedResources() {
    // Cancel any grace periods that were still active.
    ImmutableList.Builder<Credit> creditsBuilder = new ImmutableList.Builder<>();
    for (GracePeriod gracePeriod : existingResource.getGracePeriods()) {
      // No cancellation is written if the grace period was not for a billable event.
      if (gracePeriod.hasBillingEvent()) {
        ofy().save().entity(
            BillingEvent.Cancellation.forGracePeriod(gracePeriod, historyEntry, targetId));

        Money cost;
        if (gracePeriod.getType() == GracePeriodStatus.AUTO_RENEW) {
          TimeOfYear recurrenceTimeOfYear =
              checkNotNull(gracePeriod.getRecurringBillingEvent()).get().getRecurrenceTimeOfYear();
          DateTime autoRenewTime = recurrenceTimeOfYear.beforeOrAt(now);
          cost = Registry.get(existingResource.getTld())
              .getDomainRenewCost(targetId, 1, autoRenewTime);
        } else {
          cost = checkNotNull(gracePeriod.getOneTimeBillingEvent()).get().getCost();
        }
        creditsBuilder.add(Credit.create(
            cost.negated().getAmount(),
            String.format("%s credit", gracePeriod.getType().getXmlName())));
        creditsCurrencyUnit = cost.getCurrencyUnit();
      }
    }
    credits = creditsBuilder.build();

    // If the delete isn't immediate, save the poll message for when the delete will happen.
    if (deletePollMessage != null) {
      ofy().save().entity(deletePollMessage);
    }
    // Close the autorenew billing event and poll message. This may delete the poll message.
    updateAutorenewRecurrenceEndTime(existingResource, now);

    // If there's a pending transfer, the gaining client's autorenew billing
    // event and poll message will already have been deleted in
    // ResourceDeleteFlow since it's listed in serverApproveEntities.
  }

  @Override
  protected final Code getDeleteResultCode() {
    return newResource.getDeletionTime().isAfter(now)
        ? SuccessWithActionPending : Success;
  }

  @Override
  protected final ImmutableList<? extends ResponseExtension> getDeleteResponseExtensions() {
    if (!credits.isEmpty()
        && nullToEmpty(sessionMetadata.getServiceExtensionUris()).contains(
            ServiceExtension.FEE_0_6.getUri())) {
      return ImmutableList.of(new FeeDeleteResponseExtension.Builder()
          .setCurrency(checkNotNull(creditsCurrencyUnit))
          .setCredits(credits)
          .build());
    } else {
      return null;
    }
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_DELETE;
  }

  /** Domain to be deleted has subordinate hosts. */
  static class DomainToDeleteHasHostsException extends AssociationProhibitsOperationException {
    public DomainToDeleteHasHostsException() {
      super("Domain to be deleted has subordinate hosts");
    }
  }
}
