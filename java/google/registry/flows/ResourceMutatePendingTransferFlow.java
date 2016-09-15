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
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.ofy.ObjectifyService.ofy;

import google.registry.flows.EppException.ObjectNotPendingTransferException;
import google.registry.model.EppResource;
import google.registry.model.EppResource.Builder;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferStatus;

/**
 * An EPP flow that acts on a resource with a pending transfer on it.
 *
 * @param <R> the resource type being changed
 * @param <B> a builder for the resource
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class ResourceMutatePendingTransferFlow
    <R extends EppResource, B extends Builder<R, ?>, C extends SingleResourceCommand>
    extends ResourceTransferFlow<R, C> {

  /** Fail if object doesn't have a pending transfer, or if authinfo doesn't match. */
  @Override
  protected final void verifyMutationAllowed() throws EppException {
    if (existingResource.getTransferData().getTransferStatus() != TransferStatus.PENDING) {
      throw new NotPendingTransferException(targetId);
    }
    verifyPendingTransferMutationAllowed();
  }

  @SuppressWarnings("unused")
  protected void verifyPendingTransferMutationAllowed() throws EppException {}

  @Override
  @SuppressWarnings("unchecked")
  protected final R createOrMutateResource() {
    TransferData transferData = existingResource.getTransferData();
    B builder = (B) existingResource.asBuilder()
        .removeStatusValue(StatusValue.PENDING_TRANSFER)
        .setTransferData(transferData.asBuilder()
            .setTransferStatus(getTransferStatus())
            .setPendingTransferExpirationTime(now)
            .setExtendedRegistrationYears(null)
            .setServerApproveEntities(null)
            .setServerApproveBillingEvent(null)
            .setServerApproveAutorenewEvent(null)
            .setServerApproveAutorenewPollMessage(null)
            .build());
    setTransferMutateProperties(builder);
    return builder.build();
  }

  /** Get the new transfer status to set on the resource (and subordinates) after the flow. */
  protected abstract TransferStatus getTransferStatus();

  /** Set any resource-specific properties for the pending-transfer mutation. */
  protected void setTransferMutateProperties(@SuppressWarnings("unused") B builder) {}

  /**
   * Delete the billing event and poll messages that were written in case the transfer would have
   * been implicitly server approved.
   */
  @Override
  protected final void modifyRelatedResources() throws EppException {
    modifyRelatedResourcesForMutateTransfer();
    ofy().delete().keys(existingResource.getTransferData().getServerApproveEntities());
  }

  /** Subclasses can override this to make any other model changes that are implied by this flow. */
  @SuppressWarnings("unused")
  protected void modifyRelatedResourcesForMutateTransfer() throws EppException {}

  @Override
  protected final EppOutput getOutput() throws EppException {
    return createOutput(
        SUCCESS, createTransferResponse(newResource, newResource.getTransferData(), now));
  }

  /** The resource does not have a pending transfer. */
  public static class NotPendingTransferException extends ObjectNotPendingTransferException {
    public NotPendingTransferException(String objectId) {
      super(objectId);
    }
  }
}
