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

package google.registry.model.ofy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Maps.uniqueIndex;
import static com.googlecode.objectify.ObjectifyService.ofy;
import static google.registry.config.RegistryConfig.getBaseOfyRetryDuration;
import static google.registry.util.CollectionUtils.union;
import static google.registry.util.ObjectifyUtils.OBJECTS_TO_KEYS;

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.google.appengine.api.datastore.ReadPolicy.Consistency;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Deleter;
import com.googlecode.objectify.cmd.Loader;
import com.googlecode.objectify.cmd.Saver;
import google.registry.model.annotations.NotBackedUp;
import google.registry.model.annotations.VirtualEntity;
import google.registry.model.ofy.ReadOnlyWork.KillTransactionException;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import google.registry.util.Sleeper;
import google.registry.util.SystemClock;
import google.registry.util.SystemSleeper;
import java.lang.annotation.Annotation;
import java.util.Objects;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * A wrapper around ofy().
 *
 * <p>The primary purpose of this class is to add functionality to support commit logs. It is
 * simpler to wrap {@link Objectify} rather than extend it because this way we can remove some
 * methods that we don't really want exposed and add some shortcuts.
 */
public class Ofy {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /**
   * Recommended memcache expiration time, which is one hour, specified in seconds.
   *
   * <p>This value should used as a cache expiration time for any entities annotated with an
   * Objectify {@code @Cache} annotation, to put an upper bound on unlikely-but-possible divergence
   * between memcache and datastore when a memcache write fails.
   */
  public static final int RECOMMENDED_MEMCACHE_EXPIRATION = 3600;

  /** Default clock for transactions that don't provide one. */
  @NonFinalForTesting
  static Clock clock = new SystemClock();

  /** Default sleeper for transactions that don't provide one. */
  @NonFinalForTesting
  static Sleeper sleeper = new SystemSleeper();

  /**
   * An injected clock that overrides the static clock.
   *
   * <p>Eventually the static clock should go away when we are 100% injected, but for now we need to
   * preserve the old way of overriding the clock in tests by changing the static field.
   */
  private final Clock injectedClock;

  /** Retry for 8^2 * 100ms = ~25 seconds. */
  private static final int NUM_RETRIES = 8;

  @Inject
  public Ofy(Clock injectedClock) {
    this.injectedClock = injectedClock;
  }

  /**
   * Thread local transaction info. There can only be one active transaction on a thread at a given
   * time, and this will hold metadata for it.
   */
  static final ThreadLocal<TransactionInfo> TRANSACTION_INFO = new ThreadLocal<>();

  /** Returns the wrapped Objectify's ObjectifyFactory. */
  public ObjectifyFactory factory() {
    return ofy().factory();
  }

  /** Clears the session cache. */
  public void clearSessionCache() {
    ofy().clear();
  }

  public boolean inTransaction() {
    return ofy().getTransaction() != null;
  }

  public void assertInTransaction() {
    checkState(inTransaction(), "Must be called in a transaction");
  }

  public Loader load() {
    return ofy().load();
  }

  public Loader loadEventuallyConsistent() {
    return ofy().consistency(Consistency.EVENTUAL).load();
  }

  /**
   * Delete, augmented to enroll the deleted entities in a commit log.
   *
   * <p>We only allow this in transactions so commit logs can be written in tandem with the delete.
   */
  public Deleter delete() {
    return new AugmentedDeleter() {
      @Override
      protected void handleDeletion(Iterable<Key<?>> keys) {
        assertInTransaction();
        checkState(Iterables.all(keys, notNull()), "Can't delete a null key.");
        checkProhibitedAnnotations(keys, NotBackedUp.class, VirtualEntity.class);
        TRANSACTION_INFO.get().putDeletes(keys);
      }
    };
  }

  /**
   * Delete, without any augmentations except to check that we're not saving any virtual entities.
   *
   * <p>No backups get written.
   */
  public Deleter deleteWithoutBackup() {
    return new AugmentedDeleter() {
      @Override
      protected void handleDeletion(Iterable<Key<?>> keys) {
        checkProhibitedAnnotations(keys, VirtualEntity.class);
      }
    };
  }

