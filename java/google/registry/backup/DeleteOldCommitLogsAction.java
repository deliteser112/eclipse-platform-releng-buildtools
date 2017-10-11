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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static google.registry.mapreduce.MapreduceRunner.PARAM_DRY_RUN;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;
import static google.registry.util.PipelineUtils.createJobPath;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import google.registry.config.RegistryConfig.Config;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.CommitLogManifestInput;
import google.registry.mapreduce.inputs.EppResourceInputs;
import google.registry.model.EppResource;
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
              new DeleteOldCommitLogsReducer(deletionThreshold, isDryRun),
              ImmutableList.of(
                  new CommitLogManifestInput(deletionThreshold),
                  EppResourceInputs.createKeyInput(EppResource.class)))));
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
      extends Mapper<Key<?>, Key<CommitLogManifest>, Boolean> {

    private static final long serialVersionUID = -1960845380164573834L;

    private static final String KIND_MANIFEST = Key.getKind(CommitLogManifest.class);

    @Override
    public void map(final Key<?> key) {
      // key is either a Key<CommitLogManifest> or a Key<? extends EppResource>.
      //
      // If it's a CommitLogManifest we just emit it as is (no need to load it).
      if (key.getKind().equals(KIND_MANIFEST)) {
        getContext().incrementCounter("old commit log manifests found");
        // safe because we checked getKind
        @SuppressWarnings("unchecked")
        Key<CommitLogManifest> manifestKey = (Key<CommitLogManifest>) key;
        emit(manifestKey, true);
        return;
      }

      // If it isn't a Key<CommitLogManifest> then it should be an EppResource, which we need to
      // load to emit the revisions.
      //
      // We want to make sure we retry any load individually to reduce the chance of the entire
      // shard failing, hence we wrap it in a transactNew.
      Object object = ofy().transactNew(new Work<Object>() {
        @Override
        public Object run() {
          return ofy().load().key(key).now();
        }
      });
      checkNotNull(object, "Received a key to a missing object. key: %s", key);
      checkState(
          object instanceof EppResource,
          "Received a key to an object that isn't EppResource nor CommitLogManifest."
          + " Key: %s object type: %s",
          key,
          object.getClass().getName());

      getContext().incrementCounter("EPP resources found");
      EppResource eppResource = (EppResource) object;
      for (Key<CommitLogManifest> manifestKey : eppResource.getRevisions().values()) {
        emit(manifestKey, false);
      }
      getContext()
          .incrementCounter("EPP resource revisions found", eppResource.getRevisions().size());
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
  static class DeleteOldCommitLogsReducer
      extends Reducer<Key<CommitLogManifest>, Boolean, Void> {

    private static final long serialVersionUID = -4918760187627937268L;

    private final DateTime deletionThreshold;
    private final boolean isDryRun;

    @AutoValue
    abstract static class DeletionResult {
      enum Status {
        ALREADY_DELETED,
        AFTER_THRESHOLD,
        SUCCESS;
      }

      public abstract Status status();
      public abstract int numDeleted();

      static DeletionResult create(Status status, int numDeleted) {
        return
            new AutoValue_DeleteOldCommitLogsAction_DeleteOldCommitLogsReducer_DeletionResult(
                status, numDeleted);
      }
    }

    DeleteOldCommitLogsReducer(DateTime deletionThreshold, boolean isDryRun) {
      this.deletionThreshold = deletionThreshold;
      this.isDryRun = isDryRun;
    }

    @Override
    public void reduce(
        final Key<CommitLogManifest> manifestKey,
        ReducerInput<Boolean> canDeleteVerdicts) {
      ImmutableMultiset<Boolean> canDeleteMultiset = ImmutableMultiset.copyOf(canDeleteVerdicts);
      if (canDeleteMultiset.count(TRUE) > 1) {
        getContext().incrementCounter("commit log manifests incorrectly mapped multiple times");
      }
      if (canDeleteMultiset.count(FALSE) > 1) {
        getContext().incrementCounter("commit log manifests referenced multiple times");
      }
      if (canDeleteMultiset.contains(FALSE)) {
        getContext().incrementCounter(
            canDeleteMultiset.contains(TRUE)
            ? "old commit log manifests still referenced"
            : "new (or nonexistent) commit log manifests referenced");
        getContext().incrementCounter(
            "EPP resource revisions handled",
            canDeleteMultiset.count(FALSE));
        return;
      }

      DeletionResult deletionResult = ofy().transactNew(new Work<DeletionResult>() {
        @Override
        public DeletionResult run() {
          CommitLogManifest manifest = ofy().load().key(manifestKey).now();
          // It is possible that the same manifestKey was run twice, if a shard had to be restarted
          // or some weird failure. If this happens, we want to exit immediately.
          // Note that this can never happen in dryRun.
          if (manifest == null) {
            return DeletionResult.create(DeletionResult.Status.ALREADY_DELETED, 0);
          }
          // Doing a sanity check on the date. This is the only place we use the CommitLogManifest,
          // so maybe removing this test will improve performance. However, unless it's proven that
          // the performance boost is significant (and we've tested this enough to be sure it never
          // happens)- the safty of "let's not delete stuff we need from prod" is more important.
          if (manifest.getCommitTime().isAfter(deletionThreshold)) {
            return DeletionResult.create(DeletionResult.Status.AFTER_THRESHOLD, 0);
          }
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
          return DeletionResult.create(DeletionResult.Status.SUCCESS, keysToDelete.size());
        }
      });

      switch (deletionResult.status()) {
        case SUCCESS:
          getContext().incrementCounter("old commit log manifests deleted");
          getContext().incrementCounter("total entities deleted", deletionResult.numDeleted());
          break;
        case ALREADY_DELETED:
          getContext().incrementCounter("attempts to delete an already deleted manifest");
          break;
        case AFTER_THRESHOLD:
          logger.severefmt("Won't delete CommitLogManifest %s that is too recent.", manifestKey);
          getContext().incrementCounter("manifests incorrectly assigned for deletion (SEE LOGS)");
          break;
      }
    }
  }
}
