// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.net.HostAndPort;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig;

/**
 * Class to contain the configuration flags for AppEngineConnection.
 *
 * <p>This is broken out into its own class to make it cleaner to extract these from the dagger
 * module, where these values are injected.
 */
@Parameters(separators = " =")
class AppEngineConnectionFlags {

  @Parameter(names = "--server", description = "HOST[:PORT] to which remote commands are sent.")
  private static HostAndPort server = RegistryConfig.getServer();

  HostAndPort getServer() {
    return server;
  }

  @Module
  static class FlagsModule {
    AppEngineConnectionFlags flags;

    FlagsModule(AppEngineConnectionFlags flags) {
      this.flags = flags;
    }

    @Provides
    AppEngineConnectionFlags provideAppEngineConnectionFlags() {
      return flags;
    }
  }
}

