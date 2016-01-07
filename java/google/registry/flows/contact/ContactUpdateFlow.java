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

import static google.registry.flows.ResourceFlowUtils.loadAndVerifyExistence;
import static google.registry.flows.ResourceFlowUtils.verifyNoDisallowedStatuses;
import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfoForResource;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.flows.contact.ContactFlowUtils.validateAsciiPostalInfo;
import static google.registry.flows.contact.ContactFlowUtils.validateContactAgainstPolicy;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.exceptions.AddRemoveSameValueEppException;
import google.registry.flows.exceptions.ResourceHasClientUpdateProhibitedException;
import google.registry.flows.exceptions.StatusNotClientSettableException;
import google.registry.model.contact.ContactCommand.Update;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.ContactResource.Builder;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppinput.ResourceCommand.AddRemoveSameValueException;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that updates a contact.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.exceptions.AddRemoveSameValueEppException}
 * @error {@link google.registry.flows.exceptions.ResourceHasClientUpdateProhibitedException}
 * @error {@link google.registry.flows.exceptions.ResourceStatusProhibitsOperationException}
 * @error {@link google.registry.flows.exceptions.StatusNotClientSettableException}
 * @error {@link ContactFlowUtils.BadInternationalizedPostalInfoException}
 * @error {@link ContactFlowUtils.DeclineContactDisclosureFieldDisallowedPolicyException}
 */
public final class ContactUpdateFlow extends LoggedInFlow implements TransactionalFlow {

  /**
   * Note that CLIENT_UPDATE_PROHIBITED is intentionally not in this list. This is because it
   * requires special checking, since you must be able to clear the status off the object with an
   * update.
   */
  private static final ImmutableSet<StatusValue> DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_UPDATE_PROHIBITED);

  @Inject ResourceCommand resourceCommand;
  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject ContactUpdateFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
  }

  @Override
  public final EppOutput run() throws EppException {
    Update command = (Update) resourceCommand;
    ContactResource existingContact = loadAndVerifyExistence(ContactResource.class, targetId, now);
    verifyOptionalAuthInfoForResource(authInfo, existingContact);
    if (!isSuperuser) {
      verifyResourceOwnership(clientId, existingContact);
    }
    for (StatusValue statusValue : Sets.union(
        command.getInnerAdd().getStatusValues(),
        command.getInnerRemove().getStatusValues())) {
      if (!isSuperuser && !statusValue.isClientSettable()) {  // The superuser can set any status.
        throw new StatusNotClientSettableException(statusValue.getXmlName());
      }
    }
    verifyNoDisallowedStatuses(existingContact, DISALLOWED_STATUSES);
    historyBuilder
        .setType(HistoryEntry.Type.CONTACT_UPDATE)
        .setModificationTime(now)
        .setXmlBytes(null)  // We don't want to store contact details in the history entry.
        .setParent(Key.create(existingContact));
    Builder builder = existingContact.asBuilder();
    try {
      command.applyTo(builder);
    } catch (AddRemoveSameValueException e) {
      throw new AddRemoveSameValueEppException();
    }
    ContactResource newContact = builder
        .setLastEppUpdateTime(now)
        .setLastEppUpdateClientId(clientId)
        .build();
    // If the resource is marked with clientUpdateProhibited, and this update did not clear that
    // status, then the update must be disallowed (unless a superuser is requesting the change).
    if (!isSuperuser
        && existingContact.getStatusValues().contains(StatusValue.CLIENT_UPDATE_PROHIBITED)
        && newContact.getStatusValues().contains(StatusValue.CLIENT_UPDATE_PROHIBITED)) {
      throw new ResourceHasClientUpdateProhibitedException();
    }
    validateAsciiPostalInfo(newContact.getInternationalizedPostalInfo());
    validateContactAgainstPolicy(newContact);
    ofy().save().<Object>entities(newContact, historyBuilder.build());
    return createOutput(SUCCESS);
  }
}
