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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterators.peekingIterator;
import static google.registry.backup.BackupUtils.createDeserializingIterator;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static java.util.Arrays.asList;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityTranslator;
import com.google.appengine.tools.cloudstorage.GcsFileMetadata;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Result;
import com.googlecode.objectify.util.ResultNow;
import google.registry.config.RegistryEnvironment;
import google.registry.model.ImmutableObject;
import google.registry.model.ofy.CommitLogBucket;
import google.registry.model.ofy.CommitLogCheckpoint;
import google.registry.model.ofy.CommitLogCheckpointRoot;
import google.registry.model.ofy.CommitLogManifest;
import google.registry.model.ofy.CommitLogMutation;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.util.FormattingLogger;
import google.registry.util.Retrier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** Restore Registry 2 commit logs from GCS to datastore. */
@Action(
    path = RestoreCommitLogsAction.PATH,
    method = Action.Method.POST,
    automaticallyPrintOk = true)
public class RestoreCommitLogsAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  static final int BLOCK_SIZE = 1024 * 1024;  // Buffer 1mb at a time, for no particular reason.

  static final String PATH = "/_dr/task/restoreCommitLogs";
  static final String DRY_RUN_PARAM = "dryRun";
  static final String FROM_TIME_PARAM = "fromTime";

  @Inject GcsService gcsService;
  @Inject @Parameter(DRY_RUN_PARAM) boolean dryRun;
  @Inject @Parameter(FROM_TIME_PARAM) DateTime fromTime;
  @Inject DatastoreService datastoreService;
  @Inject GcsDiffFileLister diffLister;
  @Inject Retrier retrier;
  @Inject RestoreCommitLogsAction() {}

  @Override
  public void run() {
    checkArgument( // safety
        RegistryEnvironment.get() == RegistryEnvironment.ALPHA
            || RegistryEnvironment.get() == RegistryEnvironment.UNITTEST,
        "DO NOT RUN ANYWHERE ELSE EXCEPT ALPHA OR TESTS.");
    if (dryRun) {
      logger.info("Running in dryRun mode");
    }
    List<GcsFileMetadata> diffFiles = diffLister.listDiffFiles(fromTime);
    if (diffFiles.isEmpty()) {
      logger.info("Nothing to restore");
      return;
    }
    Map<Integer, DateTime> bucketTimestamps = new HashMap<>();
    CommitLogCheckpoint lastCheckpoint = null;
    for (GcsFileMetadata metadata : diffFiles) {
      logger.info("Restoring: " + metadata.getFilename().getObjectName());
      try (InputStream input = Channels.newInputStream(
          gcsService.openPrefetchingReadChannel(metadata.getFilename(), 0, BLOCK_SIZE))) {
        PeekingIterator<ImmutableObject> commitLogs =
            peekingIterator(createDeserializingIterator(input));
        lastCheckpoint = (CommitLogCheckpoint) commitLogs.next();
        saveOfy(asList(lastCheckpoint));  // Save the checkpoint itself.
        while (commitLogs.hasNext()) {
          CommitLogManifest manifest = restoreOneTransaction(commitLogs);
          bucketTimestamps.put(manifest.getBucketId(), manifest.getCommitTime());
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    // Restore the CommitLogCheckpointRoot and CommitLogBuckets.
    saveOfy(FluentIterable.from(bucketTimestamps.entrySet())
        .transform(new Function<Entry<Integer, DateTime>, ImmutableObject> () {
          @Override
          public ImmutableObject apply(Entry<Integer, DateTime> entry) {
            return new CommitLogBucket.Builder()
                .setBucketNum(entry.getKey())
                .setLastWrittenTime(entry.getValue())
                .build();
          }})
        .append(CommitLogCheckpointRoot.create(lastCheckpoint.getCheckpointTime())));
  }

  /**
   * Restore the contents of one transaction to datastore.
   *
   * <p>The objects to delete are listed in the {@link CommitLogManifest}, which will be the first
   * object in the iterable. The objects to save follow, each as a {@link CommitLogMutation}. We
   * restore by deleting the deletes and recreating the saves from their proto form. We also save
   * the commit logs themselves back to datastore, so that the commit log system itself is
   * transparently restored alongside the data.
   *
   * @return the manifest, for use in restoring the {@link CommitLogBucket}.
   */
  private CommitLogManifest restoreOneTransaction(PeekingIterator<ImmutableObject> commitLogs) {
    final CommitLogManifest manifest = (CommitLogManifest) commitLogs.next();
    Result<?> deleteResult = deleteAsync(manifest.getDeletions());
    List<Entity> entitiesToSave = Lists.newArrayList(ofy().save().toEntity(manifest));
    while (commitLogs.hasNext() && commitLogs.peek() instanceof CommitLogMutation) {
      CommitLogMutation mutation = (CommitLogMutation) commitLogs.next();
      entitiesToSave.add(ofy().save().toEntity(mutation));
      entitiesToSave.add(EntityTranslator.createFromPbBytes(mutation.getEntityProtoBytes()));
    }
    saveRaw(entitiesToSave);
    try {
      deleteResult.now();
    } catch (Exception e) {
      retry(new Runnable() {
        @Override
        public void run() {
          deleteAsync(manifest.getDeletions()).now();
        }});
    }
    return manifest;
  }

  private void saveRaw(final List<Entity> entitiesToSave) {
    if (dryRun) {
      logger.info("Would have saved " + entitiesToSave);
      return;
    }
    retry(new Runnable() {
      @Override
      public void run() {
        datastoreService.put(entitiesToSave);
      }});
  }

  private void saveOfy(final Iterable<? extends ImmutableObject> objectsToSave) {
    if (dryRun) {
      logger.info("Would have saved " + asList(objectsToSave));
      return;
    }
    retry(new Runnable() {
      @Override
      public void run() {
        ofy().saveWithoutBackup().entities(objectsToSave).now();
      }});
  }

  private Result<?> deleteAsync(Set<Key<?>> keysToDelete) {
    if (dryRun) {
      logger.info("Would have deleted " + keysToDelete);
    }
    return dryRun || keysToDelete.isEmpty()
        ? new ResultNow<Void>(null)
        : ofy().deleteWithoutBackup().keys(keysToDelete);
   }

  /** Retrier for saves and deletes, since we can't proceed with any failures. */
  private void retry(final Runnable runnable) {
    retrier.callWithRetry(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            runnable.run();
            return null;
          }},
        RuntimeException.class);
  }
}
