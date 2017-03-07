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

import com.google.appengine.api.datastore.DatastoreService;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import google.registry.batch.MapreduceEntityCleanupUtil.EligibleJobResults;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
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

@Action(path = "/_dr/task/mapreduceEntityCleanup")
public class MapreduceEntityCleanupAction implements Runnable {

  private static final int DEFAULT_DAYS_OLD = 180;
  private static final int DEFAULT_MAX_NUM_JOBS_TO_DELETE = 5;

  private static final String ERROR_BOTH_JOB_ID_AND_NAME =
      "Do not specify both a job ID and a job name";
  private static final String ERROR_BOTH_JOB_ID_AND_NUMBER_OF_JOBS =
      "Do not specify both a job ID and a number of jobs to delete";
  private static final String ERROR_BOTH_JOB_ID_AND_DAYS_OLD =
      "Do not specify both a job ID and a days old threshold";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

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
    response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
    if (jobId.isPresent()) {
      runWithJobId();
    } else {
      runWithoutJobId();
    }
  }

  private void logSevereAndSetPayload(String message) {
    logger.severe(message);
    response.setPayload(message);
  }

  /** Delete the job with the specified job ID, checking for conflicting parameters. */
  private void runWithJobId() {
    if (jobName.isPresent()) {
      logSevereAndSetPayload(ERROR_BOTH_JOB_ID_AND_NAME);
      return;
    }
    if (numJobsToDelete.isPresent()) {
      logSevereAndSetPayload(ERROR_BOTH_JOB_ID_AND_NUMBER_OF_JOBS);
      return;
    }
    if (daysOld.isPresent()) {
      logSevereAndSetPayload(ERROR_BOTH_JOB_ID_AND_DAYS_OLD);
      return;
    }
    response.setPayload(requestDeletion(ImmutableSet.of(jobId.get()), true /* generatePayload */));
  }

  /**
   * Delete jobs with a matching display name, or all jobs if no name is specified. Only pick jobs
   * which are old enough.
   */
  private void runWithoutJobId() {
    int defaultedDaysOld = daysOld.or(DEFAULT_DAYS_OLD);

    // Only generate the detailed response payload if there aren't too many jobs involved.
    boolean generatePayload =
        numJobsToDelete.isPresent() && (numJobsToDelete.get() <= DEFAULT_MAX_NUM_JOBS_TO_DELETE);
    Optional<StringBuilder> payloadBuilder =
        generatePayload ? Optional.of(new StringBuilder()) : Optional.<StringBuilder>absent();
    String defaultPayload = "done";

    // Since findEligibleJobsByJobName returns only a certain number of jobs, we must loop through
    // until we find enough, requesting deletion as we go. We also stop if we don't find anything,
    // or if there are no more jobs to be found (because no cursor is returned).
    int numJobsDeletedSoFar = 0;
    boolean isFirstTime = true;
    Optional<String> cursor = Optional.<String>absent();
    DateTime cutoffDate = clock.nowUtc().minusDays(defaultedDaysOld);
    while ((isFirstTime || cursor.isPresent())
        && (!numJobsToDelete.isPresent() || (numJobsDeletedSoFar < numJobsToDelete.get()))) {
      isFirstTime = false;
      EligibleJobResults eligibleJobResults =
          mapreduceEntityCleanupUtil.findEligibleJobsByJobName(
              jobName.orNull(), cutoffDate, numJobsToDelete, force.or(false), cursor);
      cursor = eligibleJobResults.cursor();
      if (eligibleJobResults.eligibleJobs().isEmpty()) {
        logger.infofmt(
            "No eligible job with name '%s' older than %s days old.",
            jobName.or("(null)"), defaultedDaysOld);
        if (generatePayload) {
          payloadBuilder.get().append("No eligible job.");
        }
        defaultPayload = "No eligible job.";
      } else {
        String payloadChunk = requestDeletion(eligibleJobResults.eligibleJobs(), generatePayload);
        if (generatePayload) {
          payloadBuilder.get().append(payloadChunk);
        }
        numJobsDeletedSoFar += eligibleJobResults.eligibleJobs().size();
      }
    }

    logger.infofmt("A total of %s job(s) processed", numJobsDeletedSoFar);
    if (generatePayload) {
      payloadBuilder
          .get()
          .append(String.format("A total of %d job(s) processed\n", numJobsDeletedSoFar));
      response.setPayload(payloadBuilder.get().toString());
    } else {
      response.setPayload(defaultPayload);
    }
  }

  private String requestDeletion(Set<String> actualJobIds, boolean generatePayload) {
    Optional<StringBuilder> payloadChunkBuilder =
        generatePayload ? Optional.of(new StringBuilder()) : Optional.<StringBuilder>absent();
    int errorCount = 0;
    for (String actualJobId : actualJobIds) {
      Optional<String> error =
          mapreduceEntityCleanupUtil.deleteJobAsync(datastore, actualJobId, force.or(false));
      if (error.isPresent()) {
        errorCount++;
      }
      logger.infofmt("%s: %s", actualJobId, error.or("deletion requested"));
      if (payloadChunkBuilder.isPresent()) {
        payloadChunkBuilder
            .get()
            .append(String.format("%s: %s\n", actualJobId, error.or("deletion requested")));
      }
    }
    logger.infofmt(
        "successfully requested async deletion of %s job(s); errors received on %s",
        actualJobIds.size() - errorCount,
        errorCount);
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
