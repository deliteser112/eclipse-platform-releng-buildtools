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

import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.joda.time.DateTimeZone.UTC;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobConfigurationLoad;
import com.google.api.services.bigquery.model.JobReference;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.domain.registry.bigquery.BigqueryFactory;
import com.google.domain.registry.config.TestRegistryConfig;
import com.google.domain.registry.export.BigqueryPollJobAction.BigqueryPollJobEnqueuer;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.InjectRule;
import com.google.domain.registry.testing.RegistryConfigRule;
import com.google.domain.registry.testing.TaskQueueHelper.TaskMatcher;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Unit tests for {@link LoadSnapshotServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class LoadSnapshotServletTest {

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withTaskQueue()
      .build();

  @Rule
  public final RegistryConfigRule configRule = new RegistryConfigRule(new TestRegistryConfig() {
    @Override public String getProjectId() {
      return "Project-Id";
    }

    @Override public String getSnapshotsDataset() {
      return "testdataset";
    }
  });

  @Mock
  private HttpServletRequest req;

  @Mock
  private HttpServletResponse rsp;

  @Mock
  private BigqueryFactory bigqueryFactory;

  @Mock
  private Bigquery bigquery;

  @Mock
  private Bigquery.Jobs bigqueryJobs;

  @Mock
  private Bigquery.Jobs.Insert bigqueryJobsInsert;

  @Mock
  private Bigquery.Datasets bigqueryDatasets;

  @Mock
  private Bigquery.Datasets.Insert bigqueryDatasetsInsert;

  @Mock
  private BigqueryPollJobEnqueuer bigqueryPollEnqueuer;

  private static final DateTime NOW = new DateTime(1391096117045L, UTC);

  private FakeClock clock = new FakeClock();
  private final StringWriter httpOutput = new StringWriter();
  private final LoadSnapshotServlet servlet = new LoadSnapshotServlet();

  @Before
  public void before() throws Exception {
    clock.setTo(NOW);
    inject.setStaticField(LoadSnapshotServlet.class, "clock", clock);
    inject.setStaticField(LoadSnapshotServlet.class, "bigqueryFactory", bigqueryFactory);
    inject.setStaticField(LoadSnapshotServlet.class, "bigqueryPollEnqueuer", bigqueryPollEnqueuer);

    when(rsp.getWriter()).thenReturn(new PrintWriter(httpOutput));

    when(bigqueryFactory.create(
        anyString(),
        any(HttpTransport.class),
        any(JsonFactory.class),
        any(HttpRequestInitializer.class)))
            .thenReturn(bigquery);

    when(bigquery.jobs()).thenReturn(bigqueryJobs);
    when(bigqueryJobs.insert(eq("Project-Id"), any(Job.class))).thenReturn(bigqueryJobsInsert);

    when(bigquery.datasets()).thenReturn(bigqueryDatasets);
    when(bigqueryDatasets.insert(eq("Project-Id"), any(Dataset.class)))
        .thenReturn(bigqueryDatasetsInsert);

    servlet.init(mock(ServletConfig.class));
    when(req.getMethod()).thenReturn("POST");
  }

  @Test
  public void testSuccess_enqueueLoadTask() throws Exception {
    servlet.enqueueLoadTask(
        "id12345", "gs://bucket/snapshot.backup_info", ImmutableSet.of("one", "two", "three"));
    assertTasksEnqueued(LoadSnapshotServlet.QUEUE,
        new TaskMatcher()
            .url(LoadSnapshotServlet.PATH)
            .method("POST")
            .param(LoadSnapshotServlet.SNAPSHOT_ID_PARAM, "id12345")
            .param(LoadSnapshotServlet.SNAPSHOT_FILE_PARAM, "gs://bucket/snapshot.backup_info")
            .param(LoadSnapshotServlet.SNAPSHOT_KINDS_PARAM, "one,two,three"));
  }

  @Test
  public void testSuccess_doPost() throws Exception {
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_ID_PARAM)).thenReturn("id12345");
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_FILE_PARAM))
        .thenReturn("gs://bucket/snapshot.backup_info");
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_KINDS_PARAM))
        .thenReturn("one,two,three");

    servlet.service(req, rsp);

    // Check that we attempted to create the snapshots dataset.
    ArgumentCaptor<Dataset> datasetArgument = ArgumentCaptor.forClass(Dataset.class);
    verify(bigqueryDatasets).insert(eq("Project-Id"), datasetArgument.capture());
    assertThat(datasetArgument.getValue().getDatasetReference().getProjectId())
        .isEqualTo("Project-Id");
    assertThat(datasetArgument.getValue().getDatasetReference().getDatasetId())
        .isEqualTo("testdataset");
    verify(bigqueryDatasetsInsert).execute();

    // Capture the load jobs we inserted to do additional checking on them.
    ArgumentCaptor<Job> jobArgument = ArgumentCaptor.forClass(Job.class);
    verify(bigqueryJobs, times(3)).insert(eq("Project-Id"), jobArgument.capture());
    List<Job> jobs = jobArgument.getAllValues();
    assertThat(jobs).hasSize(3);

    // Check properties that should be common to all load jobs.
    for (Job job : jobs) {
      assertThat(job.getJobReference().getProjectId()).isEqualTo("Project-Id");
      JobConfigurationLoad config = job.getConfiguration().getLoad();
      assertThat(config.getSourceFormat()).isEqualTo("DATASTORE_BACKUP");
      assertThat(config.getDestinationTable().getProjectId()).isEqualTo("Project-Id");
      assertThat(config.getDestinationTable().getDatasetId()).isEqualTo("testdataset");
    }

    // Check the job IDs for each load job.
    assertThat(transform(jobs, new Function<Job, String>() {
        @Override
        public String apply(Job job) {
          return job.getJobReference().getJobId();
        }})).containsExactly(
            "load-snapshot-id12345-one-1391096117045",
            "load-snapshot-id12345-two-1391096117045",
            "load-snapshot-id12345-three-1391096117045");

    // Check the source URI for each load job.
    assertThat(transform(jobs, new Function<Job, String>() {
        @Override
        public String apply(Job job) {
          return Iterables.getOnlyElement(job.getConfiguration().getLoad().getSourceUris());
        }})).containsExactly(
            "gs://bucket/snapshot.one.backup_info",
            "gs://bucket/snapshot.two.backup_info",
            "gs://bucket/snapshot.three.backup_info");

    // Check the destination table ID for each load job.
    assertThat(transform(jobs, new Function<Job, String>() {
        @Override
        public String apply(Job job) {
          return job.getConfiguration().getLoad().getDestinationTable().getTableId();
        }})).containsExactly("id12345_one", "id12345_two", "id12345_three");

    // Check that we executed the inserted jobs.
    verify(bigqueryJobsInsert, times(3)).execute();

    // Check that the poll tasks for each load job were enqueued.
    verify(bigqueryPollEnqueuer).enqueuePollTask(
        new JobReference()
            .setProjectId("Project-Id")
            .setJobId("load-snapshot-id12345-one-1391096117045"),
        UpdateSnapshotViewAction.createViewUpdateTask("testdataset", "id12345_one", "one"),
        QueueFactory.getQueue(UpdateSnapshotViewAction.QUEUE));
    verify(bigqueryPollEnqueuer).enqueuePollTask(
        new JobReference()
            .setProjectId("Project-Id")
            .setJobId("load-snapshot-id12345-two-1391096117045"),
        UpdateSnapshotViewAction.createViewUpdateTask("testdataset", "id12345_two", "two"),
        QueueFactory.getQueue(UpdateSnapshotViewAction.QUEUE));
    verify(bigqueryPollEnqueuer).enqueuePollTask(
        new JobReference()
            .setProjectId("Project-Id")
            .setJobId("load-snapshot-id12345-three-1391096117045"),
        UpdateSnapshotViewAction.createViewUpdateTask("testdataset", "id12345_three", "three"),
        QueueFactory.getQueue(UpdateSnapshotViewAction.QUEUE));

    verify(rsp).setStatus(SC_OK);
  }

  @Test
  public void testFailure_doPost_missingIdHeader() throws Exception {
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_FILE_PARAM))
        .thenReturn("gs://bucket/snapshot.backup_info");
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_KINDS_PARAM))
        .thenReturn("one,two,three");

    servlet.service(req, rsp);
    verify(rsp).sendError(SC_BAD_REQUEST, "Missing required parameter: id");
  }

  @Test
  public void testFailure_doPost_missingFileHeader() throws Exception {
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_ID_PARAM)).thenReturn("id12345");
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_KINDS_PARAM))
        .thenReturn("one,two,three");

    servlet.service(req, rsp);
    verify(rsp).sendError(SC_BAD_REQUEST, "Missing required parameter: file");
  }

  @Test
  public void testFailure_doPost_missingKindHeader() throws Exception {
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_ID_PARAM)).thenReturn("id12345");
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_FILE_PARAM))
        .thenReturn("gs://bucket/snapshot.backup_info");

    servlet.service(req, rsp);
    verify(rsp).sendError(SC_BAD_REQUEST, "Missing required parameter: kinds");
  }

  @Test
  public void testFailure_doPost_badGcsFilename() throws Exception {
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_ID_PARAM)).thenReturn("id12345");
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_FILE_PARAM))
        .thenReturn("gs://bucket/snapshot");
    when(req.getParameter(LoadSnapshotServlet.SNAPSHOT_KINDS_PARAM))
        .thenReturn("one,two,three");

    servlet.service(req, rsp);
    verify(rsp).sendError(SC_BAD_REQUEST, "backup info file extension missing");
  }
}
