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

import com.google.appengine.api.datastore.BaseDatastoreService;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.tools.mapreduce.impl.shardedjob.ShardedJobServiceFactory;
import com.google.appengine.tools.pipeline.NoSuchObjectException;
import com.google.appengine.tools.pipeline.impl.PipelineManager;
import com.google.appengine.tools.pipeline.impl.model.JobRecord;
import com.google.appengine.tools.pipeline.util.Pair;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** Utilities used in mapreduce datastore entity cleanup. */
class MapreduceEntityCleanupUtil {

  /** Number of jobs to fetch at a time using PipelineManager.queryRootPipelines. */
  protected static final int MAX_NUMBER_OF_JOBS_PER_SEARCH = 100;

  private static final ImmutableSet<String> JOB_PREFIXES =
      ImmutableSet.of("", "map-", "sort-", "merge-", "reduce-");

  @Inject
  MapreduceEntityCleanupUtil() {}

  /** Return value from {@link #findEligibleJobsByJobName} */
  @AutoValue
  abstract static class EligibleJobResults {
    static EligibleJobResults create(ImmutableSet<String> jobs, Optional<String> cursor) {
      return new AutoValue_MapreduceEntityCleanupUtil_EligibleJobResults(jobs, cursor);
    }

    abstract ImmutableSet<String> eligibleJobs();
    abstract Optional<String> cursor();
  }

  /**
   * Returns the maximum number of jobs to return per search request.
   *
   * <p>This method is present to allow overriding by test subclasses.
   */
  protected int getMaxNumberOfJobsPerSearch() {
    return MAX_NUMBER_OF_JOBS_PER_SEARCH;
  }

  /**
   * Finds the requested number of root pipeline jobs eligible for deletion.
   *
   * <p>Loops through the root jobs returned by the pipeline API, searching for those with a
   * matching name in an appropriate state, and older than the specified cutoff date.
   *
   * <p>Regardless of the setting of maxJobs, a maximum of {@link
   * #MAX_NUMBER_OF_JOBS_PER_SEARCH} will be returned. If there might be more jobs available to
   * find, a cursor will be returned, which can be used in a subsequent call to {@link
   * #findEligibleJobsByJobName} to continue the search.
   *
   * @param jobName the desired job name; if null, all jobs are considered to match
   * @param cutoffDate eligible jobs must have both startTime and endTime before cutoffDate; if
   *     startTime and/or endTime are null, they are considered to be old enough -- this is because
   *     many jobs do lack at least one of these, and we don't want such jobs to stick around
   *     forever and not get deleted
   * @param maxJobs the maximum number of jobs to return; if absent, return all eligible jobs (see
   *     note above about {@link #MAX_NUMBER_OF_JOBS_PER_SEARCH})
   * @param ignoreState if true, jobs will be included regardless of the state
   * @param cursor if present, a cursor returned from a previous call to the method; the search will
   *     be picked up where it left off
   * @return job IDs of the eligible jobs
   */
  EligibleJobResults findEligibleJobsByJobName(
      @Nullable String jobName,
      DateTime cutoffDate,
      Optional<Integer> maxJobs,
      boolean ignoreState,
      Optional<String> cursor) {
    if (maxJobs.isPresent() && (maxJobs.get() <= 0)) {
      return EligibleJobResults.create(ImmutableSet.of(), Optional.empty());
    }
    Set<String> eligibleJobs = new HashSet<>();
    Pair<? extends Iterable<JobRecord>, String> pair =
        PipelineManager.queryRootPipelines(jobName, cursor.orElse(null), getMaxNumberOfJobsPerSearch());
    for (JobRecord jobRecord : pair.getFirst()) {
      if (((jobRecord.getStartTime() == null)
              || jobRecord.getStartTime().before(cutoffDate.toDate()))
          && ((jobRecord.getEndTime() == null)
              || jobRecord.getEndTime().before(cutoffDate.toDate()))
          && (ignoreState
              || (jobRecord.getState() == JobRecord.State.FINALIZED)
              || (jobRecord.getState() == JobRecord.State.STOPPED))) {
        eligibleJobs.add(jobRecord.getRootJobKey().getName());
        if (maxJobs.isPresent() && (eligibleJobs.size() >= maxJobs.get())) {
          return EligibleJobResults.create(
              ImmutableSet.copyOf(eligibleJobs), Optional.empty());
        }
      }
    }
    return EligibleJobResults.create(
        ImmutableSet.copyOf(eligibleJobs), Optional.ofNullable(pair.getSecond()));
  }

