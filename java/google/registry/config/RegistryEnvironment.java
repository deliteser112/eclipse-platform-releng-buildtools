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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import javax.annotation.Nullable;

/** Registry environments. */
public enum RegistryEnvironment {

  /** Production environment. */
  PRODUCTION,

  /** Development environment. */
  ALPHA,

  /** Load/Backup/Restore Testing environment. */
  CRASH,

  /** Local machine environment. */
  LOCAL,

  /** Quality Assurance environment. */
  QA,

  /** Sandbox environment. */
  SANDBOX,

  /**
   * Unit testing environment.
   *
   * <p>This is the default enum value. This is because it's non-trivial to configure the system
   * property that specifies the environment in our unit tests.
   *
   * <p>Do not use this environment outside of unit tests.
   */
  UNITTEST;

  /** Returns environment configured by system property {@value #PROPERTY}. */
  public static RegistryEnvironment get() {
    return valueOf(Ascii.toUpperCase(System.getProperty(PROPERTY, UNITTEST.name())));
  }

  /**
   * Returns configuration for this registry environment.
   *
   * <p><b>WARNING:</b> Do not store this value to a static field, otherwise you won't be able to
   * override it for testing. You should instead store the environment object to a static field.
   */
  public RegistryConfig config() {
    if (configOverride != null) {
      return configOverride;
    } else if (this == UNITTEST) {
      return testingConfig;
    } else {
      return config;
    }
  }

  /** Globally override registry configuration from within a unit test. */
  @VisibleForTesting
  public static void overrideConfigurationForTesting(@Nullable RegistryConfig newConfig) {
    configOverride = newConfig;
  }

  @Nullable
  private static RegistryConfig configOverride;

  private static final RegistryConfig testingConfig = new TestRegistryConfig();
  private final RegistryConfig config = RegistryConfigLoader.load(this);

  /** System property for configuring which environment we should use. */
  public static final String PROPERTY = "google.registry.environment";
}
