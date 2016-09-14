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

package google.registry.flows.async;

import static google.registry.flows.async.DeleteContactsAndHostsAction.PARAM_IS_SUPERUSER;
import static google.registry.flows.async.DeleteContactsAndHostsAction.PARAM_REQUESTING_CLIENT_ID;
import static google.registry.flows.async.DeleteContactsAndHostsAction.PARAM_RESOURCE_KEY;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.googlecode.objectify.Key;
import google.registry.config.ConfigModule.Config;
import google.registry.model.EppResource;
import google.registry.util.FormattingLogger;
import google.registry.util.Retrier;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Named;
import org.joda.time.Duration;

/** Helper class to enqueue tasks for handling asynchronous deletions to pull queues. */
public final class AsyncFlowEnqueuer {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @Config("asyncDeleteFlowMapreduceDelay") Duration asyncDeleteDelay;
  @Inject @Named("async-delete-pull") Queue queue;
  @Inject Retrier retrier;
  @Inject AsyncFlowEnqueuer() {}

  /**
   * Enqueues a task to asynchronously delete a contact or host, by key.
   *
   * <p>Note that the clientId is of the logged-in registrar that is requesting the deletion, not
   * necessarily the current owner of the resource.
   */
  public void enqueueAsyncDelete(
      EppResource resourceToDelete, String clientId, boolean isSuperuser) {
    Key<EppResource> resourceKey = Key.create(resourceToDelete);
    logger.infofmt(
        "Enqueueing async action to delete %s on behalf of registrar %s.", resourceKey, clientId);
    final TaskOptions options =
        TaskOptions.Builder
            .withMethod(Method.PULL)
            .countdownMillis(asyncDeleteDelay.getMillis())
            .param(PARAM_RESOURCE_KEY, resourceKey.getString())
            .param(PARAM_REQUESTING_CLIENT_ID, clientId)
            .param(PARAM_IS_SUPERUSER, Boolean.toString(isSuperuser));
    // Retry on transient failure exceptions so that the entire flow isn't aborted unnecessarily.
    retrier.callWithRetry(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        queue.add(options);
        return null;
      }}, TransientFailureException.class);
  }
}
