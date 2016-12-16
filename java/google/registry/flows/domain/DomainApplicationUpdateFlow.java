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

package google.registry.flows.domain;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.Sets.union;
import static google.registry.flows.FlowUtils.validateClientIsLoggedIn;
import static google.registry.flows.ResourceFlowUtils.checkSameValuesNotAddedAndRemoved;
import static google.registry.flows.ResourceFlowUtils.verifyAllStatusesAreClientSettable;
import static google.registry.flows.ResourceFlowUtils.verifyExistence;
import static google.registry.flows.ResourceFlowUtils.verifyNoDisallowedStatuses;
import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfo;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.cloneAndLinkReferences;
import static google.registry.flows.domain.DomainFlowUtils.updateDsData;
import static google.registry.flows.domain.DomainFlowUtils.validateContactsHaveTypes;
import static google.registry.flows.domain.DomainFlowUtils.validateDsData;
import static google.registry.flows.domain.DomainFlowUtils.validateFeeChallenge;
import static google.registry.flows.domain.DomainFlowUtils.validateNameserversAllowedOnTld;
import static google.registry.flows.domain.DomainFlowUtils.validateNameserversCountForTld;
import static google.registry.flows.domain.DomainFlowUtils.validateNoDuplicateContacts;
import static google.registry.flows.domain.DomainFlowUtils.validateRegistrantAllowedOnTld;
import static google.registry.flows.domain.DomainFlowUtils.validateRequiredContactsPresent;
import static google.registry.flows.domain.DomainFlowUtils.verifyApplicationDomainMatchesTargetId;
import static google.registry.flows.domain.DomainFlowUtils.verifyClientUpdateNotProhibited;
import static google.registry.flows.domain.DomainFlowUtils.verifyNotInPendingDelete;
import static google.registry.model.EppResourceUtils.loadDomainApplication;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.flows.ExtensionManager;
import google.registry.flows.FlowModule.ApplicationId;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.Superuser;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.domain.DomainFlowUtils.FeesRequiredForNonFreeUpdateException;
import google.registry.flows.domain.DomainPricingLogic.FeesAndCredits;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainCommand.Update;
import google.registry.model.domain.DomainCommand.Update.AddRemove;
import google.registry.model.domain.DomainCommand.Update.Change;
import google.registry.model.domain.fee.FeeUpdateCommandExtension;
import google.registry.model.domain.flags.FlagsUpdateCommandExtension;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchUpdateExtension;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.domain.secdns.SecDnsUpdateExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * An EPP flow that updates a domain application.
 *
 * <p>Updates can change contacts, nameservers and delegation signer data of an application. Updates
 * cannot change the domain name that is being applied for.
 *
 * @error {@link google.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link google.registry.flows.ResourceFlowUtils.AddRemoveSameValueException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceFlowUtils.StatusNotClientSettableException}
 * @error {@link google.registry.flows.exceptions.ResourceHasClientUpdateProhibitedException}
 * @error {@link google.registry.flows.exceptions.ResourceStatusProhibitsOperationException}
 * @error {@link DomainFlowUtils.ApplicationDomainNameMismatchException}
 * @error {@link DomainFlowUtils.DuplicateContactForRoleException}
 * @error {@link DomainFlowUtils.EmptySecDnsUpdateException}
 * @error {@link DomainFlowUtils.FeesMismatchException}
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
public class DomainApplicationUpdateFlow implements TransactionalFlow {

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
  @Inject ExtensionManager extensionManager;
  @Inject EppInput eppInput;
  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject @ApplicationId String applicationId;
  @Inject @Superuser boolean isSuperuser;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject EppResponse.Builder responseBuilder;
  @Inject DomainPricingLogic pricingLogic;
  @Inject DomainApplicationUpdateFlow() {}

  @Override
  public final EppResponse run() throws EppException {
    extensionManager.register(
        FeeUpdateCommandExtension.class,
        LaunchUpdateExtension.class,
        MetadataExtension.class,
        SecDnsUpdateExtension.class,
        FlagsUpdateCommandExtension.class);
    extensionManager.validate();
    validateClientIsLoggedIn(clientId);
    DateTime now = ofy().getTransactionTime();
    Update command = cloneAndLinkReferences((Update) resourceCommand, now);
    DomainApplication existingApplication = verifyExistence(
        DomainApplication.class, applicationId, loadDomainApplication(applicationId, now));
    verifyApplicationDomainMatchesTargetId(existingApplication, targetId);
    verifyNoDisallowedStatuses(existingApplication, UPDATE_DISALLOWED_STATUSES);
    verifyOptionalAuthInfo(authInfo, existingApplication);
    verifyUpdateAllowed(existingApplication, command, now);
    HistoryEntry historyEntry = buildHistory(existingApplication, now);
    DomainApplication newApplication = updateApplication(existingApplication, command, now);
    validateNewApplication(newApplication);
    ofy().save().<ImmutableObject>entities(newApplication, historyEntry);
    return responseBuilder.build();
  }

