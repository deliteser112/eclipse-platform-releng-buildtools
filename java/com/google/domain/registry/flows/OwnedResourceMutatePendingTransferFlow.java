// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.flows;

import static com.google.domain.registry.flows.ResourceFlowUtils.verifyResourceOwnership;

import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.EppResource.Builder;
import com.google.domain.registry.model.eppinput.ResourceCommand.SingleResourceCommand;

/**
 * An EPP flow that acts on an owned resource with a pending transfer on it.
 *
 * @param <R> the resource type being manipulated
 * @param <B> a builder for the resource
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class OwnedResourceMutatePendingTransferFlow
    <R extends EppResource, B extends Builder<R, ?>, C extends SingleResourceCommand>
    extends ResourceMutatePendingTransferFlow<R, B, C> {

  /** Fail if this command isn't coming from the registrar that currently owns the resource. */
  @Override
  protected final void verifyPendingTransferMutationAllowed() throws EppException {
    verifyResourceOwnership(getClientId(), existingResource);
    verifyOwnedResourcePendingTransferMutationAllowed();
  }

  @SuppressWarnings("unused")
  protected void verifyOwnedResourcePendingTransferMutationAllowed() throws EppException {}
}
