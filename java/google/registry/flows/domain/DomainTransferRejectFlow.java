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

import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.updateAutorenewRecurrenceEndTime;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import google.registry.flows.EppException;
import google.registry.flows.ResourceTransferRejectFlow;
import google.registry.model.domain.DomainCommand.Transfer;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.DomainResource.Builder;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that rejects a pending transfer on a {@link DomainResource}.
 *
 * @error {@link google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link google.registry.flows.ResourceMutatePendingTransferFlow.NotPendingTransferException}
 */
public class DomainTransferRejectFlow
    extends ResourceTransferRejectFlow<DomainResource, Builder, Transfer> {

  @Inject DomainTransferRejectFlow() {}

  @Override
  protected void verifyOwnedResourcePendingTransferMutationAllowed() throws EppException {
    checkAllowedAccessToTld(getAllowedTlds(), existingResource.getTld());
  }

  /**
   * Reopen the autorenew event and poll message that we closed for the implicit transfer.
   * This may end up recreating the poll message if it was deleted upon the transfer request.
   */
  @Override
  protected final void modifyRelatedResourcesForTransferReject() {
    updateAutorenewRecurrenceEndTime(existingResource, END_OF_TIME);
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_TRANSFER_REJECT;
  }
}
