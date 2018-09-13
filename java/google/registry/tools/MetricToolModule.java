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

package google.registry.tools;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.monitoring.metrics.MetricWriter;
import com.google.monitoring.metrics.stackdriver.StackdriverWriter;
import dagger.Module;
import dagger.Provides;
import google.registry.config.CredentialModule.DefaultCredential;
import google.registry.config.RegistryConfig.Config;

/** Dagger module for metrics on the client tool. */
@Module
public final class MetricToolModule {

  @Provides
  static Monitoring provideMonitoring(
      @DefaultCredential GoogleCredential credential, @Config("projectId") String projectId) {
    return new Monitoring.Builder(
            credential.getTransport(), credential.getJsonFactory(), credential)
        .setApplicationName(projectId)
        .build();
  }

  @Provides
  static MetricWriter provideMetricWriter(
      Monitoring monitoringClient,
      @Config("projectId") String projectId,
      @Config("stackdriverMaxQps") int maxQps,
      @Config("stackdriverMaxPointsPerRequest") int maxPointsPerRequest) {
    return new StackdriverWriter(
        monitoringClient,
        projectId,
        new MonitoredResource().setType("global"),
        maxQps,
        maxPointsPerRequest);
  }
}
