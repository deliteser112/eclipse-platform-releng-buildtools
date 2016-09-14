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

package google.registry.flows;

import static google.registry.flows.ResourceFlowUtils.createTransferResponse;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.ImmutableList;
import google.registry.flows.EppException.AuthorizationErrorException;
import google.registry.model.EppResource;
import google.registry.model.EppResource.Builder;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.poll.PollMessage;
import google.registry.model.transfer.TransferStatus;

/**
 * An EPP flow that cancels a transfer on a resource.
 *
 * @param <R> the resource type being manipulated
 * @param <B> a builder for the resource
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class ResourceTransferCancelFlow
    <R extends EppResource, B extends Builder<R, ?>, C extends SingleResourceCommand>
    extends ResourceMutatePendingTransferFlow<R, B, C> {

  /** Verify that this is the correct client to cancel this pending transfer. */
  @Override
  protected final void verifyPendingTransferMutationAllowed() throws EppException {
    if (!getClientId().equals(existingResource.getTransferData().getGainingClientId())) {
      throw new NotTransferInitiatorException();
    }
    verifyTransferCancelMutationAllowed();
  }

  @SuppressWarnings("unused")
  protected void verifyTransferCancelMutationAllowed() throws EppException {}

  @Override
  protected void modifyRelatedResourcesForMutateTransfer() throws EppException {
    ofy().save().entity(new PollMessage.OneTime.Builder()
        .setClientId(existingResource.getTransferData().getLosingClientId())
        .setEventTime(now)
        .setMsg(TransferStatus.CLIENT_CANCELLED.getMessage())
        .setResponseData(ImmutableList.of(
            createTransferResponse(newResource, newResource.getTransferData(), now)))
        .setParent(historyEntry)
        .build());
    modifyRelatedResourcesForTransferCancel();
  }

  /** Subclasses can override this to modify other cancellation-related resources. */
  protected void modifyRelatedResourcesForTransferCancel() {}

  @Override
  protected final TransferStatus getTransferStatus() {
    return TransferStatus.CLIENT_CANCELLED;
  }

  /** Registrar is not the initiator of this transfer. */
  public static class NotTransferInitiatorException extends AuthorizationErrorException {
    public NotTransferInitiatorException() {
      super("Registrar is not the initiator of this transfer");
    }
  }
}
