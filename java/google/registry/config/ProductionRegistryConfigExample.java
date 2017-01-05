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

package google.registry.config;

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.config.ConfigUtils.makeUrl;
import static org.joda.time.Duration.standardDays;

import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;
import java.net.URL;
import javax.annotation.concurrent.Immutable;
import org.joda.time.Duration;

/**
 * Default production configuration for global constants that can't be injected.
 *
 * <p><b>Warning:</b> Editing this file in a forked repository is not recommended. The recommended
 * approach is to copy this file, give it a different name, and then change the system property
 * described in the {@link RegistryConfigLoader} documentation.
 */
@Immutable
public final class ProductionRegistryConfigExample extends RegistryConfig {

  private final RegistryEnvironment environment;

  private static final String RESERVED_TERMS_EXPORT_DISCLAIMER = ""
      + "# This list contains reserve terms for the TLD. Other terms may be reserved\n"
      + "# but not included in this list, including terms EXAMPLE REGISTRY chooses not\n"
      + "# to publish, and terms that ICANN commonly mandates to be reserved. This\n"
      + "# list is subject to change and the most up-to-date source is always to\n"
      + "# check availability directly with the Registry server.\n";

  public ProductionRegistryConfigExample(RegistryEnvironment environment) {
    this.environment = checkNotNull(environment);
  }

  @Override
  public Optional<String> getECatcherAddress() {
    throw new UnsupportedOperationException();  // n/a
  }

  @Override
  public HostAndPort getServer() {
    switch (environment) {
      case LOCAL:
        return HostAndPort.fromParts("localhost", 8080);
      default:
        return HostAndPort.fromParts(
            String.format("tools-dot-%s.appspot.com", getProjectId()), 443);
    }
  }

  @Override
  public String getReservedTermsExportDisclaimer() {
    return RESERVED_TERMS_EXPORT_DISCLAIMER;
  }

  @Override
  public String getRegistrarDefaultWhoisServer() {
    return "whois.nic.registry.example";
  }

  @Override
  public URL getRegistrarDefaultReferralUrl() {
    return makeUrl("https://www.registry.example");
  }

  @Override
  public int getEppResourceIndexBucketCount() {
    return 997;
  }

  @Override
  public Duration getBaseOfyRetryDuration() {
    return Duration.millis(100);
  }

  @Override
  public Duration getContactAutomaticTransferLength() {
    return standardDays(5);
  }

  @Override
  public String getCheckApiServletRegistrarClientId() {
    return "TheRegistrar";
  }
}
