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

import static google.registry.flows.ResourceFlowUtils.handlePendingTransferOnDelete;
import static google.registry.flows.ResourceFlowUtils.prepareDeletedResourceAsBuilder;
import static google.registry.flows.ResourceFlowUtils.updateForeignKeyIndexDeletionTime;
import static google.registry.flows.ResourceFlowUtils.verifyExistence;
import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfoForResource;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.verifyApplicationDomainMatchesTargetId;
import static google.registry.flows.domain.DomainFlowUtils.verifyLaunchPhase;
import static google.registry.flows.domain.DomainFlowUtils.verifyRegistryStateAllowsLaunchFlows;
import static google.registry.model.EppResourceUtils.loadDomainApplication;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Optional;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.flows.FlowModule.ApplicationId;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.launch.LaunchDeleteExtension;
import google.registry.model.domain.launch.LaunchPhase;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that deletes a domain application.
 *
 * @error {@link google.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link DomainApplicationDeleteFlow.SunriseApplicationCannotBeDeletedInLandrushException}
 * @error {@link DomainFlowUtils.ApplicationDomainNameMismatchException}
 * @error {@link DomainFlowUtils.BadCommandForRegistryPhaseException}
 * @error {@link DomainFlowUtils.LaunchPhaseMismatchException}
 * @error {@link DomainFlowUtils.NotAuthorizedForTldException}
 */
public final class DomainApplicationDeleteFlow extends LoggedInFlow implements TransactionalFlow {

  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject @ApplicationId String applicationId;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject DomainApplicationDeleteFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
    registerExtensions(LaunchDeleteExtension.class);
  }

  @Override
  public final EppOutput run() throws EppException {
    DomainApplication existingApplication = verifyExistence(
        DomainApplication.class, applicationId, loadDomainApplication(applicationId, now));
    verifyApplicationDomainMatchesTargetId(existingApplication, targetId);
    verifyOptionalAuthInfoForResource(authInfo, existingApplication);
    String tld = existingApplication.getTld();
    checkAllowedAccessToTld(getAllowedTlds(), tld);
    if (!isSuperuser) {
      verifyRegistryStateAllowsLaunchFlows(Registry.get(tld), now);
      verifyLaunchPhase(tld, eppInput.getSingleExtension(LaunchDeleteExtension.class), now);
      verifyResourceOwnership(clientId, existingApplication);
      // Don't allow deleting a sunrise application during landrush.
      if (existingApplication.getPhase().equals(LaunchPhase.SUNRISE)
          && Registry.get(tld).getTldState(now).equals(TldState.LANDRUSH)) {
        throw new SunriseApplicationCannotBeDeletedInLandrushException();
      }
    }
    DomainApplication newApplication =
        prepareDeletedResourceAsBuilder(existingApplication, now).build();
    HistoryEntry historyEntry = historyBuilder
        .setType(HistoryEntry.Type.DOMAIN_APPLICATION_DELETE)
        .setModificationTime(now)
        .setParent(Key.create(existingApplication))
        .build();
    updateForeignKeyIndexDeletionTime(newApplication);
    handlePendingTransferOnDelete(existingApplication, newApplication, now, historyEntry);
    ofy().save().<Object>entities(newApplication, historyEntry);
    return createOutput(SUCCESS);
  }

  /** A sunrise application cannot be deleted during landrush. */
  static class SunriseApplicationCannotBeDeletedInLandrushException
      extends StatusProhibitsOperationException {
    public SunriseApplicationCannotBeDeletedInLandrushException() {
      super("A sunrise application cannot be deleted during landrush");
    }
  }
}
