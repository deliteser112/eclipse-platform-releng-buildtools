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

package google.registry.proxy;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.monitoring.metrics.MetricReporter;
import com.google.monitoring.metrics.MetricWriter;
import com.google.monitoring.metrics.stackdriver.StackdriverWriter;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/** Module that provides necessary bindings to instantiate a {@link MetricReporter} */
@Module
public class MetricsModule {

  // TODO (b/64765479): change to GKE cluster and config in YAML file.
  private static final String MONITORED_RESOURCE_TYPE = "gce_instance";
  private static final String GCE_INSTANCE_ZONE = "us-east4-c";
  private static final String GCE_INSTANCE_ID = "5401454098973297721";

  @Singleton
  @Provides
  static Monitoring provideMonitoring(GoogleCredential credential, ProxyConfig config) {
    return new Monitoring.Builder(
            Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(), credential)
        .setApplicationName(config.projectId)
        .build();
  }

  @Singleton
  @Provides
  static MetricWriter provideMetricWriter(Monitoring monitoringClient, ProxyConfig config) {
    // The MonitoredResource for GAE apps is not writable (and missing fields anyway) so we just
    // use the gce_instance resource type instead.
    return new StackdriverWriter(
        monitoringClient,
        config.projectId,
        new MonitoredResource()
            .setType(MONITORED_RESOURCE_TYPE)
            .setLabels(ImmutableMap.of("zone", GCE_INSTANCE_ZONE, "instance_id", GCE_INSTANCE_ID)),
        config.metrics.stackdriverMaxQps,
        config.metrics.stackdriverMaxPointsPerRequest);
  }

  @Singleton
  @Provides
  static MetricReporter provideMetricReporter(MetricWriter metricWriter, ProxyConfig config) {
    return new MetricReporter(
        metricWriter,
        config.metrics.writeIntervalSeconds,
        new ThreadFactoryBuilder().setDaemon(true).build());
  }

  @Singleton
  @Component(modules = {MetricsModule.class, ProxyModule.class})
  interface MetricsComponent {
    MetricReporter metricReporter();
  }
}
