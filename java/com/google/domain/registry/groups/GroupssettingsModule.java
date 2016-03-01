// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.groups;

import static dagger.Provides.Type.SET_VALUES;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.groupssettings.Groupssettings;
import com.google.api.services.groupssettings.GroupssettingsScopes;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.config.ConfigModule.Config;
import com.google.domain.registry.request.DelegatedOAuthScopes;

import dagger.Module;
import dagger.Provides;

import java.util.Set;

import javax.inject.Named;

/**
 * Dagger module for the Google {@link Groupssettings} service.
 *
 * @see com.google.domain.registry.config.ConfigModule
 * @see com.google.domain.registry.request.Modules.UrlFetchTransportModule
 * @see com.google.domain.registry.request.Modules.Jackson2Module
 * @see com.google.domain.registry.request.Modules.AppIdentityCredentialModule
 * @see com.google.domain.registry.request.Modules.UseAppIdentityCredentialForGoogleApisModule
 */
@Module
public final class GroupssettingsModule {

  /** Provides OAuth2 scopes for the Groupssettings service needed by Domain Registry. */
  @Provides(type = SET_VALUES)
  @DelegatedOAuthScopes
  static Set<String> provideGroupssettingsOAuthScopes() {
    return ImmutableSet.of(GroupssettingsScopes.APPS_GROUPS_SETTINGS);
  }

  @Provides
  static Groupssettings provideGroupssettings(
      @Named("delegatedAdmin") GoogleCredential credential,
      @Config("projectId") String projectId) {
    return new Groupssettings
        .Builder(credential.getTransport(), credential.getJsonFactory(), credential)
        .setApplicationName(projectId)
        .build();
  }
}