  /**
   * Requests asynchronous deletion of entities associated with the specified job ID.
   *
   * <p>The mapreduce API is used to delete the MR-* entities, and the pipeline API is used to
   * delete the main job records. No attempt is made to check whether the deletion succeeds, only
   * whether it appeared to be a valid deletion request up front.
   *
   * @param datastore The datastore service, which can be either synchronous or asynchronous, since
   *     the only interaction with the database is via prepared queries
   * @param jobId the root pipeline job ID to be deleted; if the jobId does not exist, the deletion
   *     will be apparently successful, because the underlying library routines do not complain
   * @param force passed to the pipeline API, indicating whether jobs should be forcibly deleted
   *     even if they are not in a completed state; however, there is no force flag on the mapreduce
   *     API call, meaning that running jobs cannot be deleted
   * @return an error string, or absent if no error was detected
   */
  Optional<String> deleteJobAsync(
      BaseDatastoreService datastore, String jobId, boolean force) {

    // Try to delete the MR-* entities. This is always done asynchronously. A return value of false
    // indicates that the job is in RUNNING state, and nothing has been done.
    // TODO(mountford) check the state of all sharded jobs before deleting any
    for (String mrShardedJobId : getPossibleIdsForPipelineJob(datastore, jobId)) {
      if (!ShardedJobServiceFactory.getShardedJobService().cleanupJob(mrShardedJobId)) {
        return Optional.of(String.format("Skipping; job %s is in running state", mrShardedJobId));
      }
    }

    // If we are successful (meaning, MR-* entity deletion has been kicked off asynchronously),
    // delete the pipeline-* entities as well.
    try {
      PipelineManager.deletePipelineRecords(jobId, force, true /* async */);
      return Optional.empty();
    } catch (NoSuchObjectException ex) {
      return Optional.of("No such pipeline job");
    } catch (IllegalStateException ex) {
      return Optional.of("Job is not in FINALIZED or STOPPED state");
    }
  }

  /**
   * Returns the possible MR-ShardedJob IDs associated with the specified pipeline job and any child
   * jobs.
   *
   * @param datastore The datastore service, which can be either synchronous or asynchronous, since
   *     the only interaction with the database is via prepared queries
   * @param jobId The pipeline job ID
   * @return the IDs of MR-ShardedJob entities that the Mapreduce library might have created,
   *     depending on which steps of the mapreduce were used
   */
  private ImmutableSet<String> getPossibleIdsForPipelineJob(
      BaseDatastoreService datastore, String jobId) {
    return getPossibleIdsForPipelineJobRecur(datastore, jobId, new HashSet<>());
  }

  /**
   * Called by getPossibleIdsForPipelineJob(), and by itself recursively.
   *
   * @param datastore The datastore service, which can be either synchronous or asynchronous, since
   *     the only interaction with the database is via prepared queries
   * @param jobId The pipeline job ID
   * @param handledJobIds The set of job IDs which have been handled so far; this is a sanity check
   *     to prevent an infinite loop if, for some crazy reason, the job dependency graph is cyclic
   * @return the IDs of MR-ShardedJob entities that the Mapreduce library might have created,
   *     depending on which steps of the mapreduce were used
   */
  private ImmutableSet<String> getPossibleIdsForPipelineJobRecur(
      BaseDatastoreService datastore, String jobId, Set<String> handledJobIds) {
    if (handledJobIds.contains(jobId)) {
      return ImmutableSet.of();
    }
    handledJobIds.add(jobId);

    JobRecord jobRecord;
    try {
      jobRecord = PipelineManager.getJob(jobId);
    } catch (NoSuchObjectException e) {
      return ImmutableSet.of();
    }

    ImmutableSet.Builder<String> idSetBuilder = new ImmutableSet.Builder<>();
    for (String jobPrefix : JOB_PREFIXES) {
      idSetBuilder.add("MR-ShardedJob", jobPrefix + jobId);
    }

    for (Key childKey : jobRecord.getChildKeys()) {
      idSetBuilder
          .addAll(getPossibleIdsForPipelineJobRecur(datastore, childKey.getName(), handledJobIds));
    }
    return idSetBuilder.build();
  }
}
