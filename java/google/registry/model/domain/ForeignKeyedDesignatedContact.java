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

import google.registry.model.ImmutableObject;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 * EPP-compatible version of XML type for contact identifiers associated with a domain, which can
 * be converted to a storable {@link DesignatedContact}.
 *
 * @see "http://tools.ietf.org/html/rfc5731#section-2.2"
 */
class ForeignKeyedDesignatedContact extends ImmutableObject {
  @XmlAttribute(required = true)
  DesignatedContact.Type type;

  @XmlValue
  String contactId;

  static ForeignKeyedDesignatedContact create(DesignatedContact.Type type, String contactId) {
    ForeignKeyedDesignatedContact instance = new ForeignKeyedDesignatedContact();
    instance.type = type;
    instance.contactId = contactId;
    return instance;
  }
}
