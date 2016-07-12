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
import static google.registry.flows.domain.DomainFlowUtils.DISALLOWED_TLD_STATES_FOR_LAUNCH_FLOWS;

import com.google.common.collect.ImmutableSet;
import google.registry.flows.EppException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainApplication.Builder;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchUpdateExtension;
import google.registry.model.domain.secdns.SecDnsUpdateExtension;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that updates a domain resource.
 *
 * @error {@link google.registry.flows.EppException.UnimplementedExtensionException}
 * @error {@link google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link google.registry.flows.ResourceUpdateFlow.AddRemoveSameValueEppException}
 * @error {@link google.registry.flows.ResourceUpdateFlow.ResourceHasClientUpdateProhibitedException}
 * @error {@link google.registry.flows.ResourceUpdateFlow.StatusNotClientSettableException}
 * @error {@link google.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException}
 * @error {@link BaseDomainUpdateFlow.EmptySecDnsUpdateException}
 * @error {@link BaseDomainUpdateFlow.MaxSigLifeChangeNotSupportedException}
 * @error {@link BaseDomainUpdateFlow.SecDnsAllUsageException}
 * @error {@link BaseDomainUpdateFlow.UrgentAttributeNotSupportedException}
 * @error {@link DomainFlowUtils.DuplicateContactForRoleException}
 * @error {@link DomainFlowUtils.LinkedResourcesDoNotExistException}
 * @error {@link DomainFlowUtils.MissingAdminContactException}
 * @error {@link DomainFlowUtils.MissingContactTypeException}
 * @error {@link DomainFlowUtils.MissingTechnicalContactException}
 * @error {@link DomainFlowUtils.NameserversNotAllowedException}
 * @error {@link DomainFlowUtils.RegistrantNotAllowedException}
 * @error {@link DomainFlowUtils.TooManyDsRecordsException}
 * @error {@link DomainFlowUtils.TooManyNameserversException}
 * @error {@link DomainApplicationUpdateFlow.ApplicationStatusProhibitsUpdateException}
 */
public class DomainApplicationUpdateFlow
    extends BaseDomainUpdateFlow<DomainApplication, Builder> {

  @Inject DomainApplicationUpdateFlow() {}

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
