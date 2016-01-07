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

package google.registry.model.eppcommon;

import static com.google.common.collect.Maps.uniqueIndex;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.model.domain.allocate.AllocateCreateExtension;
import google.registry.model.domain.fee06.FeeCheckCommandExtensionV06;
import google.registry.model.domain.fee06.FeeCheckResponseExtensionV06;
import google.registry.model.domain.fee11.FeeCheckCommandExtensionV11;
import google.registry.model.domain.fee11.FeeCheckResponseExtensionV11;
import google.registry.model.domain.fee12.FeeCheckCommandExtensionV12;
import google.registry.model.domain.fee12.FeeCheckResponseExtensionV12;
import google.registry.model.domain.flags.FlagsCheckCommandExtension;
import google.registry.model.domain.launch.LaunchCreateExtension;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.domain.rgp.RgpUpdateExtension;
import google.registry.model.domain.secdns.SecDnsCreateExtension;
import google.registry.model.eppinput.EppInput.CommandExtension;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
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
    LAUNCH_EXTENSION_1_0(LaunchCreateExtension.class, null, true),
    REDEMPTION_GRACE_PERIOD_1_0(RgpUpdateExtension.class, null, true),
    SECURE_DNS_1_1(SecDnsCreateExtension.class, null, true),
    FEE_0_6(FeeCheckCommandExtensionV06.class, FeeCheckResponseExtensionV06.class, true),
    FEE_0_11(FeeCheckCommandExtensionV11.class, FeeCheckResponseExtensionV11.class, true),
    FEE_0_12(FeeCheckCommandExtensionV12.class, FeeCheckResponseExtensionV12.class, true),
    FLAGS_0_1(FlagsCheckCommandExtension.class, null, true),
    ALLOCATE_1_0(AllocateCreateExtension.class, null, false),
    METADATA_1_0(MetadataExtension.class, null, false);

    private final Class<? extends CommandExtension> commandExtensionClass;
    private final Class<? extends ResponseExtension> responseExtensionClass;
    private String uri;
    private boolean visible;

    ServiceExtension(
        Class<? extends CommandExtension> commandExtensionClass,
        Class<? extends ResponseExtension> responseExtensionClass,
        boolean visible) {
      this.commandExtensionClass = commandExtensionClass;
      this.responseExtensionClass = responseExtensionClass;
      this.uri = getCommandExtensionUri(commandExtensionClass);
      this.visible = visible;
    }

    public Class<? extends CommandExtension> getCommandExtensionClass() {
      return commandExtensionClass;
    }

    public Class<? extends ResponseExtension> getResponseExtensionClass() {
      return responseExtensionClass;
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
