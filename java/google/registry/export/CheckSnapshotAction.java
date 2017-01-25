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

package google.registry.export;

import static com.google.common.collect.Sets.intersection;
import static google.registry.export.LoadSnapshotAction.enqueueLoadSnapshotTask;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.POST;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import google.registry.export.DatastoreBackupInfo.BackupStatus;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NoContentException;
import google.registry.request.HttpException.NotModifiedException;
import google.registry.request.Parameter;
import google.registry.request.RequestMethod;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import java.util.Set;
import javax.inject.Inject;
import org.joda.time.Duration;
import org.joda.time.PeriodType;
import org.joda.time.format.PeriodFormat;

/**
 * Action that checks the status of a snapshot, and if complete, trigger loading it into BigQuery.
 */
@Action(
  path = CheckSnapshotAction.PATH,
  method = {POST, GET},
  automaticallyPrintOk = true
)
public class CheckSnapshotAction implements Runnable {

  /** Parameter names for passing parameters into this action. */
  static final String CHECK_SNAPSHOT_NAME_PARAM = "name";

  static final String CHECK_SNAPSHOT_KINDS_TO_LOAD_PARAM = "kindsToLoad";

  /** Action-specific details needed for enqueuing tasks against itself. */
  static final String QUEUE = "export-snapshot-poll"; // See queue.xml.

  static final String PATH = "/_dr/task/checkSnapshot"; // See web.xml.
  static final Duration POLL_COUNTDOWN = Duration.standardMinutes(2);

  /** The maximum amount of time we allow a backup to run before abandoning it. */
  static final Duration MAXIMUM_BACKUP_RUNNING_TIME = Duration.standardHours(20);

  private static final FormattingLogger logger = getLoggerForCallerClass();

  @Inject Response response;
  @Inject @RequestMethod Action.Method requestMethod;
  @Inject DatastoreBackupService backupService;
  @Inject @Parameter(CHECK_SNAPSHOT_NAME_PARAM) String snapshotName;
  @Inject @Parameter(CHECK_SNAPSHOT_KINDS_TO_LOAD_PARAM) String kindsToLoadParam;
  @Inject CheckSnapshotAction() {}

  @Override
  public void run() {
    if (requestMethod == POST) {
      checkAndLoadSnapshotIfComplete();
    } else {
      // This is a GET request.
      response.setPayload(getBackup().getInformation());
    }
  }

  private DatastoreBackupInfo getBackup() {
    try {
      return backupService.findByName(snapshotName);
    } catch (IllegalArgumentException e) {
      String message = String.format("Bad backup name %s: %s", snapshotName, e.getMessage());
      // TODO(b/19081569): Ideally this would return a 2XX error so the task would not be
      // retried but we might abandon backups that start late and haven't yet written to
      // datastore. We could fix that by replacing this with a two-phase polling strategy.
      throw new BadRequestException(message, e);
    }
  }

  private void checkAndLoadSnapshotIfComplete() {
    Set<String> kindsToLoad = ImmutableSet.copyOf(Splitter.on(',').split(kindsToLoadParam));
    DatastoreBackupInfo backup = getBackup();
    // Stop now if the backup is not complete.
    if (!backup.getStatus().equals(BackupStatus.COMPLETE)) {
      Duration runningTime = backup.getRunningTime();
      if (runningTime.isShorterThan(MAXIMUM_BACKUP_RUNNING_TIME)) {
        // Backup might still be running, so send a 304 to have the task retry.
        throw new NotModifiedException(
            String.format("Datastore backup %s still pending", snapshotName));
      } else {
        // Declare the backup a lost cause, and send 204 No Content so the task will
        // not be retried.
        String message =
            String.format(
                "Datastore backup %s abandoned - not complete after %s",
                snapshotName,
                PeriodFormat.getDefault()
                    .print(
                        runningTime
                            .toPeriod()
                            .normalizedStandard(PeriodType.dayTime().withMillisRemoved())));
        throw new NoContentException(message);
      }
    }
    // Get a compact string to identify this snapshot in BigQuery by trying to parse the unique
    // suffix out of the snapshot name and falling back to the start time as a string.
    String snapshotId =
        snapshotName.startsWith(ExportSnapshotAction.SNAPSHOT_PREFIX)
            ? snapshotName.substring(ExportSnapshotAction.SNAPSHOT_PREFIX.length())
            : backup.getStartTime().toString("YYYYMMdd_HHmmss");
    // Log a warning if kindsToLoad is not a subset of the exported snapshot kinds.
    if (!backup.getKinds().containsAll(kindsToLoad)) {
      logger.warningfmt(
          "Kinds to load included non-exported kinds: %s",
          Sets.difference(kindsToLoad, backup.getKinds()));
    }
    // Load kinds from the snapshot, limited to those also in kindsToLoad (if it's present).
    ImmutableSet<String> exportedKindsToLoad =
        ImmutableSet.copyOf(intersection(backup.getKinds(), kindsToLoad));
    String message = String.format("Datastore backup %s complete - ", snapshotName);
    if (exportedKindsToLoad.isEmpty()) {
      message += "no kinds to load into BigQuery";
    } else {
      enqueueLoadSnapshotTask(snapshotId, backup.getGcsFilename().get(), exportedKindsToLoad);
      message += "BigQuery load task enqueued";
    }
    logger.info(message);
    response.setPayload(message);
  }

  /** Enqueue a poll task to monitor the named snapshot for completion. */
  static TaskHandle enqueuePollTask(String snapshotName, ImmutableSet<String> kindsToLoad) {
    return QueueFactory.getQueue(QUEUE)
        .add(
            TaskOptions.Builder.withUrl(PATH)
                .method(Method.POST)
                .countdownMillis(POLL_COUNTDOWN.getMillis())
                .param(CHECK_SNAPSHOT_NAME_PARAM, snapshotName)
                .param(CHECK_SNAPSHOT_KINDS_TO_LOAD_PARAM, Joiner.on(',').join(kindsToLoad)));
  }
}
