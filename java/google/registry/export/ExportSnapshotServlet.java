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
import static com.google.common.html.HtmlEscapers.htmlEscaper;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.net.MediaType;
import google.registry.config.RegistryEnvironment;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import google.registry.util.SystemClock;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Trigger a backup-as-a-service job that writes a snapshot to Google Cloud Storage.
 *
 * <p>This is the first step of a four step workflow for exporting snapshots, with each step calling
 * the next upon successful completion:<ol>
 *   <li>The snapshot is exported to Google Cloud Storage (this servlet).
 *   <li>The {@link CheckSnapshotServlet} polls until the export is completed.
 *   <li>The {@link LoadSnapshotAction} imports the data from GCS to BigQuery.
 *   <li>The {@link UpdateSnapshotViewAction} updates the view in latest_snapshot.
 * </ol>
 */
public class ExportSnapshotServlet extends HttpServlet {

  private static final RegistryEnvironment ENVIRONMENT = RegistryEnvironment.get();

  /** Queue to use for enqueuing the task that will actually launch the backup. */
  static final String QUEUE = "export-snapshot";  // See queue.xml.

  /** Prefix to use for naming all snapshots that are started by this servlet. */
  static final String SNAPSHOT_PREFIX = "auto_snapshot_";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @NonFinalForTesting
  private static Clock clock = new SystemClock();

  @NonFinalForTesting
  private static DatastoreBackupService backupService = DatastoreBackupService.get();

  @NonFinalForTesting
  private static CheckSnapshotServlet checkSnapshotServlet = new CheckSnapshotServlet();

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    try {
      // Use a unique name for the snapshot so we can explicitly check its completion later.
      String snapshotName = SNAPSHOT_PREFIX + clock.nowUtc().toString("YYYYMMdd_HHmmss");
      backupService.launchNewBackup(
          QUEUE,
          snapshotName,
          ENVIRONMENT.config().getSnapshotsBucket(),
          ExportConstants.getBackupKinds());
      // Enqueue a poll task to monitor the backup and load reporting-related kinds into bigquery.
      checkSnapshotServlet.enqueuePollTask(snapshotName, ExportConstants.getReportingKinds());
      String message = "Datastore backup started with name: " + snapshotName;
      logger.info(message);
      rsp.setStatus(SC_OK);
      rsp.setContentType(MediaType.PLAIN_TEXT_UTF_8.toString());
      rsp.getWriter().write("OK\n\n" + message);
    } catch (Throwable e) {
      logger.severe(e, e.toString());
      rsp.sendError(
          e instanceof IllegalArgumentException ? SC_BAD_REQUEST : SC_INTERNAL_SERVER_ERROR,
          htmlEscaper().escape(firstNonNull(e.getMessage(), e.toString())));
    }
  }
}
