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

package google.registry.flows.contact;

import static google.registry.flows.ResourceFlowUtils.createPendingTransferNotificationResponse;
import static google.registry.flows.ResourceFlowUtils.createTransferResponse;
import static google.registry.flows.ResourceFlowUtils.verifyAuthInfoForResource;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.eppoutput.Result.Code.Success;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.exceptions.NotPendingTransferException;
import google.registry.flows.exceptions.ResourceToMutateDoesNotExistException;
import google.registry.model.contact.ContactCommand.Transfer;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.poll.PollMessage;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferStatus;
import javax.inject.Inject;

/**
 * An EPP flow that approves a pending transfer on a {@link ContactResource}.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.exceptions.NotPendingTransferException}
 * @error {@link google.registry.flows.exceptions.ResourceToMutateDoesNotExistException}
 */
public class ContactTransferApproveFlow extends LoggedInFlow implements TransactionalFlow {

  @Inject ResourceCommand resourceCommand;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject ContactTransferApproveFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
  }

  @Override
  public final EppOutput run() throws EppException {
    Transfer command = (Transfer) resourceCommand;
    String targetId = command.getTargetId();
    ContactResource existingResource = loadByUniqueId(ContactResource.class, targetId, now);
    if (existingResource == null) {
      throw new ResourceToMutateDoesNotExistException(ContactResource.class, targetId);
    }
    if (command.getAuthInfo() != null) {
      verifyAuthInfoForResource(command.getAuthInfo(), existingResource);
    }
    if (existingResource.getTransferData().getTransferStatus() != TransferStatus.PENDING) {
      throw new NotPendingTransferException(targetId);
    }
    verifyResourceOwnership(getClientId(), existingResource);
    TransferData oldTransferData = existingResource.getTransferData();
    ContactResource newResource = existingResource.asBuilder()
        .removeStatusValue(StatusValue.PENDING_TRANSFER)
        .setLastTransferTime(now)
        .setCurrentSponsorClientId(existingResource.getTransferData().getGainingClientId())
        .setTransferData(oldTransferData.asBuilder()
            .setTransferStatus(TransferStatus.CLIENT_APPROVED)
            .setPendingTransferExpirationTime(now)
            .setExtendedRegistrationYears(null)
            .setServerApproveEntities(null)
            .setServerApproveBillingEvent(null)
            .setServerApproveAutorenewEvent(null)
            .setServerApproveAutorenewPollMessage(null)
            .build())
        .build();
    HistoryEntry historyEntry = historyBuilder
        .setType(HistoryEntry.Type.CONTACT_TRANSFER_APPROVE)
        .setModificationTime(now)
        .setParent(Key.create(existingResource))
        .build();
    // Create a poll message for the gaining client.
    PollMessage gainingPollMessage = new PollMessage.OneTime.Builder()
        .setClientId(oldTransferData.getGainingClientId())
        .setEventTime(now)
        .setMsg(TransferStatus.CLIENT_APPROVED.getMessage())
        .setResponseData(ImmutableList.of(
            createTransferResponse(newResource, newResource.getTransferData(), now),
            createPendingTransferNotificationResponse(
                existingResource, oldTransferData.getTransferRequestTrid(), true, now)))
        .setParent(historyEntry)
        .build();
    ofy().save().<Object>entities(newResource, historyEntry, gainingPollMessage);
    // Delete the billing event and poll messages that were written in case the transfer would have
    // been implicitly server approved.
    ofy().delete().keys(existingResource.getTransferData().getServerApproveEntities());
    return createOutput(
        Success, createTransferResponse(newResource, newResource.getTransferData(), now));
  }
}