  /**
   * Save, augmented to enroll the saved entities in a commit log and to check that we're not saving
   * virtual entities.
   *
   * <p>We only allow this in transactions so commit logs can be written in tandem with the save.
   */
  public Saver save() {
    return new AugmentedSaver() {
      @Override
      protected void handleSave(Iterable<?> entities) {
        assertInTransaction();
        checkState(Iterables.all(entities, notNull()), "Can't save a null entity.");
        checkProhibitedAnnotations(entities, NotBackedUp.class, VirtualEntity.class);
        ImmutableMap<Key<?>, ?> keysToEntities = uniqueIndex(entities, OBJECTS_TO_KEYS);
        TRANSACTION_INFO.get().putSaves(keysToEntities);
      }
    };
  }

  /**
   * Save, without any augmentations except to check that we're not saving any virtual entities.
   *
   * <p>No backups get written.
   */
  public Saver saveWithoutBackup() {
    return new AugmentedSaver() {
      @Override
      protected void handleSave(Iterable<?> entities) {
        checkProhibitedAnnotations(entities, VirtualEntity.class);
      }
    };
  }

  private Clock getClock() {
    return injectedClock == null ? clock : injectedClock;
  }

  /** Execute a transaction. */
  public <R> R transact(Work<R> work) {
    // If we are already in a transaction, don't wrap in a CommitLoggedWork.
    return inTransaction() ? work.run() : transactNew(work);
  }

  /** Pause the current transaction (if any) and complete this one before returning to it. */
  public <R> R transactNew(Work<R> work) {
    // Wrap the Work in a CommitLoggedWork so that we can give transactions a frozen view of time
    // and maintain commit logs for them.
    return transactCommitLoggedWork(new CommitLoggedWork<>(work, getClock()));
  }

  /**
   * Transact with commit logs and retry with exponential backoff.
   *
   * <p>This method is broken out from {@link #transactNew(Work)} for testing purposes.
   */
  @VisibleForTesting
  <R> R transactCommitLoggedWork(CommitLoggedWork<R> work) {
    long baseRetryMillis = getBaseOfyRetryDuration().getMillis();
    for (long attempt = 0, sleepMillis = baseRetryMillis;
        true;
        attempt++, sleepMillis *= 2) {
      try {
        ofy().transactNew(work);
        return work.getResult();
      } catch (TransientFailureException
          | TimestampInversionException
          | DatastoreTimeoutException
          | DatastoreFailureException e) {
        // TransientFailureExceptions come from task queues and always mean nothing committed.
        // TimestampInversionExceptions are thrown by our code and are always retryable as well.
        // However, datastore exceptions might get thrown even if the transaction succeeded.
        if ((e instanceof DatastoreTimeoutException || e instanceof DatastoreFailureException)
            && checkIfAlreadySucceeded(work)) {
          return work.getResult();
        }
        if (attempt == NUM_RETRIES) {
          throw e;  // Give up.
        }
        sleeper.sleepUninterruptibly(Duration.millis(sleepMillis));
        logger.infofmt(e, "Retrying %s, attempt %s", e.getClass().getSimpleName(), attempt);
      }
    }
  }

  /**
   * We can determine whether a transaction has succeded by trying to read the commit log back in
   * its own retryable read-only transaction.
   */
  private <R> Boolean checkIfAlreadySucceeded(final CommitLoggedWork<R> work) {
      return work.hasRun() && transactNewReadOnly(new Work<Boolean>() {
        @Override
        public Boolean run() {
          CommitLogManifest manifest = work.getManifest();
          if (manifest == null) {
            // Work ran but no commit log was created. This might mean that the transaction did not
            // write anything to datastore. We can safely retry because it only reads. (Although the
            // transaction might have written a task to a queue, we consider that safe to retry too
            // since we generally assume that tasks might be doubly executed.) Alternatively it
            // might mean that the transaction wrote to datastore but turned off commit logs by
            // exclusively using save/deleteWithoutBackups() rather than save/delete(). Although we
            // have no hard proof that retrying is safe, we use these methods judiciously and it is
            // reasonable to assume that if the transaction really did succeed that the retry will
            // either be idempotent or will fail with a non-transient error.
            return false;
          }
          return Objects.equals(
              union(work.getMutations(), manifest),
              ImmutableSet.copyOf(ofy().load().ancestor(manifest)));
        }});
  }

