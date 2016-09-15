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

import google.registry.flows.EppException.AuthorizationErrorException;
import google.registry.flows.EppException.CommandUseErrorException;
import google.registry.model.EppResource;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.eppoutput.EppOutput;

/**
 * An EPP flow that queries the state of a pending transfer on a resource.
 *
 * @param <R> the resource type being manipulated
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class ResourceTransferQueryFlow<R extends EppResource,
       C extends SingleResourceCommand> extends ResourceQueryFlow<R, C> {

  @Override
  protected final void verifyQueryIsAllowed() throws EppException {
    // Most of the fields on the transfer response are required, so there's no way to return valid
    // XML if the object has never been transferred (and hence the fields aren't populated).
    if (existingResource.getTransferData().getTransferStatus() == null) {
      throw new NoTransferHistoryToQueryException();
    }

    // Note that the authorization info on the command (if present) has already been verified by the
    // parent class. If it's present, then the other checks are unnecessary.
    if (command.getAuthInfo() == null &&
        !getClientId().equals(existingResource.getTransferData().getGainingClientId()) &&
        !getClientId().equals(existingResource.getTransferData().getLosingClientId())) {
      throw new NotAuthorizedToViewTransferException();
    }
  }

  @Override
  public final EppOutput runResourceFlow() throws EppException {
    return createOutput(
        SUCCESS, createTransferResponse(existingResource, existingResource.getTransferData(), now));
  }

  /** Registrar is not authorized to view transfer status. */
  public static class NotAuthorizedToViewTransferException
      extends AuthorizationErrorException {
    public NotAuthorizedToViewTransferException() {
      super("Registrar is not authorized to view transfer status");
    }
  }

  /** Object has no transfer history. */
  public static class NoTransferHistoryToQueryException extends CommandUseErrorException {
    public NoTransferHistoryToQueryException() {
      super("Object has no transfer history");
    }
  }
}
