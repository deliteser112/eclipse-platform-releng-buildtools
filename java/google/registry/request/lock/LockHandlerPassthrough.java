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

import google.registry.model.server.Lock;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.Duration;

/**
 * Implementation of {@link LockHandler} that uses Lock as is.
 *
 * This is a temporary implementation to help migrate from Lock to LockHandler. Once the migration
 * is complete - we will create a "proper" LockHandlerImpl class and remove this one.
 *
 * TODO(guyben):delete this class once LockHandlerImpl is done.
 */
public class LockHandlerPassthrough implements LockHandler {

  private static final long serialVersionUID = 6551645164118637767L;

  @Inject public LockHandlerPassthrough() {}

  /**
   * Acquire one or more locks and execute a Void {@link Callable}.
   *
   * <p>Runs on a thread that will be killed if it doesn't complete before the lease expires.
   *
   * <p>This is a simple passthrough to {@link Lock#executeWithLocks}.
   *
   * @return true if all locks were acquired and the callable was run; false otherwise.
   */
  @Override
  public boolean executeWithLocks(
      final Callable<Void> callable,
      @Nullable String tld,
      Duration leaseLength,
      String... lockNames) {
    return Lock.executeWithLocks(callable, tld, leaseLength, lockNames);
  }
}
