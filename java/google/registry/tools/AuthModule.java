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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;
import javax.inject.Singleton;

/**
 * Module providing the dependency graph for authorization credentials.
 */
@Module
public class AuthModule {

  private static final File DATA_STORE_DIR =
      new File(System.getProperty("user.home"), ".config/nomulus/credentials");

  @Provides
  public Credential provideCredential(
      GoogleAuthorizationCodeFlow flow,
      @ClientScopeQualifier String clientScopeQualifier) {
    try {
      // Try to load the credentials, throw an exception if we fail.
      Credential credential = flow.loadCredential(clientScopeQualifier);
      if (credential == null) {
        throw new LoginRequiredException();
      }
      return credential;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Provides
  GoogleAuthorizationCodeFlow provideAuthorizationCodeFlow(
      JsonFactory jsonFactory,
      GoogleClientSecrets clientSecrets,
      @Config("requiredOauthScopes") ImmutableSet<String> requiredOauthScopes,
      AbstractDataStoreFactory dataStoreFactory) {
    try {
      return new GoogleAuthorizationCodeFlow.Builder(
              new NetHttpTransport(), jsonFactory, clientSecrets, requiredOauthScopes)
          .setDataStoreFactory(dataStoreFactory)
          .build();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Provides
  AuthorizationCodeInstalledApp provideAuthorizationCodeInstalledApp(
      GoogleAuthorizationCodeFlow flow) {
    return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver());
  }

  @Provides
  GoogleClientSecrets provideClientSecrets(
      @Config("clientSecretFilename") String clientSecretFilename, JsonFactory jsonFactory) {
    try {
      // Load the client secrets file.
      InputStream secretResourceStream = getClass().getResourceAsStream(clientSecretFilename);
      if (secretResourceStream == null) {
        throw new RuntimeException("No client secret file found: " + clientSecretFilename);
      }
      return GoogleClientSecrets.load(jsonFactory,
          new InputStreamReader(secretResourceStream, UTF_8));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Provides
  @OAuthClientId String provideClientId(GoogleClientSecrets clientSecrets) {
    return clientSecrets.getDetails().getClientId();
  }

  @Provides
  @ClientScopeQualifier String provideClientScopeQualifier(
      @OAuthClientId String clientId, @Config("requiredOauthScopes") ImmutableSet<String> scopes) {
    return clientId + " " + Joiner.on(" ").join(Ordering.natural().sortedCopy(scopes));
  }

  @Provides
  @Singleton
  public AbstractDataStoreFactory provideDataStoreFactory() {
    try {
      return new FileDataStoreFactory(DATA_STORE_DIR);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /** Wrapper class to hold the login() function. */
  public static class Authorizer {
    /** Initiate the login flow. */
    public static void login(
        GoogleAuthorizationCodeFlow flow,
        @ClientScopeQualifier String clientScopeQualifier) throws IOException {
      new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
          .authorize(clientScopeQualifier);
    }
  }

  /** Raised when we need a user login. */
  public static class LoginRequiredException extends RuntimeException {
    public LoginRequiredException() {}
  }

  /** Dagger qualifier for the credential qualifier consisting of client and scopes. */
  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ClientScopeQualifier {}

  /** Dagger qualifier for the OAuth2 client id. */
  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface OAuthClientId {}
}
