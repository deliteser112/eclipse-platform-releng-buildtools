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

import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfoForResource;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.flows.contact.ContactFlowUtils.createGainingTransferPollMessage;
import static google.registry.flows.contact.ContactFlowUtils.createTransferResponse;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Optional;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.exceptions.NotPendingTransferException;
import google.registry.flows.exceptions.ResourceToMutateDoesNotExistException;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.poll.PollMessage;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferStatus;
import javax.inject.Inject;

/**
 * An EPP flow that rejects a pending transfer on a {@link ContactResource}.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.exceptions.NotPendingTransferException}
 * @error {@link google.registry.flows.exceptions.ResourceToMutateDoesNotExistException}
 */
public class ContactTransferRejectFlow extends LoggedInFlow implements TransactionalFlow {

  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject ContactTransferRejectFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
  }

  @Override
  protected final EppOutput run() throws EppException {
    ContactResource existingResource = loadByUniqueId(ContactResource.class, targetId, now);
    if (existingResource == null) {
      throw new ResourceToMutateDoesNotExistException(ContactResource.class, targetId);
    }
    verifyOptionalAuthInfoForResource(authInfo, existingResource);
    if (existingResource.getTransferData().getTransferStatus() != TransferStatus.PENDING) {
      throw new NotPendingTransferException(targetId);
    }
    verifyResourceOwnership(clientId, existingResource);
    ContactResource newResource = existingResource.asBuilder()
        .clearPendingTransfer(TransferStatus.CLIENT_REJECTED, now)
        .build();
    HistoryEntry historyEntry = historyBuilder
        .setType(HistoryEntry.Type.CONTACT_TRANSFER_REJECT)
        .setModificationTime(now)
        .setParent(Key.create(existingResource))
        .build();
    PollMessage gainingPollMessage =
        createGainingTransferPollMessage(targetId, newResource.getTransferData(), historyEntry);
    ofy().save().<Object>entities(newResource, historyEntry, gainingPollMessage);
    // Delete the billing event and poll messages that were written in case the transfer would have
    // been implicitly server approved.
    ofy().delete().keys(existingResource.getTransferData().getServerApproveEntities());
    return createOutput(SUCCESS, createTransferResponse(targetId, newResource.getTransferData()));
  }
}
