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

package google.registry.export;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.export.UpdateSnapshotViewAction.createViewUpdateTask;
import static google.registry.request.Action.Method.POST;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobConfiguration;
import com.google.api.services.bigquery.model.JobConfigurationLoad;
import com.google.api.services.bigquery.model.JobReference;
import com.google.api.services.bigquery.model.TableReference;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.bigquery.BigqueryFactory;
import google.registry.bigquery.BigqueryUtils.SourceFormat;
import google.registry.bigquery.BigqueryUtils.WriteDisposition;
import google.registry.config.ConfigModule.Config;
import google.registry.export.BigqueryPollJobAction.BigqueryPollJobEnqueuer;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.request.Parameter;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** Action to load a Datastore snapshot from Google Cloud Storage into BigQuery. */
@Action(path = LoadSnapshotAction.PATH, method = POST)
public class LoadSnapshotAction implements Runnable {

  /** Parameter names for passing parameters into the servlet. */
  static final String LOAD_SNAPSHOT_ID_PARAM = "id";
  static final String LOAD_SNAPSHOT_FILE_PARAM = "file";
  static final String LOAD_SNAPSHOT_KINDS_PARAM = "kinds";

  static final String SNAPSHOTS_DATASET = "snapshots";

  /** Servlet-specific details needed for enqueuing tasks against itself. */
  static final String QUEUE = "export-snapshot";  // See queue.xml.
  static final String PATH = "/_dr/task/loadSnapshot";  // See web.xml.

  private static final FormattingLogger logger = getLoggerForCallerClass();

  @Inject BigqueryFactory bigqueryFactory;
  @Inject BigqueryPollJobEnqueuer bigqueryPollEnqueuer;
  @Inject Clock clock;
  @Inject @Config("projectId") String projectId;
  @Inject @Parameter(LOAD_SNAPSHOT_FILE_PARAM) String snapshotFile;
  @Inject @Parameter(LOAD_SNAPSHOT_ID_PARAM) String snapshotId;
  @Inject @Parameter(LOAD_SNAPSHOT_KINDS_PARAM) String snapshotKinds;
  @Inject LoadSnapshotAction() {}

  /** Enqueue a task for starting a backup load. */
  public static TaskHandle enqueueLoadSnapshotTask(
      String snapshotId, String gcsFile, ImmutableSet<String> kinds) {
    return getQueue(QUEUE).add(
        TaskOptions.Builder.withUrl(PATH)
            .method(Method.POST)
            .param(LOAD_SNAPSHOT_ID_PARAM, snapshotId)
            .param(LOAD_SNAPSHOT_FILE_PARAM, gcsFile)
            .param(LOAD_SNAPSHOT_KINDS_PARAM, Joiner.on(',').join(kinds)));
  }

  @Override
  public void run() {
    try {
      String message =
          loadSnapshot(snapshotId, snapshotFile, Splitter.on(',').split(snapshotKinds));
      logger.infofmt("Loaded snapshot successfully: %s", message);
    } catch (Throwable e) {
      logger.severe(e, "Error loading snapshot");
      if (e instanceof IllegalArgumentException) {
        throw new BadRequestException("Error calling load snapshot: " + e.getMessage(), e);
      } else {
        throw new InternalServerErrorException(
            "Error loading snapshot: " + firstNonNull(e.getMessage(), e.toString()));
      }
    }
  }

  private String loadSnapshot(String snapshotId, String gcsFilename, Iterable<String> kinds)
      throws IOException {
    Bigquery bigquery = bigqueryFactory.create(projectId, SNAPSHOTS_DATASET);
    DateTime now = clock.nowUtc();
    String loadMessage =
        String.format("Loading datastore snapshot %s from %s...", snapshotId, gcsFilename);
    logger.info(loadMessage);
    StringBuilder builder = new StringBuilder(loadMessage + "\n");
    builder.append("Load jobs:\n");

    for (String kindName : kinds) {
      String jobId = String.format("load-snapshot-%s-%s-%d", snapshotId, kindName, now.getMillis());
      JobReference jobRef = new JobReference().setProjectId(projectId).setJobId(jobId);
      String sourceUri = getBackupInfoFileForKind(gcsFilename, kindName);
      String tableId = String.format("%s_%s", snapshotId, kindName);

      // Launch the load job.
      Job job = makeLoadJob(jobRef, sourceUri, tableId);
      bigquery.jobs().insert(projectId, job).execute();

      // Enqueue a task to check on the load job's completion, and if it succeeds, to update a
      // well-known view in BigQuery to point at the newly loaded snapshot table for this kind.
      bigqueryPollEnqueuer.enqueuePollTask(
          jobRef,
          createViewUpdateTask(SNAPSHOTS_DATASET, tableId, kindName),
          getQueue(UpdateSnapshotViewAction.QUEUE));

      builder.append(String.format(" - %s:%s\n", projectId, jobId));
      logger.infofmt("Submitted load job %s:%s", projectId, jobId);
    }
    return builder.toString();
  }

  private static String getBackupInfoFileForKind(String backupInfoFile, String kindName) {
    String extension = ".backup_info";
    checkArgument(backupInfoFile.endsWith(extension), "backup info file extension missing");
    String prefix = backupInfoFile.substring(0, backupInfoFile.length() - extension.length());
    return Joiner.on('.').join(prefix, kindName, extension.substring(1));
  }

  private Job makeLoadJob(JobReference jobRef, String sourceUri, String tableId) {
    TableReference tableReference = new TableReference()
        .setProjectId(jobRef.getProjectId())
        .setDatasetId(SNAPSHOTS_DATASET)
        .setTableId(tableId);
    return new Job()
        .setJobReference(jobRef)
        .setConfiguration(new JobConfiguration()
            .setLoad(new JobConfigurationLoad()
                .setWriteDisposition(WriteDisposition.WRITE_EMPTY.toString())
                .setSourceFormat(SourceFormat.DATASTORE_BACKUP.toString())
                .setSourceUris(ImmutableList.of(sourceUri))
                .setDestinationTable(tableReference)));
  }
}