  protected final void verifyUpdateAllowed(
      DomainApplication existingApplication, Update command, DateTime now) throws EppException {
    AddRemove add = command.getInnerAdd();
    AddRemove remove = command.getInnerRemove();
    if (!isSuperuser) {
      verifyResourceOwnership(clientId, existingApplication);
      verifyClientUpdateNotProhibited(command, existingApplication);
      verifyAllStatusesAreClientSettable(union(add.getStatusValues(), remove.getStatusValues()));
    }
    String tld = existingApplication.getTld();
    checkAllowedAccessToTld(clientId, tld);
    if (UPDATE_DISALLOWED_APPLICATION_STATUSES
        .contains(existingApplication.getApplicationStatus())) {
      throw new ApplicationStatusProhibitsUpdateException(
          existingApplication.getApplicationStatus());
    }
    FeesAndCredits feesAndCredits =
        pricingLogic.getApplicationUpdatePrice(Registry.get(tld), existingApplication, now);
    FeeUpdateCommandExtension feeUpdate =
        eppInput.getSingleExtension(FeeUpdateCommandExtension.class);
    // If the fee extension is present, validate it (even if the cost is zero, to check for price
    // mismatches). Don't rely on the the validateFeeChallenge check for feeUpdate nullness, because
    // it throws an error if the name is premium, and we don't want to do that here.
    Money totalCost = feesAndCredits.getTotalCost();
    if (feeUpdate != null) {
      validateFeeChallenge(targetId, tld, now, feeUpdate, totalCost);
    } else if (!totalCost.isZero()) {
      // If it's not present but the cost is not zero, throw an exception.
      throw new FeesRequiredForNonFreeUpdateException();
    }
    verifyNotInPendingDelete(
        add.getContacts(),
        command.getInnerChange().getRegistrant(),
        add.getNameservers());
    validateContactsHaveTypes(add.getContacts());
    validateContactsHaveTypes(remove.getContacts());
    validateRegistrantAllowedOnTld(tld, command.getInnerChange().getRegistrantContactId());
    validateNameserversAllowedOnTld(
        tld, add.getNameserverFullyQualifiedHostNames());
  }

  private HistoryEntry buildHistory(DomainApplication existingApplication, DateTime now) {
    return historyBuilder
        .setType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE)
        .setModificationTime(now)
        .setParent(Key.create(existingApplication))
        .build();
  }

  private DomainApplication updateApplication(
      DomainApplication application, Update command, DateTime now) throws EppException {
    AddRemove add = command.getInnerAdd();
    AddRemove remove = command.getInnerRemove();
    checkSameValuesNotAddedAndRemoved(add.getNameservers(), remove.getNameservers());
    checkSameValuesNotAddedAndRemoved(add.getContacts(), remove.getContacts());
    checkSameValuesNotAddedAndRemoved(add.getStatusValues(), remove.getStatusValues());
    Change change = command.getInnerChange();
    SecDnsUpdateExtension secDnsUpdate = eppInput.getSingleExtension(SecDnsUpdateExtension.class);
    return application.asBuilder()
        // Handle the secDNS extension.
        .setDsData(secDnsUpdate != null
            ? updateDsData(application.getDsData(), secDnsUpdate)
            : application.getDsData())
        .setLastEppUpdateTime(now)
        .setLastEppUpdateClientId(clientId)
        .addStatusValues(add.getStatusValues())
        .removeStatusValues(remove.getStatusValues())
        .addNameservers(add.getNameservers())
        .removeNameservers(remove.getNameservers())
        .addContacts(add.getContacts())
        .removeContacts(remove.getContacts())
        .setRegistrant(firstNonNull(change.getRegistrant(), application.getRegistrant()))
        .setAuthInfo(firstNonNull(change.getAuthInfo(), application.getAuthInfo()))
        .build();
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
