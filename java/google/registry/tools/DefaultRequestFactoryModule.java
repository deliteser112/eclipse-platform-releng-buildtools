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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig;
import javax.inject.Named;
import javax.inject.Provider;

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

  @Provides
  @Named("default")
  public HttpRequestFactory provideHttpRequestFactory(
      Provider<Credential> credentialProvider) {
    if (RegistryConfig.areServersLocal()) {
      return new NetHttpTransport()
          .createRequestFactory(
              request -> request
                  .getHeaders()
                  .setCookie("dev_appserver_login=test@example.com:true:1858047912411"));
    } else {
      return new NetHttpTransport().createRequestFactory(credentialProvider.get());
    }
  }

  /**
   * Module for providing HttpRequestFactory.
   *
   * <p>Localhost connections go to the App Engine dev server. The dev server differs from most HTTP
   * connections in that it doesn't require OAuth2 credentials, but instead requires a special
   * cookie.
   */
  @Module
  abstract static class RequestFactoryModule {

    @Binds
    public abstract HttpRequestFactory provideHttpRequestFactory(
        @Named("default") HttpRequestFactory requestFactory);
  }
}
