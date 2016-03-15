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

import com.google.appengine.tools.mapreduce.InputReader;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.EppResourceIndexBucket;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;

import java.util.NoSuchElementException;

/** Reader that maps over {@link EppResourceIndex} and returns resources. */
class EppResourceEntityReader<R extends EppResource> extends EppResourceBaseReader<R> {

  private static final long serialVersionUID = -8042933349899971801L;

  /**
   * The resource classes to postfilter for.
   *
   * <p>This can be {@link EppResource} or any descendant classes, regardless of whether those
   * classes map directly to a kind in datastore, with the restriction that none of the classes
   * is a supertype of any of the others.
   */
  private final ImmutableSet<Class<? extends R>> resourceClasses;

  public EppResourceEntityReader(
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
