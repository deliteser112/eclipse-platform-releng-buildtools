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

import static google.registry.flows.ResourceFlowUtils.createPendingTransferNotificationResponse;
import static google.registry.flows.ResourceFlowUtils.createTransferResponse;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.ImmutableList;
import google.registry.model.EppResource;
import google.registry.model.EppResource.Builder;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.poll.PollMessage;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferStatus;

/**
 * An EPP flow that rejects a transfer on a resource.
 *
 * @param <R> the resource type being manipulated
 * @param <B> a builder for the resource
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class ResourceTransferRejectFlow
    <R extends EppResource, B extends Builder<R, ?>, C extends SingleResourceCommand>
    extends OwnedResourceMutatePendingTransferFlow<R, B, C> {

  @Override
  protected final TransferStatus getTransferStatus() {
    return TransferStatus.CLIENT_REJECTED;
  }

  @Override
  protected void modifyRelatedResourcesForMutateTransfer() throws EppException {
    TransferData oldTransferData = existingResource.getTransferData();
    ofy().save().entity(new PollMessage.OneTime.Builder()
        .setClientId(oldTransferData.getGainingClientId())
        .setEventTime(now)
        .setMsg(TransferStatus.CLIENT_REJECTED.getMessage())
        .setResponseData(ImmutableList.of(
            createTransferResponse(newResource, newResource.getTransferData(), now),
            createPendingTransferNotificationResponse(
                existingResource, oldTransferData.getTransferRequestTrid(), false, now)))
        .setParent(historyEntry)
        .build());
    modifyRelatedResourcesForTransferReject();
  }

  /** Subclasses can override this to modify other rejection-related resources. */
  protected void modifyRelatedResourcesForTransferReject() {}
}
