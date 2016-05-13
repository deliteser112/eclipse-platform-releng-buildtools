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

package com.google.domain.registry.model.eppcommon;

import static com.google.common.collect.Maps.uniqueIndex;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.domain.allocate.AllocateCreateExtension;
import com.google.domain.registry.model.domain.fee.FeeCheckExtension;
import com.google.domain.registry.model.domain.launch.LaunchCreateExtension;
import com.google.domain.registry.model.domain.metadata.MetadataExtension;
import com.google.domain.registry.model.domain.rgp.RgpUpdateExtension;
import com.google.domain.registry.model.domain.secdns.SecDnsCreateExtension;
import com.google.domain.registry.model.eppinput.EppInput.CommandExtension;

import java.util.EnumSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlSchema;

/** Constants that define the EPP protocol version we support. */
public class ProtocolDefinition {
  public static final String VERSION = "1.0";

  public static final String LANGUAGE = "en";

  public static final Set<String> SUPPORTED_OBJECT_SERVICES = ImmutableSet.of(
      "urn:ietf:params:xml:ns:host-1.0",
      "urn:ietf:params:xml:ns:domain-1.0",
      "urn:ietf:params:xml:ns:contact-1.0");

  /** Enums repesenting valid service extensions that are recognized by the server. */
  public enum ServiceExtension {
    LAUNCH_EXTENSION_1_0(LaunchCreateExtension.class, true),
    REDEMPTION_GRACE_PERIOD_1_0(RgpUpdateExtension.class, true),
    SECURE_DNS_1_1(SecDnsCreateExtension.class, true),
    FEE_0_6(FeeCheckExtension.class, true),
    ALLOCATE_1_0(AllocateCreateExtension.class, false),
    METADATA_1_0(MetadataExtension.class, false);

    private String uri;
    private boolean visible;

    ServiceExtension(Class<? extends CommandExtension> clazz, boolean visible) {
      this.uri = getCommandExtensionUri(clazz);
      this.visible = visible;
    }

    public String getUri() {
      return uri;
    }

    public boolean getVisible() {
      return visible;
    }

    /** Returns the namespace URI of the command extension class. */
    public static String getCommandExtensionUri(Class<? extends CommandExtension> clazz) {
      return clazz.getPackage().getAnnotation(XmlSchema.class).namespace();
    }
  }

  /** Converts a service extension enum to its URI. */
  private static final Function<ServiceExtension, String> TO_URI_FUNCTION =
      new Function<ServiceExtension, String>() {
        @Override
        public String apply(ServiceExtension serviceExtension) {
          return serviceExtension.getUri();
        }};

  /** This stores a map from URI back to the service extension enum. */
  private static final ImmutableMap<String, ServiceExtension> serviceExtensionByUri =
      uniqueIndex(EnumSet.allOf(ServiceExtension.class), TO_URI_FUNCTION);

  /** Returns the service extension enum associated with a URI, or null if none are associated. */
  public static ServiceExtension getServiceExtensionFromUri(String uri) {
    return serviceExtensionByUri.get(uri);
  }

  /** A set of all the visible extension URIs. */
  private static final ImmutableSet<String> visibleServiceExtensionUris =
      FluentIterable.from(EnumSet.allOf(ServiceExtension.class))
          .filter(
              new Predicate<ServiceExtension>() {
                @Override
                public boolean apply(ServiceExtension serviceExtension) {
                  return serviceExtension.getVisible();
                }
              })
          .transform(TO_URI_FUNCTION)
          .toSet();

  /** Return the set of all visible service extension URIs. */
  public static ImmutableSet<String> getVisibleServiceExtensionUris() {
    return visibleServiceExtensionUris;
  }
}
