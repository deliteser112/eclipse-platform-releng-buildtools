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

package google.registry.flows.domain;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static google.registry.flows.ResourceFlowUtils.verifyExistence;
import static google.registry.flows.ResourceFlowUtils.verifyNoDisallowedStatuses;
import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfoForResource;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.cloneAndLinkReferences;
import static google.registry.flows.domain.DomainFlowUtils.updateDsData;
import static google.registry.flows.domain.DomainFlowUtils.validateContactsHaveTypes;
import static google.registry.flows.domain.DomainFlowUtils.validateDsData;
import static google.registry.flows.domain.DomainFlowUtils.validateNameserversAllowedOnTld;
import static google.registry.flows.domain.DomainFlowUtils.validateNameserversCountForTld;
import static google.registry.flows.domain.DomainFlowUtils.validateNoDuplicateContacts;
import static google.registry.flows.domain.DomainFlowUtils.validateRegistrantAllowedOnTld;
import static google.registry.flows.domain.DomainFlowUtils.validateRequiredContactsPresent;
import static google.registry.flows.domain.DomainFlowUtils.verifyApplicationDomainMatchesTargetId;
import static google.registry.flows.domain.DomainFlowUtils.verifyClientUpdateNotProhibited;
import static google.registry.flows.domain.DomainFlowUtils.verifyNotInPendingDelete;
import static google.registry.flows.domain.DomainFlowUtils.verifyStatusChangesAreClientSettable;
import static google.registry.model.EppResourceUtils.loadDomainApplication;
import static google.registry.model.domain.fee.Fee.FEE_UPDATE_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.flows.FlowModule.ApplicationId;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.exceptions.AddRemoveSameValueEppException;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainApplication.Builder;
import google.registry.model.domain.DomainCommand.Update;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchUpdateExtension;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.domain.secdns.SecDnsUpdateExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppinput.ResourceCommand.AddRemoveSameValueException;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that updates a domain application.
 *
 * <p>Updates can change contacts, nameservers and delegation signer data of an application. Updates
 * cannot change the domain name that is being applied for.
 *
 * @error {@link google.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.exceptions.AddRemoveSameValueEppException}
 * @error {@link google.registry.flows.exceptions.ResourceHasClientUpdateProhibitedException}
 * @error {@link google.registry.flows.exceptions.ResourceStatusProhibitsOperationException}
 * @error {@link google.registry.flows.exceptions.StatusNotClientSettableException}
 * @error {@link DomainFlowUtils.ApplicationDomainNameMismatchException}
 * @error {@link DomainFlowUtils.DuplicateContactForRoleException}
 * @error {@link DomainFlowUtils.EmptySecDnsUpdateException}
 * @error {@link DomainFlowUtils.LinkedResourcesDoNotExistException}
 * @error {@link DomainFlowUtils.MaxSigLifeChangeNotSupportedException}
 * @error {@link DomainFlowUtils.MissingAdminContactException}
 * @error {@link DomainFlowUtils.MissingContactTypeException}
 * @error {@link DomainFlowUtils.MissingTechnicalContactException}
 * @error {@link DomainFlowUtils.NameserversNotAllowedException}
 * @error {@link DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link DomainFlowUtils.RegistrantNotAllowedException}
 * @error {@link DomainFlowUtils.SecDnsAllUsageException}
 * @error {@link DomainFlowUtils.TooManyDsRecordsException}
 * @error {@link DomainFlowUtils.TooManyNameserversException}
 * @error {@link DomainFlowUtils.UrgentAttributeNotSupportedException}
 * @error {@link DomainApplicationUpdateFlow.ApplicationStatusProhibitsUpdateException}
 */
public class DomainApplicationUpdateFlow extends LoggedInFlow implements TransactionalFlow {

  /**
   * Note that CLIENT_UPDATE_PROHIBITED is intentionally not in this list. This is because it
   * requires special checking, since you must be able to clear the status off the object with an
   * update.
   */
  private static final ImmutableSet<StatusValue> UPDATE_DISALLOWED_STATUSES =
      Sets.immutableEnumSet(
          StatusValue.PENDING_DELETE,
          StatusValue.SERVER_UPDATE_PROHIBITED);

