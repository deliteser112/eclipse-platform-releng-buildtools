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

package google.registry.backup;

import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;
import static google.registry.model.ofy.CommitLogBucket.getBucketKey;
import static google.registry.request.Action.Method.POST;

import com.google.common.base.Function;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Loader;
import com.googlecode.objectify.cmd.Query;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.ofy.CommitLogBucket;
import google.registry.model.ofy.CommitLogManifest;
import google.registry.model.ofy.CommitLogMutation;
import google.registry.model.ofy.Ofy;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import java.util.List;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Task that garbage collects old {@link CommitLogManifest} entities.
 *
 * <p>Once commit logs have been written to GCS, we don't really need them in datastore anymore,
 * except to reconstruct point-in-time snapshots of the database. But that functionality is not
 * useful after a certain amount of time, e.g. thirty days. So this task runs periodically to delete
 * the old data.
 *
 * <p>This task should be invoked in a fanout style for each {@link CommitLogBucket} ID. It then
 * queries {@code CommitLogManifest} entities older than the threshold, using an ancestor query
 * operating under the assumption under the assumption that the ID is the transaction timestamp in
 * milliseconds since the UNIX epoch. It then deletes them inside a transaction, along with their
 * associated {@link CommitLogMutation} entities.
 *
 * <p>If additional data is leftover, we show a warning at the INFO level, because it's not
 * actionable. If anything, it just shows that the system was under high load thirty days ago, and
 * therefore serves little use as an early warning to increase the number of buckets.
 *
 * <p>Before running, this task will perform an eventually consistent count query outside of a
 * transaction to see how much data actually exists to delete. If it's less than a tenth of {@link
 * #maxDeletes}, then we don't bother running the task. This is to minimize contention on the bucket
 * and avoid wasting resources.
 *
 * <h3>Dimensioning</h3>
 *
 * <p>This entire operation operates on a single entity group, within a single transaction. Since
 * there's a 10mB upper bound on transaction size and a four minute time limit, we can only delete
 * so many commit logs at once. So given the above constraints, five hundred would make a safe
 * default value for {@code maxDeletes}. See {@linkplain
 * google.registry.config.RegistryConfig.ConfigModule#provideCommitLogMaxDeletes()
 * commitLogMaxDeletes} for further documentation on this matter.
 *
 * <p>Finally, we need to pick an appropriate cron interval time for this task. Since a bucket
 * represents a single datastore entity group, it's only guaranteed to have one transaction per
 * second. So we just need to divide {@code maxDeletes} by sixty to get an appropriate minute
 * interval. Assuming {@code maxDeletes} is five hundred, this rounds up to ten minutes, which we'll
 * double, since this task can always catch up in off-peak hours.
 *
 * <p>There's little harm in keeping the data around a little longer, since this task is engaged in
 * a zero-sum resource struggle with the EPP transactions. Each transaction we perform here, is one
 * less transaction that's available to EPP. Furthermore, a well-administered system should have
 * enough buckets that we'll never brush up against the 1/s entity group transaction SLA.
 */
@Action(path = "/_dr/task/deleteOldCommitLogs", method = POST, automaticallyPrintOk = true)
public final class DeleteOldCommitLogsAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject Clock clock;
  @Inject Ofy ofy;
  @Inject @Parameter("bucket") int bucketNum;
  @Inject @Config("commitLogDatastoreRetention") Duration maxAge;
  @Inject @Config("commitLogMaxDeletes") int maxDeletes;
  @Inject DeleteOldCommitLogsAction() {}

  @Override
  public void run() {
    if (!doesEnoughDataExistThatThisTaskIsWorthRunning()) {
      return;
    }
    Integer deleted = ofy.transact(new Work<Integer>() {
      @Override
      public Integer run() {
        // Load at most maxDeletes manifest keys of commit logs older than the deletion threshold.
        List<Key<CommitLogManifest>> manifestKeys =
            queryManifests(ofy.load())
                .limit(maxDeletes)
                .keys()
                .list();
        // transform() is lazy so copyOf() ensures all the subqueries happen in parallel, because
        // the queries are launched by iterable(), put into a list, and then the list of iterables
        // is consumed and concatenated.
        ofy.deleteWithoutBackup().keys(concat(copyOf(transform(manifestKeys,
            new Function<Key<CommitLogManifest>, Iterable<Key<CommitLogMutation>>>() {
              @Override
              public Iterable<Key<CommitLogMutation>> apply(Key<CommitLogManifest> manifestKey) {
                return ofy.load()
                    .type(CommitLogMutation.class)
                    .ancestor(manifestKey)
                    .keys()
                    .iterable(); // launches the query asynchronously
              }}))));
        ofy.deleteWithoutBackup().keys(manifestKeys);
        return manifestKeys.size();
      }});
    if (deleted == maxDeletes) {
      logger.infofmt("Additional old commit logs might exist in bucket %d", bucketNum);
    }
  }

  /** Returns the point in time at which commit logs older than that point will be deleted. */
  private DateTime getDeletionThreshold() {
    return clock.nowUtc().minus(maxAge);
  }

  private boolean doesEnoughDataExistThatThisTaskIsWorthRunning() {
    int tenth = Math.max(1, maxDeletes / 10);
    int count = queryManifests(ofy.loadEventuallyConsistent())
        .limit(tenth)
        .count();
    if (0 < count && count < tenth) {
      logger.infofmt("Not enough old commit logs to bother running: %d < %d", count, tenth);
    }
    return count >= tenth;
  }

  private Query<CommitLogManifest> queryManifests(Loader loader) {
    long thresholdMillis = getDeletionThreshold().getMillis();
    Key<CommitLogBucket> bucketKey = getBucketKey(bucketNum);
    return loader
        .type(CommitLogManifest.class)
        .ancestor(bucketKey)
        .filterKey("<", Key.create(bucketKey, CommitLogManifest.class, thresholdMillis));
  }
}
