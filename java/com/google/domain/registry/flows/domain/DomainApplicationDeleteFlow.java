// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.flows.domain;

import static com.google.domain.registry.flows.domain.DomainFlowUtils.DISALLOWED_TLD_STATES_FOR_LAUNCH_FLOWS;
import static com.google.domain.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static com.google.domain.registry.flows.domain.DomainFlowUtils.verifyLaunchApplicationIdMatchesDomain;
import static com.google.domain.registry.flows.domain.DomainFlowUtils.verifyLaunchPhase;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.EppException.StatusProhibitsOperationException;
import com.google.domain.registry.flows.ResourceSyncDeleteFlow;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.model.domain.DomainApplication.Builder;
import com.google.domain.registry.model.domain.DomainCommand.Delete;
import com.google.domain.registry.model.domain.launch.LaunchDeleteExtension;
import com.google.domain.registry.model.domain.launch.LaunchPhase;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.model.reporting.HistoryEntry;

import java.util.Set;

/**
 * An EPP flow that deletes a domain application.
 *
 * @error {@link com.google.domain.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link com.google.domain.registry.flows.ResourceFlow.BadCommandForRegistryPhaseException}
 * @error {@link com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link DomainApplicationDeleteFlow.SunriseApplicationCannotBeDeletedInLandrushException}
 * @error {@link DomainFlowUtils.ApplicationDomainNameMismatchException}
 * @error {@link DomainFlowUtils.LaunchPhaseMismatchException}
 */
public class DomainApplicationDeleteFlow
    extends ResourceSyncDeleteFlow<DomainApplication, Builder, Delete> {

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
        && !superuser) {
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
