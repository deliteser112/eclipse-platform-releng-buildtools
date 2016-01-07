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

package google.registry.model.server;

import static com.google.common.truth.Truth.assertThat;

import google.registry.model.ofy.Ofy;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import org.joda.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Lock}. */
@RunWith(JUnit4.class)
public class LockTest {

  private static final String RESOURCE_NAME = "foo";
  private static final Duration ONE_DAY = Duration.standardDays(1);
  private static final Duration TWO_MILLIS = Duration.millis(2);

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testReleasedExplicitly() throws Exception {
    Lock lock = Lock.acquire(getClass(), RESOURCE_NAME, "", ONE_DAY);
    assertThat(lock).isNotNull();
    // We can't get it again at the same time.
    assertThat(Lock.acquire(getClass(), RESOURCE_NAME, "", ONE_DAY)).isNull();
    // But if we release it, it's available.
    lock.release();
    assertThat(Lock.acquire(getClass(), RESOURCE_NAME, "", ONE_DAY)).isNotNull();
  }

  @Test
  public void testReleasedAfterTimeout() throws Exception {
    FakeClock clock = new FakeClock();
    inject.setStaticField(Ofy.class, "clock", clock);
    assertThat(Lock.acquire(getClass(), RESOURCE_NAME, "", TWO_MILLIS)).isNotNull();
    // We can't get it again at the same time.
    assertThat(Lock.acquire(getClass(), RESOURCE_NAME, "", TWO_MILLIS)).isNull();
    // A second later we still can't get the lock.
    clock.advanceOneMilli();
    assertThat(Lock.acquire(getClass(), RESOURCE_NAME, "", TWO_MILLIS)).isNull();
    // But two seconds later we can get it.
    clock.advanceOneMilli();
    assertThat(Lock.acquire(getClass(), RESOURCE_NAME, "", TWO_MILLIS)).isNotNull();
  }

  @Test
  public void testTldsAreIndependent() throws Exception {
    Lock lockA = Lock.acquire(getClass(), RESOURCE_NAME, "a", ONE_DAY);
    assertThat(lockA).isNotNull();
    // For a different tld we can still get a lock with the same name.
    Lock lockB = Lock.acquire(getClass(), RESOURCE_NAME, "b", ONE_DAY);
    assertThat(lockB).isNotNull();
    // We can't get lockB again at the same time.
    assertThat(Lock.acquire(getClass(), RESOURCE_NAME, "b", ONE_DAY)).isNull();
    // Releasing lockA has no effect on lockB (even though we are still using the "b" tld).
    lockA.release();
    assertThat(Lock.acquire(getClass(), RESOURCE_NAME, "b", ONE_DAY)).isNull();
  }

  @Test
  public void testQueueing() throws Exception {
    // This should work... there's nothing on the queue.
    Lock lock = Lock.acquire(String.class, RESOURCE_NAME, "", TWO_MILLIS);
    assertThat(lock).isNotNull();
    lock.release();
    // Queue up a request from "Object".
    Lock.joinQueue(Object.class, RESOURCE_NAME, "");
    // We can't get the lock because the "requester" is different than what's on the queue.
    assertThat(Lock.acquire(String.class, RESOURCE_NAME, "", TWO_MILLIS)).isNull();
    // But this will work because the requester is the same as what's on the queue.
    lock = Lock.acquire(Object.class, RESOURCE_NAME, "", TWO_MILLIS);
    assertThat(lock).isNotNull();
    lock.release();
    // Now the queue is empty again so we can get the lock.
    assertThat(Lock.acquire(String.class, RESOURCE_NAME, "", TWO_MILLIS)).isNotNull();
  }

  @Test
  public void testFailure_emptyResourceName() throws Exception {
    thrown.expect(IllegalArgumentException.class, "resourceName cannot be null or empty");
    Lock.acquire(String.class, "", "", TWO_MILLIS);
  }
}
