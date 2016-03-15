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

package com.google.domain.registry.mapreduce.inputs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.domain.registry.util.CollectionUtils.difference;

import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.InputReader;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.EppResourceIndexBucket;

import com.googlecode.objectify.Key;

import java.util.List;

/** Base class for {@link Input} classes that map over {@link EppResourceIndex}. */
abstract class EppResourceBaseInput<I> extends Input<I> {

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

  static <R extends EppResource> void checkResourceClassesForInheritance(
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

