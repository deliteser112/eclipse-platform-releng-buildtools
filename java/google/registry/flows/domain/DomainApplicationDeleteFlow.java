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

import static google.registry.flows.domain.DomainFlowUtils.DISALLOWED_TLD_STATES_FOR_LAUNCH_FLOWS;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.verifyLaunchApplicationIdMatchesDomain;
import static google.registry.flows.domain.DomainFlowUtils.verifyLaunchPhase;

import com.google.common.collect.ImmutableSet;
import google.registry.flows.EppException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.flows.ResourceSyncDeleteFlow;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainApplication.Builder;
import google.registry.model.domain.DomainCommand.Delete;
import google.registry.model.domain.launch.LaunchDeleteExtension;
import google.registry.model.domain.launch.LaunchPhase;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.reporting.HistoryEntry;
import java.util.Set;
import javax.inject.Inject;

/**
 * An EPP flow that deletes a domain application.
 *
 * @error {@link google.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link google.registry.flows.ResourceFlow.BadCommandForRegistryPhaseException}
 * @error {@link google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link DomainApplicationDeleteFlow.SunriseApplicationCannotBeDeletedInLandrushException}
 * @error {@link DomainFlowUtils.ApplicationDomainNameMismatchException}
 * @error {@link DomainFlowUtils.LaunchPhaseMismatchException}
 */
public class DomainApplicationDeleteFlow
    extends ResourceSyncDeleteFlow<DomainApplication, Builder, Delete> {

  @Inject DomainApplicationDeleteFlow() {}

  @Override
  protected void initResourceCreateOrMutateFlow() throws EppException {
    registerExtensions(LaunchDeleteExtension.class);
  }

  @Override
  protected void verifyMutationOnOwnedResourceAllowed() throws EppException {
    String tld = existingResource.getTld();
    checkRegistryStateForTld(tld);
    checkAllowedAccessToTld(getAllowedTlds(), tld);
    verifyLaunchPhase(tld, eppInput.getSingleExtension(LaunchDeleteExtension.class), now);
    verifyLaunchApplicationIdMatchesDomain(command, existingResource);
    // Don't allow deleting a sunrise application during landrush.
    if (existingResource.getPhase().equals(LaunchPhase.SUNRISE)
        && Registry.get(existingResource.getTld()).getTldState(now).equals(TldState.LANDRUSH)
        && !isSuperuser) {
      throw new SunriseApplicationCannotBeDeletedInLandrushException();
    }
  }

  @Override
  protected final ImmutableSet<TldState> getDisallowedTldStates() {
    return DISALLOWED_TLD_STATES_FOR_LAUNCH_FLOWS;
  }

  /** Domain applications do not respect status values that prohibit various operations. */
  @Override
  protected Set<StatusValue> getDisallowedStatuses() {
    return ImmutableSet.of();
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_APPLICATION_DELETE;
  }

  /** A sunrise application cannot be deleted during landrush. */
  static class SunriseApplicationCannotBeDeletedInLandrushException
      extends StatusProhibitsOperationException {
    public SunriseApplicationCannotBeDeletedInLandrushException() {
      super("A sunrise application cannot be deleted during landrush");
    }
  }
}
