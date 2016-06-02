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

package google.registry.model.ofy;

import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.DatastoreAttributes;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Index;
import com.google.appengine.api.datastore.Index.IndexState;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyRange;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** A proxy for {@link AsyncDatastoreService} that exposes call counts. */
public class RequestCountingAsyncDatastoreService implements AsyncDatastoreService {

  private final AsyncDatastoreService delegate;

  // We use static counters because we care about overall calls to datastore, not calls via a
  // specific instance of the service.

  private static AtomicInteger reads = new AtomicInteger();
  private static AtomicInteger puts = new AtomicInteger();
  private static AtomicInteger deletes = new AtomicInteger();

  RequestCountingAsyncDatastoreService(AsyncDatastoreService delegate) {
    this.delegate = delegate;
  }

  public static int getReadsCount() {
    return reads.get();
  }

  public static int getPutsCount() {
    return puts.get();
  }

  public static int getDeletesCount() {
    return deletes.get();
  }

  @Override
  public Collection<Transaction> getActiveTransactions() {
    return delegate.getActiveTransactions();
  }

  @Override
  public Transaction getCurrentTransaction() {
    return delegate.getCurrentTransaction();
  }

  @Override
  public Transaction getCurrentTransaction(Transaction transaction) {
    return delegate.getCurrentTransaction(transaction);
  }

  @Override
  public PreparedQuery prepare(Query query) {
    return delegate.prepare(query);
  }

  @Override
  public PreparedQuery prepare(Transaction transaction, Query query) {
    return delegate.prepare(transaction, query);
  }

  @Override
  public Future<KeyRange> allocateIds(String kind, long num) {
    return delegate.allocateIds(kind, num);
  }

  @Override
  public Future<KeyRange> allocateIds(Key parent, String kind, long num) {
    return delegate.allocateIds(parent, kind, num);
  }

  @Override
  public Future<Transaction> beginTransaction() {
    return delegate.beginTransaction();
  }

  @Override
  public Future<Transaction> beginTransaction(TransactionOptions transaction) {
    return delegate.beginTransaction(transaction);
  }

  @Override
  public Future<Void> delete(Key... keys) {
    deletes.incrementAndGet();
    return delegate.delete(keys);
  }

  @Override
  public Future<Void> delete(Iterable<Key> keys) {
    deletes.incrementAndGet();
    return delegate.delete(keys);
  }

  @Override
  public Future<Void> delete(Transaction transaction, Key... keys) {
    deletes.incrementAndGet();
    return delegate.delete(transaction, keys);
  }

  @Override
  public Future<Void> delete(Transaction transaction, Iterable<Key> keys) {
    deletes.incrementAndGet();
    return delegate.delete(transaction, keys);
  }

  @Override
  public Future<Entity> get(Key key) {
    reads.incrementAndGet();
    return delegate.get(key);
  }

  @Override
  public Future<Map<Key, Entity>> get(Iterable<Key> keys) {
    reads.incrementAndGet();
    return delegate.get(keys);
  }

  @Override
  public Future<Entity> get(Transaction transaction, Key key) {
    reads.incrementAndGet();
    return delegate.get(transaction, key);
  }

  @Override
  public Future<Map<Key, Entity>> get(Transaction transaction, Iterable<Key> keys) {
    reads.incrementAndGet();
    return delegate.get(transaction, keys);
  }

  @Override
  public Future<DatastoreAttributes> getDatastoreAttributes() {
    return delegate.getDatastoreAttributes();
  }

  @Override
  public Future<Map<Index, IndexState>> getIndexes() {
    return delegate.getIndexes();
  }

  @Override
  public Future<Key> put(Entity entity) {
    puts.incrementAndGet();
    return delegate.put(entity);
  }

  @Override
  public Future<List<Key>> put(Iterable<Entity> entities) {
    puts.incrementAndGet();
    return delegate.put(entities);
  }

  @Override
  public Future<Key> put(Transaction transaction, Entity entity) {
    puts.incrementAndGet();
    return delegate.put(transaction, entity);
  }

  @Override
  public Future<List<Key>> put(Transaction transaction, Iterable<Entity> entities) {
    puts.incrementAndGet();
    return delegate.put(transaction, entities);
  }
}
