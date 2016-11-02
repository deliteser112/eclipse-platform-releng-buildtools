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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Iterables.skip;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.nullToEmpty;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static google.registry.util.DateTimeUtils.isAtOrAfter;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
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
import java.util.LinkedHashSet;
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
   * Insertion-ordered set of classes requesting access to the lock.
   *
   * <p>A class can only acquire the lock if the queue is empty or if it is at the top of the
   * queue.  This allows us to prevent starvation between processes competing for the lock.
   */
  LinkedHashSet<String> queue = new LinkedHashSet<>();

  /**
   * Create a new {@link Lock} for the given resource name in the specified tld (which can be
   * null for cross-tld locks).
   */
  private static Lock create(
      String resourceName,
      @Nullable String tld,
      DateTime expirationTime,
      LinkedHashSet<String> queue) {
    checkArgument(!Strings.isNullOrEmpty(resourceName), "resourceName cannot be null or empty");
    Lock instance = new Lock();
    // Add the tld to the Lock's id so that it is unique for locks acquiring the same resource
    // across different TLDs.
    instance.lockId = makeLockId(resourceName, tld);
    instance.expirationTime = expirationTime;
    instance.queue = queue;
    return instance;
  }

  private static String makeLockId(String resourceName, @Nullable String tld) {
    return String.format("%s-%s", tld, resourceName);
  }

  /** Join the queue waiting on this lock (unless you are already in the queue). */
  static void joinQueue(
      final Class<?> requester,
      final String resourceName,
      @Nullable final String tld) {
    // This transaction doesn't use the clock, so it's fine to use the default.
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        Lock lock = ofy().load().type(Lock.class).id(makeLockId(resourceName, tld)).now();
        LinkedHashSet<String> queue = (lock == null)
            ? new LinkedHashSet<String>() : newLinkedHashSet(lock.queue);
        queue.add(requester.getCanonicalName());
        DateTime expirationTime = (lock == null) ? START_OF_TIME : lock.expirationTime;
        ofy().saveWithoutBackup().entity(create(resourceName, tld, expirationTime, queue));
      }});
  }

  /** Try to acquire a lock. Returns null if it can't be acquired. */
  static Lock acquire(
      final Class<?> requester,
      final String resourceName,
      @Nullable final String tld,
      final Duration leaseLength) {
    // It's important to use transactNew rather than transact, because a Lock can be used to control
    // access to resources like GCS that can't be transactionally rolled back. Therefore, the lock
    // must be definitively acquired before it is used, even when called inside another transaction.
    return ofy().transactNew(new Work<Lock>() {
      @Override
      public Lock run() {
        Lock lock = ofy().load().type(Lock.class).id(makeLockId(resourceName, tld)).now();
        if (lock == null || isAtOrAfter(ofy().getTransactionTime(), lock.expirationTime)) {
          String requesterName = (requester == null) ? "" : requester.getCanonicalName();
          if (!getFirst(nullToEmpty((lock == null) ? null : lock.queue), requesterName)
              .equals(requesterName)) {
            // Another class is at the top of the queue; we can't acquire the lock.
            return null;
          }
          Lock newLock = create(
              resourceName,
              tld,
              ofy().getTransactionTime().plus(leaseLength),
              newLinkedHashSet((lock == null)
                  ? ImmutableList.<String>of() : skip(lock.queue, 1)));
          // Locks are not parented under an EntityGroupRoot (so as to avoid write contention) and
          // don't need to be backed up.
          ofy().saveWithoutBackup().entity(newLock);
          return newLock;
        }
        return null;
      }});
  }

  /** Release the lock. */
  void release() {
    // Just use the default clock because we aren't actually doing anything that will use the clock.
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        // To release a lock, check that no one else has already obtained it and if not delete it.
        // If the lock in datastore was different then this lock is gone already; this can happen
        // if release() is called around the expiration time and the lock expires underneath us.
        Lock loadedLock = ofy().load().type(Lock.class).id(lockId).now();
        if (Lock.this.equals(loadedLock)) {
          // Use noBackupOfy() so that we don't create a commit log entry for deleting the lock.
          ofy().deleteWithoutBackup().entity(Lock.this);
        }
      }});
  }

  /**
   * Acquire one or more locks and execute a Void {@link Callable} on a thread that will be
   * killed if it doesn't complete before the lease expires.
   *
   * <p>If the requester isn't null, this will join each lock's queue before attempting to acquire
   * that lock. Clients that are concerned with starvation should specify a requester and those that
   * aren't shouldn't.
   *
   * <p>Note that locks are specific either to a given tld or to the entire system (in which case
   * tld should be passed as null).
   *
   * @return whether all locks were acquired and the callable was run.
   */
  public static boolean executeWithLocks(
      final Callable<Void> callable,
      @Nullable Class<?> requester,
      @Nullable String tld,
      Duration leaseLength,
      String... lockNames) {
    try {
      return AppEngineTimeLimiter.create().callWithTimeout(
          new LockingCallable(
              callable, requester, Strings.emptyToNull(tld), leaseLength, lockNames),
          leaseLength.minus(LOCK_TIMEOUT_FUDGE).getMillis(),
          TimeUnit.MILLISECONDS,
          true);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  /** A {@link Callable} that acquires and releases a lock around a delegate {@link Callable}. */
  private static class LockingCallable implements Callable<Boolean> {
    final Callable<Void> delegate;
    final Class<?> requester;
    @Nullable final String tld;
    final Duration leaseLength;
    final Set<String> lockNames;

    LockingCallable(
        Callable<Void> delegate,
        Class<?> requester,
        String tld,
        Duration leaseLength,
        String... lockNames) {
      checkArgument(leaseLength.isLongerThan(LOCK_TIMEOUT_FUDGE));
      this.delegate = delegate;
      this.requester = requester;
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
          if (requester != null) {
            joinQueue(requester, lockName, tld);
          }
          Lock lock = acquire(requester, lockName, tld, leaseLength);
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
