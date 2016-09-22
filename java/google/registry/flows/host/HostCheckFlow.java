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

import static google.registry.flows.ResourceFlowUtils.verifyTargetIdCount;
import static google.registry.model.EppResourceUtils.checkResourcesExist;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;

import com.google.common.collect.ImmutableList;
import google.registry.config.ConfigModule.Config;
import google.registry.flows.EppException;
import google.registry.flows.LoggedInFlow;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.CheckData.HostCheck;
import google.registry.model.eppoutput.CheckData.HostCheckData;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.host.HostCommand.Check;
import google.registry.model.host.HostResource;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * An EPP flow that checks whether a host can be provisioned.
 *
 * <p>This flows can check the existence of multiple hosts simultaneously.
 *
 * @error {@link google.registry.flows.exceptions.TooManyResourceChecksException}
 */
public final class HostCheckFlow extends LoggedInFlow {

  @Inject ResourceCommand resourceCommand;
  @Inject @Config("maxChecks") int maxChecks;
  @Inject HostCheckFlow() {}

  @Override
  protected final EppOutput run() throws EppException {
    List<String> targetIds = ((Check) resourceCommand).getTargetIds();
    verifyTargetIdCount(targetIds, maxChecks);
    Set<String> existingIds = checkResourcesExist(HostResource.class, targetIds, now);
    ImmutableList.Builder<HostCheck> checks = new ImmutableList.Builder<>();
    for (String id : targetIds) {
      boolean unused = !existingIds.contains(id);
      checks.add(HostCheck.create(unused, id, unused ? null : "In use"));
    }
    return createOutput(SUCCESS, HostCheckData.create(checks.build()));
  }
}
