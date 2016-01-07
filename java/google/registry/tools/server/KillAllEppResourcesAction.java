// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools.server;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.POST;
import static google.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import google.registry.config.RegistryEnvironment;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.EppResourceInputs;
import google.registry.model.EppResource;
import google.registry.model.domain.DomainApplication;
import google.registry.model.index.DomainApplicationIndex;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.request.Action;
import google.registry.request.Response;
import javax.inject.Inject;

/** Deletes all {@link EppResource} objects in datastore, including indices and descendants. */
@Action(path = "/_dr/task/killAllEppResources", method = POST)
public class KillAllEppResourcesAction implements Runnable {

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject KillAllEppResourcesAction() {}

  @Override
  public void run() {
    checkArgument( // safety
        RegistryEnvironment.get() == RegistryEnvironment.CRASH
            || RegistryEnvironment.get() == RegistryEnvironment.UNITTEST,
        "DO NOT RUN ANYWHERE ELSE EXCEPT CRASH OR TESTS.");
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Delete all EppResources, children, and indices")
        .setModuleName("tools")
        .runMapreduce(
            new KillAllEppResourcesMapper(),
            new KillAllEntitiesReducer(),
            ImmutableList.of(EppResourceInputs.createIndexInput()))));
  }

  static class KillAllEppResourcesMapper extends Mapper<EppResourceIndex, Key<?>, Key<?>> {

    private static final long serialVersionUID = 8205309000002507407L;

    /**
     * Delete an {@link EppResourceIndex}, its referent, all descendants of each referent, and the
     * {@link ForeignKeyIndex} or {@link DomainApplicationIndex} of the referent, as appropriate.
     *
     * <p>This will delete:
     * <ul>
     *   <li>All {@link ForeignKeyIndex} types
     *   <li>{@link DomainApplicationIndex}
     *   <li>{@link EppResourceIndex}
     *   <li>All {@link EppResource} types
     *   <li>{@code HistoryEntry}
     *   <li>All {@code BillingEvent} types
     *   <li>All {@code PollMessage} types
     * </ul>
     */
    @Override
    public void map(final EppResourceIndex eri) {
      Key<EppResourceIndex> eriKey = Key.create(eri);
      emitAndIncrementCounter(eriKey, eriKey);
      Key<?> resourceKey = eri.getKey();
      for (Key<Object> key : ofy().load().ancestor(resourceKey).keys()) {
        emitAndIncrementCounter(resourceKey, key);
      }
      // Load in a transaction to make sure we don't get stale data (in case of host renames).
      // TODO(b/27424173): A transaction is overkill. When we have memcache-skipping, use that.
      EppResource resource = ofy().transactNewReadOnly(
          new Work<EppResource>() {
            @Override
            public EppResource run() {
              return ofy().load().key(eri.getKey()).now();
            }});
      // TODO(b/28247733): What about FKI's for renamed hosts?
      Key<?> indexKey = resource instanceof DomainApplication
          ? DomainApplicationIndex.createKey((DomainApplication) resource)
          : ForeignKeyIndex.createKey(resource);
      emitAndIncrementCounter(indexKey, indexKey);
    }

    private void emitAndIncrementCounter(Key<?> ancestor, Key<?> child) {
      emit(ancestor, child);
      getContext().incrementCounter("entities emitted");
      getContext().incrementCounter(String.format("%s emitted", child.getKind()));
    }
  }
}