  /** A read-only transaction is useful to get strongly consistent reads at a shared timestamp. */
  public <R> R transactNewReadOnly(Work<R> work) {
    ReadOnlyWork<R> readOnlyWork = new ReadOnlyWork<>(work, getClock());
    try {
      ofy().transactNew(readOnlyWork);
    } catch (TransientFailureException | DatastoreTimeoutException | DatastoreFailureException e) {
      // These are always retryable for a read-only operation.
      return transactNewReadOnly(work);
    } catch (KillTransactionException e) {
      // Expected; we killed the transaction as a safety measure, and now we can return the result.
      return readOnlyWork.getResult();
    }
    throw new AssertionError();  // How on earth did we get here?
  }

  /** Execute some work in a transactionless context. */
  public <R> R doTransactionless(Work<R> work) {
    try {
      com.googlecode.objectify.ObjectifyService.push(
          com.googlecode.objectify.ObjectifyService.ofy().transactionless());
      return work.run();
    } finally {
      com.googlecode.objectify.ObjectifyService.pop();
    }
  }

  /**
   * Execute some work with a fresh session cache.
   *
   * <p>This is useful in cases where we want to load the latest possible data from datastore but
   * don't need point-in-time consistency across loads and consequently don't need a transaction.
   * Note that unlike a transaction's fresh session cache, the contents of this cache will be
   * discarded once the work completes, rather than being propagated into the enclosing session.
   */
  public <R> R doWithFreshSessionCache(Work<R> work) {
    try {
      com.googlecode.objectify.ObjectifyService.push(
          com.googlecode.objectify.ObjectifyService.factory().begin());
      return work.run();
    } finally {
      com.googlecode.objectify.ObjectifyService.pop();
    }
  }

  /** Get the time associated with the start of this particular transaction attempt. */
  public DateTime getTransactionTime() {
    assertInTransaction();
    return TRANSACTION_INFO.get().transactionTime;
  }

  /** Returns key of {@link CommitLogManifest} that will be saved when the transaction ends. */
  public Key<CommitLogManifest> getCommitLogManifestKey() {
    assertInTransaction();
    TransactionInfo info = TRANSACTION_INFO.get();
    return Key.create(info.bucketKey, CommitLogManifest.class, info.transactionTime.getMillis());
  }

  /**
   * Returns the @Entity-annotated base class for an object that is either an {@code Key<?>} or an
   * object of an entity class registered with Objectify.
   */
  @VisibleForTesting
  static Class<?> getBaseEntityClassFromEntityOrKey(Object entityOrKey) {
    // Convert both keys and entities into keys, so that we get consistent behavior in either case.
    Key<?> key = (entityOrKey instanceof Key<?> ? (Key<?>) entityOrKey : Key.create(entityOrKey));
    // Get the entity class associated with this key's kind, which should be the base @Entity class
    // from which the kind name is derived.  Don't be tempted to use getMetadata(String kind) or
    // getMetadataForEntity(T pojo) instead; the former won't throw an exception for an unknown
    // kind (it just returns null) and the latter will return the @EntitySubclass if there is one.
    return ofy().factory().getMetadata(key).getEntityClass();
  }

  /**
   * Checks that the base @Entity classes for the provided entities or keys don't have any of the
   * specified forbidden annotations.
   */
  @SafeVarargs
  private static void checkProhibitedAnnotations(
      Iterable<?> entitiesOrKeys, Class<? extends Annotation>... annotations) {
    for (Object entityOrKey : entitiesOrKeys) {
      Class<?> entityClass = getBaseEntityClassFromEntityOrKey(entityOrKey);
      for (Class<? extends Annotation> annotation : annotations) {
        checkArgument(!entityClass.isAnnotationPresent(annotation),
            "Can't save/delete a @%s entity: %s", annotation.getSimpleName(), entityClass);
      }
    }
  }
}
