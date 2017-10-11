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

package google.registry.request.lock;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import google.registry.model.server.Lock;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LockHandler}. */
@RunWith(JUnit4.class)
public final class LockHandlerImplTest {

  private static final Duration ONE_DAY = Duration.standardDays(1);

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private static class CountingCallable implements Callable<Void> {
    int numCalled = 0;

    @Override
    public Void call() {
      numCalled += 1;
      return null;
    }
  }

  private static class ThrowingCallable implements Callable<Void> {
    Exception exception;

    ThrowingCallable(Exception exception) {
      this.exception = exception;
    }

    @Override
    public Void call() throws Exception {
      throw exception;
    }
  }

  private boolean executeWithLocks(Callable<Void> callable, final @Nullable Lock acquiredLock) {
    LockHandler lockHandler = new LockHandlerImpl() {
      private static final long serialVersionUID = 0L;
      @Override
      Optional<Lock> acquire(String resourceName, String tld, Duration leaseLength) {
        assertThat(resourceName).isEqualTo("resourceName");
        assertThat(tld).isEqualTo("tld");
        assertThat(leaseLength).isEqualTo(ONE_DAY);
        return Optional.ofNullable(acquiredLock);
      }
    };

    return lockHandler.executeWithLocks(callable, "tld", ONE_DAY, "resourceName");
  }

  @Before public void setUp() {
  }

  @Test
  public void testLockSucceeds() throws Exception {
    Lock lock = mock(Lock.class);
    CountingCallable countingCallable = new CountingCallable();
    assertThat(executeWithLocks(countingCallable, lock)).isTrue();
    assertThat(countingCallable.numCalled).isEqualTo(1);
    verify(lock, times(1)).release();
  }

  @Test
  public void testLockSucceeds_uncheckedException() throws Exception {
    Lock lock = mock(Lock.class);
    Exception expectedException = new RuntimeException("test");
    try {
      executeWithLocks(new ThrowingCallable(expectedException), lock);
      fail("Expected RuntimeException");
    } catch (RuntimeException exception) {
      assertThat(exception).isSameAs(expectedException);
    }
    verify(lock, times(1)).release();
  }

  @Test
  public void testLockSucceeds_checkedException() throws Exception {
    Lock lock = mock(Lock.class);
    Exception expectedException = new Exception("test");
    try {
      executeWithLocks(new ThrowingCallable(expectedException), lock);
      fail("Expected RuntimeException");
    } catch (RuntimeException exception) {
      assertThat(exception).hasCauseThat().isSameAs(expectedException);
    }
    verify(lock, times(1)).release();
  }

  @Test
  public void testLockFailed() throws Exception {
    Lock lock = null;
    CountingCallable countingCallable = new CountingCallable();
    assertThat(executeWithLocks(countingCallable, lock)).isFalse();
    assertThat(countingCallable.numCalled).isEqualTo(0);
  }
}
