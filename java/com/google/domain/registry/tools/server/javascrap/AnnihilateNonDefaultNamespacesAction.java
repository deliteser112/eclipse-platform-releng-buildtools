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

package com.google.domain.registry.tools.server.javascrap;

import static com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService;
import static com.google.common.base.Preconditions.checkState;
import static com.google.domain.registry.mapreduce.MapreduceRunner.PARAM_DRY_RUN;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entities;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.inputs.DatastoreKeyInput;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.mapreduce.MapreduceAction;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.mapreduce.inputs.ChunkingKeyInput;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.Parameter;
import com.google.domain.registry.request.Response;
import com.google.domain.registry.util.FormattingLogger;
import com.google.domain.registry.util.PipelineUtils;

import com.googlecode.objectify.VoidWork;

import java.util.List;

import javax.inject.Inject;

/**
 * A mapreduce that deletes all entities in all namespaces except for the default namespace.
 */
@Action(path = "/_dr/task/annihilateNonDefaultNamespaces")
public class AnnihilateNonDefaultNamespacesAction implements MapreduceAction {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject DatastoreService datastoreService;
  @Inject @Parameter(PARAM_DRY_RUN) boolean isDryRun;
  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject AnnihilateNonDefaultNamespacesAction() {}

  @Override
  public void run() {
    response.sendJavaScriptRedirect(PipelineUtils.createJobPath(mrRunner
        .setJobName("Annihilate non-default namespaces")
        .setModuleName("backend")
        .runMapOnly(
            new AnnihilateNonDefaultNamespacesMapper(isDryRun),
            getInputs())));
  }

  /** Mapper to delete all entities in non-default namespaces. */
  public static class AnnihilateNonDefaultNamespacesMapper extends Mapper<List<Key>, Void, Void> {

    private static final long serialVersionUID = 8415923063881853727L;
    private final boolean isDryRun;

    public AnnihilateNonDefaultNamespacesMapper(boolean isDryRun) {
      this.isDryRun = isDryRun;
    }

    @Override
    public final void map(final List<Key> keys) {
      if (isDryRun) {
        logger.infofmt("Would delete these entities: %s", keys);
      } else {
        for (Key key : keys) {
          // Additional safety check to prevent deleting real data.
          checkState(
              !Strings.isNullOrEmpty(key.getNamespace()),
              "Will not delete key %s is in default namespace",
              key);
        }
        ofy().transact(new VoidWork() {
          @Override
          public void vrun() {
            getDatastoreService().delete(keys);
          }});
      }
      getContext().incrementCounter("entities deleted", keys.size());
    }
  }

  private Iterable<Input<List<Key>>> getInputs() {
    ImmutableSet.Builder<String> namespaces = new ImmutableSet.Builder<>();
    Query namespacesQuery = new Query(Entities.NAMESPACE_METADATA_KIND).setKeysOnly();
    for (Entity entity : datastoreService.prepare(namespacesQuery).asIterable()) {
      // Don't delete anything in the default namespace!
      if (!Strings.isNullOrEmpty(entity.getKey().getName())) {
        namespaces.add(entity.getKey().getName());
      }
    }

    ImmutableSet.Builder<Input<List<Key>>> inputs = new ImmutableSet.Builder<>();
    for (String namespace : namespaces.build()) {
      NamespaceManager.set(namespace);
      ImmutableSet.Builder<String> kindsBuilder = new ImmutableSet.Builder<>();
      Query kindsQuery = new Query(Entities.KIND_METADATA_KIND).setKeysOnly();
      for (Entity entity : datastoreService.prepare(kindsQuery).asIterable()) {
        // Don't delete built-in kinds such as __Stat_* entities.
        if (!entity.getKey().getName().startsWith("_")) {
          kindsBuilder.add(entity.getKey().getName());
        }
      }
      ImmutableSet<String> kinds = kindsBuilder.build();
      logger.infofmt("For namespace %s, found kinds: %s", namespace, kinds);
      for (String kind : kinds) {
        // Don't try to parallelize here, because Registry 1.0 entities are almost all in a single
        // entity group.
        inputs.add(new ChunkingKeyInput(new DatastoreKeyInput(kind, 1, namespace), 20));
      }
    }
    NamespaceManager.set("");
    return inputs.build();
  }
}
