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

import com.google.common.annotations.VisibleForTesting;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Embed;
import com.googlecode.objectify.annotation.Index;
import google.registry.model.ImmutableObject;
import google.registry.model.contact.ContactResource;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlValue;

/**
 * XML type for contact identifiers associated with a domain.
 *
 * @see "http://tools.ietf.org/html/rfc5731#section-2.2"
 */
@Embed
public class DesignatedContact extends ImmutableObject {

  /**
   * XML type for contact types. This can be either: {@code "admin"}, {@code "billing"}, or
   * {@code "tech"} and corresponds to {@code contactAttrType} in {@code domain-1.0.xsd}.
   */
  public enum Type {
    @XmlEnumValue("admin")
    ADMIN,
    @XmlEnumValue("billing")
    BILLING,
    @XmlEnumValue("tech")
    TECH,
    /** The registrant type is not reflected in XML and exists only for internal use. */
    REGISTRANT;
  }

  @VisibleForTesting
  public static DesignatedContact create(Type type, Key<ContactResource> contact) {
    DesignatedContact instance = new DesignatedContact();
    instance.type = type;
    instance.contactId = ReferenceUnion.create(contact);
    instance.contact = contact;
    return instance;
  }

  @XmlAttribute(required = true)
  Type type;

  @Index
  @XmlValue
  //TODO(b/28713909): Remove contactId and replace with contact.
  ReferenceUnion<ContactResource> contactId;

  @Index
  Key<ContactResource> contact;

  public Type getType() {
    return type;
  }

  public Key<ContactResource> getContactKey() {
    return contact;
  }
}
