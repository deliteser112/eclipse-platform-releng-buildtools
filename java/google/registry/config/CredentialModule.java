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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.util.Utils;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import google.registry.keyring.api.KeyModule.Key;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
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
      @Config("defaultCredentialOauthScopes") ImmutableList<String> requiredScopes) {
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

  /**
   * Provides a {@link GoogleCredential} from the service account's JSON key file.
   *
   * <p>On App Engine, a thread created using Java's built-in API needs this credential when it
   * calls App Engine API. The Google Sheets API also needs this credential.
   */
  @JsonCredential
  @Provides
  @Singleton
  public static GoogleCredential provideJsonCredential(
      @Config("defaultCredentialOauthScopes") ImmutableList<String> requiredScopes,
      @Key("jsonCredential") String jsonCredential) {
    GoogleCredential credential;
    try {
      credential =
          GoogleCredential.fromStream(
              new ByteArrayInputStream(jsonCredential.getBytes(UTF_8)),
              // We cannot use UrlFetchTransport as that uses App Engine API.
              GoogleNetHttpTransport.newTrustedTransport(),
              Utils.getDefaultJsonFactory());
    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(requiredScopes);
    }
    return credential;
  }

  /**
   * Provides a {@link GoogleCredential} with delegated admin access for a G Suite domain.
   *
   * <p>The G Suite domain must grant delegated admin access to the registry service account with
   * all scopes in {@code requiredScopes}, including ones not related to G Suite.
   */
  @DelegatedCredential
  @Provides
  @Singleton
  public static GoogleCredential provideDelegatedCredential(
      @Config("delegatedCredentialOauthScopes") ImmutableList<String> requiredScopes,
      @JsonCredential GoogleCredential googleCredential,
      @Config("gSuiteAdminAccountEmailAddress") String gSuiteAdminAccountEmailAddress) {
    return new GoogleCredential.Builder()
        .setTransport(Utils.getDefaultTransport())
        .setJsonFactory(Utils.getDefaultJsonFactory())
        .setServiceAccountId(googleCredential.getServiceAccountId())
        .setServiceAccountPrivateKey(googleCredential.getServiceAccountPrivateKey())
        .setServiceAccountScopes(requiredScopes)
        .setServiceAccountUser(gSuiteAdminAccountEmailAddress)
        .build();
  }

  /**
   * Provides a {@link AppIdentityCredential} with access for App Engine Admin API.
   *
   * <p>{@link AppIdentityCredential} is an OAuth 2.0 credential in which a client Google App Engine
   * application needs to access data that it owns.
   */
  @AppEngineAdminApiCredential
  @Provides
  @Singleton
  public static AppIdentityCredential provideAppEngineAdminApiCredential(
      @Config("appEngineAdminApiCredentialOauthScopes") ImmutableList<String> requiredScopes) {
    return new AppIdentityCredential(requiredScopes);
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

  /** Dagger qualifier for a credential with access for App Engine Admin API. */
  @Qualifier
  public @interface AppEngineAdminApiCredential {}
}
