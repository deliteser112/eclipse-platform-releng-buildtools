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
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.isAtOrAfter;

import com.google.common.base.Strings;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import google.registry.model.ImmutableObject;
import google.registry.model.annotations.NotBackedUp;
import google.registry.model.annotations.NotBackedUp.Reason;
import google.registry.util.FormattingLogger;
import google.registry.util.RequestStatusChecker;
import google.registry.util.RequestStatusCheckerImpl;
import java.util.Optional;
import javax.annotation.Nullable;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * A lock on some shared resource.
 *
 * <p>Locks are either specific to a tld or global to the entire system, in which case a tld of
 * null is used.
 *
 * <p>This is the "barebone" lock implementation, that requires manual locking and unlocking. For
 * safe calls that automatically lock and unlock, see LockHandler.
 */
@Entity
@NotBackedUp(reason = Reason.TRANSIENT)
public class Lock extends ImmutableObject {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** The name of the locked resource. */
  @Id
  String lockId;

  /**
   * Unique log ID of the request that owns this lock.
   *
   * <p>When that request is no longer running (is finished), the lock can be considered implicitly
   * released.
   *
   * <p>See {@link RequestStatusCheckerImpl#getLogId} for details about how it's created in
   * practice.
   */
  String requestLogId;

  /** When the lock can be considered implicitly released. */
  DateTime expirationTime;

  /**
   * Create a new {@link Lock} for the given resource name in the specified tld (which can be
   * null for cross-tld locks).
   */
  private static Lock create(
      String resourceName,
      @Nullable String tld,
      String requestLogId,
      DateTime expirationTime) {
    checkArgument(!Strings.isNullOrEmpty(resourceName), "resourceName cannot be null or empty");
    Lock instance = new Lock();
    // Add the tld to the Lock's id so that it is unique for locks acquiring the same resource
    // across different TLDs.
    instance.lockId = makeLockId(resourceName, tld);
    instance.requestLogId = requestLogId;
    instance.expirationTime = expirationTime;
    return instance;
  }

  private static String makeLockId(String resourceName, @Nullable String tld) {
    return String.format("%s-%s", tld, resourceName);
  }

  /** Try to acquire a lock. Returns absent if it can't be acquired. */
  public static Optional<Lock> acquire(
      final String resourceName,
      @Nullable final String tld,
      final Duration leaseLength,
      final RequestStatusChecker requestStatusChecker) {
    // It's important to use transactNew rather than transact, because a Lock can be used to control
    // access to resources like GCS that can't be transactionally rolled back. Therefore, the lock
    // must be definitively acquired before it is used, even when called inside another transaction.
    return Optional.ofNullable(ofy().transactNew(new Work<Lock>() {
      @Override
      public Lock run() {
        String lockId = makeLockId(resourceName, tld);
        DateTime now = ofy().getTransactionTime();

        // Checking if an unexpired lock still exists - if so, the lock can't be acquired.
        Lock lock = ofy().load().type(Lock.class).id(lockId).now();
        if (lock != null) {
          logger.infofmt(
              "Loaded existing lock: %s for request: %s", lock.lockId, lock.requestLogId);
        }
        // TODO(b/63982642): remove check on requestLogId being null once migration is done
        // Until then we assume missing requestLogId means the app is still running (since we have
        // no information to the contrary)
        if (lock != null
            && !isAtOrAfter(now, lock.expirationTime)
            && (lock.requestLogId == null || requestStatusChecker.isRunning(lock.requestLogId))) {
          logger.infofmt(
              "Existing lock by request %s is still valid now %s (until %s) lock: %s",
              lock.requestLogId,
              now,
              lock.expirationTime,
              lockId);
          return null;
        }

        if (lock != null) {
          logger.infofmt(
              "Existing lock by request %s is timed out now %s (was valid until %s) lock: %s",
              lock.requestLogId,
              now,
              lock.expirationTime,
              lockId);
        }
        Lock newLock = create(
            resourceName,
            tld,
            requestStatusChecker.getLogId(),
            now.plus(leaseLength));
        // Locks are not parented under an EntityGroupRoot (so as to avoid write contention) and
        // don't need to be backed up.
        ofy().saveWithoutBackup().entity(newLock);
        logger.infofmt(
            "acquire succeeded %s lock: %s",
            newLock,
            lockId);
        return newLock;
      }}));
  }

  /** Release the lock. */
  public void release() {
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
}
