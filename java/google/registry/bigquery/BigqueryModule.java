// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.bigquery;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.common.collect.ImmutableList;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.Multibinds;

import google.registry.config.ConfigModule.Config;
import google.registry.request.OAuthScopes;

import java.util.Map;
import java.util.Set;

/**
 * Dagger module for Google {@link Bigquery} connection objects.
 *
 * @see google.registry.config.ConfigModule
 * @see google.registry.request.Modules.UrlFetchTransportModule
 * @see google.registry.request.Modules.Jackson2Module
 * @see google.registry.request.Modules.AppIdentityCredentialModule
 * @see google.registry.request.Modules.UseAppIdentityCredentialForGoogleApisModule
 */
@Module
public abstract class BigqueryModule {

  /** Provides a map of BigQuery table names to field names. */
  @Multibinds
  abstract Map<String, ImmutableList<TableFieldSchema>> bigquerySchemas();

  /** Provides OAuth2 scopes for the Bigquery service needed by Domain Registry. */
  @Provides
  @ElementsIntoSet
  @OAuthScopes
  static Set<String> provideBigqueryOAuthScopes() {
    return BigqueryScopes.all();
  }

  @Provides
  static Bigquery provideBigquery(
      HttpTransport transport,
      JsonFactory jsonFactory,
      HttpRequestInitializer httpRequestInitializer,
      @Config("projectId") String projectId) {
    return new Bigquery.Builder(transport, jsonFactory, httpRequestInitializer)
        .setApplicationName(projectId)
        .build();
  }

  // No subclasses.
  private BigqueryModule() {}
}
