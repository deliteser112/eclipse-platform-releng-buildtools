// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.backup;

import static google.registry.mapreduce.MapreduceRunner.PARAM_DRY_RUN;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;
import static google.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import google.registry.config.RegistryConfig.Config;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.CommitLogManifestInput;
import google.registry.mapreduce.inputs.EppResourceInputs;
import google.registry.model.EppResource;
import google.registry.model.ImmutableObject;
import google.registry.model.ofy.CommitLogManifest;
import google.registry.model.ofy.CommitLogMutation;
import google.registry.model.translators.CommitLogRevisionsTranslatorFactory;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Task that garbage collects old {@link CommitLogManifest} entities.
 *
 * <p>Once commit logs have been written to GCS, we don't really need them in Datastore anymore,
 * except to reconstruct point-in-time snapshots of the database. To make that possible, {@link
 * EppResource}s have a {@link EppResource#getRevisions} method that returns the commit logs for
 * older points in time. But that functionality is not useful after a certain amount of time, e.g.
 * thirty days, so unneeded revisions are deleted
 * (see {@link CommitLogRevisionsTranslatorFactory}). This leaves commit logs in the system that are
 * unneeded (have no revisions pointing to them). So this task runs periodically to delete the
 * "orphan" commit logs.
 *
 * <p>This action runs a mapreduce that goes over all existing {@link EppResource} and all {@link
 * CommitLogManifest} older than commitLogDatastreRetention, and erases the commit logs aren't in an
 * EppResource.
 *
 */
@Action(
  path = "/_dr/task/deleteOldCommitLogs",
  auth = Auth.AUTH_INTERNAL_ONLY
)
public final class DeleteOldCommitLogsAction implements Runnable {

  private static final int NUM_MAP_SHARDS = 20;
  private static final int NUM_REDUCE_SHARDS = 10;
  private static final FormattingLogger logger = getLoggerForCallerClass();

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject Clock clock;
  @Inject @Config("commitLogDatastoreRetention") Duration maxAge;
  @Inject @Parameter(PARAM_DRY_RUN) boolean isDryRun;
  @Inject DeleteOldCommitLogsAction() {}

  @Override
  public void run() {
    DateTime deletionThreshold = clock.nowUtc().minus(maxAge);
    logger.infofmt(
        "Processing asynchronous deletion of unreferenced CommitLogManifests older than %s",
        deletionThreshold);

    response.sendJavaScriptRedirect(createJobPath(mrRunner
          .setJobName("Delete old commit logs")
          .setModuleName("backend")
          .setDefaultMapShards(NUM_MAP_SHARDS)
          .setDefaultReduceShards(NUM_REDUCE_SHARDS)
          .runMapreduce(
              new DeleteOldCommitLogsMapper(),
              new DeleteOldCommitLogsReducer(isDryRun),
              ImmutableList.of(
                  new CommitLogManifestInput(Optional.of(deletionThreshold)),
                  EppResourceInputs.createEntityInput(EppResource.class)))));
  }

  /**
   * A mapper that iterates over all {@link EppResource} and {CommitLogManifest} entities.
   *
   * <p>It emits the target key and {@code false} for all revisions of each EppResources (meaning
   * "don't delete this"), and {@code true} for all CommitLogRevisions (meaning "delete this").
   *
   * <p>The reducer will then delete all CommitLogRevisions that only have {@code true}.
   */
  private static class DeleteOldCommitLogsMapper
      extends Mapper<ImmutableObject, Key<CommitLogManifest>, Boolean> {

    private static final long serialVersionUID = -1960845380164573834L;

    @Override
    public void map(ImmutableObject object) {
      if (object instanceof EppResource) {
        getContext().incrementCounter("Epp resources found");
        EppResource eppResource = (EppResource) object;
        for (Key<CommitLogManifest> manifestKey : eppResource.getRevisions().values()) {
          emit(manifestKey, false);
        }
        getContext()
            .incrementCounter("Epp resource revisions found", eppResource.getRevisions().size());
      } else if (object instanceof CommitLogManifest) {
        getContext().incrementCounter("old commit log manifests found");
        emit(Key.create((CommitLogManifest) object), true);
      } else {
        throw new IllegalArgumentException(
            String.format(
                "Received object of type %s, expected either EppResource or CommitLogManifest",
                object.getClass().getName()));
      }
    }
  }

  /**
   * Reducer that deletes unreferenced {@link CommitLogManifest} + child {@link CommitLogMutation}.
   *
   * <p>It receives the manifestKey to possibly delete, and a list of boolean 'verdicts' from
   * various sources (the "old manifests" source and the "still referenced" source) on whether it's
   * OK to delete this manifestKey. If even one source returns "false" (meaning "it's not OK to
   * delete this manifest") then it won't be deleted.
   */
  private static class DeleteOldCommitLogsReducer
      extends Reducer<Key<CommitLogManifest>, Boolean, Void> {

    private static final long serialVersionUID = -5122986392078633220L;

    private final boolean isDryRun;

    DeleteOldCommitLogsReducer(boolean isDryRun) {
      this.isDryRun = isDryRun;
    }

    @Override
    public void reduce(
        final Key<CommitLogManifest> manifestKey,
        ReducerInput<Boolean> canDeleteVerdicts) {
      if (Iterators.contains(canDeleteVerdicts, false)) {
        getContext().incrementCounter("old commit log manifests still referenced");
        return;
      }
      Integer deletedCount = ofy().transact(new Work<Integer>() {
        @Override
        public Integer run() {
          Iterable<Key<CommitLogMutation>> commitLogMutationKeys = ofy().load()
              .type(CommitLogMutation.class)
              .ancestor(manifestKey)
              .keys()
              .iterable();
          ImmutableList<Key<?>> keysToDelete = ImmutableList.<Key<?>>builder()
              .addAll(commitLogMutationKeys)
              .add(manifestKey)
              .build();
          // Normally in a dry run we would log the entities that would be deleted, but those can
          // number in the millions so we skip the logging.
          if (!isDryRun) {
            ofy().deleteWithoutBackup().keys(keysToDelete);
          }
          return keysToDelete.size();
        }
      });
      getContext().incrementCounter("old commit log manifests deleted");
      getContext().incrementCounter("total entities deleted", deletedCount);
    }
  }
}
