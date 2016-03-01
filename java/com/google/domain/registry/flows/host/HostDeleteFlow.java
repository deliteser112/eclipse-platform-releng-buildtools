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

package com.google.domain.registry.flows.host;

import static com.google.domain.registry.model.EppResourceUtils.queryDomainsUsingResource;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.ResourceAsyncDeleteFlow;
import com.google.domain.registry.flows.async.AsyncFlowUtils;
import com.google.domain.registry.flows.async.DeleteEppResourceAction;
import com.google.domain.registry.flows.async.DeleteHostResourceAction;
import com.google.domain.registry.model.domain.DomainBase;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.host.HostCommand.Delete;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.host.HostResource.Builder;
import com.google.domain.registry.model.reporting.HistoryEntry;

import com.googlecode.objectify.Key;

/**
 * An EPP flow that deletes a host resource.
 *
 * @error {@link com.google.domain.registry.flows.ResourceAsyncDeleteFlow.ResourceToDeleteIsReferencedException}
 * @error {@link com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link com.google.domain.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException}
 */
public class HostDeleteFlow extends ResourceAsyncDeleteFlow<HostResource, Builder, Delete> {

  /** In {@link #isLinkedForFailfast}, check this (arbitrary) number of resources from the query. */
  private static final int FAILFAST_CHECK_COUNT = 5;

  @Override
  protected boolean isLinkedForFailfast(final ReferenceUnion<HostResource> ref) {
    // Query for the first few linked domains, and if found, actually load them. The query is
    // eventually consistent and so might be very stale, but the direct load will not be stale,
    // just non-transactional. If we find at least one actual reference then we can reliably
    // fail. If we don't find any, we can't trust the query and need to do the full mapreduce.
    return Iterables.any(
        ofy().load().keys(
            queryDomainsUsingResource(
                  HostResource.class, ref.getLinked(), now, FAILFAST_CHECK_COUNT)).values(),
        new Predicate<DomainBase>() {
            @Override
            public boolean apply(DomainBase domain) {
              return domain.getNameservers().contains(ref);
            }});
  }

  /** Enqueues a host resource deletion on the mapreduce queue. */
  @Override
  protected final void enqueueTasks() throws EppException {
    AsyncFlowUtils.enqueueMapreduceAction(
        DeleteHostResourceAction.class,
        ImmutableMap.of(
            DeleteEppResourceAction.PARAM_RESOURCE_KEY,
            Key.create(existingResource).getString(),
            DeleteEppResourceAction.PARAM_REQUESTING_CLIENT_ID,
            getClientId(),
            DeleteEppResourceAction.PARAM_IS_SUPERUSER,
            Boolean.toString(superuser)),
        RegistryEnvironment.get().config().getAsyncDeleteFlowMapreduceDelay());
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.HOST_PENDING_DELETE;
  }
}
