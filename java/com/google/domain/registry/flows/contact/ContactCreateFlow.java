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

import static com.google.domain.registry.flows.contact.ContactFlowUtils.validateAsciiPostalInfo;
import static com.google.domain.registry.flows.contact.ContactFlowUtils.validateContactAgainstPolicy;
import static com.google.domain.registry.model.EppResourceUtils.createContactHostRoid;
import static com.google.domain.registry.model.eppoutput.Result.Code.Success;

import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.ResourceCreateFlow;
import com.google.domain.registry.model.contact.ContactCommand.Create;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.contact.ContactResource.Builder;
import com.google.domain.registry.model.eppoutput.CreateData.ContactCreateData;
import com.google.domain.registry.model.eppoutput.EppOutput;
import com.google.domain.registry.model.ofy.ObjectifyService;
import com.google.domain.registry.model.reporting.HistoryEntry;

/**
 * An EPP flow that creates a new contact resource.
 *
 * @error {@link com.google.domain.registry.flows.ResourceCreateFlow.ResourceAlreadyExistsException}
 * @error {@link ContactFlowUtils.BadInternationalizedPostalInfoException}
 * @error {@link ContactFlowUtils.DeclineContactDisclosureFieldDisallowedPolicyException}
 */
public class ContactCreateFlow extends ResourceCreateFlow<ContactResource, Builder, Create> {
  @Override
  protected EppOutput getOutput() {
    return createOutput(Success, ContactCreateData.create(newResource.getContactId(), now));
  }

  @Override
  protected String createFlowRepoId() {
    return createContactHostRoid(ObjectifyService.allocateId());
  }

  @Override
  protected void verifyNewStateIsAllowed() throws EppException {
    validateAsciiPostalInfo(newResource.getInternationalizedPostalInfo());
    validateContactAgainstPolicy(newResource);
  }

  @Override
  protected boolean storeXmlInHistoryEntry() {
    return false;
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.CONTACT_CREATE;
  }
}
