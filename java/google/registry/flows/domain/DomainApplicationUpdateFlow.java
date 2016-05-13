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

package com.google.domain.registry.flows.domain;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.domain.registry.flows.domain.DomainFlowUtils.DISALLOWED_TLD_STATES_FOR_LAUNCH_FLOWS;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.EppException.StatusProhibitsOperationException;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.model.domain.DomainApplication.Builder;
import com.google.domain.registry.model.domain.launch.ApplicationStatus;
import com.google.domain.registry.model.domain.launch.LaunchUpdateExtension;
import com.google.domain.registry.model.domain.secdns.SecDnsUpdateExtension;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.model.reporting.HistoryEntry;

/**
 * An EPP flow that updates a domain resource.
 *
 * @error {@link com.google.domain.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link com.google.domain.registry.flows.ResourceUpdateFlow.AddRemoveSameValueEppException}
 * @error {@link com.google.domain.registry.flows.ResourceUpdateFlow.ResourceHasClientUpdateProhibitedException}
 * @error {@link com.google.domain.registry.flows.ResourceUpdateFlow.StatusNotClientSettableException}
 * @error {@link com.google.domain.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException}
 * @error {@link BaseDomainUpdateFlow.EmptySecDnsUpdateException}
 * @error {@link BaseDomainUpdateFlow.MaxSigLifeChangeNotSupportedException}
 * @error {@link BaseDomainUpdateFlow.SecDnsAllUsageException}
 * @error {@link BaseDomainUpdateFlow.UrgentAttributeNotSupportedException}
 * @error {@link DomainFlowUtils.DuplicateContactForRoleException}
 * @error {@link DomainFlowUtils.LinkedResourceDoesNotExistException}
 * @error {@link DomainFlowUtils.MissingAdminContactException}
 * @error {@link DomainFlowUtils.MissingContactTypeException}
 * @error {@link DomainFlowUtils.MissingTechnicalContactException}
 * @error {@link DomainFlowUtils.NameserverNotAllowedException}
 * @error {@link DomainFlowUtils.RegistrantNotAllowedException}
 * @error {@link DomainFlowUtils.TooManyDsRecordsException}
 * @error {@link DomainFlowUtils.TooManyNameserversException}
 * @error {@link DomainApplicationUpdateFlow.ApplicationStatusProhibitsUpdateException}
 */
public class DomainApplicationUpdateFlow
    extends BaseDomainUpdateFlow<DomainApplication, Builder> {

  @Override
  protected void initDomainUpdateFlow() throws EppException {
    registerExtensions(LaunchUpdateExtension.class, SecDnsUpdateExtension.class);
  }

  @Override
  protected final void verifyDomainUpdateIsAllowed() throws EppException {
    switch (existingResource.getApplicationStatus()) {
      case PENDING_ALLOCATION:
      case PENDING_VALIDATION:
      case VALIDATED:
        return;
      default:
        throw new ApplicationStatusProhibitsUpdateException(
            existingResource.getApplicationStatus());
    }
  }

  @Override
  protected final ImmutableSet<TldState> getDisallowedTldStates() {
    return DISALLOWED_TLD_STATES_FOR_LAUNCH_FLOWS;
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE;
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
