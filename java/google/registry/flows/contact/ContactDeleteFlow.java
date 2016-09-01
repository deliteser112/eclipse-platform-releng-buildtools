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

import static google.registry.model.EppResourceUtils.queryDomainsUsingResource;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.googlecode.objectify.Key;
import google.registry.config.RegistryEnvironment;
import google.registry.flows.EppException;
import google.registry.flows.ResourceAsyncDeleteFlow;
import google.registry.flows.async.AsyncFlowUtils;
import google.registry.flows.async.DeleteContactResourceAction;
import google.registry.flows.async.DeleteEppResourceAction;
import google.registry.model.contact.ContactCommand.Delete;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.ContactResource.Builder;
import google.registry.model.domain.DomainBase;
import google.registry.model.reporting.HistoryEntry;
import javax.inject.Inject;

/**
 * An EPP flow that deletes a contact resource.
 *
 * @error {@link google.registry.flows.ResourceAsyncDeleteFlow.ResourceToDeleteIsReferencedException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link google.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException}
 */
public class ContactDeleteFlow extends ResourceAsyncDeleteFlow<ContactResource, Builder, Delete> {

  /** In {@link #isLinkedForFailfast}, check this (arbitrary) number of resources from the query. */
  private static final int FAILFAST_CHECK_COUNT = 5;

  @Inject ContactDeleteFlow() {}

  @Override
  protected boolean isLinkedForFailfast(final Key<ContactResource> key) {
    // Query for the first few linked domains, and if found, actually load them. The query is
    // eventually consistent and so might be very stale, but the direct load will not be stale,
    // just non-transactional. If we find at least one actual reference then we can reliably
    // fail. If we don't find any, we can't trust the query and need to do the full mapreduce.
    return Iterables.any(
        ofy().load().keys(
            queryDomainsUsingResource(
                  ContactResource.class, key, now, FAILFAST_CHECK_COUNT)).values(),
        new Predicate<DomainBase>() {
            @Override
            public boolean apply(DomainBase domain) {
              return domain.getReferencedContacts().contains(key);
            }});
  }

  /** Enqueues a contact resource deletion on the mapreduce queue. */
  @Override
  protected final void enqueueTasks() throws EppException {
    AsyncFlowUtils.enqueueMapreduceAction(
        DeleteContactResourceAction.class,
        ImmutableMap.of(
            DeleteEppResourceAction.PARAM_RESOURCE_KEY,
            Key.create(existingResource).getString(),
            DeleteEppResourceAction.PARAM_REQUESTING_CLIENT_ID,
            getClientId(),
            DeleteEppResourceAction.PARAM_IS_SUPERUSER,
            Boolean.toString(isSuperuser)),
        RegistryEnvironment.get().config().getAsyncDeleteFlowMapreduceDelay());
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.CONTACT_PENDING_DELETE;
  }
}
