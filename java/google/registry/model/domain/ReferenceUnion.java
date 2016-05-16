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

package google.registry.model.domain;

import com.googlecode.objectify.Ref;
import com.googlecode.objectify.annotation.Embed;
import com.googlecode.objectify.annotation.Index;

import google.registry.model.EppResource;
import google.registry.model.ImmutableObject;
import google.registry.model.contact.ContactResource;
import google.registry.model.host.HostResource;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * Legacy shell of a "union" type to represent referenced objects as either a foreign key or as a
 * link to another object in the datastore. In its current form it merely wraps a {@link Ref}.
 * <p>
 * This type always marshals as the "foreign key". We no longer use this type for unmarshalling.
 *
 * @param <T> the type being referenced
 */
@Embed
public class ReferenceUnion<T extends EppResource> extends ImmutableObject {

  @Index
  Ref<T> linked;

  public Ref<T> getLinked() {
    return linked;
  }

  /** An adapter that marshals the linked {@link Ref} as its loaded foreign key. */
  public static class Adapter<T extends EppResource>
      extends XmlAdapter<String, ReferenceUnion<T>> {

    @Override
    public ReferenceUnion<T> unmarshal(String foreignKey) throws Exception {
      throw new UnsupportedOperationException();
    }

    @Override
    public String marshal(ReferenceUnion<T> reference) throws Exception {
      return reference.getLinked().get().getForeignKey();
    }
  }

  /** An adapter for references to contacts. */
  static class ContactReferenceUnionAdapter extends Adapter<ContactResource>{}

  /** An adapter for references to hosts. */
  static class HostReferenceUnionAdapter extends Adapter<HostResource>{}

  public static <T extends EppResource> ReferenceUnion<T> create(Ref<T> linked) {
    ReferenceUnion<T> instance = new ReferenceUnion<>();
    instance.linked = linked;
    return instance;
  }
}
