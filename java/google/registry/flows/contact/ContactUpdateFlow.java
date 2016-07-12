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

import google.registry.flows.EppException;
import google.registry.flows.ResourceUpdateFlow;
import google.registry.model.contact.ContactCommand.Update;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.ContactResource.Builder;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that updates a contact resource.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link google.registry.flows.ResourceUpdateFlow.ResourceHasClientUpdateProhibitedException}
 * @error {@link google.registry.flows.ResourceUpdateFlow.StatusNotClientSettableException}
 * @error {@link google.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException}
 * @error {@link ContactFlowUtils.BadInternationalizedPostalInfoException}
 * @error {@link ContactFlowUtils.DeclineContactDisclosureFieldDisallowedPolicyException}
 */
public class ContactUpdateFlow extends ResourceUpdateFlow<ContactResource, Builder, Update> {

  @Inject ContactUpdateFlow() {}

  @Override
  protected void verifyNewUpdatedStateIsAllowed() throws EppException {
    validateAsciiPostalInfo(newResource.getInternationalizedPostalInfo());
    validateContactAgainstPolicy(newResource);
  }

  @Override
  protected boolean storeXmlInHistoryEntry() {
    return false;
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.CONTACT_UPDATE;
  }
}
