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

import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeSleeper;
import java.util.concurrent.Callable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Retrier}. */
@RunWith(JUnit4.class)
public class RetrierTest {

  @Rule
  public ExceptionRule thrown = new ExceptionRule();

  Retrier retrier = new Retrier(new FakeSleeper(new FakeClock()), 3);

  /** An exception to throw from {@link CountingThrower}. */
  class CountingException extends RuntimeException {
    CountingException(int count) {
      super("" + count);
    }
  }

  /** Test object that always throws an exception with the current count. */
  class CountingThrower implements Callable<Object> {

    int count = 0;

    @Override
    public Object call() {
      count++;
      throw new CountingException(count);
    }
  }

  @Test
  public void testRetryableException() throws Exception {
    thrown.expect(CountingException.class, "3");
    retrier.callWithRetry(new CountingThrower(), CountingException.class);
  }

  @Test
  public void testUnretryableException() throws Exception {
    thrown.expect(CountingException.class, "1");
    retrier.callWithRetry(new CountingThrower(), IllegalArgumentException.class);
  }
}
