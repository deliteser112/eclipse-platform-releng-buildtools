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

package google.registry.monitoring.whitebox;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.MonitoringScopes;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import google.registry.config.ConfigModule.Config;
import google.registry.request.OAuthScopes;
import java.util.Set;

/** Dagger module for Google Stackdriver service connection objects. */
@Module
public final class StackdriverModule {

  /** Provides OAuth2 scopes for the Stackdriver service needed by Domain Registry. */
  @Provides
  @ElementsIntoSet
  @OAuthScopes
  static Set<String> provideStackdriverOAuthScopes() {
    return MonitoringScopes.all();
  }

  @Provides
  static Monitoring provideMonitoring(
      HttpTransport transport,
      JsonFactory jsonFactory,
      HttpRequestInitializer httpRequestInitializer,
      @Config("projectId") String projectId) {
    return new Monitoring.Builder(transport, jsonFactory, httpRequestInitializer)
        .setApplicationName(projectId)
        .build();
  }
}
