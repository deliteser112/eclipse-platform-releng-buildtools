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

import static google.registry.flows.contact.ContactFlowUtils.validateAsciiPostalInfo;
import static google.registry.flows.contact.ContactFlowUtils.validateContactAgainstPolicy;
import static google.registry.model.EppResourceUtils.createContactHostRoid;
import static google.registry.model.eppoutput.Result.Code.Success;

import google.registry.flows.EppException;
import google.registry.flows.ResourceCreateFlow;
import google.registry.model.contact.ContactCommand.Create;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.ContactResource.Builder;
import google.registry.model.eppoutput.CreateData.ContactCreateData;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.ofy.ObjectifyService;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that creates a new contact resource.
 *
 * @error {@link google.registry.flows.ResourceCreateFlow.ResourceAlreadyExistsException}
 * @error {@link ContactFlowUtils.BadInternationalizedPostalInfoException}
 * @error {@link ContactFlowUtils.DeclineContactDisclosureFieldDisallowedPolicyException}
 */
public class ContactCreateFlow extends ResourceCreateFlow<ContactResource, Builder, Create> {

  @Inject ContactCreateFlow() {}

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
