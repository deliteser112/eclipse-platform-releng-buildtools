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

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import google.registry.batch.MapreduceEntityCleanupUtil.EligibleJobResults;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.Clock;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * Action to delete entities associated with the App Engine Mapreduce library.
 *
 * <p>To delete a specific job, set the jobId parameter. To delete all jobs with a specific job name
 * which are older than the specified age, set the jobName parameter. Otherwise, all jobs older than
 * the specified age are deleted. Examples:
 *
 * <ul>
 * <li>jobId=12345: delete only the root pipeline job with ID 12345, and all descendant jobs
 * <li>jobName=Generate+Important+Files: delete all root pipeline jobs with the display name
 *     "Generate Important Files" (subject to the limits imposed by the daysOld and numJobsToDelete
 *     parameters), and all descendant jobs
 * <li>(neither specified): delete all jobs (subject to the limits imposed by the daysOld and
 *     numJobsToDelete parameters)
 * </ul>
 *
 * <p>More about display names: The pipeline library assigns each root pipeline job a "display
 * name". You can see the display name of each job using the pipeline Web interface, available at
 * /_ah/pipeline/list, where the display name column is confusingly labeled "Class Path". Usually,
 * the display name is set to a fixed value by the mapreduce code. For instance, when a pipeline job
 * is created by the {@link MapreduceRunner} class, the display name is set by the
 * {@link MapreduceRunner#setJobName} method. When formulating a URL to invoke {@link
 * MapreduceEntityCleanupAction}, the display name must of course be URL-encoded -- spaces are
 * replaced by the plus sign, and so forth. For more information, see <a
 * href="https://en.wikipedia.org/wiki/Percent-encoding">the Wikipedia article on percent
 * encoding.</a>
 *
 * <p>The daysOld parameter specifies the minimum allowable age of a job in days for it to be
 * eligible for deletion. Jobs will not be deleted if they are newer than this threshold, unless
 * specifically named using the jobId parameter.
 *
 * <p>The numJobsToDelete parameter specifies the maximum number of jobs to delete. If this is fewer
 * than would ordinarily be deleted, the jobs to be deleted are chosen arbitrarily.
 *
 * <p>The force parameter, if present and true, indicates that jobs should be deleted even if they
 * are not in FINALIZED or STOPPED state.
 */

@Action(
  path = "/_dr/task/mapreduceEntityCleanup",
  auth = Auth.AUTH_INTERNAL_ONLY
)
public class MapreduceEntityCleanupAction implements Runnable {

  private static final int DEFAULT_DAYS_OLD = 180;
  private static final int DEFAULT_MAX_NUM_JOBS_TO_DELETE = 5;

  private static final String ERROR_BOTH_JOB_ID_AND_NAME =
      "Do not specify both a job ID and a job name";
  private static final String ERROR_BOTH_JOB_ID_AND_NUMBER_OF_JOBS =
      "Do not specify both a job ID and a number of jobs to delete";
  private static final String ERROR_BOTH_JOB_ID_AND_DAYS_OLD =
      "Do not specify both a job ID and a days old threshold";
  private static final String ERROR_NON_POSITIVE_JOBS_TO_DELETE =
      "Do not specify a non-positive integer for the number of jobs to delete";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Optional<String> jobId;
  private final Optional<String> jobName;
  private final Optional<Integer> numJobsToDelete;
  private final Optional<Integer> daysOld;
  private final Optional<Boolean> force;
  private final MapreduceEntityCleanupUtil mapreduceEntityCleanupUtil;
  private final Clock clock;
  private final DatastoreService datastore;
  private final Response response;

  @Inject
  MapreduceEntityCleanupAction(
      @Parameter("jobId") Optional<String> jobId,
      @Parameter("jobName") Optional<String> jobName,
      @Parameter("numJobsToDelete") Optional<Integer> numJobsToDelete,
      @Parameter("daysOld") Optional<Integer> daysOld,
      @Parameter("force") Optional<Boolean> force,
      MapreduceEntityCleanupUtil mapreduceEntityCleanupUtil,
      Clock clock,
      DatastoreService datastore,
      Response response) {
    this.jobId = jobId;
    this.jobName = jobName;
    this.numJobsToDelete = numJobsToDelete;
    this.daysOld = daysOld;
    this.force = force;
    this.mapreduceEntityCleanupUtil = mapreduceEntityCleanupUtil;
    this.clock = clock;
    this.datastore = datastore;
    this.response = response;
  }

  @Override
  public void run() {
    response.setContentType(PLAIN_TEXT_UTF_8);
    if (jobId.isPresent()) {
      runWithJobId();
    } else {
      runWithoutJobId();
    }
  }

