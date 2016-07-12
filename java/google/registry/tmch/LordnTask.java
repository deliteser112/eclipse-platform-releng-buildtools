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

package google.registry.tmch;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.api.taskqueue.LeaseOptions;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.apphosting.api.DeadlineExceededException;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import google.registry.model.domain.DomainResource;
import google.registry.model.registrar.Registrar;
import google.registry.util.NonFinalForTesting;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Helper methods for creating tasks containing CSV line data in the lordn-sunrise and lordn-claims
 * queues based on DomainResource changes.
 */
public class LordnTask {

  public static final String QUEUE_SUNRISE = "lordn-sunrise";
  public static final String QUEUE_CLAIMS = "lordn-claims";
  public static final String COLUMNS_CLAIMS = "roid,domain-name,notice-id,registrar-id,"
      + "registration-datetime,ack-datetime,application-datetime";
  public static final String COLUMNS_SUNRISE = "roid,domain-name,SMD-id,registrar-id,"
      + "registration-datetime,application-datetime";
  private static final Duration LEASE_PERIOD = Duration.standardHours(1);

  /** This is the max allowable batch size. */
  private static final long BATCH_SIZE = 1000;

  @NonFinalForTesting
  private static Long backOffMillis = 2000L;

  /**
   * Converts a list of queue tasks, each containing a row of CSV data, into a single newline-
   * delimited String.
   */
  public static String convertTasksToCsv(List<TaskHandle> tasks, DateTime now, String columns) {
    String header = String.format("1,%s,%d\n%s\n", now, tasks.size(), columns);
    StringBuilder csv = new StringBuilder(header);
    for (TaskHandle task : checkNotNull(tasks)) {
      String payload = new String(task.getPayload());
      if (!Strings.isNullOrEmpty(payload)) {
        csv.append(payload).append("\n");
      }
    }
    return csv.toString();
  }

  /** Leases and returns all tasks from the queue with the specified tag tld, in batches. */
  public static List<TaskHandle> loadAllTasks(Queue queue, String tld) {
    ImmutableList.Builder<TaskHandle> allTasks = new ImmutableList.Builder<>();
    int numErrors = 0;
    long backOff = backOffMillis;
    while (true) {
      try {
        List<TaskHandle> tasks = queue.leaseTasks(LeaseOptions.Builder
            .withTag(tld)
            .leasePeriod(LEASE_PERIOD.getMillis(), TimeUnit.MILLISECONDS)
            .countLimit(BATCH_SIZE));
        allTasks.addAll(tasks);
        if (tasks.isEmpty()) {
          return allTasks.build();
        }
      } catch (TransientFailureException | DeadlineExceededException e) {
        if (++numErrors >= 3) {
          throw new RuntimeException("Error leasing tasks", e);
        }
        Uninterruptibles.sleepUninterruptibly(backOff, TimeUnit.MILLISECONDS);
        backOff *= 2;
      }
    }
  }

  /**
   * Enqueues a task in the LORDN queue representing a line of CSV for LORDN export.
   */
  public static void enqueueDomainResourceTask(DomainResource domain) {
    ofy().assertInTransaction();
    // This method needs to use ofy transactionTime as the DomainResource's creationTime because
    // CreationTime isn't yet populated when this method is called during the resource flow.
    String tld = domain.getTld();
    if (domain.getLaunchNotice() == null) {
      getQueue(QUEUE_SUNRISE).add(TaskOptions.Builder
          .withTag(tld)
          .method(Method.PULL)
          .payload(getCsvLineForSunriseDomain(domain, ofy().getTransactionTime())));
    } else {
      getQueue(QUEUE_CLAIMS).add(TaskOptions.Builder
          .withTag(tld)
          .method(Method.PULL)
          .payload(getCsvLineForClaimsDomain(domain, ofy().getTransactionTime())));
    }
  }

  /** Returns the corresponding CSV LORDN line for a sunrise domain. */
  public static String getCsvLineForSunriseDomain(DomainResource domain, DateTime transactionTime) {
    // Only skip nulls in the outer join because only application time is allowed to be null.
    Joiner joiner = Joiner.on(',');
    return joiner.skipNulls().join(
        joiner.join(
            domain.getRepoId(),
            domain.getFullyQualifiedDomainName(),
            domain.getSmdId(),
            getIanaIdentifier(domain.getCreationClientId()),
            transactionTime), // Used as creation time.
        domain.getApplicationTime()); // This may be null for sunrise QLP domains.
  }

  /** Returns the corresponding CSV LORDN line for a claims domain. */
  public static String getCsvLineForClaimsDomain(DomainResource domain, DateTime transactionTime) {
    // Only skip nulls in the outer join because only application time is allowed to be null.
    Joiner joiner = Joiner.on(',');
    return joiner.skipNulls().join(
        joiner.join(
            domain.getRepoId(),
            domain.getFullyQualifiedDomainName(),
            domain.getLaunchNotice().getNoticeId().getTcnId(),
            getIanaIdentifier(domain.getCreationClientId()),
            transactionTime, // Used as creation time.
            domain.getLaunchNotice().getAcceptedTime()),
        domain.getApplicationTime());  // This may be null if this wasn't from landrush.
  }

  /** Retrieves the IANA identifier for a registrar based on the client id. */
  private static String getIanaIdentifier(String clientId) {
     Registrar registrar = checkNotNull(
         Registrar.loadByClientId(clientId),
         "No registrar found for client id: %s", clientId);
    // Return the string "null" for null identifiers, since some Registrar.Types such as OTE will
    // have null iana ids.
    return String.valueOf(registrar.getIanaIdentifier());
  }
}
