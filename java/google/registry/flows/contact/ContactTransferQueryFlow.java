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
import static google.registry.flows.contact.ContactFlowUtils.createTransferResponse;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;

import com.google.common.base.Optional;
import google.registry.flows.EppException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.exceptions.NoTransferHistoryToQueryException;
import google.registry.flows.exceptions.NotAuthorizedToViewTransferException;
import google.registry.flows.exceptions.ResourceToQueryDoesNotExistException;
import google.registry.model.contact.ContactResource;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppoutput.EppOutput;
import javax.inject.Inject;

/**
 * An EPP flow that queries a pending transfer on a {@link ContactResource}.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.exceptions.NoTransferHistoryToQueryException}
 * @error {@link google.registry.flows.exceptions.NotAuthorizedToViewTransferException}
 * @error {@link google.registry.flows.exceptions.ResourceToQueryDoesNotExistException}
 */
public class ContactTransferQueryFlow extends LoggedInFlow {

  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject ContactTransferQueryFlow() {}

  @Override
  public final EppOutput run() throws EppException {
    ContactResource existingResource = loadByUniqueId(ContactResource.class, targetId, now);
    if (existingResource == null) {
      throw new ResourceToQueryDoesNotExistException(ContactResource.class, targetId);
    }
    verifyOptionalAuthInfoForResource(authInfo, existingResource);
    // Most of the fields on the transfer response are required, so there's no way to return valid
    // XML if the object has never been transferred (and hence the fields aren't populated).
    if (existingResource.getTransferData().getTransferStatus() == null) {
      throw new NoTransferHistoryToQueryException();
    }
    // Note that the authorization info on the command (if present) has already been verified. If
    // it's present, then the other checks are unnecessary.
    if (!authInfo.isPresent()
        && !clientId.equals(existingResource.getTransferData().getGainingClientId())
        && !clientId.equals(existingResource.getTransferData().getLosingClientId())) {
      throw new NotAuthorizedToViewTransferException();
    }
    return createOutput(
        SUCCESS, createTransferResponse(targetId, existingResource.getTransferData()));
  }
}