  private static final ImmutableSet<ApplicationStatus> UPDATE_DISALLOWED_APPLICATION_STATUSES =
      Sets.immutableEnumSet(
          ApplicationStatus.INVALID,
          ApplicationStatus.REJECTED,
          ApplicationStatus.ALLOCATED);

  @Inject ResourceCommand resourceCommand;
  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject @ApplicationId String applicationId;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject DomainApplicationUpdateFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(FEE_UPDATE_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER);
    registerExtensions(
        MetadataExtension.class, LaunchUpdateExtension.class, SecDnsUpdateExtension.class);
  }

  @Override
  public final EppOutput run() throws EppException {
    Update command = cloneAndLinkReferences((Update) resourceCommand, now);
    DomainApplication existingApplication = verifyExistence(
        DomainApplication.class, applicationId, loadDomainApplication(applicationId, now));
    verifyApplicationDomainMatchesTargetId(existingApplication, targetId);
    verifyNoDisallowedStatuses(existingApplication, UPDATE_DISALLOWED_STATUSES);
    verifyOptionalAuthInfoForResource(authInfo, existingApplication);
    verifyUpdateAllowed(existingApplication, command);
    HistoryEntry historyEntry = buildHistory(existingApplication);
    DomainApplication newApplication = updateApplication(existingApplication, command);
    validateNewApplication(newApplication);
    ofy().save().<ImmutableObject>entities(newApplication, historyEntry);
    return createOutput(SUCCESS);
  }

  protected final void verifyUpdateAllowed(
      DomainApplication existingApplication, Update command) throws EppException {
    if (!isSuperuser) {
      verifyResourceOwnership(clientId, existingApplication);
      verifyClientUpdateNotProhibited(command, existingApplication);
      verifyStatusChangesAreClientSettable(command);
    }
    String tld = existingApplication.getTld();
    checkAllowedAccessToTld(getAllowedTlds(), tld);
    if (UPDATE_DISALLOWED_APPLICATION_STATUSES
        .contains(existingApplication.getApplicationStatus())) {
      throw new ApplicationStatusProhibitsUpdateException(
          existingApplication.getApplicationStatus());
    }
    verifyNotInPendingDelete(
        command.getInnerAdd().getContacts(),
        command.getInnerChange().getRegistrant(),
        command.getInnerAdd().getNameservers());
    validateContactsHaveTypes(command.getInnerAdd().getContacts());
    validateContactsHaveTypes(command.getInnerRemove().getContacts());
    validateRegistrantAllowedOnTld(tld, command.getInnerChange().getRegistrantContactId());
    validateNameserversAllowedOnTld(
        tld, command.getInnerAdd().getNameserverFullyQualifiedHostNames());
  }

  private HistoryEntry buildHistory(DomainApplication existingApplication) {
    return historyBuilder
        .setType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE)
        .setModificationTime(now)
        .setParent(Key.create(existingApplication))
        .build();
  }

  private DomainApplication updateApplication(
      DomainApplication existingApplication, Update command) throws EppException {
    Builder builder = existingApplication.asBuilder();
    try {
      command.applyTo(builder);
    } catch (AddRemoveSameValueException e) {
      throw new AddRemoveSameValueEppException();
    }
    builder.setLastEppUpdateTime(now).setLastEppUpdateClientId(clientId);
    // Handle the secDNS extension.
    SecDnsUpdateExtension secDnsUpdate = eppInput.getSingleExtension(SecDnsUpdateExtension.class);
    if (secDnsUpdate != null) {
      builder.setDsData(updateDsData(existingApplication.getDsData(), secDnsUpdate));
    }
    return builder.build();
  }

  private void validateNewApplication(DomainApplication newApplication) throws EppException {
    validateNoDuplicateContacts(newApplication.getContacts());
    validateRequiredContactsPresent(newApplication.getRegistrant(), newApplication.getContacts());
    validateDsData(newApplication.getDsData());
    validateNameserversCountForTld(newApplication.getTld(), newApplication.getNameservers().size());
  }

  /** Application status prohibits this domain update. */
  static class ApplicationStatusProhibitsUpdateException extends StatusProhibitsOperationException {
    public ApplicationStatusProhibitsUpdateException(ApplicationStatus status) {
      super(String.format(
          "Applications in state %s can not be updated",
          UPPER_UNDERSCORE.to(LOWER_CAMEL, status.name())));
    }
  }
}
