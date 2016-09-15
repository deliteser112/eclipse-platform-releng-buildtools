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

package google.registry.flows.host;

import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfoForResource;
import static google.registry.model.EppResourceUtils.cloneResourceWithLinkedStatus;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;

import com.google.common.base.Optional;
import google.registry.flows.EppException;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.exceptions.ResourceToQueryDoesNotExistException;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.host.HostCommand.Info;
import google.registry.model.host.HostResource;
import javax.inject.Inject;

/**
 * An EPP flow that reads a host.
 *
 * @error {@link google.registry.flows.exceptions.ResourceToQueryDoesNotExistException}
 */
public class HostInfoFlow extends LoggedInFlow {

  @Inject ResourceCommand resourceCommand;
  @Inject Optional<AuthInfo> authInfo;
  @Inject HostInfoFlow() {}

  @Override
  public EppOutput run() throws EppException {
    Info command = (Info) resourceCommand;
    String targetId = command.getTargetId();
    HostResource existingResource = loadByUniqueId(HostResource.class, targetId, now);
    if (existingResource == null) {
      throw new ResourceToQueryDoesNotExistException(HostResource.class, targetId);
    }
    verifyOptionalAuthInfoForResource(authInfo, existingResource);
    return createOutput(SUCCESS, cloneResourceWithLinkedStatus(existingResource, now));
  }
}
