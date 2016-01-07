// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

import static google.registry.flows.ResourceFlowUtils.verifyResourceDoesNotExist;
import static google.registry.flows.contact.ContactFlowUtils.validateAsciiPostalInfo;
import static google.registry.flows.contact.ContactFlowUtils.validateContactAgainstPolicy;
import static google.registry.model.EppResourceUtils.createContactHostRoid;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.model.contact.ContactCommand.Create;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.ContactResource.Builder;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.CreateData.ContactCreateData;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.ofy.ObjectifyService;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that creates a new contact.
 *
 * @error {@link google.registry.flows.exceptions.ResourceAlreadyExistsException}
 * @error {@link ContactFlowUtils.BadInternationalizedPostalInfoException}
 * @error {@link ContactFlowUtils.DeclineContactDisclosureFieldDisallowedPolicyException}
 */
public final class ContactCreateFlow extends LoggedInFlow implements TransactionalFlow {

  @Inject ResourceCommand resourceCommand;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject ContactCreateFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
  }

  @Override
  protected final EppOutput run() throws EppException {
    Create command = (Create) resourceCommand;
    verifyResourceDoesNotExist(ContactResource.class, targetId, now);
    Builder builder = new Builder();
    command.applyTo(builder);
    ContactResource newContact = builder
        .setCreationClientId(clientId)
        .setCurrentSponsorClientId(clientId)
        .setRepoId(createContactHostRoid(ObjectifyService.allocateId()))
        .build();
    validateAsciiPostalInfo(newContact.getInternationalizedPostalInfo());
    validateContactAgainstPolicy(newContact);
    historyBuilder
        .setType(HistoryEntry.Type.CONTACT_CREATE)
        .setModificationTime(now)
        .setXmlBytes(null)  // We don't want to store contact details in the history entry.
        .setParent(Key.create(newContact));
    ofy().save().entities(
        newContact,
        historyBuilder.build(),
        ForeignKeyIndex.create(newContact, newContact.getDeletionTime()),
        EppResourceIndex.create(Key.create(newContact)));
    return createOutput(SUCCESS, ContactCreateData.create(newContact.getContactId(), now));
  }
}
