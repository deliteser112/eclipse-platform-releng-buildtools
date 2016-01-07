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

package google.registry.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckReturnValue;
import org.junit.rules.ExternalResource;

/** JUnit Rule that uses Mockito to spy on I/O streams to make sure they're healthy. */
public final class IoSpyRule extends ExternalResource {

  private boolean checkClosedOnlyOnce;
  private boolean checkClosedAtLeastOnce;
  private int checkCharIoMaxCalls = -1;
  private final List<Closeable> spiedCloseables = new ArrayList<>();
  private final List<InputStream> spiedInputStreams = new ArrayList<>();
  private final List<OutputStream> spiedOutputStreams = new ArrayList<>();

  public IoSpyRule() {}

  /**
   * Enables check where {@link Closeable#close() close} must be called EXACTLY once.
   *
   * <p>This is sort of pedantic, since Java's contract for close specifies that it must permit
   * multiple calls.
   */
  public IoSpyRule checkClosedOnlyOnce() {
    checkState(!checkClosedAtLeastOnce, "you're already using checkClosedAtLeastOnce()");
    checkClosedOnlyOnce = true;
    return this;
  }

  /** Enables check where {@link Closeable#close() close} must be called at least once. */
  public IoSpyRule checkClosedAtLeastOnce() {
    checkState(!checkClosedOnlyOnce, "you're already using checkClosedOnlyOnce()");
    checkClosedAtLeastOnce = true;
    return this;
  }

  /** Enables check to make sure your streams aren't going too slow with char-based I/O. */
  public IoSpyRule checkCharIoMaxCalls(int value) {
    checkArgument(value >= 0, "value >= 0");
    checkCharIoMaxCalls = value;
    return this;
  }

  /** Adds your {@link Closeable} to the list of streams to check, and returns its mocked self. */
  @CheckReturnValue
  public <T extends Closeable> T register(T stream) {
    T res = spy(stream);
    spiedCloseables.add(res);
    if (stream instanceof InputStream) {
      spiedInputStreams.add((InputStream) res);
    }
    if (stream instanceof OutputStream) {
      spiedOutputStreams.add((OutputStream) res);
    }
    return res;
  }

  @Override
  protected void after() {
    checkState(checkClosedOnlyOnce
        || checkClosedAtLeastOnce
        || checkCharIoMaxCalls != -1,
        "At least one check must be enabled.");
    try {
      check();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      spiedCloseables.clear();
      spiedInputStreams.clear();
      spiedOutputStreams.clear();
    }
  }

  private void check() throws IOException {
    for (Closeable stream : spiedCloseables) {
      if (checkClosedAtLeastOnce) {
        verify(stream, atLeastOnce()).close();
      } else if (checkClosedOnlyOnce) {
        verify(stream, times(1)).close();
      }
    }
    if (checkCharIoMaxCalls != -1) {
      for (InputStream stream : spiedInputStreams) {
        verify(stream, atMost(checkCharIoMaxCalls)).read();
      }
      for (OutputStream stream : spiedOutputStreams) {
        verify(stream, atMost(checkCharIoMaxCalls)).write(anyInt());
      }
    }
  }
}
