// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.mapreduce;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Lists.asList;
import static com.google.domain.registry.model.EntityClasses.CLASS_TO_KIND_FUNCTION;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.util.CollectionUtils.difference;
import static com.google.domain.registry.util.TypeUtils.hasAnnotation;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.InputReader;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.EppResourceIndexBucket;
import com.google.domain.registry.util.FormattingLogger;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import com.googlecode.objectify.annotation.EntitySubclass;
import com.googlecode.objectify.cmd.Query;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Mapreduce {@link Input} types (and related helpers) for {@link EppResource} keys and objects.
 *
 * <p>The inputs provided by this class are not deletion-aware and do not project the resources
 * forward in time. That is the responsibility of mappers that use these inputs.
 */
public class EppResourceInputs {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** Number of bytes in 1MB of memory, used for memory estimates. */
  private static final long ONE_MB = 1024 * 1024;

  /** Returns a MapReduce {@link Input} that loads all {@link EppResourceIndex} objects. */
  public static <R extends EppResource> Input<EppResourceIndex> createIndexInput() {
    return new IndexInput();
  }

  /**
   * Returns a MapReduce {@link Input} that loads all {@link EppResource} objects of a given type,
   * including deleted resources.
   *
   * <p>Note: Do not concatenate multiple EntityInputs together (this is inefficient as it iterates
   * through all buckets multiple times). Specify the types in a single input, or load all types by
   * specifying {@link EppResource} as the class.
   */
  @SafeVarargs
  public static <R extends EppResource> Input<R> createEntityInput(
      Class<? extends R> resourceClass,
      Class<? extends R>... moreResourceClasses) {
    return new EntityInput<R>(ImmutableSet.copyOf(asList(resourceClass, moreResourceClasses)));
  }

  /**
   * Returns a MapReduce {@link Input} that loads keys to all {@link EppResource} objects of a given
   * type, including deleted resources.
   *
   * <p>Note: Do not concatenate multiple KeyInputs together (this is inefficient as it iterates
   * through all buckets multiple times). Specify the types in a single input, or load all types by
   * specifying {@link EppResource} as the class.
   */
  @SafeVarargs
  public static <R extends EppResource> Input<Key<R>> createKeyInput(
      Class<? extends R> resourceClass,
      Class<? extends R>... moreResourceClasses) {
    ImmutableSet<Class<? extends R>> resourceClasses =
        ImmutableSet.copyOf(asList(resourceClass, moreResourceClasses));
    checkArgument(
        all(resourceClasses, not(hasAnnotation(EntitySubclass.class))),
        "Mapping over keys requires a non-polymorphic Entity");
    return new KeyInput<>(resourceClasses);
  }

  /** Base class for {@link Input} classes that map over {@link EppResourceIndex}. */
  private abstract static class BaseInput<I> extends Input<I> {

    private static final long serialVersionUID = -6681886718929462122L;

    @Override
    public List<InputReader<I>> createReaders() {
      ImmutableList.Builder<InputReader<I>> readers = new ImmutableList.Builder<>();
      for (Key<EppResourceIndexBucket> bucketKey : EppResourceIndexBucket.getAllBuckets()) {
        readers.add(bucketToReader(bucketKey));
      }
      return readers.build();
    }

    /** Creates a reader that returns the resources under a bucket. */
    protected abstract InputReader<I> bucketToReader(Key<EppResourceIndexBucket> bucketKey);
  }

  /**
   * A MapReduce {@link Input} that loads all {@link EppResourceIndex} entities.
   */
  private static class IndexInput extends BaseInput<EppResourceIndex> {

    private static final long serialVersionUID = -1231269296567279059L;

    @Override
    protected InputReader<EppResourceIndex> bucketToReader(Key<EppResourceIndexBucket> bucketKey) {
      return new IndexReader(bucketKey);
    }
  }

  /** A MapReduce {@link Input} that loads all {@link EppResource} objects of a given type. */
  private static class EntityInput<R extends EppResource> extends BaseInput<R> {

    private static final long serialVersionUID = 8162607479124406226L;

    private final ImmutableSet<Class<? extends R>> resourceClasses;

    public EntityInput(ImmutableSet<Class<? extends R>> resourceClasses) {
      this.resourceClasses = resourceClasses;
      checkResourceClassesForInheritance(resourceClasses);
    }

    @Override
    protected InputReader<R> bucketToReader(Key<EppResourceIndexBucket> bucketKey) {
      return new EntityReader<R>(bucketKey, resourceClasses);
    }
  }

  /**
   * A MapReduce {@link Input} that loads keys to all {@link EppResource} objects of a given type.
   *
   * <p>When mapping over keys we can't distinguish between Objectify polymorphic types.
   */
  private static class KeyInput<R extends EppResource> extends BaseInput<Key<R>> {

    private static final long serialVersionUID = -5426821384707653743L;

    private final ImmutableSet<Class<? extends R>> resourceClasses;

    public KeyInput(ImmutableSet<Class<? extends R>> resourceClasses) {
      this.resourceClasses = resourceClasses;
      checkResourceClassesForInheritance(resourceClasses);
    }

