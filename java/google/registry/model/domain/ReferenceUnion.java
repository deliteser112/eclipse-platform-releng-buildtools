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

package com.google.domain.registry.model.domain;

import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.host.HostResource;

import com.googlecode.objectify.Ref;
import com.googlecode.objectify.annotation.Embed;
import com.googlecode.objectify.annotation.Ignore;
import com.googlecode.objectify.annotation.Index;

import java.io.Serializable;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * A "union" type to represent referenced objects as either a foreign key or as a link to another
 * object in the datastore.
 * <p>
 * This type always marshals as the "foreign key". When it is explicitly storing a foreign key it
 * gets the value from its own string field. When it is linked to another object, it gets the value
 * from the other object.
 * <p>
 * When a {@link ReferenceUnion} comes in from Epp, either in an update or a delete, it fills in the
 * "foreign key" string field, but as soon as the relevant Flow runs it deletes that field and
 * replaces it with a linked {@link Ref} to the object named by that string. We can't do this in a
 * {@code XmlJavaTypeAdapter} because failing a lookup is a business logic error, not a failure to
 * parse the XML.
 *
 * @param <T> the type being referenced
 */
@Embed
public class ReferenceUnion<T extends EppResource> extends ImmutableObject implements Serializable {

  @Index
  Ref<T> linked;

  /** This is never persisted, and only ever populated to marshal or unmarshal to or from XML. */
  @Ignore
  String foreignKey;

  public Ref<T> getLinked() {
    return linked;
  }

  public String getForeignKey() {
    return foreignKey;
  }

  /** An adapter that is aware of the union inside {@link ReferenceUnion}. */
  public static class Adapter<T extends EppResource>
      extends XmlAdapter<String, ReferenceUnion<T>> {

    @Override
    public ReferenceUnion<T> unmarshal(String foreignKey) throws Exception {
      return ReferenceUnion.<T>create(foreignKey);
    }

    @Override
    public String marshal(ReferenceUnion<T> reference) throws Exception {
      return reference.getForeignKey() == null
          ? reference.getLinked().get().getForeignKey()
          : reference.getForeignKey();
    }
  }

  /** An adapter for references to contacts. */
  static class ContactReferenceUnionAdapter extends ReferenceUnion.Adapter<ContactResource>{}

  /** An adapter for references to hosts. */
  static class HostReferenceUnionAdapter extends ReferenceUnion.Adapter<HostResource>{}

  public static <T extends EppResource> ReferenceUnion<T> create(String foreignKey) {
    ReferenceUnion<T> instance = new ReferenceUnion<>();
    instance.foreignKey = foreignKey;
    return instance;
  }

  public static <T extends EppResource> ReferenceUnion<T> create(Ref<T> linked) {
    ReferenceUnion<T> instance = new ReferenceUnion<>();
    instance.linked = linked;
    return instance;
  }

  /** Convenience method. */
  public static <T extends EppResource> ReferenceUnion<T> create(T resource) {
    return create(Ref.create(resource));
  }
}
