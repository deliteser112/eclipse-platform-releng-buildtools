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

package google.registry.tools;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;

/**
 * Request factory for dealing with a "localhost" connection.
 *
 * <p>Localhost connections go to the App Engine dev server. The dev server differs from most HTTP
 * connections in that they don't require OAuth2 credentials, but instead require a special cookie.
 *
 * <p>This is an immplementation of HttpRequestFactoryComponent which can be a component dependency
 * in a Dagger graph used for providing a request factory.
 */
class LocalhostRequestFactoryComponent implements HttpRequestFactoryComponent {
  @Override
  public HttpRequestFactory httpRequestFactory() {
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
  }
}

