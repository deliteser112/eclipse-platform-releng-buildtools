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

package com.google.domain.registry.tools.server;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.partition;
import static com.google.common.collect.Lists.partition;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.request.Action.Method.POST;
import static com.google.domain.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.inputs.InMemoryInput;
import com.google.common.collect.ImmutableList;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.mapreduce.MapreduceAction;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.model.ofy.CommitLogBucket;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.Response;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;

import java.util.List;

import javax.inject.Inject;

/**
 * Deletes all commit logs in datastore.
 *
 * <p>Before running this, use the datastore admin page to delete all {@code CommitLogManifest} and
 * {@code CommitLogMutation} entities. That will take care of most (likely all) commit log entities
 * (except perhaps for very recently created entities that are missed by the eventually consistent
 * query driving that deletion) and it will be much faster than this mapreduce. After that, run this
 * to get a guarantee that everything was deleted.
 */
@Action(path = "/_dr/task/killAllCommitLogs", method = POST)
public class KillAllCommitLogsAction implements MapreduceAction {

  private static final int BATCH_SIZE = 100;

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject KillAllCommitLogsAction() {}

  @Override
  public void run() {
    checkArgument( // safety
        RegistryEnvironment.get() == RegistryEnvironment.ALPHA
            || RegistryEnvironment.get() == RegistryEnvironment.UNITTEST,
        "DO NOT RUN ANYWHERE ELSE EXCEPT ALPHA OR TESTS.");
    // Create a in-memory input, assigning each bucket to its own shard for maximum parallelization.
    Input<Key<CommitLogBucket>> input =
        new InMemoryInput<>(partition(CommitLogBucket.getAllBucketKeys().asList(), 1));
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Delete all commit logs")
        .setModuleName("tools")
        .runMapOnly(new KillAllCommitLogsMapper(), ImmutableList.of(input))));
  }

  /**
   * Mapper to delete a {@link CommitLogBucket} and any commit logs in the bucket.
   *
   * <p>This will delete:
   * <ul>
   *   <li>{@link CommitLogBucket}
   *   <li>{@code CommitLogManifest}
   *   <li>{@code CommitLogMutation}
   * </ul>
   */
  static class KillAllCommitLogsMapper extends Mapper<Key<CommitLogBucket>, Void, Void> {

    private static final long serialVersionUID = 1504266335352952033L;

    @Override
    public void map(Key<CommitLogBucket> bucket) {
      // The query on the bucket could time out, but we are not worried about that because of the
      // procedure outlined above.
      for (final List<Key<Object>> batch
          : partition(ofy().load().ancestor(bucket).keys(), BATCH_SIZE)) {
        ofy().transact(new VoidWork() {
          @Override
          public void vrun() {
            ofy().deleteWithoutBackup().entities(batch);
          }});
        getContext().incrementCounter("deleted entities", batch.size());
      }
      getContext().incrementCounter("completed buckets");
    }
  }
}

