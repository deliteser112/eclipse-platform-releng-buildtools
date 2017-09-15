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

package google.registry.model.server;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import google.registry.model.ofy.Ofy;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.util.RequestStatusChecker;
import org.joda.time.Duration;
import org.junit.Before;
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
  private static final RequestStatusChecker requestStatusChecker = mock(RequestStatusChecker.class);

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private Optional<Lock> acquire(String tld, Duration leaseLength) {
    return Lock.acquire(RESOURCE_NAME, tld, leaseLength, requestStatusChecker);
  }

  @Before public void setUp() {
    when(requestStatusChecker.getLogId()).thenReturn("current-request-id");
    when(requestStatusChecker.isRunning("current-request-id")).thenReturn(true);
  }

  @Test
  public void testReleasedExplicitly() throws Exception {
    Optional<Lock> lock = acquire("", ONE_DAY);
    assertThat(lock).isPresent();
    // We can't get it again at the same time.
    assertThat(acquire("", ONE_DAY)).isAbsent();
    // But if we release it, it's available.
    lock.get().release();
    assertThat(acquire("", ONE_DAY)).isPresent();
  }

  @Test
  public void testReleasedAfterTimeout() throws Exception {
    FakeClock clock = new FakeClock();
    inject.setStaticField(Ofy.class, "clock", clock);
    assertThat(acquire("", TWO_MILLIS)).isPresent();
    // We can't get it again at the same time.
    assertThat(acquire("", TWO_MILLIS)).isAbsent();
    // A second later we still can't get the lock.
    clock.advanceOneMilli();
    assertThat(acquire("", TWO_MILLIS)).isAbsent();
    // But two seconds later we can get it.
    clock.advanceOneMilli();
    assertThat(acquire("", TWO_MILLIS)).isPresent();
  }

  @Test
  public void testReleasedAfterRequestFinish() throws Exception {
    assertThat(acquire("", ONE_DAY)).isPresent();
    // We can't get it again while request is active
    assertThat(acquire("", ONE_DAY)).isAbsent();
    // But if request is finished, we can get it.
    when(requestStatusChecker.isRunning("current-request-id")).thenReturn(false);
    assertThat(acquire("", ONE_DAY)).isPresent();
  }

  @Test
  public void testTldsAreIndependent() throws Exception {
    Optional<Lock> lockA = acquire("a", ONE_DAY);
    assertThat(lockA).isPresent();
    // For a different tld we can still get a lock with the same name.
    Optional<Lock> lockB = acquire("b", ONE_DAY);
    assertThat(lockB).isPresent();
    // We can't get lockB again at the same time.
    assertThat(acquire("b", ONE_DAY)).isAbsent();
    // Releasing lockA has no effect on lockB (even though we are still using the "b" tld).
    lockA.get().release();
    assertThat(acquire("b", ONE_DAY)).isAbsent();
  }

  @Test
  public void testFailure_emptyResourceName() throws Exception {
    thrown.expect(IllegalArgumentException.class, "resourceName cannot be null or empty");
    Lock.acquire("", "", TWO_MILLIS, requestStatusChecker);
  }
}
