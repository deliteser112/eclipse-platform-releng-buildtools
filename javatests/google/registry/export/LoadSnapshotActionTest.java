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

package google.registry.export;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.export.LoadSnapshotAction.LATEST_SNAPSHOT_VIEW_NAME;
import static google.registry.export.LoadSnapshotAction.LOAD_SNAPSHOT_FILE_PARAM;
import static google.registry.export.LoadSnapshotAction.LOAD_SNAPSHOT_ID_PARAM;
import static google.registry.export.LoadSnapshotAction.LOAD_SNAPSHOT_KINDS_PARAM;
import static google.registry.export.LoadSnapshotAction.PATH;
import static google.registry.export.LoadSnapshotAction.QUEUE;
import static google.registry.export.LoadSnapshotAction.enqueueLoadSnapshotTask;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static org.joda.time.DateTimeZone.UTC;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobConfigurationLoad;
import com.google.api.services.bigquery.model.JobReference;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import google.registry.bigquery.CheckedBigquery;
import google.registry.export.BigqueryPollJobAction.BigqueryPollJobEnqueuer;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import java.io.IOException;
import java.util.List;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link LoadSnapshotAction}. */
@RunWith(JUnit4.class)
public class LoadSnapshotActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withTaskQueue()
      .build();
  private final CheckedBigquery checkedBigquery = mock(CheckedBigquery.class);
  private final Bigquery bigquery = mock(Bigquery.class);
  private final Bigquery.Jobs bigqueryJobs = mock(Bigquery.Jobs.class);
  private final Bigquery.Jobs.Insert bigqueryJobsInsert = mock(Bigquery.Jobs.Insert.class);
  private final Bigquery.Datasets bigqueryDatasets = mock(Bigquery.Datasets.class);
  private final Bigquery.Datasets.Insert bigqueryDatasetsInsert =
      mock(Bigquery.Datasets.Insert.class);
  private final BigqueryPollJobEnqueuer bigqueryPollEnqueuer = mock(BigqueryPollJobEnqueuer.class);

  private FakeClock clock = new FakeClock(new DateTime(1391096117045L, UTC));
  private LoadSnapshotAction action;

  @Before
  public void before() throws Exception {
    when(checkedBigquery.ensureDataSetExists("Project-Id", "snapshots")).thenReturn(bigquery);
    when(bigquery.jobs()).thenReturn(bigqueryJobs);
    when(bigqueryJobs.insert(eq("Project-Id"), any(Job.class))).thenReturn(bigqueryJobsInsert);
    when(bigquery.datasets()).thenReturn(bigqueryDatasets);
    when(bigqueryDatasets.insert(eq("Project-Id"), any(Dataset.class)))
        .thenReturn(bigqueryDatasetsInsert);
    action = new LoadSnapshotAction();
    action.checkedBigquery = checkedBigquery;
    action.bigqueryPollEnqueuer = bigqueryPollEnqueuer;
    action.clock = clock;
    action.projectId = "Project-Id";
    action.snapshotFile = "gs://bucket/snapshot.backup_info";
    action.snapshotId = "id12345";
    action.snapshotKinds = "one,two,three";
  }

  @Test
  public void testSuccess_enqueueLoadTask() {
    enqueueLoadSnapshotTask(
        "id12345", "gs://bucket/snapshot.backup_info", ImmutableSet.of("one", "two", "three"));
    assertTasksEnqueued(
        QUEUE,
        new TaskMatcher()
            .url(PATH)
            .method("POST")
            .param(LOAD_SNAPSHOT_ID_PARAM, "id12345")
            .param(LOAD_SNAPSHOT_FILE_PARAM, "gs://bucket/snapshot.backup_info")
            .param(LOAD_SNAPSHOT_KINDS_PARAM, "one,two,three"));
  }

  @Test
  public void testSuccess_doPost() throws Exception {
    action.run();

    // Verify that checkedBigquery was called in a way that would create the dataset if it didn't
    // already exist.
    verify(checkedBigquery).ensureDataSetExists("Project-Id", "snapshots");

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
      assertThat(config.getDestinationTable().getDatasetId()).isEqualTo("snapshots");
    }

    // Check the job IDs for each load job.
    assertThat(transform(jobs, job -> job.getJobReference().getJobId()))
        .containsExactly(
            "load-snapshot-id12345-one-1391096117045",
            "load-snapshot-id12345-two-1391096117045",
            "load-snapshot-id12345-three-1391096117045");

    // Check the source URI for each load job.
    assertThat(
            transform(
                jobs,
                job -> Iterables.getOnlyElement(job.getConfiguration().getLoad().getSourceUris())))
        .containsExactly(
            "gs://bucket/snapshot.one.backup_info",
            "gs://bucket/snapshot.two.backup_info",
            "gs://bucket/snapshot.three.backup_info");

    // Check the destination table ID for each load job.
    assertThat(
            transform(
                jobs, job -> job.getConfiguration().getLoad().getDestinationTable().getTableId()))
        .containsExactly("id12345_one", "id12345_two", "id12345_three");

    // Check that we executed the inserted jobs.
    verify(bigqueryJobsInsert, times(3)).execute();

    // Check that the poll tasks for each load job were enqueued.
    verify(bigqueryPollEnqueuer)
        .enqueuePollTask(
            new JobReference()
                .setProjectId("Project-Id")
                .setJobId("load-snapshot-id12345-one-1391096117045"),
            UpdateSnapshotViewAction.createViewUpdateTask(
                "snapshots", "id12345_one", "one", LATEST_SNAPSHOT_VIEW_NAME),
            QueueFactory.getQueue(UpdateSnapshotViewAction.QUEUE));
    verify(bigqueryPollEnqueuer)
        .enqueuePollTask(
            new JobReference()
                .setProjectId("Project-Id")
                .setJobId("load-snapshot-id12345-two-1391096117045"),
            UpdateSnapshotViewAction.createViewUpdateTask(
                "snapshots", "id12345_two", "two", LATEST_SNAPSHOT_VIEW_NAME),
            QueueFactory.getQueue(UpdateSnapshotViewAction.QUEUE));
    verify(bigqueryPollEnqueuer)
        .enqueuePollTask(
            new JobReference()
                .setProjectId("Project-Id")
                .setJobId("load-snapshot-id12345-three-1391096117045"),
            UpdateSnapshotViewAction.createViewUpdateTask(
                "snapshots", "id12345_three", "three", LATEST_SNAPSHOT_VIEW_NAME),
            QueueFactory.getQueue(UpdateSnapshotViewAction.QUEUE));
  }

  @Test
  public void testFailure_doPost_badGcsFilename() {
    action.snapshotFile = "gs://bucket/snapshot";
    BadRequestException thrown = assertThrows(BadRequestException.class, action::run);
    assertThat(thrown)
        .hasMessageThat()
        .contains("Error calling load snapshot: backup info file extension missing");
  }

  @Test
  public void testFailure_doPost_bigqueryThrowsException() throws Exception {
    when(bigqueryJobsInsert.execute()).thenThrow(new IOException("The Internet has gone missing"));
    InternalServerErrorException thrown =
        assertThrows(InternalServerErrorException.class, action::run);
    assertThat(thrown)
        .hasMessageThat()
        .contains("Error loading snapshot: The Internet has gone missing");
  }
}
