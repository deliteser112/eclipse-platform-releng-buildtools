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

import static google.registry.request.Actions.getPathForAction;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import google.registry.config.RegistryEnvironment;
import google.registry.util.FormattingLogger;
import java.util.Map.Entry;
import org.joda.time.Duration;

/** Utility methods specific to async flows. */
// TODO(b/26140521): Delete this class once non-batched async operations are deleted.
public final class AsyncFlowUtils {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @VisibleForTesting
  public static final String ASYNC_FLOW_QUEUE_NAME = "flows-async";  // See queue.xml.

  private AsyncFlowUtils() {}

  /** Enqueues a mapreduce action to perform an async flow operation. */
  public static TaskHandle enqueueMapreduceAction(
      Class<? extends Runnable> action,
      ImmutableMap<String, String> params,
      Duration executionDelay) {
    Queue queue = QueueFactory.getQueue(ASYNC_FLOW_QUEUE_NAME);
    String path = getPathForAction(action);
    logger.infofmt("Enqueueing async mapreduce action with path %s and params %s", path, params);
    // Aggressively back off if the task fails, to minimize flooding the logs.
    RetryOptions retryOptions = RetryOptions.Builder.withMinBackoffSeconds(
        RegistryEnvironment.get().config().getAsyncFlowFailureBackoff().getStandardSeconds());
    TaskOptions options = TaskOptions.Builder
        .withUrl(path)
        .retryOptions(retryOptions)
        .countdownMillis(executionDelay.getMillis())
        .method(Method.GET);
    for (Entry<String, String> entry : params.entrySet()) {
      options.param(entry.getKey(), entry.getValue());
    }
    return queue.add(options);
  }
}
