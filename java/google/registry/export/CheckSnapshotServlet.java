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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.Sets.intersection;
import static com.google.common.html.HtmlEscapers.htmlEscaper;
import static google.registry.export.LoadSnapshotAction.enqueueLoadSnapshotTask;
import static google.registry.request.RequestParameters.extractRequiredParameter;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;
import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.net.MediaType;
import google.registry.export.DatastoreBackupInfo.BackupStatus;
import google.registry.request.HttpException.BadRequestException;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import java.io.IOException;
import java.util.Set;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.Duration;
import org.joda.time.PeriodType;
import org.joda.time.format.PeriodFormat;

/** Check the status of a snapshot, and if complete, trigger loading it into BigQuery. */
public class CheckSnapshotServlet extends HttpServlet {

  /** Parameter names for passing parameters into this servlet. */
  static final String SNAPSHOT_NAME_PARAM = "name";
  static final String SNAPSHOT_KINDS_TO_LOAD_PARAM = "kindsToLoad";

  /** Servlet-specific details needed for enqueuing tasks against itself. */
  static final String QUEUE = "export-snapshot-poll";  // See queue.xml.
  static final String PATH = "/_dr/task/checkSnapshot";  // See web.xml.
  static final Duration POLL_COUNTDOWN = Duration.standardMinutes(2);

  /** The maximum amount of time we allow a backup to run before abandoning it. */
  static final Duration MAXIMUM_BACKUP_RUNNING_TIME = Duration.standardHours(20);

  private static final FormattingLogger logger = getLoggerForCallerClass();

  @NonFinalForTesting
  private static DatastoreBackupService backupService = DatastoreBackupService.get();

  @Override
  public void service(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    try {
      rsp.setStatus(SC_OK);
      rsp.setContentType(MediaType.PLAIN_TEXT_UTF_8.toString());
      rsp.getWriter().write("OK\n\n");
      super.service(req, rsp);
    } catch (Throwable e) {
      logger.severe(e, e.toString());
      rsp.sendError(
          e instanceof IllegalArgumentException ? SC_BAD_REQUEST : SC_INTERNAL_SERVER_ERROR,
          htmlEscaper().escape(firstNonNull(e.getMessage(), e.toString())));
    }
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    // TODO(b/28266757): Remove this try/catch/rethrow block once this servlet is Daggerized.
    try {
      String snapshotName = extractRequiredParameter(req, SNAPSHOT_NAME_PARAM);
      rsp.getWriter().write(backupService.findByName(snapshotName).getInformation());
    } catch (BadRequestException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    String snapshotName;
    String kindsToLoadParam;
    // TODO(b/28266757): Remove this try/catch/rethrow block once this servlet is Daggerized.
    try {
      snapshotName = extractRequiredParameter(req, SNAPSHOT_NAME_PARAM);
      kindsToLoadParam = extractRequiredParameter(req, SNAPSHOT_KINDS_TO_LOAD_PARAM);
    } catch (BadRequestException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
    Set<String> kindsToLoad = ImmutableSet.copyOf(Splitter.on(',').split(kindsToLoadParam));

    // Look up the backup by the provided name, stopping if we can't find it.
    DatastoreBackupInfo backup;
    try {
      backup = backupService.findByName(snapshotName);
    } catch (IllegalArgumentException e) {
      String message = String.format("Bad backup name %s: %s", snapshotName, e.getMessage());
      logger.severe(e, message);
      // TODO(b/19081569): Ideally this would return a 2XX error so the task would not be retried,
      //   but we might abandon backups that start late and haven't yet written to datastore.
      //   We could fix that by replacing this with a two-phase polling strategy.
      rsp.sendError(SC_BAD_REQUEST, htmlEscaper().escape(message));
      return;
    }
    // Stop now if the backup is not complete.
    if (!backup.getStatus().equals(BackupStatus.COMPLETE)) {
      Duration runningTime = backup.getRunningTime();
      if (runningTime.isShorterThan(MAXIMUM_BACKUP_RUNNING_TIME)) {
        // Backup might still be running, so send a 304 to have the task retry.
        rsp.sendError(SC_NOT_MODIFIED,
            htmlEscaper().escape(String.format("Datastore backup %s still pending", snapshotName)));
      } else {
        // Declare the backup a lost cause, and send 202 Accepted so the task will not be retried.
        String message = String.format("Datastore backup %s abandoned - not complete after %s",
            snapshotName,
            PeriodFormat.getDefault().print(
                runningTime.toPeriod().normalizedStandard(
                    PeriodType.dayTime().withMillisRemoved())));
        logger.severe(message);
        rsp.sendError(SC_ACCEPTED, htmlEscaper().escape(message));
      }
      return;
    }
    // Get a compact string to identify this snapshot in BigQuery by trying to parse the unique
    // suffix out of the snapshot name and falling back to the start time as a string.
    String snapshotId = snapshotName.startsWith(ExportSnapshotServlet.SNAPSHOT_PREFIX)
        ? snapshotName.substring(ExportSnapshotServlet.SNAPSHOT_PREFIX.length())
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
    rsp.getWriter().write(message);
  }

  /** Enqueue a poll task to monitor the named snapshot for completion. */
  TaskHandle enqueuePollTask(String snapshotName, ImmutableSet<String> kindsToLoad) {
    return QueueFactory.getQueue(QUEUE).add(
        TaskOptions.Builder.withUrl(PATH)
            .method(Method.POST)
            .countdownMillis(POLL_COUNTDOWN.getMillis())
            .param(SNAPSHOT_NAME_PARAM, snapshotName)
            .param(SNAPSHOT_KINDS_TO_LOAD_PARAM, Joiner.on(',').join(kindsToLoad)));
  }
}
