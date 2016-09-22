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

import static google.registry.model.EppResourceUtils.checkResourcesExist;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;

import com.google.common.collect.ImmutableList;
import google.registry.config.ConfigModule.Config;
import google.registry.flows.EppException;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.exceptions.TooManyResourceChecksException;
import google.registry.model.contact.ContactCommand.Check;
import google.registry.model.contact.ContactResource;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.CheckData.ContactCheck;
import google.registry.model.eppoutput.CheckData.ContactCheckData;
import google.registry.model.eppoutput.EppOutput;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * An EPP flow that checks whether a contact can be provisioned.
 *
 * <p>This flows can check the existence of multiple contacts simultaneously.
 *
 * @error {@link google.registry.flows.exceptions.TooManyResourceChecksException}
 */
public final class ContactCheckFlow extends LoggedInFlow {

  @Inject ResourceCommand resourceCommand;
  @Inject @Config("maxChecks") int maxChecks;
  @Inject ContactCheckFlow() {}

  @Override
  public final EppOutput run() throws EppException {
    List<String> targetIds = ((Check) resourceCommand).getTargetIds();
    if (targetIds.size() > maxChecks) {
      throw new TooManyResourceChecksException(maxChecks);
    }
    Set<String> existingIds = checkResourcesExist(ContactResource.class, targetIds, now);
    ImmutableList.Builder<ContactCheck> checks = new ImmutableList.Builder<>();
    for (String id : targetIds) {
      boolean unused = !existingIds.contains(id);
      checks.add(ContactCheck.create(unused, id, unused ? null : "In use"));
    }
    return createOutput(SUCCESS, ContactCheckData.create(checks.build()));
  }
}
