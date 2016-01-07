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

package google.registry.util;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.common.collect.ImmutableList;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeSleeper;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TaskEnqueuer}. */
@RunWith(JUnit4.class)
public final class TaskEnqueuerTest {

  private static final int MAX_RETRIES = 3;

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));
  private final FakeSleeper sleeper = new FakeSleeper(clock);
  private final TaskEnqueuer taskEnqueuer =
      new TaskEnqueuer(new Retrier(sleeper, MAX_RETRIES));
  private final Queue queue = mock(Queue.class);
  private final TaskOptions task = withUrl("url").taskName("name");
  private final TaskHandle handle = new TaskHandle(task, "handle");

  @Test
  public void testEnqueue_worksOnFirstTry_doesntSleep() throws Exception {
    when(queue.add(ImmutableList.of(task))).thenReturn(ImmutableList.of(handle));
    assertThat(taskEnqueuer.enqueue(queue, task)).isSameAs(handle);
    verify(queue).add(ImmutableList.of(task));
    assertThat(clock.nowUtc()).isEqualTo(DateTime.parse("2000-01-01TZ"));
  }

  @Test
  public void testEnqueue_twoTransientErrorsThenSuccess_stillWorksAfterSleeping() throws Exception {
    when(queue.add(ImmutableList.of(task)))
        .thenThrow(new TransientFailureException(""))
        .thenThrow(new TransientFailureException(""))
        .thenReturn(ImmutableList.of(handle));
    assertThat(taskEnqueuer.enqueue(queue, task)).isSameAs(handle);
    verify(queue, times(3)).add(ImmutableList.of(task));
    assertThat(clock.nowUtc()).isEqualTo(DateTime.parse("2000-01-01T00:00:00.6Z"));  // 200 + 400ms
  }

  @Test
  public void testEnqueue_multiple() throws Exception {
    TaskOptions taskA = withUrl("a").taskName("a");
    TaskOptions taskB = withUrl("b").taskName("b");
    ImmutableList<TaskHandle> handles =
        ImmutableList.of(new TaskHandle(taskA, "a"), new TaskHandle(taskB, "b"));
    when(queue.add(ImmutableList.of(taskA, taskB))).thenReturn(handles);
    assertThat(taskEnqueuer.enqueue(queue, ImmutableList.of(taskA, taskB))).isSameAs(handles);
    assertThat(clock.nowUtc()).isEqualTo(DateTime.parse("2000-01-01TZ"));
  }

  @Test
  public void testEnqueue_maxRetries_givesUp() throws Exception {
    when(queue.add(ImmutableList.of(task)))
        .thenThrow(new TransientFailureException("one"))
        .thenThrow(new TransientFailureException("two"))
        .thenThrow(new TransientFailureException("three"))
        .thenThrow(new TransientFailureException("four"));
    thrown.expect(TransientFailureException.class, "three");
    taskEnqueuer.enqueue(queue, task);
  }

  @Test
  public void testEnqueue_transientErrorThenInterrupt_throwsTransientError() throws Exception {
    when(queue.add(ImmutableList.of(task))).thenThrow(new TransientFailureException(""));
    try {
      Thread.currentThread().interrupt();
      thrown.expect(TransientFailureException.class);
      taskEnqueuer.enqueue(queue, task);
    } finally {
      Thread.interrupted();  // Clear interrupt state so it doesn't pwn other tests.
    }
  }
}
