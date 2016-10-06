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
import static google.registry.flows.domain.DomainFlowUtils.getReservationType;
import static google.registry.model.EppResourceUtils.loadDomainApplication;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.pricing.PricingEngineProxy.getDomainCreateCost;
import static google.registry.util.CollectionUtils.isNullOrEmpty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.AuthorizationErrorException;
import google.registry.flows.EppException.ObjectDoesNotExistException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainResource.Builder;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.allocate.AllocateCreateExtension;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchInfoResponseExtension;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.poll.PendingActionNotificationResponse.DomainPendingActionNotificationResponse;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservationType;
import google.registry.model.reporting.HistoryEntry;
import google.registry.tmch.LordnTask;
import javax.inject.Inject;

/**
 * An EPP flow that allocates a new domain resource from a domain application.
 *
 * @error {@link google.registry.flows.ResourceCreateFlow.ResourceAlreadyExistsException}
 * @error {@link google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link DomainAllocateFlow.HasFinalStatusException}
 * @error {@link DomainAllocateFlow.MissingApplicationException}
 * @error {@link DomainAllocateFlow.OnlySuperuserCanAllocateException}
 */
public class DomainAllocateFlow extends DomainCreateOrAllocateFlow {

  protected AllocateCreateExtension allocateCreate;
  protected DomainApplication application;

  @Inject DomainAllocateFlow() {}

  @Override
  protected final void initDomainCreateOrAllocateFlow() {
    registerExtensions(AllocateCreateExtension.class);
    allocateCreate = eppInput.getSingleExtension(AllocateCreateExtension.class);
  }

  @Override
  protected final void verifyDomainCreateIsAllowed() throws EppException {
    if (!isSuperuser) {
      throw new OnlySuperuserCanAllocateException();
    }
    String applicationRoid = allocateCreate.getApplicationRoid();
    application = loadDomainApplication(applicationRoid, now);
    if (application == null) {
      throw new MissingApplicationException(applicationRoid);
    }
    if (application.getApplicationStatus().isFinalStatus()) {
      throw new HasFinalStatusException();
    }
  }

  @Override
  protected final void setDomainCreateOrAllocateProperties(Builder builder) {
    boolean sunrushAddGracePeriod = isNullOrEmpty(command.getNameservers());
    Registry registry = Registry.get(getTld());
    ImmutableSet.Builder<Flag> billingFlagsBuilder = new ImmutableSet.Builder<>();
    if (!application.getEncodedSignedMarks().isEmpty()) {
      billingFlagsBuilder.add(Flag.SUNRISE);
    } else {
      billingFlagsBuilder.add(Flag.LANDRUSH);
    }
    BillingEvent.OneTime billingEvent = new BillingEvent.OneTime.Builder()
        .setReason(Reason.CREATE)
        .setFlags(billingFlagsBuilder.add(Flag.ALLOCATION).build())
        .setTargetId(targetId)
        .setClientId(getClientId())
        // Note that the cost is calculated as of now, i.e. the event time, not the billing time,
        // which may be some additional days into the future.
        .setCost(getDomainCreateCost(targetId, now, command.getPeriod().getValue()))
        .setPeriodYears(command.getPeriod().getValue())
        .setEventTime(now)
        // If there are no nameservers on the domain, then they get the benefit of the sunrush
        // add grace period, which is longer than the standard add grace period.
        .setBillingTime(
            now.plus(
                sunrushAddGracePeriod
                    ? registry.getSunrushAddGracePeriodLength()
                    : registry.getAddGracePeriodLength()))
        .setParent(historyEntry)
        .build();
    ReservationType reservationType = getReservationType(domainName);
    ofy().save().<Object>entities(
        // Save the billing event
        billingEvent,
        // Update the application itself.
        application.asBuilder()
            .setApplicationStatus(ApplicationStatus.ALLOCATED)
            .removeStatusValue(StatusValue.PENDING_CREATE)
            .build(),
        // Create a poll message informing the registrar that the application status was updated.
        new PollMessage.OneTime.Builder()
            .setClientId(application.getCurrentSponsorClientId())
            .setEventTime(ofy().getTransactionTime())
            .setMsg(reservationType == ReservationType.NAME_COLLISION
                // Change the poll message to remind the registrar of the name collision policy.
                ? "Domain on the name collision list was allocated. "
                    + "But by policy, the domain will not be delegated. "
                    + "Please visit https://www.icann.org/namecollision "
                    + "for more information on name collision."
                : "Domain was allocated")
            .setResponseData(ImmutableList.of(
                DomainPendingActionNotificationResponse.create(
                    application.getFullyQualifiedDomainName(),
                    true,
                    // If the creation TRID is not present on the application (this can happen for
                    // older applications written before this field was added), then we must read
                    // the earliest history entry for the application to retrieve it.
                    application.getCreationTrid() == null
                        ? checkNotNull(ofy()
                            .load()
                            .type(HistoryEntry.class)
                            .ancestor(application)
                            .order("modificationTime")
                            .first()
                            .now()
                            .getTrid())
                        : application.getCreationTrid(),
                    now)))
            .setResponseExtensions(ImmutableList.of(
                new LaunchInfoResponseExtension.Builder()
                    .setApplicationId(application.getForeignKey())
                    .setPhase(application.getPhase())
                    .setApplicationStatus(ApplicationStatus.ALLOCATED)
                    .build()))
            .setParent(historyEntry)
            .build(),
        // Create a history entry (with no xml or trid) to record that we updated the application.
        new HistoryEntry.Builder()
            .setType(HistoryEntry.Type.DOMAIN_APPLICATION_STATUS_UPDATE)
            .setParent(application)
            .setModificationTime(now)
            .setClientId(application.getCurrentSponsorClientId())
            .setBySuperuser(true)
            .build());
    // Set the properties on the new domain.
    builder
        .addGracePeriod(GracePeriod.forBillingEvent(
            sunrushAddGracePeriod ? GracePeriodStatus.SUNRUSH_ADD : GracePeriodStatus.ADD,
            billingEvent))
        .setApplicationTime(allocateCreate.getApplicationTime())
        .setApplication(Key.create(application))
        .setSmdId(allocateCreate.getSmdId())
        .setLaunchNotice(allocateCreate.getNotice());
    // Names on the collision list will not be delegated. Set server hold.
    if (ReservationType.NAME_COLLISION == reservationType) {
      builder.addStatusValue(StatusValue.SERVER_HOLD);
    }
  }

  @Override
  protected void enqueueLordnTaskIfNeeded() {
    if (allocateCreate.getSmdId() != null || allocateCreate.getNotice() != null) {
      LordnTask.enqueueDomainResourceTask(newResource);
    }
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_ALLOCATE;
  }

  /** Domain application with specific ROID does not exist. */
  static class MissingApplicationException extends ObjectDoesNotExistException {
    public MissingApplicationException(String applicationRoid) {
      super(DomainApplication.class, applicationRoid);
    }
  }

  /** Domain application already has a final status. */
  static class HasFinalStatusException extends StatusProhibitsOperationException {
    public HasFinalStatusException() {
      super("Domain application already has a final status");
    }
  }

  /** Only a superuser can allocate domains. */
  static class OnlySuperuserCanAllocateException extends AuthorizationErrorException {
    public OnlySuperuserCanAllocateException() {
      super("Only a superuser can allocate domains");
    }
  }
}
