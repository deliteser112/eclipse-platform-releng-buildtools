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

package com.google.domain.registry.model.poll;


import com.google.common.annotations.VisibleForTesting;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.eppoutput.Response.ResponseData;

import com.googlecode.objectify.annotation.Embed;

import org.joda.time.DateTime;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

/** The {@link ResponseData} returned when completing a pending action on a domain. */
@XmlTransient
public abstract class PendingActionNotificationResponse
    extends ImmutableObject implements ResponseData {

  /** The inner name type that contains a name and the result boolean. */
  @Embed
  static class NameOrId extends ImmutableObject {
    @XmlValue
    String value;

    @XmlAttribute(name = "paResult")
    boolean actionResult;
  }

  @XmlTransient
  NameOrId nameOrId;

  @XmlElement(name = "paTRID")
  Trid trid;

  @XmlElement(name = "paDate")
  DateTime processedDate;

  public String getNameAsString() {
    return nameOrId.value;
  }

  @VisibleForTesting
  public Trid getTrid() {
    return trid;
  }

  @VisibleForTesting
  public boolean getActionResult() {
    return nameOrId.actionResult;
  }

  @SuppressWarnings("unchecked")
  protected <T extends PendingActionNotificationResponse> T init(
      String nameOrId, boolean actionResult, Trid trid, DateTime processedDate) {
    this.nameOrId = new NameOrId();
    this.nameOrId.value = nameOrId;
    this.nameOrId.actionResult = actionResult;
    this.trid = trid;
    this.processedDate = processedDate;
    return (T) this;
  }

  /** An adapter to output the XML in response to resolving a pending command on a domain. */
  @Embed
  @XmlRootElement(name = "panData", namespace = "urn:ietf:params:xml:ns:domain-1.0")
  @XmlType(
      propOrder = {"name", "trid", "processedDate"},
      namespace = "urn:ietf:params:xml:ns:domain-1.0")
  public static class DomainPendingActionNotificationResponse
      extends PendingActionNotificationResponse {

    @XmlElement
    NameOrId getName() {
      return nameOrId;
    }

    public static DomainPendingActionNotificationResponse create(
        String fullyQualifiedDomainName, boolean actionResult, Trid trid, DateTime processedDate) {
      return new DomainPendingActionNotificationResponse().init(
          fullyQualifiedDomainName, actionResult, trid, processedDate);
    }
  }

  /** An adapter to output the XML in response to resolving a pending command on a contact. */
  @Embed
  @XmlRootElement(name = "panData", namespace = "urn:ietf:params:xml:ns:contact-1.0")
  @XmlType(
      propOrder = {"id", "trid", "processedDate"},
      namespace = "urn:ietf:params:xml:ns:contact-1.0")
  public static class ContactPendingActionNotificationResponse
      extends PendingActionNotificationResponse {

    @XmlElement
    NameOrId getId() {
      return nameOrId;
    }

    public static ContactPendingActionNotificationResponse create(
        String contactId, boolean actionResult, Trid trid, DateTime processedDate) {
      return new ContactPendingActionNotificationResponse().init(
          contactId, actionResult, trid, processedDate);
    }
  }
}
