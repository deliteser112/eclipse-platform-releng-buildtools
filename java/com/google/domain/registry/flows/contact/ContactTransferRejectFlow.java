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

package com.google.domain.registry.flows.contact;

import com.google.domain.registry.flows.ResourceTransferRejectFlow;
import com.google.domain.registry.model.contact.ContactCommand.Transfer;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.contact.ContactResource.Builder;
import com.google.domain.registry.model.reporting.HistoryEntry;

/**
 * An EPP flow that rejects a pending transfer on a {@link ContactResource}.
 *
 * @error {@link com.google.domain.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link com.google.domain.registry.flows.ResourceMutatePendingTransferFlow.NotPendingTransferException}
 */
public class ContactTransferRejectFlow
    extends ResourceTransferRejectFlow<ContactResource, Builder, Transfer> {
  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.CONTACT_TRANSFER_REJECT;
  }
}
