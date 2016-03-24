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

package com.google.domain.registry.export;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.html.HtmlEscapers.htmlEscaper;
import static com.google.domain.registry.util.HttpServletUtils.getRequiredParameterValue;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.DatasetReference;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobConfiguration;
import com.google.api.services.bigquery.model.JobConfigurationLoad;
import com.google.api.services.bigquery.model.JobReference;
import com.google.api.services.bigquery.model.TableReference;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import com.google.domain.registry.bigquery.BigqueryFactory;
import com.google.domain.registry.bigquery.BigqueryJobFailureException;
import com.google.domain.registry.bigquery.BigqueryUtils.SourceFormat;
import com.google.domain.registry.bigquery.BigqueryUtils.WriteDisposition;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.export.BigqueryPollJobAction.BigqueryPollJobEnqueuer;
import com.google.domain.registry.util.Clock;
import com.google.domain.registry.util.FormattingLogger;
import com.google.domain.registry.util.NonFinalForTesting;
import com.google.domain.registry.util.Retrier;
import com.google.domain.registry.util.SystemClock;
import com.google.domain.registry.util.SystemSleeper;
import com.google.domain.registry.util.TaskEnqueuer;

import org.joda.time.DateTime;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Load a datastore snapshot from Google Cloud Storage into BigQuery. */
public class LoadSnapshotServlet extends HttpServlet {

  private static final RegistryEnvironment ENVIRONMENT = RegistryEnvironment.get();

  /** Parameter names for passing parameters into the servlet. */
  static final String SNAPSHOT_ID_PARAM = "id";
  static final String SNAPSHOT_FILE_PARAM = "file";
  static final String SNAPSHOT_KINDS_PARAM = "kinds";

  /** Servlet-specific details needed for enqueuing tasks against itself. */
  static final String QUEUE = "export-snapshot";  // See queue.xml.
  static final String PATH = "/_dr/task/loadSnapshot";  // See web.xml.

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @NonFinalForTesting
  private static Clock clock = new SystemClock();

  @NonFinalForTesting
  private static BigqueryFactory bigqueryFactory = new BigqueryFactory();

  @NonFinalForTesting
  private static BigqueryPollJobEnqueuer bigqueryPollEnqueuer = new BigqueryPollJobEnqueuer(
      new TaskEnqueuer(new Retrier(new SystemSleeper(), 5)));

  /** Enqueue a task for starting a backup load.  Not static for better testability. */
  public TaskHandle enqueueLoadTask(
      String snapshotId, String gcsFile, ImmutableSet<String> kinds) {
    return QueueFactory.getQueue(QUEUE).add(
        TaskOptions.Builder.withUrl(PATH)
            .method(Method.POST)
            .param(SNAPSHOT_ID_PARAM, snapshotId)
            .param(SNAPSHOT_FILE_PARAM, gcsFile)
            .param(SNAPSHOT_KINDS_PARAM, Joiner.on(',').join(kinds)));
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    try {
      String snapshotId = getRequiredParameterValue(req, SNAPSHOT_ID_PARAM);
      String gcsFilename = getRequiredParameterValue(req, SNAPSHOT_FILE_PARAM);
      ImmutableList<String> kinds = ImmutableList.copyOf(
          Splitter.on(',').splitToList(getRequiredParameterValue(req, SNAPSHOT_KINDS_PARAM)));

      String message = loadSnapshot(snapshotId, gcsFilename, kinds);

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

  private String loadSnapshot(String snapshotId, String gcsFilename, ImmutableList<String> kinds)
      throws IOException {
    Bigquery bigquery = bigqueryFactory.create(
        getClass().getSimpleName(),
        new UrlFetchTransport(),
        new JacksonFactory(),
        new AppIdentityCredential(BigqueryScopes.all()));
    String projectId = ENVIRONMENT.config().getProjectId();
    DateTime now = clock.nowUtc();

    ensureDataset(bigquery, projectId, ENVIRONMENT.config().getSnapshotsDataset());

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
          UpdateSnapshotViewServlet.createViewUpdateTask(
              ENVIRONMENT.config().getSnapshotsDataset(), tableId, kindName),
          QueueFactory.getQueue(UpdateSnapshotViewServlet.QUEUE));

      builder.append(String.format(" - %s:%s\n", projectId, jobId));
      logger.infofmt("Submitted load job %s:%s", projectId, jobId);
    }
    return builder.toString();
  }

  private static void ensureDataset(Bigquery bigquery, String projectId, String datasetId)
      throws IOException {
    try {
      bigquery.datasets()
          .insert(projectId,
              new Dataset().setDatasetReference(
                  new DatasetReference()
                      .setProjectId(projectId)
                      .setDatasetId(datasetId)))
          .execute();
    } catch (IOException e) {
      // Swallow errors about a duplicate dataset, and throw any other ones.
      if (!BigqueryJobFailureException.create(e).getReason().equals("duplicate")) {
        throw e;
      }
    }
  }

  private static String getBackupInfoFileForKind(String backupInfoFile, String kindName) {
    String extension = ".backup_info";
    checkArgument(backupInfoFile.endsWith(extension), "backup info file extension missing");
    String prefix = backupInfoFile.substring(0, backupInfoFile.length() - extension.length());
    return Joiner.on('.').join(prefix, kindName, extension.substring(1));
  }

  private static Job makeLoadJob(JobReference jobRef, String sourceUri, String tableId) {
    TableReference tableReference = new TableReference()
        .setProjectId(jobRef.getProjectId())
        .setDatasetId(ENVIRONMENT.config().getSnapshotsDataset())
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
