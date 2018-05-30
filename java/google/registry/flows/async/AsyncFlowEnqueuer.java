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

package google.registry.flows.async;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.googlecode.objectify.Key;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.EppResource;
import google.registry.model.eppcommon.Trid;
import google.registry.model.host.HostResource;
import google.registry.util.Retrier;
import javax.inject.Inject;
import javax.inject.Named;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Helper class to enqueue tasks for handling asynchronous operations in flows. */
public final class AsyncFlowEnqueuer {

  /** The HTTP parameter names used by async flows. */
  public static final String PARAM_RESOURCE_KEY = "resourceKey";
  public static final String PARAM_REQUESTING_CLIENT_ID = "requestingClientId";
  public static final String PARAM_CLIENT_TRANSACTION_ID = "clientTransactionId";
  public static final String PARAM_SERVER_TRANSACTION_ID = "serverTransactionId";
  public static final String PARAM_IS_SUPERUSER = "isSuperuser";
  public static final String PARAM_HOST_KEY = "hostKey";
  public static final String PARAM_REQUESTED_TIME = "requestedTime";

  /** The task queue names used by async flows. */
  public static final String QUEUE_ASYNC_DELETE = "async-delete-pull";
  public static final String QUEUE_ASYNC_HOST_RENAME = "async-host-rename-pull";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Duration asyncDeleteDelay;
  private final Queue asyncDeletePullQueue;
  private final Queue asyncDnsRefreshPullQueue;
  private final Retrier retrier;

  @VisibleForTesting
  @Inject
  public AsyncFlowEnqueuer(
      @Named(QUEUE_ASYNC_DELETE) Queue asyncDeletePullQueue,
      @Named(QUEUE_ASYNC_HOST_RENAME) Queue asyncDnsRefreshPullQueue,
      @Config("asyncDeleteFlowMapreduceDelay") Duration asyncDeleteDelay,
      Retrier retrier) {
    this.asyncDeletePullQueue = asyncDeletePullQueue;
    this.asyncDnsRefreshPullQueue = asyncDnsRefreshPullQueue;
    this.asyncDeleteDelay = asyncDeleteDelay;
    this.retrier = retrier;
  }

  /** Enqueues a task to asynchronously delete a contact or host, by key. */
  public void enqueueAsyncDelete(
      EppResource resourceToDelete,
      DateTime now,
      String requestingClientId,
      Trid trid,
      boolean isSuperuser) {
    Key<EppResource> resourceKey = Key.create(resourceToDelete);
    logger.atInfo().log(
        "Enqueuing async deletion of %s on behalf of registrar %s.",
        resourceKey, requestingClientId);
    TaskOptions task =
        TaskOptions.Builder.withMethod(Method.PULL)
            .countdownMillis(asyncDeleteDelay.getMillis())
            .param(PARAM_RESOURCE_KEY, resourceKey.getString())
            .param(PARAM_REQUESTING_CLIENT_ID, requestingClientId)
            .param(PARAM_SERVER_TRANSACTION_ID, trid.getServerTransactionId())
            .param(PARAM_IS_SUPERUSER, Boolean.toString(isSuperuser))
            .param(PARAM_REQUESTED_TIME, now.toString());
    if (trid.getClientTransactionId().isPresent()) {
      task.param(PARAM_CLIENT_TRANSACTION_ID, trid.getClientTransactionId().get());
    }
    addTaskToQueueWithRetry(asyncDeletePullQueue, task);
  }

  /** Enqueues a task to asynchronously refresh DNS for a renamed host. */
  public void enqueueAsyncDnsRefresh(HostResource host, DateTime now) {
    Key<HostResource> hostKey = Key.create(host);
    logger.atInfo().log("Enqueuing async DNS refresh for renamed host %s.", hostKey);
    addTaskToQueueWithRetry(
        asyncDnsRefreshPullQueue,
        TaskOptions.Builder.withMethod(Method.PULL)
            .param(PARAM_HOST_KEY, hostKey.getString())
            .param(PARAM_REQUESTED_TIME, now.toString()));
  }

  /**
   * Adds a task to a queue with retrying, to avoid aborting the entire flow over a transient issue
   * enqueuing a task.
   */
  private void addTaskToQueueWithRetry(final Queue queue, final TaskOptions task) {
    retrier.callWithRetry(() -> queue.add(task), TransientFailureException.class);
  }
}
