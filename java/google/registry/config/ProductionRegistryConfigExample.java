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

import com.google.common.base.Optional;
import javax.annotation.concurrent.Immutable;

/**
 * Default production configuration for global constants that can't be injected.
 *
 * <p><b>Warning:</b> Editing this file in a forked repository is not recommended. The recommended
 * approach is to copy this file, give it a different name, and then change the system property
 * described in the {@link RegistryConfigLoader} documentation.
 */
@Immutable
public final class ProductionRegistryConfigExample extends RegistryConfig {

  @SuppressWarnings("unused")
  private final RegistryEnvironment environment;

  public ProductionRegistryConfigExample(RegistryEnvironment environment) {
    this.environment = checkNotNull(environment);
  }

  @Override
  public Optional<String> getECatcherAddress() {
    throw new UnsupportedOperationException();  // n/a
  }
}
