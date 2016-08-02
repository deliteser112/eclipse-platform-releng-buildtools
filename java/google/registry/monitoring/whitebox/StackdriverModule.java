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
import com.google.common.base.Function;
import dagger.Module;
import dagger.Provides;
import google.registry.config.ConfigModule.Config;
import java.util.Set;

/** Dagger module for Google Stackdriver service connection objects. */
@Module
public final class StackdriverModule {

  @Provides
  static Monitoring provideMonitoring(
      HttpTransport transport,
      JsonFactory jsonFactory,
      Function<Set<String>, ? extends HttpRequestInitializer> credential,
      @Config("projectId") String projectId) {
    return new Monitoring.Builder(transport, jsonFactory, credential.apply(MonitoringScopes.all()))
        .setApplicationName(projectId)
        .build();
  }
}