    @Override
    protected InputReader<Key<R>> bucketToReader(Key<EppResourceIndexBucket> bucketKey) {
      return new KeyReader<>(bucketKey, resourceClasses);
    }
  }

  /** Base class for {@link InputReader} classes that map over {@link EppResourceIndex}. */
  private abstract static class BaseReader<T> extends InputReader<T> {

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

    BaseReader(
        Key<EppResourceIndexBucket>
        bucketKey,
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
  }

  /** Reader that maps over {@link EppResourceIndex} and returns the index objects themselves. */
  private static class IndexReader extends BaseReader<EppResourceIndex> {

    private static final long serialVersionUID = -4816383426796766911L;

    public IndexReader(Key<EppResourceIndexBucket> bucketKey) {
      // Estimate 1MB of memory for this reader, which is massive overkill.
      // Use an empty set for the filter kinds, which disables filtering.
      super(bucketKey, ONE_MB, ImmutableSet.<String>of());
    }

    /**
     * Called for each map invocation.
     *
     * @throws NoSuchElementException if there are no more elements, as specified in the
     *         {@link InputReader#next} Javadoc.
     */
    @Override
    public EppResourceIndex next() throws NoSuchElementException {
      return nextEri();
    }
  }

  /**
   * Reader that maps over {@link EppResourceIndex} and returns resource keys.
   *
   * <p>When mapping over keys we can't distinguish between Objectify polymorphic types.
   */
  private static class KeyReader<R extends EppResource> extends BaseReader<Key<R>> {

    private static final long serialVersionUID = -428232054739189774L;

    public KeyReader(
        Key<EppResourceIndexBucket> bucketKey, ImmutableSet<Class<? extends R>> resourceClasses) {
      super(
          bucketKey,
          ONE_MB,  // Estimate 1MB of memory for this reader, which is massive overkill.
          varargsToKinds(resourceClasses));
    }

    /**
     * Called for each map invocation.
     *
     * @throws NoSuchElementException if there are no more elements, as specified in the
     *         {@link InputReader#next} Javadoc.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Key<R> next() throws NoSuchElementException {
      // This is a safe cast because we filtered on kind inside the query.
      return (Key<R>) nextEri().getReference().getKey();
    }
  }

  /** Reader that maps over {@link EppResourceIndex} and returns resources. */
  private static class EntityReader<R extends EppResource> extends BaseReader<R> {

    private static final long serialVersionUID = -8042933349899971801L;

    /**
     * The resource classes to postfilter for.
     *
     * <p>This can be {@link EppResource} or any descendant classes, regardless of whether those
     * classes map directly to a kind in datastore, with the restriction that none of the classes
     * is a supertype of any of the others.
     */
    private final ImmutableSet<Class<? extends R>> resourceClasses;

    public EntityReader(
        Key<EppResourceIndexBucket> bucketKey,
        ImmutableSet<Class<? extends R>> resourceClasses) {
      super(
          bucketKey,
          ONE_MB * 2,  // Estimate 2MB of memory for this reader, since it loads a (max 1MB) entity.
          varargsToKinds(resourceClasses));
      this.resourceClasses = resourceClasses;
    }

    /**
     * Called for each map invocation.
     *
     * @throws NoSuchElementException if there are no more elements, as specified in the
     *         {@link InputReader#next} Javadoc.
     */
    @Override
    public R next() throws NoSuchElementException {
      // Loop until we find a value, or nextRef() throws a NoSuchElementException.
      while (true) {
        Ref<? extends EppResource> reference = nextEri().getReference();
        EppResource resource = reference.get();
        if (resource == null) {
          logger.severefmt("Broken ERI reference: %s", reference.getKey());
          continue;
        }
        // Postfilter to distinguish polymorphic types (e.g. DomainBase and DomainResource).
        for (Class<? extends R> resourceClass : resourceClasses) {
          if (resourceClass.isAssignableFrom(resource.getClass())) {
            @SuppressWarnings("unchecked")
            R r = (R) resource;
            return r;
          }
        }
      }
    }
  }

  private static <R extends EppResource> ImmutableSet<String> varargsToKinds(
      ImmutableSet<Class<? extends R>> resourceClasses) {
    // Ignore EppResource when finding kinds, since it doesn't have one and doesn't imply filtering.
    return resourceClasses.contains(EppResource.class)
          ? ImmutableSet.<String>of()
          : FluentIterable.from(resourceClasses).transform(CLASS_TO_KIND_FUNCTION).toSet();
  }

  private static <R extends EppResource> void checkResourceClassesForInheritance(
      ImmutableSet<Class<? extends R>> resourceClasses) {
    for (Class<? extends R> resourceClass : resourceClasses) {
      for (Class<? extends R> potentialSuperclass : difference(resourceClasses, resourceClass)) {
        checkArgument(
            !potentialSuperclass.isAssignableFrom(resourceClass),
            "Cannot specify resource classes with inheritance relationship: %s extends %s",
            resourceClass,
            potentialSuperclass);
      }
    }
  }
}
