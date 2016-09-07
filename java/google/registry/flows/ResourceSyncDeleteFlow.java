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

import static google.registry.flows.ResourceFlowUtils.handlePendingTransferOnDelete;
import static google.registry.flows.ResourceFlowUtils.prepareDeletedResourceAsBuilder;
import static google.registry.flows.ResourceFlowUtils.updateForeignKeyIndexDeletionTime;

import google.registry.model.EppResource;
import google.registry.model.EppResource.Builder;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.index.ForeignKeyIndex;

/**
 * An EPP flow that deletes a resource synchronously.
 *
 * @param <R> the resource type being changed
 * @param <B> a builder for the resource
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class ResourceSyncDeleteFlow
    <R extends EppResource, B extends Builder<R, ?>, C extends SingleResourceCommand>
    extends ResourceDeleteFlow<R, C> {

  @Override
  @SuppressWarnings("unchecked")
  protected final R createOrMutateResource() throws EppException {
    B builder = (B) prepareDeletedResourceAsBuilder(existingResource, now);
    setDeleteProperties(builder);
    return builder.build();
  }

  /** Update the relevant {@link ForeignKeyIndex} to cache the new deletion time. */
  @Override
  protected final void modifyRelatedResources() throws EppException {
    updateForeignKeyIndexDeletionTime(newResource);
    handlePendingTransferOnDelete(existingResource, newResource, now, historyEntry);
    modifySyncDeleteRelatedResources();
  }

  /** Set any resource-specific properties before deleting. */
  @SuppressWarnings("unused")
  protected void setDeleteProperties(B builder) throws EppException {}

  /** Modify any other resources that need to be informed of this delete. */
  protected void modifySyncDeleteRelatedResources() {}
}
