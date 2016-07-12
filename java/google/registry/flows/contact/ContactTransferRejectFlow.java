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

package google.registry.flows.contact;

import google.registry.flows.ResourceTransferRejectFlow;
import google.registry.model.contact.ContactCommand.Transfer;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.ContactResource.Builder;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that rejects a pending transfer on a {@link ContactResource}.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link google.registry.flows.ResourceMutatePendingTransferFlow.NotPendingTransferException}
 */
public class ContactTransferRejectFlow
    extends ResourceTransferRejectFlow<ContactResource, Builder, Transfer> {

  @Inject ContactTransferRejectFlow() {}

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.CONTACT_TRANSFER_REJECT;
  }
}
