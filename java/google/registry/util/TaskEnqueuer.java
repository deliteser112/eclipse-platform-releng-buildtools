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

package google.registry.util;

import static java.util.Arrays.asList;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TransientFailureException;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;

/** Utilities for dealing with App Engine task queues. */
public class TaskEnqueuer implements Serializable {

  private static final long serialVersionUID = 7893211200220508362L;

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private final Retrier retrier;

  @Inject
  public TaskEnqueuer(Retrier retrier) {
    this.retrier = retrier;
  }

  /**
   * Adds a task to a App Engine task queue in a reliable manner.
   *
   * <p>This is the same as {@link Queue#add(TaskOptions)} except it'll automatically retry with
   * exponential backoff if {@link TransientFailureException} is thrown.
   *
   * @throws TransientFailureException if retrying failed for the maximum period of time, or an
   *     {@link InterruptedException} told us to stop trying
   * @return successfully enqueued task
   */
  public TaskHandle enqueue(Queue queue, TaskOptions task) {
    return enqueue(queue, asList(task)).get(0);
  }

  /**
   * Adds tasks to an App Engine task queue in a reliable manner.
   *
   * <p>This is the same as {@link Queue#add(Iterable)} except it'll automatically retry with
   * exponential backoff if {@link TransientFailureException} is thrown.
   *
   * @throws TransientFailureException if retrying failed for the maximum period of time, or an
   *     {@link InterruptedException} told us to stop trying
   * @return successfully enqueued tasks
   */
  public List<TaskHandle> enqueue(final Queue queue, final Iterable<TaskOptions> tasks) {
    return retrier.callWithRetry(
        new Callable<List<TaskHandle>>() {
          @Override
          public List<TaskHandle> call() {
            for (TaskOptions task : tasks) {
              logger.infofmt(
                  "Enqueuing queue='%s' endpoint='%s'", queue.getQueueName(), task.getUrl());
            }
            return queue.add(tasks);
          }},
        TransientFailureException.class);
  }
}
