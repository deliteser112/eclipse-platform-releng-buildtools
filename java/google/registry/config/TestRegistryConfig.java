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

import static google.registry.config.ConfigUtils.makeUrl;
import static org.joda.time.Duration.standardDays;

import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;
import java.net.URL;
import org.joda.time.Duration;

/**
 * An implementation of RegistryConfig for unit testing that contains suitable testing data.
 */
public class TestRegistryConfig extends RegistryConfig {

  public TestRegistryConfig() {}

  @Override
  public Optional<String> getECatcherAddress() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HostAndPort getServer() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getReservedTermsExportDisclaimer() {
    return "This is a disclaimer.\n";
  }

  @Override
  public String getRegistrarDefaultWhoisServer() {
    return "whois.nic.fakewhois.example";
  }

  @Override
  public URL getRegistrarDefaultReferralUrl() {
    return makeUrl("http://www.referral.example/path");
  }

  @Override
  public int getEppResourceIndexBucketCount() {
    return 2;
  }

  @Override
  public Duration getBaseOfyRetryDuration() {
    return Duration.ZERO;
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
