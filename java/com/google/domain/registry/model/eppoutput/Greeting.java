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

package com.google.domain.registry.model.eppoutput;

import static org.joda.time.DateTimeZone.UTC;

import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.eppcommon.PresenceMarker;
import com.google.domain.registry.model.eppcommon.ProtocolDefinition;
import com.google.domain.registry.model.eppoutput.EppOutput.ResponseOrGreeting;

import org.joda.time.DateTime;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * A greeting, defined in {@link "http://tools.ietf.org/html/rfc5730"}.
 * <p>
 * It would be nice to make this a singleton, but we need the {@link #svDate} field to stay current.
 */
public class Greeting extends ImmutableObject implements ResponseOrGreeting {

  String svID = "Charleston Road Registry";
  DateTime svDate = DateTime.now(UTC);

  /** This is never changed, so it might as well be static for efficiency. */
  @XmlElement
  static SvcMenu svcMenu = new SvcMenu();

  /** This is never changed, so it might as well be static for efficiency. */
  @XmlElement
  static Dcp dcp = new Dcp();

  static class SvcMenu extends ImmutableObject {
    String version = ProtocolDefinition.VERSION;
    String lang = ProtocolDefinition.LANGUAGE;
    Set<String> objURI = ProtocolDefinition.SUPPORTED_OBJECT_SERVICES;

    @XmlElementWrapper(name = "svcExtension")
    Set<String> extURI = ProtocolDefinition.getVisibleServiceExtensionUris();
  }

  static class Dcp extends ImmutableObject {
    Access access = new Access();
    Statement statement = new Statement();
  }

  static class Access extends ImmutableObject {
    PresenceMarker all = new PresenceMarker();
  }

  static class Statement extends ImmutableObject {
    Purpose purpose = new Purpose();
    Recipient recipient = new Recipient();
    Retention retention = new Retention();
  }

  static class Purpose extends ImmutableObject {
    PresenceMarker admin = new PresenceMarker();
    PresenceMarker prov = new PresenceMarker();
  }

  static class Recipient extends ImmutableObject {
    PresenceMarker ours = new PresenceMarker();

    @XmlElement(name = "public")
    PresenceMarker publicObj = new PresenceMarker();
  }

  static class Retention extends ImmutableObject {
    PresenceMarker indefinite = new PresenceMarker();
  }
}