  private void handleBadRequest(String message) {
    logger.atSevere().log(message);
    response.setPayload(message);
    response.setStatus(SC_BAD_REQUEST);
  }

  /** Delete the job with the specified job ID, checking for conflicting parameters. */
  private void runWithJobId() {
    if (jobName.isPresent()) {
      handleBadRequest(ERROR_BOTH_JOB_ID_AND_NAME);
      return;
    }
    if (numJobsToDelete.isPresent()) {
      handleBadRequest(ERROR_BOTH_JOB_ID_AND_NUMBER_OF_JOBS);
      return;
    }
    if (daysOld.isPresent()) {
      handleBadRequest(ERROR_BOTH_JOB_ID_AND_DAYS_OLD);
      return;
    }
    response.setPayload(requestDeletion(ImmutableSet.of(jobId.get()), true /* verbose */));
  }

  /**
   * Delete jobs with a matching display name, or all jobs if no name is specified. Only pick jobs
   * which are old enough.
   */
  private void runWithoutJobId() {
    if (numJobsToDelete.isPresent() && numJobsToDelete.get() <= 0) {
      handleBadRequest(ERROR_NON_POSITIVE_JOBS_TO_DELETE);
      return;
    }
    int defaultedDaysOld = daysOld.orElse(DEFAULT_DAYS_OLD);
    // Only generate the detailed response payload if there aren't too many jobs involved.
    boolean verbose =
        numJobsToDelete.isPresent() && (numJobsToDelete.get() <= DEFAULT_MAX_NUM_JOBS_TO_DELETE);
    StringBuilder payload = new StringBuilder();

    // Since findEligibleJobsByJobName returns only a certain number of jobs, we must loop through
    // until we find enough, requesting deletion as we go.
    int numJobsProcessed = 0;
    DateTime cutoffDate = clock.nowUtc().minusDays(defaultedDaysOld);
    Optional<String> cursor = Optional.empty();
    do {
      Optional<Integer> numJobsToRequest =
          Optional.ofNullable(
              numJobsToDelete.isPresent() ? numJobsToDelete.get() - numJobsProcessed : null);
      EligibleJobResults batch =
          mapreduceEntityCleanupUtil.findEligibleJobsByJobName(
              jobName.orElse(null), cutoffDate, numJobsToRequest, force.orElse(false), cursor);
      cursor = batch.cursor();
      // Individual batches can come back empty if none of the returned jobs meet the requirements
      // or if all jobs have been exhausted.
      if (!batch.eligibleJobs().isEmpty()) {
        String payloadChunk = requestDeletion(batch.eligibleJobs(), verbose);
        if (verbose) {
          payload.append(payloadChunk);
        }
        numJobsProcessed += batch.eligibleJobs().size();
      }
      // Stop iterating when all jobs have been exhausted (cursor is absent) or enough have been
      // processed.
    } while (cursor.isPresent()
        && (!numJobsToDelete.isPresent() || (numJobsProcessed < numJobsToDelete.get())));

    if (numJobsProcessed == 0) {
      logger.atInfo().log(
          "No eligible jobs found with name '%s' older than %d days old.",
          jobName.orElse("(any)"), defaultedDaysOld);
      payload.append("No eligible jobs found");
    } else {
      logger.atInfo().log("A total of %d job(s) processed.", numJobsProcessed);
      payload.append(String.format("A total of %d job(s) processed", numJobsProcessed));
    }
    response.setPayload(payload.toString());
  }

  private String requestDeletion(Set<String> actualJobIds, boolean verbose) {
    Optional<StringBuilder> payloadChunkBuilder =
        verbose ? Optional.of(new StringBuilder()) : Optional.empty();
    int errorCount = 0;
    for (String actualJobId : actualJobIds) {
      Optional<String> error =
          mapreduceEntityCleanupUtil.deleteJobAsync(datastore, actualJobId, force.orElse(false));
      if (error.isPresent()) {
        errorCount++;
      }
      logger.atInfo().log("%s: %s", actualJobId, error.orElse("deletion requested"));
      payloadChunkBuilder.ifPresent(
          stringBuilder ->
              stringBuilder.append(
                  String.format("%s: %s\n", actualJobId, error.orElse("deletion requested"))));
    }
    logger.atInfo().log(
        "successfully requested async deletion of %d job(s); errors received on %d",
        actualJobIds.size() - errorCount, errorCount);
    if (payloadChunkBuilder.isPresent()) {
      payloadChunkBuilder.get().append(String.format(
          "successfully requested async deletion of %d job(s); errors received on %d\n",
          actualJobIds.size() - errorCount,
          errorCount));
      return payloadChunkBuilder.get().toString();
    } else {
      return "";
    }
  }
}
