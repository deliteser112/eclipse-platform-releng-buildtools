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

import static com.google.domain.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static com.google.domain.registry.flows.domain.DomainFlowUtils.updateAutorenewRecurrenceEndTime;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.ResourceTransferCancelFlow;
import com.google.domain.registry.model.domain.DomainCommand.Transfer;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.DomainResource.Builder;
import com.google.domain.registry.model.reporting.HistoryEntry;

/**
 * An EPP flow that cancels a pending transfer on a {@link DomainResource}.
 *
 * @error {@link com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link com.google.domain.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link com.google.domain.registry.flows.ResourceMutatePendingTransferFlow.NotPendingTransferException}
 * @error {@link com.google.domain.registry.flows.ResourceTransferCancelFlow.NotTransferInitiatorException}
 */
public class DomainTransferCancelFlow
    extends ResourceTransferCancelFlow<DomainResource, Builder, Transfer> {

  /**
   * Reopen the autorenew event and poll message that we closed for the implicit transfer.
   * This may end up recreating the autorenew poll message if it was deleted when the transfer
   * request was made.
   */
  @Override
  protected final void modifyRelatedResourcesForTransferCancel() {
    updateAutorenewRecurrenceEndTime(existingResource, END_OF_TIME);
  }

  @Override
  protected void verifyTransferCancelMutationAllowed() throws EppException {
    checkAllowedAccessToTld(getAllowedTlds(), existingResource.getTld());
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.DOMAIN_TRANSFER_CANCEL;
  }
}
