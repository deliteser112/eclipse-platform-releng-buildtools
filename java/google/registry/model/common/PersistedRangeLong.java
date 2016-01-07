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

package google.registry.model.common;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.googlecode.objectify.annotation.Embed;
import google.registry.model.ImmutableObject;

/** An object that's equivalent to a {@code Range<Long>} that can be persisted to datastore. */
@Embed
public class PersistedRangeLong extends ImmutableObject {

  private Long lowerBound = null;
  private BoundType lowerBoundType = null;

  private Long upperBound = null;
  private BoundType upperBoundType = null;

  public Range<Long> asRange() {
    Range<Long> range = Range.all();
    if (lowerBound != null) {
      range = range.intersection(Range.downTo(lowerBound, lowerBoundType));
    }
    if (upperBound != null) {
      range = range.intersection(Range.upTo(upperBound, upperBoundType));
    }
    return range;
  }

  public static PersistedRangeLong create(Range<Long> range) {
    PersistedRangeLong instance = new PersistedRangeLong();
    if (range.hasLowerBound()) {
      instance.lowerBound = range.lowerEndpoint();
      instance.lowerBoundType = range.lowerBoundType();
    }
    if (range.hasUpperBound()) {
      instance.upperBound = range.upperEndpoint();
      instance.upperBoundType = range.upperBoundType();
    }
    return instance;
  }
}
