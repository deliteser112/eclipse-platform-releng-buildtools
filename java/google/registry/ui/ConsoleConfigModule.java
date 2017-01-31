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

package google.registry.ui;

import com.google.appengine.api.users.UserService;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;

/** Dagger module for UI configuration. */
@Module
public final class ConsoleConfigModule {  // TODO(b/26829015): Move to config package.

  @Provides
  static ConsoleDebug provideConsoleDebug() {
    return ConsoleDebug.get();
  }

  /** URL of Braintree iframe sandbox iframe static HTML file. */
  @Provides
  @Config("brainframe")
  static String provideBrainframe(
      ConsoleDebug debug,
      UserService userService,
      @Config("projectId") String projectId) {
    switch (debug) {
      case PRODUCTION:
        return String.format("https://%s.storage.googleapis.com/%s",
            projectId,
            userService.isUserLoggedIn() && userService.isUserAdmin()
                ? "brainframe-map.html"
                : "brainframe.html");
      case DEBUG:
      case RAW:
      case TEST:
        return "/assets/html/insecure-brainframe.html";
      default:
        throw new AssertionError(debug.toString());
    }
  }
}
