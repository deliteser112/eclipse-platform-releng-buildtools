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

package google.registry.flows;

import com.google.common.collect.ImmutableSet;
import google.registry.monitoring.metrics.EventMetric;
import google.registry.monitoring.metrics.IncrementableMetric;
import google.registry.monitoring.metrics.LabelDescriptor;
import google.registry.monitoring.metrics.MetricRegistryImpl;
import google.registry.monitoring.whitebox.EppMetric;
import javax.inject.Inject;

/** EPP Instrumentation. */
public class EppMetrics {

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS =
      ImmutableSet.of(
          LabelDescriptor.create("command", "The name of the command."),
          LabelDescriptor.create("client_id", "The name of the client."),
          LabelDescriptor.create("status", "The return status of the command."));

  private static final IncrementableMetric eppRequests =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/epp/requests", "Count of EPP Requests", "count", LABEL_DESCRIPTORS);

  private static final EventMetric processingTime =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/epp/processing_time",
              "EPP Processing Time",
              "milliseconds",
              LABEL_DESCRIPTORS,
              EventMetric.DEFAULT_FITTER);

  @Inject
  public EppMetrics() {}

  /**
   * Increment a counter which tracks EPP requests.
   *
   * @see EppController
   * @see FlowRunner
   */
  public void incrementEppRequests(EppMetric metric) {
    String eppStatusCode =
        metric.getStatus().isPresent() ? String.valueOf(metric.getStatus().get().code) : "";
    eppRequests.increment(
        metric.getCommandName().or(""),
        metric.getClientId().or(""),
        eppStatusCode);
  }

  /** Record the server-side processing time for an EPP request. */
  public void recordProcessingTime(EppMetric metric) {
    String eppStatusCode =
        metric.getStatus().isPresent() ? String.valueOf(metric.getStatus().get().code) : "";
    processingTime.record(
        metric.getEndTimestamp().getMillis() - metric.getStartTimestamp().getMillis(),
        metric.getCommandName().or(""),
        metric.getClientId().or(""),
        eppStatusCode);
  }
}
