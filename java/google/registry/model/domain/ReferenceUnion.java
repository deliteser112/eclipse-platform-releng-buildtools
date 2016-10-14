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

package google.registry.model.domain;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Embed;
import com.googlecode.objectify.annotation.Index;
import google.registry.model.EppResource;
import google.registry.model.ImmutableObject;

/**
 * Legacy shell of a "union" type to represent referenced objects as either a foreign key or as a
 * link to another object in the datastore. In its current form it merely wraps a {@link Key}.
 *
 * @param <T> the type being referenced
 */
// TODO(b/28713909): Delete ReferenceUnion entirely.
@Embed
@Deprecated
public class ReferenceUnion<T extends EppResource> extends ImmutableObject {

  @Index
  Key<T> linked;

  public Key<T> getLinked() {
    return linked;
  }

  public static <T extends EppResource> ReferenceUnion<T> create(Key<T> linked) {
    ReferenceUnion<T> instance = new ReferenceUnion<>();
    instance.linked = linked;
    return instance;
  }
}
