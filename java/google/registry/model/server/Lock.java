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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.isAtOrAfter;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedSet;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import google.registry.model.ImmutableObject;
import google.registry.model.annotations.NotBackedUp;
import google.registry.model.annotations.NotBackedUp.Reason;
import google.registry.util.AppEngineTimeLimiter;
import google.registry.util.FormattingLogger;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * A lock on some shared resource. Locks are either specific to a tld or global to the entire
 * system, in which case a tld of null is used.
 */
@Entity
@NotBackedUp(reason = Reason.TRANSIENT)
public class Lock extends ImmutableObject {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** Fudge factor to make sure we kill threads before a lock actually expires. */
  private static final Duration LOCK_TIMEOUT_FUDGE = Duration.standardSeconds(5);

  /** The name of the locked resource. */
  @Id
  String lockId;

  /** When the lock can be considered implicitly released. */
  DateTime expirationTime;

  /**
   * Create a new {@link Lock} for the given resource name in the specified tld (which can be
   * null for cross-tld locks).
   */
  private static Lock create(
      String resourceName,
      @Nullable String tld,
      DateTime expirationTime) {
    checkArgument(!Strings.isNullOrEmpty(resourceName), "resourceName cannot be null or empty");
    Lock instance = new Lock();
    // Add the tld to the Lock's id so that it is unique for locks acquiring the same resource
    // across different TLDs.
    instance.lockId = makeLockId(resourceName, tld);
    instance.expirationTime = expirationTime;
    return instance;
  }

  private static String makeLockId(String resourceName, @Nullable String tld) {
    return String.format("%s-%s", tld, resourceName);
  }

  /** Try to acquire a lock. Returns null if it can't be acquired. */
  static Lock acquire(
      final String resourceName,
      @Nullable final String tld,
      final Duration leaseLength) {
    // It's important to use transactNew rather than transact, because a Lock can be used to control
    // access to resources like GCS that can't be transactionally rolled back. Therefore, the lock
    // must be definitively acquired before it is used, even when called inside another transaction.
    return ofy().transactNew(new Work<Lock>() {
      @Override
      public Lock run() {
        String lockId = makeLockId(resourceName, tld);
        DateTime now = ofy().getTransactionTime();

        // Checking if an unexpired lock still exists - if so, the lock can't be acquired.
        Lock lock = ofy().load().type(Lock.class).id(lockId).now();
        if (lock != null && !isAtOrAfter(now, lock.expirationTime)) {
          logger.infofmt(
              "Existing lock is still valid now %s (until %s) lock: %s",
              now,
              lock.expirationTime,
              lockId);
          return null;
        }

        if (lock != null) {
          logger.infofmt(
              "Existing lock is timed out now %s (was valid until %s) lock: %s",
              now,
              lock.expirationTime,
              lockId);
        }
        Lock newLock = create(
            resourceName,
            tld,
            now.plus(leaseLength));
        // Locks are not parented under an EntityGroupRoot (so as to avoid write contention) and
        // don't need to be backed up.
        ofy().saveWithoutBackup().entity(newLock);
        logger.infofmt(
            "acquire succeeded %s lock: %s",
            newLock,
            lockId);
        return newLock;
      }});
  }

  /** Release the lock. */
  void release() {
    // Just use the default clock because we aren't actually doing anything that will use the clock.
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        // To release a lock, check that no one else has already obtained it and if not delete it.
        // If the lock in Datastore was different then this lock is gone already; this can happen
        // if release() is called around the expiration time and the lock expires underneath us.
        Lock loadedLock = ofy().load().type(Lock.class).id(lockId).now();
        if (Lock.this.equals(loadedLock)) {
          // Use noBackupOfy() so that we don't create a commit log entry for deleting the lock.
          logger.infofmt("Deleting lock: %s", lockId);
          ofy().deleteWithoutBackup().entity(Lock.this);
        } else {
          logger.severefmt(
              "The lock we acquired was transferred to someone else before we"
              + " released it! Did action take longer than lease length?"
              + " Our lock: %s, current lock: %s",
              Lock.this,
              loadedLock);
          logger.infofmt("Not deleting lock: %s - someone else has it: %s", lockId, loadedLock);
        }
      }});
  }

  /**
   * Acquire one or more locks and execute a Void {@link Callable} on a thread that will be
   * killed if it doesn't complete before the lease expires.
   *
   * <p>Note that locks are specific either to a given tld or to the entire system (in which case
   * tld should be passed as null).
   *
   * @return whether all locks were acquired and the callable was run.
   */
  public static boolean executeWithLocks(
      final Callable<Void> callable,
      @Nullable String tld,
      Duration leaseLength,
      String... lockNames) {
    try {
      return AppEngineTimeLimiter.create().callWithTimeout(
          new LockingCallable(callable, Strings.emptyToNull(tld), leaseLength, lockNames),
          leaseLength.minus(LOCK_TIMEOUT_FUDGE).getMillis(),
          TimeUnit.MILLISECONDS,
          true);
    } catch (Exception e) {
      throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  /** A {@link Callable} that acquires and releases a lock around a delegate {@link Callable}. */
  private static class LockingCallable implements Callable<Boolean> {
    final Callable<Void> delegate;
    @Nullable final String tld;
    final Duration leaseLength;
    final Set<String> lockNames;

    LockingCallable(
        Callable<Void> delegate,
        String tld,
        Duration leaseLength,
        String... lockNames) {
      checkArgument(leaseLength.isLongerThan(LOCK_TIMEOUT_FUDGE));
      this.delegate = delegate;
      this.tld = tld;
      this.leaseLength = leaseLength;
      // Make sure we join locks in a fixed (lexicographical) order to avoid deadlock.
      this.lockNames = ImmutableSortedSet.copyOf(lockNames);
    }

    @Override
    public Boolean call() throws Exception {
      Set<Lock> acquiredLocks = new HashSet<>();
      try {
        for (String lockName : lockNames) {
          Lock lock = acquire(lockName, tld, leaseLength);
          if (lock == null) {
            logger.infofmt("Couldn't acquire lock: %s", lockName);
            return false;
          }
          logger.infofmt("Acquired lock: %s", lockName);
          acquiredLocks.add(lock);
        }
        delegate.call();
        return true;
      } finally {
        for (Lock lock : acquiredLocks) {
          lock.release();
          logger.infofmt("Released lock: %s", lock.lockId);
        }
      }
    }
  }
}
