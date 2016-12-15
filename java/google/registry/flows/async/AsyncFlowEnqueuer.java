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

package google.registry.flows.async;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.common.annotations.VisibleForTesting;
import com.googlecode.objectify.Key;
import google.registry.config.ConfigModule.Config;
import google.registry.model.EppResource;
import google.registry.model.host.HostResource;
import google.registry.util.FormattingLogger;
import google.registry.util.Retrier;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Named;
import org.joda.time.Duration;

/** Helper class to enqueue tasks for handling asynchronous operations in flows. */
public final class AsyncFlowEnqueuer {

  /** The HTTP parameter names used by async flows. */
  public static final String PARAM_RESOURCE_KEY = "resourceKey";
  public static final String PARAM_REQUESTING_CLIENT_ID = "requestingClientId";
  public static final String PARAM_IS_SUPERUSER = "isSuperuser";
  public static final String PARAM_HOST_KEY = "hostKey";

  /** The task queue names used by async flows. */
  public static final String QUEUE_ASYNC_DELETE = "async-delete-pull";
  public static final String QUEUE_ASYNC_HOST_RENAME = "async-host-rename-pull";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @VisibleForTesting
  @Inject
  @Config("asyncDeleteFlowMapreduceDelay")
  public Duration asyncDeleteDelay;

  @VisibleForTesting
  @Inject
  @Named("async-delete-pull")
  public Queue asyncDeletePullQueue;

  @VisibleForTesting
  @Inject
  @Named(QUEUE_ASYNC_HOST_RENAME)
  public Queue asyncDnsRefreshPullQueue;

  @VisibleForTesting
  @Inject
  public Retrier retrier;

  @VisibleForTesting
  @Inject
  public AsyncFlowEnqueuer() {}

  /** Enqueues a task to asynchronously delete a contact or host, by key. */
  public void enqueueAsyncDelete(
      EppResource resourceToDelete, String requestingClientId, boolean isSuperuser) {
    Key<EppResource> resourceKey = Key.create(resourceToDelete);
    logger.infofmt(
        "Enqueuing async deletion of %s on behalf of registrar %s.",
        resourceKey, requestingClientId);
    TaskOptions task =
        TaskOptions.Builder
            .withMethod(Method.PULL)
            .countdownMillis(asyncDeleteDelay.getMillis())
            .param(PARAM_RESOURCE_KEY, resourceKey.getString())
            .param(PARAM_REQUESTING_CLIENT_ID, requestingClientId)
            .param(PARAM_IS_SUPERUSER, Boolean.toString(isSuperuser));
    addTaskToQueueWithRetry(asyncDeletePullQueue, task);
  }

  /** Enqueues a task to asynchronously refresh DNS for a renamed host. */
  public void enqueueAsyncDnsRefresh(HostResource host) {
    Key<HostResource> hostKey = Key.create(host);
    logger.infofmt("Enqueuing async DNS refresh for renamed host %s.", hostKey);
    addTaskToQueueWithRetry(
        asyncDnsRefreshPullQueue,
        TaskOptions.Builder.withMethod(Method.PULL).param(PARAM_HOST_KEY, hostKey.getString()));
  }

  /**
   * Adds a task to a queue with retrying, to avoid aborting the entire flow over a transient issue
   * enqueuing a task.
   */
  private void addTaskToQueueWithRetry(final Queue queue, final TaskOptions task) {
    retrier.callWithRetry(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        queue.add(task);
        return null;
      }}, TransientFailureException.class);
  }
}
