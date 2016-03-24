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

package com.google.domain.registry.tools.server;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.partition;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.request.Action.Method.POST;
import static com.google.domain.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.mapreduce.MapreduceAction;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.mapreduce.inputs.EppResourceInputs;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.model.index.DomainApplicationIndex;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.Response;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;

import java.util.List;

import javax.inject.Inject;

/** Deletes all {@link EppResource} objects in datastore, including indices and descendants. */
@Action(path = "/_dr/task/killAllEppResources", method = POST)
public class KillAllEppResourcesAction implements MapreduceAction {

  private static final int BATCH_SIZE = 100;

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject KillAllEppResourcesAction() {}

  @Override
  public void run() {
    checkArgument( // safety
        RegistryEnvironment.get() == RegistryEnvironment.ALPHA
            || RegistryEnvironment.get() == RegistryEnvironment.UNITTEST,
        "DO NOT RUN ANYWHERE ELSE EXCEPT ALPHA OR TESTS.");
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Delete all EppResources, children, and indices")
        .setModuleName("tools")
        .runMapOnly(
            new KillAllEppResourcesMapper(),
            ImmutableList.of(EppResourceInputs.createIndexInput()))));
  }

  static class KillAllEppResourcesMapper extends Mapper<EppResourceIndex, Void, Void> {

    private static final long serialVersionUID = 103826288518612669L;

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
      EppResource resource = eri.getReference().get();
      for (final List<Key<Object>> batch
          : partition(ofy().load().ancestor(resource).keys(), BATCH_SIZE)) {
        ofy().transact(new VoidWork() {
          @Override
          public void vrun() {
            ofy().deleteWithoutBackup().entities(batch);
          }});
        getContext().incrementCounter("deleted descendants", batch.size());
      }
      final Key<?> foreignKey = resource instanceof DomainApplication
          ? DomainApplicationIndex.createKey((DomainApplication) resource)
          : ForeignKeyIndex.createKey(resource);
      ofy().transact(new VoidWork() {
        @Override
        public void vrun() {
          ofy().deleteWithoutBackup().keys(Key.create(eri), foreignKey).now();
        }});
      getContext().incrementCounter("deleted eris");
    }
  }
}
