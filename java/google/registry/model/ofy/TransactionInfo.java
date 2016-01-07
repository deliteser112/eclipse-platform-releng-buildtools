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

package google.registry.model.ofy;

import static com.google.common.base.Functions.constant;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.filterValues;
import static com.google.common.collect.Maps.toMap;
import static com.googlecode.objectify.ObjectifyService.ofy;
import static google.registry.model.ofy.CommitLogBucket.getArbitraryBucketId;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import java.util.Map;
import org.joda.time.DateTime;

/** Metadata for an {@link Ofy} transaction that saves commit logs. */
class TransactionInfo {

  private static final Predicate<Object> IS_DELETE = Predicates.<Object>equalTo(Delete.SENTINEL);

  private enum Delete { SENTINEL }

  /** Logical "now" of the transaction. */
  DateTime transactionTime;

  /** Whether this is a read-only transaction. */
  private boolean readOnly;

  /** Bucket shard to under which commit log will be stored, chosen at random (in production). */
  final Key<CommitLogBucket> bucketKey = CommitLogBucket.getBucketKey(getArbitraryBucketId());

  /**
   * Accumulator of save/delete operations performed in transaction.
   *
   * <p>The {@link ImmutableMap} builder provides us the benefit of not permitting duplicates.
   * This allows us to avoid potential race conditions where the same key is mutated twice in a
   * transaction.
   */
  private final ImmutableMap.Builder<Key<?>, Object> changesBuilder = new ImmutableMap.Builder<>();

  TransactionInfo(DateTime now) {
    this.transactionTime = now;
    ofy().load().key(bucketKey);  // Asynchronously load value into session cache.
  }

  TransactionInfo setReadOnly() {
    this.readOnly = true;
    return this;
  }

  void assertNotReadOnly() {
    checkState(!readOnly, "This is a read only transaction.");
  }

  void putSaves(Map<Key<?>, ?> keysToEntities) {
    assertNotReadOnly();
    changesBuilder.putAll(keysToEntities);
  }

  void putDeletes(Iterable<Key<?>> keys) {
    assertNotReadOnly();
    changesBuilder.putAll(toMap(keys, constant(TransactionInfo.Delete.SENTINEL)));
  }

  ImmutableSet<Key<?>> getTouchedKeys() {
    return ImmutableSet.copyOf(changesBuilder.build().keySet());
  }

  ImmutableSet<Key<?>> getDeletes() {
    return ImmutableSet.copyOf(filterValues(changesBuilder.build(), IS_DELETE).keySet());
  }

  ImmutableSet<Object> getSaves() {
    return FluentIterable
        .from(changesBuilder.build().values())
        .filter(Predicates.not(IS_DELETE))
        .toSet();
  }
}
