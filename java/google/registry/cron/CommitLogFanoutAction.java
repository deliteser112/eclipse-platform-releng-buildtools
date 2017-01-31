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

package google.registry.cron;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.common.base.Optional;
import google.registry.model.ofy.CommitLogBucket;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.util.TaskEnqueuer;
import java.util.Random;
import javax.inject.Inject;

/** Action for fanning out cron tasks for each commit log bucket. */
@Action(path = "/_dr/cron/commitLogFanout", automaticallyPrintOk = true)
public final class CommitLogFanoutAction implements Runnable {

  public static final String BUCKET_PARAM = "bucket";

  private static final Random random = new Random();

  @Inject TaskEnqueuer taskEnqueuer;
  @Inject @Parameter("endpoint") String endpoint;
  @Inject @Parameter("queue") String queue;
  @Inject @Parameter("jitterSeconds") Optional<Integer> jitterSeconds;
  @Inject CommitLogFanoutAction() {}

  @Override
  public void run() {
    Queue taskQueue = getQueue(queue);
    for (int bucketId : CommitLogBucket.getBucketIds()) {
      TaskOptions taskOptions = TaskOptions.Builder.withUrl(endpoint)
          .param(BUCKET_PARAM, Integer.toString(bucketId))
          .countdownMillis(jitterSeconds.isPresent()
              ? random.nextInt((int) SECONDS.toMillis(jitterSeconds.get()))
              : 0);
      taskEnqueuer.enqueue(taskQueue, taskOptions);
    }
  }
}
