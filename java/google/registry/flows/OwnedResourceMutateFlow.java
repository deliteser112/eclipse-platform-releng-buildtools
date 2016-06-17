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

import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;

import google.registry.model.EppResource;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;

/**
 * An EPP flow that mutates a single stored resource that is owned by the current registrar.
 *
 * @param <R> the resource type being changed
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class OwnedResourceMutateFlow
    <R extends EppResource, C extends SingleResourceCommand>
    extends ResourceMutateFlow<R, C> {
  /** Fail if the object doesn't exist or was deleted. */
  @Override
  protected final void verifyMutationAllowed() throws EppException {
    if (!isSuperuser) {
      verifyResourceOwnership(getClientId(), existingResource);
    }
    verifyMutationOnOwnedResourceAllowed();
  }

  /** Check invariants before allowing the command to proceed. */
  @SuppressWarnings("unused")
  protected void verifyMutationOnOwnedResourceAllowed() throws EppException {}
}
