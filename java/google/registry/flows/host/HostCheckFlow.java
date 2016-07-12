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

import static google.registry.model.EppResourceUtils.checkResourcesExist;

import com.google.common.collect.ImmutableList;
import google.registry.flows.ResourceCheckFlow;
import google.registry.model.eppoutput.CheckData;
import google.registry.model.eppoutput.CheckData.HostCheck;
import google.registry.model.eppoutput.CheckData.HostCheckData;
import google.registry.model.host.HostCommand.Check;
import google.registry.model.host.HostResource;
import java.util.Set;
import javax.inject.Inject;

/**
 * An EPP flow that checks whether a host can be provisioned.
 *
 * @error {@link google.registry.flows.ResourceCheckFlow.TooManyResourceChecksException}
 */
public class HostCheckFlow extends ResourceCheckFlow<HostResource, Check> {

  @Inject HostCheckFlow() {}

  @Override
  protected CheckData getCheckData() {
    Set<String> existingIds = checkResourcesExist(resourceClass, targetIds, now);
    ImmutableList.Builder<HostCheck> checks = new ImmutableList.Builder<>();
    for (String id : targetIds) {
      boolean unused = !existingIds.contains(id);
      checks.add(HostCheck.create(unused, id, unused ? null : "In use"));
    }
    return HostCheckData.create(checks.build());
  }
}
