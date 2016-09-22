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

import static google.registry.flows.ResourceFlowUtils.denyPendingTransfer;
import static google.registry.flows.ResourceFlowUtils.loadResourceToMutate;
import static google.registry.flows.ResourceFlowUtils.verifyHasPendingTransfer;
import static google.registry.flows.ResourceFlowUtils.verifyIsGainingRegistrar;
import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfoForResource;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.createLosingTransferPollMessage;
import static google.registry.flows.domain.DomainFlowUtils.createTransferResponse;
import static google.registry.flows.domain.DomainFlowUtils.updateAutorenewRecurrenceEndTime;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Optional;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferStatus;
import javax.inject.Inject;

/**
 * An EPP flow that cancels a pending transfer on a domain.
 *
 * <p>The "gaining" registrar requests a transfer from the "losing" (aka current) registrar. The
 * losing registrar has a "transfer" time period to respond (by default five days) after which the
 * transfer is automatically approved. Within that window, this flow allows the gaining client to
 * withdraw the transfer request.
 *
 * <p>When the transfer was requested, poll messages and billing events were saved to Datastore with
 * timestamps such that they only would become active when the transfer period passed. In this flow,
 * those speculative objects are deleted.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.exceptions.NotPendingTransferException}
 * @error {@link google.registry.flows.exceptions.NotTransferInitiatorException}
 * @error {@link google.registry.flows.exceptions.ResourceToMutateDoesNotExistException}
 * @error {@link DomainFlowUtils.NotAuthorizedForTldException}
 */
public final class DomainTransferCancelFlow extends LoggedInFlow implements TransactionalFlow {

  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject DomainTransferCancelFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
  }

  @Override
  public final EppOutput run() throws EppException {
    DomainResource existingDomain = loadResourceToMutate(DomainResource.class, targetId, now);
    verifyOptionalAuthInfoForResource(authInfo, existingDomain);
    verifyHasPendingTransfer(existingDomain);
    verifyIsGainingRegistrar(existingDomain, clientId);
    checkAllowedAccessToTld(getAllowedTlds(), existingDomain.getTld());
    HistoryEntry historyEntry = historyBuilder
        .setType(HistoryEntry.Type.DOMAIN_TRANSFER_CANCEL)
        .setModificationTime(now)
        .setParent(Key.create(existingDomain))
        .build();
    DomainResource newDomain =
        denyPendingTransfer(existingDomain, TransferStatus.CLIENT_CANCELLED, now);
    ofy().save().<ImmutableObject>entities(
        newDomain,
        historyEntry,
        createLosingTransferPollMessage(
            targetId, newDomain.getTransferData(), null, historyEntry));
    // Reopen the autorenew event and poll message that we closed for the implicit transfer. This
    // may recreate the autorenew poll message if it was deleted when the transfer request was made.
    updateAutorenewRecurrenceEndTime(existingDomain, END_OF_TIME);
    // Delete the billing event and poll messages that were written in case the transfer would have
    // been implicitly server approved.
    ofy().delete().keys(existingDomain.getTransferData().getServerApproveEntities());
    return createOutput(
        SUCCESS,
        createTransferResponse(targetId, newDomain.getTransferData(), null));
  }
}
