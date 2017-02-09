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

package google.registry.request.auth;

import com.google.appengine.api.oauth.OAuthService;
import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.users.UserService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;

/**
 * Dagger module for authentication routines.
 */
@Module
public class AuthModule {

  /** Provides the internal authentication mechanism. */
  @Provides
  AppEngineInternalAuthenticationMechanism provideAppEngineInternalAuthenticationMechanism() {
    return new AppEngineInternalAuthenticationMechanism();
  }

  /** Provides the custom authentication mechanisms (including OAuth). */
  @Provides
  ImmutableList<AuthenticationMechanism> provideApiAuthenticationMechanisms(
      OAuthService oauthService,
      @Config("availableOauthScopes") ImmutableSet<String> availableOauthScopes,
      @Config("requiredOauthScopes") ImmutableSet<String> requiredOauthScopes,
      @Config("allowedOauthClientIds") ImmutableSet<String> allowedOauthClientIds) {
    return ImmutableList.<AuthenticationMechanism>of(
        new OAuthAuthenticationMechanism(
            oauthService, availableOauthScopes, requiredOauthScopes, allowedOauthClientIds));
  }

  /** Provides the legacy authentication mechanism. */
  @Provides
  LegacyAuthenticationMechanism provideLegacyAuthenticationMechanism(UserService userService) {
    return new LegacyAuthenticationMechanism(userService);
  }

  /** Provides the OAuthService instance. */
  @Provides
  OAuthService provideOauthService() {
    return OAuthServiceFactory.getOAuthService();
  }
}
