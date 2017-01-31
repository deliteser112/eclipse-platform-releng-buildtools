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

import static google.registry.model.EntityClasses.ALL_CLASSES;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.tools.mapreduce.InputReader;
import com.google.appengine.tools.mapreduce.ShardContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.cmd.Query;
import google.registry.model.EppResource;
import google.registry.model.ImmutableObject;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.EppResourceIndexBucket;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Reader that maps over {@link EppResourceIndex} and returns resources that are children of
 * {@link EppResource} objects.
 */
class ChildEntityReader<R extends EppResource, I extends ImmutableObject> extends InputReader<I> {

  private static final long serialVersionUID = -7430731417793849164L;

  static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** This reader uses an EppResourceEntityReader under the covers to iterate over EPP resources. */
  private final EppResourceEntityReader<? extends R> eppResourceEntityReader;
  /** The current EPP resource being referenced for child entity queries. */
  private Key<? extends R> currentEppResource;

  /** The child resource classes to postfilter for. */
  private final ImmutableList<Class<? extends I>> childResourceClasses;
  /** The index within the list above for the next ofy query. */
  private int childResourceClassIndex;

  /** An iterator over queries for child entities of EppResources. */
  private transient QueryResultIterator<I> childQueryIterator;
  /** A cursor for queries for child entities of EppResources. */
  private Cursor childCursor;

  public ChildEntityReader(
      Key<EppResourceIndexBucket> bucketKey,
      ImmutableSet<Class<? extends R>> resourceClasses,
      ImmutableSet<Class<? extends I>> childResourceClasses) {
    this.childResourceClasses = expandPolymorphicClasses(childResourceClasses);
    this.eppResourceEntityReader = new EppResourceEntityReader<>(bucketKey, resourceClasses);
  }

  /** Expands non-entity polymorphic classes into their child types. */
  @SuppressWarnings("unchecked")
  private ImmutableList<Class<? extends I>> expandPolymorphicClasses(
      ImmutableSet<Class<? extends I>> resourceClasses) {
    ImmutableList.Builder<Class<? extends I>> builder = new ImmutableList.Builder<>();
    for (Class<? extends I> clazz : resourceClasses) {
      if (clazz.isAnnotationPresent(Entity.class)) {
        builder.add(clazz);
      } else {
        for (Class<? extends ImmutableObject> entityClass : ALL_CLASSES) {
          if (clazz.isAssignableFrom(entityClass)) {
            builder.add((Class<? extends I>) entityClass);
          }
        }
      }
    }
    return builder.build();
  }

  /**
   * Get the next {@link ImmutableObject} (i.e. child element) from the query.
   *
   * @throws NoSuchElementException if there are no more EPP resources to iterate over.
   */
  I nextChild() throws NoSuchElementException {
    try {
      while (true) {
        if (currentEppResource == null) {
          currentEppResource = Key.create(eppResourceEntityReader.next());
          childResourceClassIndex = 0;
          childQueryIterator = null;
        }
        if (childQueryIterator == null) {
          childQueryIterator = childQuery().iterator();
        }
        try {
          return childQueryIterator.next();
        } catch (NoSuchElementException e) {
          childQueryIterator = null;
          childResourceClassIndex++;
          if (childResourceClassIndex >= childResourceClasses.size()) {
            currentEppResource = null;
          }
        }
      }
    } finally {
      ofy().clearSessionCache(); // Try not to leak memory.
    }
  }

  @Override
  public I next() throws NoSuchElementException {
    while (true) {
      I entity = nextChild();
      if (entity != null) {
        // Postfilter to distinguish polymorphic types.
        for (Class<? extends I> resourceClass : childResourceClasses) {
          if (resourceClass.isInstance(entity)) {
            return entity;
          }
        }
      }
    }
  }

  /** Query for children of the current resource and of the current child class. */
  private Query<I> childQuery() {
    @SuppressWarnings("unchecked")
    Query<I> query = (Query<I>) ofy().load()
        .type(childResourceClasses.get(childResourceClassIndex))
        .ancestor(currentEppResource);
    return query;
  }

  @Override
  public void beginSlice() {
    eppResourceEntityReader.beginSlice();
    if (childCursor != null) {
      Query<I> query = childQuery().startAt(childCursor);
      childQueryIterator = query.iterator();
    }
  }

  @Override
  public void endSlice() {
    if (childQueryIterator != null) {
      childCursor = childQueryIterator.getCursor();
    }
    eppResourceEntityReader.endSlice();
  }

  @Override
  public Double getProgress() {
    return eppResourceEntityReader.getProgress();
  }

  @Override
  public long estimateMemoryRequirement() {
    return eppResourceEntityReader.estimateMemoryRequirement();
  }

  @Override
  public ShardContext getContext() {
    return eppResourceEntityReader.getContext();
  }

  @Override
  public void setContext(ShardContext context) {
    eppResourceEntityReader.setContext(context);
  }

  @Override
  public void beginShard() throws IOException {
    eppResourceEntityReader.beginShard();
  }

  @Override
  public void endShard() throws IOException {
    eppResourceEntityReader.endShard();
  }
}
