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

package com.google.domain.registry.flows.contact;

import static com.google.domain.registry.model.EppResourceUtils.checkResourcesExist;

import com.google.common.collect.ImmutableList;
import com.google.domain.registry.flows.ResourceCheckFlow;
import com.google.domain.registry.model.contact.ContactCommand.Check;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.eppoutput.CheckData;
import com.google.domain.registry.model.eppoutput.CheckData.ContactCheck;
import com.google.domain.registry.model.eppoutput.CheckData.ContactCheckData;

import java.util.Set;

/**
 * An EPP flow that checks whether a contact can be provisioned.
 *
 * @error {@link com.google.domain.registry.flows.ResourceCheckFlow.TooManyResourceChecksException}
 */
public class ContactCheckFlow extends ResourceCheckFlow<ContactResource, Check> {
  @Override
  protected CheckData getCheckData() {
    Set<String> existingIds = checkResourcesExist(resourceClass, targetIds, now);
    ImmutableList.Builder<ContactCheck> checks = new ImmutableList.Builder<>();
    for (String id : targetIds) {
      boolean unused = !existingIds.contains(id);
      checks.add(ContactCheck.create(unused, id, unused ? null : "In use"));
    }
    return ContactCheckData.create(checks.build());
  }
}
