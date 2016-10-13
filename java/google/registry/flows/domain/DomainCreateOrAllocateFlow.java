// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.leapSafeAddYears;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.dns.DnsQueue;
import google.registry.flows.EppException;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.DomainResource.Builder;
import google.registry.model.domain.Period;
import google.registry.model.eppoutput.CreateData.DomainCreateData;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.Result;
import google.registry.model.poll.PollMessage;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** An EPP flow that creates or allocates a new domain resource. */
public abstract class DomainCreateOrAllocateFlow
    extends BaseDomainCreateFlow<DomainResource, Builder> {

  protected boolean isAnchorTenantViaExtension;

  @Inject DnsQueue dnsQueue;

  @Override
  protected final void initDomainCreateFlow() {
    isAnchorTenantViaExtension =
        (metadataExtension != null && metadataExtension.getIsAnchorTenant());
    initDomainCreateOrAllocateFlow();
  }

  protected abstract void initDomainCreateOrAllocateFlow();

  @Override
  protected final void setDomainCreateProperties(Builder builder) throws EppException {
    DateTime registrationExpirationTime = leapSafeAddYears(now, command.getPeriod().getValue());
    // Create a new autorenew billing event and poll message starting at the expiration time.
    BillingEvent.Recurring autorenewEvent = new BillingEvent.Recurring.Builder()
        .setReason(Reason.RENEW)
        .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
        .setTargetId(targetId)
        .setClientId(getClientId())
        .setEventTime(registrationExpirationTime)
        .setRecurrenceEndTime(END_OF_TIME)
        .setParent(historyEntry)
        .build();
    PollMessage.Autorenew autorenewPollMessage = new PollMessage.Autorenew.Builder()
        .setTargetId(targetId)
        .setClientId(getClientId())
        .setEventTime(registrationExpirationTime)
        .setMsg("Domain was auto-renewed.")
        .setParent(historyEntry)
        .build();
    ofy().save().<Object>entities(autorenewEvent, autorenewPollMessage);

    builder
        .setRegistrationExpirationTime(registrationExpirationTime)
        .setAutorenewBillingEvent(Key.create(autorenewEvent))
        .setAutorenewPollMessage(Key.create(autorenewPollMessage));
    setDomainCreateOrAllocateProperties(builder);
  }

  /** Subclasses must override this to set more fields, like any grace period. */
  protected abstract void setDomainCreateOrAllocateProperties(Builder builder) throws EppException;

  @Override
  protected final void enqueueTasks() {
    if (newResource.shouldPublishToDns()) {
      dnsQueue.addDomainRefreshTask(newResource.getFullyQualifiedDomainName());
    }
    enqueueLordnTaskIfNeeded();
  }

  /** Subclasses must override this to enqueue any additional tasks. */
  protected abstract void enqueueLordnTaskIfNeeded();

  @Override
  protected final Period getCommandPeriod() {
    return command.getPeriod();
  }

  @Override
  protected final EppOutput getOutput() {
    return createOutput(
        Result.Code.SUCCESS,
        DomainCreateData.create(
            newResource.getFullyQualifiedDomainName(),
            now,
            newResource.getRegistrationExpirationTime()),
        (feeCreate == null) ? null : ImmutableList.of(
            feeCreate.createResponseBuilder()
                .setCurrency(commandOperations.getCurrency())
                .setFees(commandOperations.getFees())
                .setCredits(commandOperations.getCredits())
                .build()));
  }
}
