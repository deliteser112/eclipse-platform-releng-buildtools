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

package google.registry.flows.contact;

import static google.registry.flows.ResourceFlowUtils.loadAndVerifyExistence;
import static google.registry.flows.ResourceFlowUtils.verifyNoDisallowedStatuses;
import static google.registry.flows.ResourceFlowUtils.verifyRequiredAuthInfoForResourceTransfer;
import static google.registry.flows.contact.ContactFlowUtils.createGainingTransferPollMessage;
import static google.registry.flows.contact.ContactFlowUtils.createLosingTransferPollMessage;
import static google.registry.flows.contact.ContactFlowUtils.createTransferResponse;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_WITH_ACTION_PENDING;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.config.ConfigModule.Config;
import google.registry.flows.EppException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.exceptions.AlreadyPendingTransferException;
import google.registry.flows.exceptions.ObjectAlreadySponsoredException;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.poll.PollMessage;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferStatus;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * An EPP flow that requests a transfer on a contact.
 *
 * <p>The "gaining" registrar requests a transfer from the "losing" (aka current) registrar. The
 * losing registrar has a "transfer" time period to respond (by default five days) after which the
 * transfer is automatically approved. Within that window, the transfer might be approved explicitly
 * by the losing registrar or rejected, and the gaining registrar can also cancel the transfer
 * request.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException}
 * @error {@link google.registry.flows.exceptions.AlreadyPendingTransferException}
 * @error {@link google.registry.flows.exceptions.MissingTransferRequestAuthInfoException}
 * @error {@link google.registry.flows.exceptions.ObjectAlreadySponsoredException}
 */
public final class ContactTransferRequestFlow extends LoggedInFlow implements TransactionalFlow {

  private static final ImmutableSet<StatusValue> DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.CLIENT_TRANSFER_PROHIBITED,
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_TRANSFER_PROHIBITED);

  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String gainingClientId;
  @Inject @TargetId String targetId;
  @Inject @Config("contactAutomaticTransferLength") Duration automaticTransferLength;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject ContactTransferRequestFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
  }

  @Override
  protected final EppOutput run() throws EppException {
    ContactResource existingContact = loadAndVerifyExistence(ContactResource.class, targetId, now);
    verifyRequiredAuthInfoForResourceTransfer(authInfo, existingContact);
    // Verify that the resource does not already have a pending transfer.
    if (TransferStatus.PENDING.equals(existingContact.getTransferData().getTransferStatus())) {
      throw new AlreadyPendingTransferException(targetId);
    }
    String losingClientId = existingContact.getCurrentSponsorClientId();
    // Verify that this client doesn't already sponsor this resource.
    if (gainingClientId.equals(losingClientId)) {
      throw new ObjectAlreadySponsoredException();
    }
    verifyNoDisallowedStatuses(existingContact, DISALLOWED_STATUSES);
    HistoryEntry historyEntry = historyBuilder
        .setType(HistoryEntry.Type.CONTACT_TRANSFER_REQUEST)
        .setModificationTime(now)
        .setParent(Key.create(existingContact))
        .build();
    DateTime transferExpirationTime = now.plus(automaticTransferLength);
    TransferData serverApproveTransferData = new TransferData.Builder()
        .setTransferRequestTime(now)
        .setTransferRequestTrid(trid)
        .setGainingClientId(gainingClientId)
        .setLosingClientId(losingClientId)
        .setPendingTransferExpirationTime(transferExpirationTime)
        .setTransferStatus(TransferStatus.SERVER_APPROVED)
        .build();
    // If the transfer is server approved, this message will be sent to the losing registrar. */
    PollMessage serverApproveLosingPollMessage =
        createLosingTransferPollMessage(targetId, serverApproveTransferData, historyEntry);
    // If the transfer is server approved, this message will be sent to the gaining registrar. */
    PollMessage serverApproveGainingPollMessage =
        createGainingTransferPollMessage(targetId, serverApproveTransferData, historyEntry);
    TransferData pendingTransferData = serverApproveTransferData.asBuilder()
        .setTransferStatus(TransferStatus.PENDING)
        .setServerApproveEntities(
            ImmutableSet.<Key<? extends TransferData.TransferServerApproveEntity>>of(
                Key.create(serverApproveGainingPollMessage),
                Key.create(serverApproveLosingPollMessage)))
        .build();
    // When a transfer is requested, a poll message is created to notify the losing registrar.
    PollMessage requestPollMessage =
        createLosingTransferPollMessage(targetId, pendingTransferData, historyEntry).asBuilder()
            .setEventTime(now)  // Unlike the serverApprove messages, this applies immediately.
            .build();
    ContactResource newContact = existingContact.asBuilder()
        .setTransferData(pendingTransferData)
        .addStatusValue(StatusValue.PENDING_TRANSFER)
        .build();
    ofy().save().<Object>entities(
        newContact,
        historyEntry,
        requestPollMessage,
        serverApproveGainingPollMessage,
        serverApproveLosingPollMessage);
    return createOutput(
        SUCCESS_WITH_ACTION_PENDING,
        createTransferResponse(targetId, newContact.getTransferData()));
  }
}

