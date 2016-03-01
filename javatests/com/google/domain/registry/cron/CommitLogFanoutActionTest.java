// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.cron;

import static com.google.domain.registry.cron.CommitLogFanoutAction.BUCKET_PARAM;
import static com.google.domain.registry.testing.TaskQueueHelper.assertTasksEnqueued;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.domain.registry.model.ofy.CommitLogBucket;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.testing.TaskQueueHelper.TaskMatcher;
import com.google.domain.registry.util.Retrier;
import com.google.domain.registry.util.TaskEnqueuer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link CommitLogFanoutAction}. */
@RunWith(JUnit4.class)
public class CommitLogFanoutActionTest {

  private static final String ENDPOINT = "/the/servlet";
  private static final String QUEUE = "the-queue";

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue(Joiner.on('\n').join(
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
          "<queue-entries>",
          "  <queue>",
          "    <name>the-queue</name>",
          "    <rate>1/s</rate>",
          "  </queue>",
          "</queue-entries>"))
      .build();

  @Test
  public void testSuccess() throws Exception {
    CommitLogFanoutAction action = new CommitLogFanoutAction();
    action.taskEnqueuer = new TaskEnqueuer(new Retrier(null, 1));
    action.endpoint = ENDPOINT;
    action.queue = QUEUE;
    action.jitterSeconds = Optional.absent();
    action.run();
    List<TaskMatcher> matchers = new ArrayList<>();
    for (int bucketId : CommitLogBucket.getBucketIds()) {
      matchers.add(new TaskMatcher().url(ENDPOINT).param(BUCKET_PARAM, Integer.toString(bucketId)));
    }
    assertTasksEnqueued(QUEUE, matchers);
  }
}
