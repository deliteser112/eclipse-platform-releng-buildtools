package google.registry.export;

import static google.registry.export.CheckSnapshotAction.enqueuePollTask;
import static google.registry.request.Action.Method.POST;

import google.registry.config.RegistryConfig;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;

/**
 * Action to trigger a datastore backup job that writes a snapshot to Google Cloud Storage.
 *
 * <p>This is the first step of a four step workflow for exporting snapshots, with each step calling
 * the next upon successful completion:
 *
 * <ol>
 *   <li>The snapshot is exported to Google Cloud Storage (this action).
 *   <li>The {@link CheckSnapshotAction} polls until the export is completed.
 *   <li>The {@link LoadSnapshotAction} imports the data from GCS to BigQuery.
 *   <li>The {@link UpdateSnapshotViewAction} updates the view in latest_snapshot.
 * </ol>
 */
@Action(path = ExportSnapshotAction.PATH, method = POST, automaticallyPrintOk = true)
public class ExportSnapshotAction implements Runnable {

  /** Queue to use for enqueuing the task that will actually launch the backup. */
  static final String QUEUE = "export-snapshot"; // See queue.xml.

  static final String PATH = "/_dr/task/exportSnapshot"; // See web.xml.

  /** Prefix to use for naming all snapshots that are started by this servlet. */
  static final String SNAPSHOT_PREFIX = "auto_snapshot_";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject Clock clock;
  @Inject DatastoreBackupService backupService;
  @Inject Response response;

  @Inject
  ExportSnapshotAction() {}

  @Override
  public void run() {
    // Use a unique name for the snapshot so we can explicitly check its completion later.
    String snapshotName = SNAPSHOT_PREFIX + clock.nowUtc().toString("YYYYMMdd_HHmmss");
    backupService.launchNewBackup(
        QUEUE, snapshotName, RegistryConfig.getSnapshotsBucket(), ExportConstants.getBackupKinds());
    // Enqueue a poll task to monitor the backup and load reporting-related kinds into bigquery.
    enqueuePollTask(snapshotName, ExportConstants.getReportingKinds());
    String message = "Datastore backup started with name: " + snapshotName;
    logger.info(message);
    response.setPayload(message);
  }
}
