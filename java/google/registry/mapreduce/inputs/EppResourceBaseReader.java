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

package google.registry.mapreduce.inputs;

import static google.registry.model.EntityClasses.CLASS_TO_KIND_FUNCTION;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.tools.mapreduce.InputReader;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;
import google.registry.model.EppResource;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.EppResourceIndexBucket;
import google.registry.util.FormattingLogger;
import java.util.NoSuchElementException;

/** Base class for {@link InputReader} classes that map over {@link EppResourceIndex}. */
abstract class EppResourceBaseReader<T> extends InputReader<T> {

  static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** Number of bytes in 1MB of memory, used for memory estimates. */
  static final long ONE_MB = 1024 * 1024;

  private static final long serialVersionUID = -2970253037856017147L;

  /**
   * The resource kinds to filter for.
   *
   * <p>This can be empty, or any of {"ContactResource", "HostResource", "DomainBase"}. It will
   * never contain "EppResource", "DomainResource" or "DomainApplication" since these aren't
   * actual kinds in Datastore.
   */
  private final ImmutableSet<String> filterKinds;

  private final Key<EppResourceIndexBucket> bucketKey;
  private final long memoryEstimate;

  private Cursor cursor;
  private int total;
  private int loaded;

  private transient QueryResultIterator<EppResourceIndex> queryIterator;

  EppResourceBaseReader(
      Key<EppResourceIndexBucket> bucketKey,
      long memoryEstimate,
      ImmutableSet<String> filterKinds) {
    this.bucketKey = bucketKey;
    this.memoryEstimate = memoryEstimate;
    this.filterKinds = filterKinds;
  }

  /** Called once at start. Cache the expected size. */
  @Override
  public void beginShard() {
    total = query().count();
  }

  /** Called every time we are deserialized. Create a new query or resume an existing one. */
  @Override
  public void beginSlice() {
    Query<EppResourceIndex> query = query();
    if (cursor != null) {
      // The underlying query is strongly consistent, and according to the documentation at
      // https://cloud.google.com/appengine/docs/java/datastore/queries#Java_Data_consistency
      // "strongly consistent queries are always transactionally consistent". However, each time
      // we restart the query at a cursor we have a new effective query, and "if the results for a
      // query change between uses of a cursor, the query notices only changes that occur in
      // results after the cursor. If a new result appears before the cursor's position for the
      // query, it will not be returned when the results after the cursor are fetched."
      // What this means in practice is that entities that are created after the initial query
      // begins may or may not be seen by this reader, depending on whether the query was
      // paused and restarted with a cursor before it would have reached the new entity.
      query = query.startAt(cursor);
    }
    queryIterator = query.iterator();
  }

  /** Called occasionally alongside {@link #next}. */
  @Override
  public Double getProgress() {
    // Cap progress at 1.0, since the query's count() can increase during the run of the mapreduce
    // if more entities are written, but we've cached the value once in "total".
    return Math.min(1.0, ((double) loaded) / total);
  }

  /** Called before we are serialized. Save a serializable cursor for this query. */
  @Override
  public void endSlice() {
    cursor = queryIterator.getCursor();
  }

  /** Query for children of this bucket. */
  Query<EppResourceIndex> query() {
    Query<EppResourceIndex> query = ofy().load().type(EppResourceIndex.class).ancestor(bucketKey);
    return filterKinds.isEmpty() ? query : query.filter("kind in", filterKinds);
  }

  /** Returns the estimated memory that will be used by this reader in bytes. */
  @Override
  public long estimateMemoryRequirement() {
    return memoryEstimate;
  }

  /**
   * Get the next {@link EppResourceIndex} from the query.
   *
   * @throws NoSuchElementException if there are no more elements.
   */
  EppResourceIndex nextEri() {
    loaded++;
    try {
      return queryIterator.next();
    } finally {
      ofy().clearSessionCache();  // Try not to leak memory.
    }
  }

  static <R extends EppResource> ImmutableSet<String> varargsToKinds(
      ImmutableSet<Class<? extends R>> resourceClasses) {
    // Ignore EppResource when finding kinds, since it doesn't have one and doesn't imply filtering.
    return resourceClasses.contains(EppResource.class)
          ? ImmutableSet.<String>of()
          : FluentIterable.from(resourceClasses).transform(CLASS_TO_KIND_FUNCTION).toSet();
  }
}
