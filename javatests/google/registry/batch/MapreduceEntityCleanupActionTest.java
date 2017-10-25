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

package google.registry.batch;

import static com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.truth.Truth.assertThat;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.tools.mapreduce.MapReduceJob;
import com.google.appengine.tools.mapreduce.MapReduceSettings;
import com.google.appengine.tools.mapreduce.MapReduceSpecification;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.google.appengine.tools.mapreduce.impl.shardedjob.IncrementalTaskState;
import com.google.appengine.tools.mapreduce.impl.shardedjob.ShardRetryState;
import com.google.appengine.tools.mapreduce.inputs.InMemoryInput;
import com.google.appengine.tools.mapreduce.outputs.InMemoryOutput;
import com.google.appengine.tools.pipeline.JobSetting;
import com.google.appengine.tools.pipeline.PipelineService;
import com.google.appengine.tools.pipeline.PipelineServiceFactory;
import com.google.appengine.tools.pipeline.impl.model.Barrier;
import com.google.appengine.tools.pipeline.impl.model.FanoutTaskRecord;
import com.google.appengine.tools.pipeline.impl.model.JobInstanceRecord;
import com.google.appengine.tools.pipeline.impl.model.JobRecord;
import com.google.appengine.tools.pipeline.impl.model.ShardedValue;
import com.google.appengine.tools.pipeline.impl.model.Slot;
import com.google.common.collect.ImmutableList;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.mapreduce.MapreduceTestCase;
import java.util.List;
import java.util.Optional;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MapreduceEntityCleanupActionTest
    extends MapreduceTestCase<MapreduceEntityCleanupAction> {

  @Rule public final ExceptionRule thrown = new ExceptionRule();

  private static final DatastoreService datastore = getDatastoreService();
  private static final FetchOptions FETCH_OPTIONS = FetchOptions.Builder.withChunkSize(200);
  private static final String QUEUE_NAME = "mapreduce";

  private final TestMapreduceEntityCleanupUtil mapreduceEntityCleanupUtil =
      new TestMapreduceEntityCleanupUtil();
  private final FakeClock clock = new FakeClock(DateTime.now(UTC));
  private final FakeResponse response = new FakeResponse();

  private static final ImmutableList<List<String>> inputStrings = ImmutableList.of(
      ImmutableList.of("a", "b", "c"),
      ImmutableList.of("d", "e", "f", "g", "h"),
      ImmutableList.of("i", "j", "k"),
      ImmutableList.of("l"),
      ImmutableList.of("m", "n"));

  private static final InMemoryInput<String> input = new InMemoryInput<>(inputStrings);

  private static class TestMapper extends Mapper<String, String, String> {
    private static final long serialVersionUID = 8472979502634351364L;

    @Override
    public void map(String s) {
      emit(s, s);
    }
  }

  private static class TestReducer extends Reducer<String, String, String> {
    private static final long serialVersionUID = 5344368517893904668L;

    @Override
    public void reduce(final String key, ReducerInput<String> values) {
      while (values.hasNext()) {
        emit(values.next());
      }
    }
  }

  private static String createMapreduce(String jobName) throws Exception {
    MapReduceJob<String, String, String, String, List<List<String>>> mapReduceJob =
        new MapReduceJob<>(
            new MapReduceSpecification.Builder<String, String, String, String, List<List<String>>>()
                .setJobName(jobName)
                .setInput(input)
                .setMapper(new TestMapper())
                .setReducer(new TestReducer())
                .setOutput(new InMemoryOutput<String>())
                .setNumReducers(2)
                .build(),
            new MapReduceSettings.Builder().setWorkerQueueName(QUEUE_NAME).build());
    PipelineService pipelineService = PipelineServiceFactory.newPipelineService();
    return pipelineService.startNewPipeline(mapReduceJob, new JobSetting.OnQueue(QUEUE_NAME));
  }

  private void setAnyJobAndDaysOld(int daysOld) {
    setJobIdJobNameAndDaysOld(Optional.empty(), Optional.empty(), Optional.of(daysOld));
  }

  private void setJobId(String jobId) {
    setJobIdJobNameAndDaysOld(Optional.of(jobId), Optional.empty(), Optional.empty());
  }

  private void setJobName(String jobName) {
    setJobIdJobNameAndDaysOld(Optional.empty(), Optional.of(jobName), Optional.empty());
  }

  private void setJobNameAndDaysOld(String jobName, int daysOld) {
    setJobIdJobNameAndDaysOld(Optional.empty(), Optional.of(jobName), Optional.of(daysOld));
  }

  private void setJobIdJobNameAndDaysOld(
      Optional<String> jobId, Optional<String> jobName, Optional<Integer> daysOld) {
    clock.setTo(DateTime.now(UTC));
    action = new MapreduceEntityCleanupAction(
        jobId,
        jobName,
        Optional.empty(), // numJobsToDelete
        daysOld,
        Optional.empty(), // force
        mapreduceEntityCleanupUtil,
        clock,
        DatastoreServiceFactory.getDatastoreService(),
        response);
  }

  /**
   * Get the keys of a particular kind.
   *
   * <p>We really just care about the count of keys, but the exception messages are much clearer if
   * we print out the complete list, rather than just the count.
   */
  private static List<Entity> getKeys(String kind) {
    return datastore.prepare(new Query(kind).setKeysOnly()).asList(FETCH_OPTIONS);
  }

  private static void assertNumMapreducesAndShardedJobs(
      int numberOfMapreduces, int numberOfShardedJobEntities) {
    assertThat(getKeys(JobRecord.DATA_STORE_KIND)).hasSize(14 * numberOfMapreduces);
    assertThat(getKeys(Slot.DATA_STORE_KIND)).hasSize(32 * numberOfMapreduces);
    assertThat(getKeys(ShardedValue.DATA_STORE_KIND)).isEmpty();
    assertThat(getKeys(Barrier.DATA_STORE_KIND)).hasSize(28 * numberOfMapreduces);
    assertThat(getKeys(JobInstanceRecord.DATA_STORE_KIND)).hasSize(14 * numberOfMapreduces);
    assertThat(getKeys(FanoutTaskRecord.DATA_STORE_KIND)).hasSize(18 * numberOfMapreduces);
    assertThat(getKeys("MR-ShardedJob")).hasSize(numberOfShardedJobEntities);
    assertThat(getKeys("MR-ShardedValue")).isEmpty();
    assertThat(getKeys(ShardRetryState.Serializer.makeKey("dummy").getKind())).isEmpty();
    assertThat(getKeys(IncrementalTaskState.Serializer.makeKey("dummy").getKind()))
        .hasSize(numberOfMapreduces * (2 + 2 * inputStrings.size()));
  }

  @Test
  public void testCleanup_succeeds() throws Exception {

    // Create and run the mapreduce.
    String jobId = createMapreduce("jobname");
    executeTasksUntilEmpty(QUEUE_NAME, clock);

    // The desired entities should be present.
    assertNumMapreducesAndShardedJobs(1, 3);

    // Now run the cleanup action.
    setJobId(jobId);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo(
        jobId
        + ": deletion requested\n"
        + "successfully requested async deletion of 1 job(s); errors received on 0\n");

    // The entities should still be there, because everything executes asynchronously, and in fact
    // there should be more pipeline entities, because the deletion tasks are there as well.
    // However, the MR-* sharded entities are gone for some reason.
    assertThat(getKeys(JobRecord.DATA_STORE_KIND)).hasSize(17);
    assertThat(getKeys(Slot.DATA_STORE_KIND)).hasSize(38);
    assertThat(getKeys(ShardedValue.DATA_STORE_KIND)).isEmpty();
    assertThat(getKeys(Barrier.DATA_STORE_KIND)).hasSize(34);
    assertThat(getKeys(JobInstanceRecord.DATA_STORE_KIND)).hasSize(17);
    assertThat(getKeys(FanoutTaskRecord.DATA_STORE_KIND)).hasSize(21);
    assertThat(getKeys("MR-ShardedJob")).isEmpty();
    assertThat(getKeys("MR-ShardedValue")).isEmpty();
    assertThat(getKeys(ShardRetryState.Serializer.makeKey("dummy").getKind())).isEmpty();
    assertThat(getKeys(IncrementalTaskState.Serializer.makeKey("dummy").getKind()))
        .hasSize(2 + 2 * inputStrings.size());

    // Run the async deletion.
    executeTasksUntilEmpty(QUEUE_NAME, clock);

    // Everything should be gone.
    assertNumMapreducesAndShardedJobs(0, 0);
  }

  @Test
  public void testNonexistentJobName_fails() throws Exception {
    setJobName("nonexistent");

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("No eligible jobs found");
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(1);
  }

  @Test
  public void testJobNameOfRecentJob_fails() throws Exception {
    createMapreduce("jobname");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    setJobName("jobname");

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(1);
    assertThat(response.getPayload()).isEqualTo("No eligible jobs found");
  }

  @Test
  public void testJobNameOfRecentJob_succeedsWithDaysOldParameter() throws Exception {
    createMapreduce("jobname");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    setJobNameAndDaysOld("jobname", 0);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("A total of 1 job(s) processed");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(0, 0);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(1);
  }

  @Test
  public void testAnyJob_fails() throws Exception {
    createMapreduce("jobname");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    setJobIdJobNameAndDaysOld(
        Optional.<String>empty(), Optional.<String>empty(), Optional.<Integer>empty());

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("No eligible jobs found");
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(1);
  }

  @Test
  public void testAnyJob_succeedsWithDaysOldParameter() throws Exception {
    createMapreduce("jobname");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    setJobNameAndDaysOld("jobname", 0);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("A total of 1 job(s) processed");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(0, 0);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(1);
  }

  @Test
  public void testNonexistentJobId_succeeds() throws Exception {
    setJobId("nonexistent");

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo(
        "nonexistent: deletion requested\n"
            + "successfully requested async deletion of 1 job(s); errors received on 0\n");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(0, 0);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(0);
  }

  @Test
  public void testDeleteTwoJobs_succeedsWithDaysOldParameter() throws Exception {
    createMapreduce("jobname1");
    createMapreduce("jobname2");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    setAnyJobAndDaysOld(0);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("A total of 2 job(s) processed");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(0, 0);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(1);
  }

  @Test
  public void testDeleteTwoJobsInTwoBatches_succeeds() throws Exception {
    createMapreduce("jobname1");
    createMapreduce("jobname2");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    setAnyJobAndDaysOld(0);
    mapreduceEntityCleanupUtil.setMaxNumberOfJobsPerSearch(1);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("A total of 2 job(s) processed");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(0, 0);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(2);
  }

  @Test
  public void testDeleteOneOfTwoJobs_succeedsWithDaysOldParameter() throws Exception {
    createMapreduce("jobname1");
    createMapreduce("jobname2");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    clock.setTo(DateTime.now(UTC));
    action = new MapreduceEntityCleanupAction(
        Optional.<String>empty(), // jobId
        Optional.<String>empty(), // jobName
        Optional.<Integer>of(1), // numJobsToDelete
        Optional.<Integer>of(0), // daysOld
        Optional.<Boolean>empty(), // force
        mapreduceEntityCleanupUtil,
        clock,
        DatastoreServiceFactory.getDatastoreService(),
        response);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).endsWith(
        ": deletion requested\n"
        + "successfully requested async deletion of 1 job(s); errors received on 0\n"
        + "A total of 1 job(s) processed");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(1, 3);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(1);
  }

  @Test
  public void testDeleteOneOfTwoJobsByJobName_succeedsWithDaysOldParameter() throws Exception {
    createMapreduce("jobname1");
    createMapreduce("jobname2");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    setJobNameAndDaysOld("jobname1", 0);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("A total of 1 job(s) processed");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(1, 3);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(1);
  }

  @Test
  public void testDeleteOneOfTwoJobsByJobId_succeeds() throws Exception {
    String jobId1 = createMapreduce("jobname1");
    createMapreduce("jobname2");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    setJobId(jobId1);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).endsWith(
        ": deletion requested\n"
        + "successfully requested async deletion of 1 job(s); errors received on 0\n");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(1, 3);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(0);
  }

  @Test
  public void testDeleteTwoJobsByJobId_succeeds() throws Exception {
    String jobId1 = createMapreduce("jobname1");
    String jobId2 = createMapreduce("jobname2");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    setJobId(jobId1);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo(
        jobId1
        + ": deletion requested\n"
        + "successfully requested async deletion of 1 job(s); errors received on 0\n");

    FakeResponse response2 = new FakeResponse();
    clock.setTo(DateTime.now(UTC));
    action = new MapreduceEntityCleanupAction(
        Optional.of(jobId2), // jobId
        Optional.<String>empty(), // jobName
        Optional.<Integer>empty(), // numJobsToDelete
        Optional.<Integer>empty(), // daysOld
        Optional.<Boolean>empty(), // force
        mapreduceEntityCleanupUtil,
        clock,
        DatastoreServiceFactory.getDatastoreService(),
        response2);

    action.run();

    assertThat(response2.getStatus()).isEqualTo(SC_OK);
    assertThat(response2.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response2.getPayload()).isEqualTo(
        jobId2
        + ": deletion requested\n"
        + "successfully requested async deletion of 1 job(s); errors received on 0\n");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(0, 0);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(0);
  }

  @Test
  public void testDeleteOfRunningJob_fails() throws Exception {
    String jobId = createMapreduce("jobname");
    executeTasks(QUEUE_NAME, clock, Optional.<Integer>of(10));
    setJobId(jobId);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).endsWith(
        ": Job is not in FINALIZED or STOPPED state\n"
        + "successfully requested async deletion of 0 job(s); errors received on 1\n");
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(0);
  }

  @Test
  public void testDeleteOfRunningJob_succeedsWithForce() throws Exception {
    String jobId = createMapreduce("jobname");
    executeTasks(QUEUE_NAME, clock, Optional.<Integer>of(10));
    clock.setTo(DateTime.now(UTC));
    action = new MapreduceEntityCleanupAction(
        Optional.of(jobId),
        Optional.<String>empty(), // jobName
        Optional.<Integer>empty(), // numJobsToDelete
        Optional.<Integer>empty(), // daysOld
        Optional.of(true), // force
        mapreduceEntityCleanupUtil,
        clock,
        DatastoreServiceFactory.getDatastoreService(),
        response);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).endsWith(
        ": deletion requested\n"
        + "successfully requested async deletion of 1 job(s); errors received on 0\n");
    executeTasksUntilEmpty(QUEUE_NAME, clock);
    assertNumMapreducesAndShardedJobs(0, 0);
    assertThat(mapreduceEntityCleanupUtil.getNumSearchesPerformed()).isEqualTo(0);
  }

  @Test
  public void testJobIdAndJobName_fails() throws Exception {
    setJobIdJobNameAndDaysOld(
        Optional.of("jobid"), Optional.of("jobname"), Optional.<Integer>empty());

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("Do not specify both a job ID and a job name");
    assertNumMapreducesAndShardedJobs(0, 0);
  }

  @Test
  public void testJobIdAndDaysOld_fails() throws Exception {
    setJobIdJobNameAndDaysOld(Optional.of("jobid"), Optional.<String>empty(), Optional.of(0));

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload())
        .isEqualTo("Do not specify both a job ID and a days old threshold");
    assertNumMapreducesAndShardedJobs(0, 0);
  }

  @Test
  public void testJobIdAndNumJobs_fails() throws Exception {
    action = new MapreduceEntityCleanupAction(
        Optional.of("jobid"),
        Optional.<String>empty(), // jobName
        Optional.of(1), // numJobsToDelete
        Optional.<Integer>empty(), // daysOld
        Optional.<Boolean>empty(), // force
        mapreduceEntityCleanupUtil,
        clock,
        DatastoreServiceFactory.getDatastoreService(),
        response);

    action.run();

    assertThat(response.getStatus()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload())
        .isEqualTo("Do not specify both a job ID and a number of jobs to delete");
    assertNumMapreducesAndShardedJobs(0, 0);
  }

  @Test
  public void testDeleteZeroJobs_throwsUsageError() throws Exception {
    new MapreduceEntityCleanupAction(
            Optional.<String>empty(), // jobId
            Optional.<String>empty(), // jobName
            Optional.<Integer>of(0), // numJobsToDelete
            Optional.<Integer>empty(), // daysOld
            Optional.<Boolean>empty(), // force
            mapreduceEntityCleanupUtil,
            clock,
            DatastoreServiceFactory.getDatastoreService(),
            response)
        .run();
    assertThat(response.getStatus()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.getPayload())
        .isEqualTo("Do not specify a non-positive integer for the number of jobs to delete");
  }

}
