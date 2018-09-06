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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import java.io.IOException;
import javax.inject.Qualifier;
import javax.inject.Singleton;

/**
 * Dagger module that provides all {@link GoogleCredential GoogleCredentials} used in the
 * application.
 */
@Module
public abstract class CredentialModule {

  /** Provides the default {@link GoogleCredential} from the Google Cloud runtime. */
  @DefaultCredential
  @Provides
  @Singleton
  public static GoogleCredential provideDefaultCredential(
      @Config("credentialOauthScopes") ImmutableList<String> requiredScopes) {
    GoogleCredential credential;
    try {
      credential = GoogleCredential.getApplicationDefault();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (credential.createScopedRequired()) {
      return credential.createScoped(requiredScopes);
    }
    return credential;
  }

  /** Dagger qualifier for the Application Default Credential. */
  @Qualifier
  public @interface DefaultCredential {}

  /**
   * Dagger qualifier for a credential from a service account's JSON key, to be used in non-request
   * threads.
   */
  @Qualifier
  public @interface JsonCredential {}

  /**
   * Dagger qualifier for a credential with delegated admin access for a dasher domain (for G
   * Suite).
   */
  @Qualifier
  public @interface DelegatedCredential {}
}
