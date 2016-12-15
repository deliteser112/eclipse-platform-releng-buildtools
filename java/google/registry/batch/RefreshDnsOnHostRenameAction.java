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

package google.registry.batch;

import static com.google.appengine.api.taskqueue.QueueConstants.maxLeaseCount;
import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.flows.async.AsyncFlowEnqueuer.PARAM_HOST_KEY;
import static google.registry.flows.async.AsyncFlowEnqueuer.QUEUE_ASYNC_HOST_RENAME;
import static google.registry.mapreduce.inputs.EppResourceInputs.createEntityInput;
import static google.registry.model.EppResourceUtils.isActive;
import static google.registry.model.EppResourceUtils.isDeleted;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.latestOf;
import static google.registry.util.PipelineUtils.createJobPath;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.appengine.api.taskqueue.LeaseOptions;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.googlecode.objectify.Key;
import google.registry.dns.DnsQueue;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.NullInput;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.Retrier;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import org.joda.time.DateTime;

/** Performs batched DNS refreshes for applicable domains following a host rename. */
@Action(path = "/_dr/task/refreshDnsOnHostRename")
public class RefreshDnsOnHostRenameAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();
  private static final long LEASE_MINUTES = 20;

  @Inject Clock clock;
  @Inject MapreduceRunner mrRunner;
  @Inject @Named(QUEUE_ASYNC_HOST_RENAME) Queue pullQueue;
  @Inject Response response;
  @Inject Retrier retrier;
  @Inject RefreshDnsOnHostRenameAction() {}

  @Override
  public void run() {
    LeaseOptions options =
        LeaseOptions.Builder.withCountLimit(maxLeaseCount()).leasePeriod(LEASE_MINUTES, MINUTES);
    List<TaskHandle> tasks = pullQueue.leaseTasks(options);
    if (tasks.isEmpty()) {
      response.setPayload("No DNS refresh on host rename tasks to process in pull queue.");
      return;
    }
    ImmutableList.Builder<DnsRefreshRequest> requestsBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<Key<HostResource>> hostKeys = new ImmutableList.Builder<>();
    final List<TaskHandle> tasksToDelete = new ArrayList<>();

    for (TaskHandle task : tasks) {
      try {
        Optional<DnsRefreshRequest> request =
            DnsRefreshRequest.createFromTask(task, clock.nowUtc());
        if (request.isPresent()) {
          requestsBuilder.add(request.get());
          hostKeys.add(request.get().hostKey());
        } else {
          // Skip hosts that are deleted.
          tasksToDelete.add(task);
        }
      } catch (Exception e) {
        logger.severefmt(
            e, "Could not parse DNS refresh for host request, delaying task for a day: %s", task);
        // Grab the lease for a whole day, so it won't continue throwing errors every five minutes.
        pullQueue.modifyTaskLease(task, 1L, DAYS);
      }
    }

    deleteTasksWithRetry(tasksToDelete, pullQueue, retrier);
    ImmutableList<DnsRefreshRequest> refreshRequests = requestsBuilder.build();
    if (refreshRequests.isEmpty()) {
      logger.info(
          "No asynchronous DNS refreshes to process because all renamed hosts are deleted.");
      response.setPayload("All requested DNS refreshes are on hosts that were since deleted.");
    } else {
      logger.infofmt("Processing asynchronous DNS refresh for renamed hosts: %s", hostKeys.build());
      runMapreduce(refreshRequests, tasks);
    }
  }

  private void runMapreduce(
      ImmutableList<DnsRefreshRequest> refreshRequests, List<TaskHandle> tasks) {
    try {
      response.sendJavaScriptRedirect(createJobPath(mrRunner
          .setJobName("Enqueue DNS refreshes for domains referencing renamed hosts")
          .setModuleName("backend")
          .setDefaultReduceShards(1)
          .runMapreduce(
              new RefreshDnsOnHostRenameMapper(refreshRequests, retrier),
              new RefreshDnsOnHostRenameReducer(tasks, retrier),
              // Add an extra NullInput so that the reducer always fires exactly once.
              ImmutableList.of(
                  new NullInput<DomainResource>(), createEntityInput(DomainResource.class)))));
    } catch (Throwable t) {
      logger.severefmt(t, "Error while kicking off mapreduce to refresh DNS for renamed hosts.");
    }
  }

  /** Map over domains and refresh the DNS of those that reference the renamed hosts. */
  public static class RefreshDnsOnHostRenameMapper
      extends Mapper<DomainResource, Boolean, Boolean> {

    private static final long serialVersionUID = -5261698524424335531L;
    private static final DnsQueue dnsQueue = DnsQueue.create();

    private final ImmutableList<DnsRefreshRequest> refreshRequests;
    private final Retrier retrier;

    RefreshDnsOnHostRenameMapper(
        ImmutableList<DnsRefreshRequest> refreshRequests, Retrier retrier) {
      this.refreshRequests = refreshRequests;
      this.retrier = retrier;
    }

    @Override
    public final void map(@Nullable final DomainResource domain) {
      if (domain == null) {
        // Emit a single value so that the reducer always runs.  The key and value don't matter.
        emit(true, true);
        return;
      }
      Key<HostResource> referencingHostKey = null;
      for (DnsRefreshRequest request : refreshRequests) {
        if (isActive(domain, request.lastUpdateTime())
            && domain.getNameservers().contains(request.hostKey())) {
          referencingHostKey = request.hostKey();
          break;
        }
      }
      if (referencingHostKey != null) {
        retrier.callWithRetry(
            new Callable<Void>() {
              @Override
              public Void call() throws Exception {
                dnsQueue.addDomainRefreshTask(domain.getFullyQualifiedDomainName());
                return null;
              }}, TransientFailureException.class);
        logger.infofmt(
            "Enqueued DNS refresh for domain %s referenced by host %s.",
            domain.getFullyQualifiedDomainName(), referencingHostKey);
        getContext().incrementCounter("domains refreshed");
      } else {
        getContext().incrementCounter("domains not refreshed");
      }

      // Don't catch errors -- we allow the mapreduce to terminate on any errors that can't be
      // resolved by retrying the transaction. The reducer only fires if the mapper completes
      // without errors, meaning that it is acceptable to delete all tasks.
    }
  }

  /**
   * A reducer that always fires exactly once.
   *
   * <p>This is really a reducer in name only; what it's really doing is waiting for all of the
   * mapper tasks to finish, and then delete the pull queue tasks. Note that this only happens if
   * the mapper completes execution without errors.
   */
  public static class RefreshDnsOnHostRenameReducer extends Reducer<Boolean, Boolean, Void> {

    private static final long serialVersionUID = -2850944843275790412L;

    private final Retrier retrier;
    private final List<TaskHandle> tasks;

    RefreshDnsOnHostRenameReducer(List<TaskHandle> tasks, Retrier retrier) {
      this.tasks = tasks;
      this.retrier = retrier;
    }

    @Override
    public void reduce(Boolean key, ReducerInput<Boolean> values) {
      deleteTasksWithRetry(tasks, getQueue(QUEUE_ASYNC_HOST_RENAME), retrier);
    }
  }

  /** Deletes a list of tasks from the given queue using a retrier. */
  private static void deleteTasksWithRetry(
      final List<TaskHandle> tasks, final Queue queue, Retrier retrier) {
    if (tasks.isEmpty()) {
      return;
    }
    retrier.callWithRetry(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            queue.deleteTask(tasks);
            return null;
          }}, TransientFailureException.class);
  }

  /** A class that encapsulates the values of a request to refresh DNS for a renamed host. */
  @AutoValue
  abstract static class DnsRefreshRequest implements Serializable {

    private static final long serialVersionUID = 2188894914017230887L;

    abstract Key<HostResource> hostKey();
    abstract DateTime lastUpdateTime();

    /**
     * Returns a packaged-up {@link DnsRefreshRequest} parsed from a task queue task, or absent if
     * the host specified is already deleted.
     */
    static Optional<DnsRefreshRequest> createFromTask(TaskHandle task, DateTime now)
        throws Exception {
      ImmutableMap<String, String> params = ImmutableMap.copyOf(task.extractParams());
      Key<HostResource> hostKey =
          Key.create(checkNotNull(params.get(PARAM_HOST_KEY), "Host to refresh not specified"));
      HostResource host =
          checkNotNull(ofy().load().key(hostKey).now(), "Host to refresh doesn't exist");
      if (isDeleted(host, latestOf(now, host.getUpdateAutoTimestamp().getTimestamp()))) {
        logger.infofmt("Host %s is already deleted, not refreshing DNS.", hostKey);
        return Optional.absent();
      }
      return Optional.<DnsRefreshRequest>of(
          new AutoValue_RefreshDnsOnHostRenameAction_DnsRefreshRequest(
              hostKey, host.getUpdateAutoTimestamp().getTimestamp()));
    }
  }
}
