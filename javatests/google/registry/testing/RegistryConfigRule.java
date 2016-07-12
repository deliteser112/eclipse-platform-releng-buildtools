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

package google.registry.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import google.registry.config.RegistryConfig;
import google.registry.config.RegistryEnvironment;
import google.registry.config.TestRegistryConfig;
import org.junit.rules.ExternalResource;

/** JUnit Rule for overriding Domain Registry configuration values. */
public final class RegistryConfigRule extends ExternalResource {

  private final Optional<RegistryConfig> override;

  /** Creates a new instance where {@link #override(RegistryConfig)} will be called as needed. */
  public RegistryConfigRule() {
    this.override = Optional.absent();
  }

  /** Creates a new instance where {@code override} will be used for each test method. */
  public RegistryConfigRule(RegistryConfig override) {
    this.override = Optional.of(override);
  }

  /** Override registry configuration from inside a test method. */
  public void override(RegistryConfig override) {
    RegistryEnvironment.overrideConfigurationForTesting(checkNotNull(override));
  }

  /** Override registry configuration to use TMCH production CA. */
  public void useTmchProdCert() {
    override(new TestRegistryConfig() {
      @Override
      public boolean getTmchCaTestingMode() {
        return false;
      }});
  }

  @Override
  protected void before() throws Exception {
    if (override.isPresent()) {
      RegistryEnvironment.overrideConfigurationForTesting(override.get());
    }
  }

  @Override
  protected void after() {
    RegistryEnvironment.overrideConfigurationForTesting(null);
  }
}
