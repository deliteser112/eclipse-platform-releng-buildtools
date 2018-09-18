// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.config.RegistryConfig.ConfigModule;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Unit tests for environment-specific CloudDns configurations. */
@RunWith(Parameterized.class)
public class CloudDnsConfigTest {

  @Parameters
  public static final Collection<RegistryEnvironment> environments() {
    return ImmutableList.copyOf(RegistryEnvironment.values());
  }

  private static final ImmutableList<Optional<String>> TEST_CONFIGS =
      ImmutableList.of(
          Optional.of("https://staging-www.sandbox.googleapis.com"),
          Optional.of("dns/v2beta1_staging/projects/"));

  private static final ImmutableList<Optional<String>> PROD_CONFIGS =
      ImmutableList.of(Optional.empty(), Optional.empty());

  private static final Map<RegistryEnvironment, ImmutableList<Optional<String>>> data =
      ImmutableMap.of(
          RegistryEnvironment.PRODUCTION, PROD_CONFIGS,
          RegistryEnvironment.SANDBOX, PROD_CONFIGS);

  @Parameter public RegistryEnvironment environment;

  private RegistryConfigSettings registryConfigSettings;

  @Before
  public void setup() {
    System.setProperty(RegistryEnvironment.PROPERTY, environment.name());
    registryConfigSettings = YamlUtils.getConfigSettings();
  }

  @Test
  public void test() {
    ImmutableList<Optional<String>> expectedConfigs = data.getOrDefault(environment, TEST_CONFIGS);
    assertThat(ConfigModule.getCloudDnsRootUrl(registryConfigSettings))
        .isEqualTo(expectedConfigs.get(0));
    assertThat(ConfigModule.getCloudDnsServicePath(registryConfigSettings))
        .isEqualTo(expectedConfigs.get(1));
  }
}
