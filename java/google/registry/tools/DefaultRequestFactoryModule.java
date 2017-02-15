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
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Documented;
import java.util.Collections;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;

/**
 * Module for providing the default HttpRequestFactory.
 *
 *
 * <p>This module provides a standard NetHttpTransport-based HttpRequestFactory binding.
 * The binding is qualified with the name named "default" and is not consumed directly.  The
 * RequestFactoryModule module binds the "default" HttpRequestFactory to the unqualified
 * HttpRequestFactory, allowing users to override the actual, unqualified HttpRequestFactory
 * binding by replacing RequestFactoryfModule with their own module, optionally providing
 * the "default" factory in some circumstances.
 *
 * <p>Localhost connections go to the App Engine dev server. The dev server differs from most HTTP
 * connections in that they don't require OAuth2 credentials, but instead require a special cookie.
 */
@Module
class DefaultRequestFactoryModule {

  // TODO(mmuller): Use @Config("requiredOauthScopes")
  private static final String DEFAULT_SCOPE =
      "https://www.googleapis.com/auth/userinfo.email";

  private static final File DATA_STORE_DIR =
      new File(System.getProperty("user.home"), ".config/nomulus/credentials");

  // TODO(mmuller): replace with a config parameter.
  private static final String CLIENT_SECRET_FILENAME =
      "/google/registry/tools/resources/client_secret.json";

  @Provides
  @ClientSecretFilename
  String provideClientSecretFilename() {
    return CLIENT_SECRET_FILENAME;
  }

  /** Returns the credential object for the user. */
  @Provides
  Credential provideCredential(
      AbstractDataStoreFactory dataStoreFactory,
      Authorizer authorizer,
      @ClientSecretFilename String clientSecretFilename) {
    try {
      // Load the client secrets file.
      JacksonFactory jsonFactory = new JacksonFactory();
      InputStream secretResourceStream = getClass().getResourceAsStream(clientSecretFilename);
      if (secretResourceStream == null) {
        throw new RuntimeException("No client secret file found: " + clientSecretFilename);
      }
      GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory,
          new InputStreamReader(secretResourceStream, UTF_8));

      return authorizer.authorize(clientSecrets);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Provides
  @Named("default")
  public HttpRequestFactory provideHttpRequestFactory(
      AppEngineConnectionFlags connectionFlags,
      Provider<Credential> credentialProvider) {
    if (connectionFlags.getServer().getHost().equals("localhost")) {
      return new NetHttpTransport()
          .createRequestFactory(
              new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest request) {
                  request
                      .getHeaders()
                      .setCookie("dev_appserver_login=test@example.com:true:1858047912411");
                }
              });
    } else {
      return new NetHttpTransport().createRequestFactory(credentialProvider.get());
    }
  }

  /**
   * Module for providing HttpRequestFactory.
   *
   * <p>Localhost connections go to the App Engine dev server. The dev server differs from most HTTP
   * connections in that the types whose annotations affect the use of annotaty don't require
   * OAuth2 credentials, but instead require a special cookie.
   */
  @Module
  abstract static class RequestFactoryModule {

    @Binds
    public abstract HttpRequestFactory provideHttpRequestFactory(
        @Named("default") HttpRequestFactory requestFactory);
  }

  @Module
  static class DataStoreFactoryModule {
    @Provides
    @Singleton
    public AbstractDataStoreFactory provideDataStoreFactory() {
      try {
        return new FileDataStoreFactory(DATA_STORE_DIR);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  /**
   * Module to create the Authorizer used by DefaultRequestFactoryModule.
   */
  @Module
  static class AuthorizerModule {
    @Provides
    public Authorizer provideAuthorizer(
        final JsonFactory jsonFactory, final AbstractDataStoreFactory dataStoreFactory) {
      return new Authorizer() {
        @Override
        public Credential authorize(GoogleClientSecrets clientSecrets) {
          try {
            // Run a new auth flow.
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    new NetHttpTransport(), jsonFactory, clientSecrets,
                    Collections.singleton(DEFAULT_SCOPE))
                .setDataStoreFactory(dataStoreFactory)
                .build();

            // TODO(mmuller): "credentials" directory needs to be qualified with the scopes and
            // client id.

            // We pass client id to the authorize method so we can safely persist credentials for
            // multiple client ids.
            return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
                .authorize(clientSecrets.getDetails().getClientId());
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        }
      };
    }
  }

  /**
   * Interface that encapsulates the authorization logic to produce a credential for the user,
   * allowing us to override the behavior for unit tests.
   */
  interface Authorizer {
    Credential authorize(GoogleClientSecrets clientSecrets);
  }

  /** Dagger qualifier for the client secret filename.
   *
   * <p>TODO(mmuller): move this to config.
   */
  @Qualifier
  @Documented
  public @interface ClientSecretFilename {}
}
