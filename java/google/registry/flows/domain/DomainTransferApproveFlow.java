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
import static google.registry.flows.domain.DomainFlowUtils.updateAutorenewRecurrenceEndTime;
import static google.registry.model.domain.DomainResource.extendRegistrationWithCap;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.pricing.PricingEngineProxy.getDomainRenewCost;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.ResourceTransferApproveFlow;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainCommand.Transfer;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.DomainResource.Builder;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferData;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * An EPP flow that approves a pending transfer on a {@link DomainResource}.
 *
 * <p>The logic in this flow, which handles client approvals, very closely parallels the logic in
 * {@link DomainResource#cloneProjectedAtTime} which handles implicit server approvals.
 *
 * @error {@link google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link google.registry.flows.ResourceMutatePendingTransferFlow.NotPendingTransferException}
 */
public class DomainTransferApproveFlow extends
    ResourceTransferApproveFlow<DomainResource, Builder, Transfer> {

  @Inject DomainTransferApproveFlow() {}

  @Override
  protected void verifyOwnedResourcePendingTransferMutationAllowed() throws EppException {
    checkAllowedAccessToTld(getAllowedTlds(), existingResource.getTld());
  }

  @Override
  protected final void setTransferApproveProperties(Builder builder) {
    TransferData transferData = existingResource.getTransferData();
    String gainingClientId = transferData.getGainingClientId();
    String tld = existingResource.getTld();
    int extraYears = transferData.getExtendedRegistrationYears();
    // Bill for the transfer.
    BillingEvent.OneTime billingEvent =
        new BillingEvent.OneTime.Builder()
            .setReason(Reason.TRANSFER)
            .setTargetId(targetId)
            .setClientId(gainingClientId)
            .setPeriodYears(extraYears)
            .setCost(
                getDomainRenewCost(targetId, transferData.getTransferRequestTime(), extraYears))
            .setEventTime(now)
            .setBillingTime(now.plus(Registry.get(tld).getTransferGracePeriodLength()))
            .setParent(historyEntry)
            .build();
    ofy().save().entity(billingEvent);
    // If we are within an autorenew grace period, cancel the autorenew billing event and reduce
    // the number of years to extend the registration by one.
    GracePeriod autorenewGrace = Iterables.getOnlyElement(FluentIterable
        .from(existingResource.getGracePeriods())
        .filter(new Predicate<GracePeriod>(){
            @Override
            public boolean apply(GracePeriod gracePeriod) {
              return GracePeriodStatus.AUTO_RENEW.equals(gracePeriod.getType());
            }}), null);
    if (autorenewGrace != null) {
      extraYears--;
      ofy().save().entity(
          BillingEvent.Cancellation.forGracePeriod(autorenewGrace, historyEntry, targetId));
    }
    // Close the old autorenew event and poll message at the transfer time (aka now). This may end
    // up deleting the poll message.
    updateAutorenewRecurrenceEndTime(existingResource, now);
    DateTime newExpirationTime = extendRegistrationWithCap(
        now, existingResource.getRegistrationExpirationTime(), extraYears);
    // Create a new autorenew event starting at the expiration time.
    BillingEvent.Recurring autorenewEvent = new BillingEvent.Recurring.Builder()
        .setReason(Reason.RENEW)
        .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
        .setTargetId(targetId)
        .setClientId(gainingClientId)
        .setEventTime(newExpirationTime)
        .setRecurrenceEndTime(END_OF_TIME)
        .setParent(historyEntry)
        .build();
    ofy().save().entity(autorenewEvent);
    // Create a new autorenew poll message.
    PollMessage.Autorenew gainingClientAutorenewPollMessage = new PollMessage.Autorenew.Builder()
        .setTargetId(targetId)
        .setClientId(gainingClientId)
        .setEventTime(newExpirationTime)
        .setAutorenewEndTime(END_OF_TIME)
        .setMsg("Domain was auto-renewed.")
        .setParent(historyEntry)
        .build();
    ofy().save().entity(gainingClientAutorenewPollMessage);
    builder
        .setRegistrationExpirationTime(newExpirationTime)
        .setAutorenewBillingEvent(Key.create(autorenewEvent))
        .setAutorenewPollMessage(Key.create(gainingClientAutorenewPollMessage))
        // Remove all the old grace periods and add a new one for the transfer.
        .setGracePeriods(ImmutableSet.of(
            GracePeriod.forBillingEvent(GracePeriodStatus.TRANSFER, billingEvent)));
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_TRANSFER_APPROVE;
  }
}
