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

package google.registry.dns;

import static google.registry.request.RequestParameters.PARAM_TLD;

import com.google.common.collect.ImmutableSet;
import google.registry.config.RegistryEnvironment;
import google.registry.monitoring.metrics.DistributionFitter;
import google.registry.monitoring.metrics.EventMetric;
import google.registry.monitoring.metrics.ExponentialFitter;
import google.registry.monitoring.metrics.FibonacciFitter;
import google.registry.monitoring.metrics.IncrementableMetric;
import google.registry.monitoring.metrics.LabelDescriptor;
import google.registry.monitoring.metrics.MetricRegistryImpl;
import google.registry.request.Parameter;
import javax.inject.Inject;
import org.joda.time.Duration;

/** DNS instrumentation. */
// TODO(b/67947699):Once load testing is done, revisit these to rename them and delete the ones that
// we don't expect to need long-term.
public class DnsMetrics {

  /** Disposition of a publish request. */
  public enum PublishStatus { ACCEPTED, REJECTED }

  /** Disposition of writer.commit(). */
  public enum CommitStatus { SUCCESS, FAILURE }

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS_FOR_PUBLISH_REQUESTS =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "TLD"),
          LabelDescriptor.create(
              "status", "Whether the publish request was accepted or rejected."));

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS_FOR_COMMIT =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "TLD"),
          LabelDescriptor.create(
              "status", "Whether writer.commit() succeeded or failed."));

  // Finer-grained fitter than the DEFAULT_FITTER, allows values between 1. and 2^20, which gives
  // over 15 minutes.
  private static final DistributionFitter EXPONENTIAL_FITTER =
      ExponentialFitter.create(20, 2.0, 1.0);

  // Fibonacci fitter more suitible for integer-type values. Allows values between 0 and 10946,
  // which is the 21th Fibonacci number.
  private static final DistributionFitter FIBONACCI_FITTER =
      FibonacciFitter.create(10946);

  private static final IncrementableMetric publishDomainRequests =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/dns/publish_domain_requests",
              "count of publishDomain requests",
              "count",
              LABEL_DESCRIPTORS_FOR_PUBLISH_REQUESTS);

  private static final IncrementableMetric publishHostRequests =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/dns/publish_host_requests",
              "count of publishHost requests",
              "count",
              LABEL_DESCRIPTORS_FOR_PUBLISH_REQUESTS);

  private static final IncrementableMetric commitCount =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/dns/commit_requests",
              "Count of writer.commit() calls",
              "count",
              LABEL_DESCRIPTORS_FOR_COMMIT);

  private static final IncrementableMetric domainsCommittedCount =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/dns/domains_committed",
              "Count of domains committed",
              "count",
              LABEL_DESCRIPTORS_FOR_COMMIT);

  private static final IncrementableMetric hostsCommittedCount =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/dns/hosts_committed",
              "Count of hosts committed",
              "count",
              LABEL_DESCRIPTORS_FOR_COMMIT);

  private static final EventMetric processingTimePerCommitDist =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/dns/per_batch/processing_time",
              "publishDnsUpdates Processing Time",
              "milliseconds",
              LABEL_DESCRIPTORS_FOR_COMMIT,
              EXPONENTIAL_FITTER);

  private static final EventMetric normalizedProcessingTimePerCommitDist =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/dns/per_batch/processing_time_per_dns_update",
              "publishDnsUpdates Processing Time, divided by the batch size",
              "milliseconds",
              LABEL_DESCRIPTORS_FOR_COMMIT,
              EXPONENTIAL_FITTER);

  private static final EventMetric totalBatchSizePerCommitDist =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/dns/per_batch/batch_size",
              "Number of hosts and domains committed in each publishDnsUpdates",
              "count",
              LABEL_DESCRIPTORS_FOR_COMMIT,
              FIBONACCI_FITTER);

  private static final EventMetric processingTimePerItemDist =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/dns/per_item/processing_time",
              "publishDnsUpdates Processing Time",
              "milliseconds",
              LABEL_DESCRIPTORS_FOR_COMMIT,
              EXPONENTIAL_FITTER);

  private static final EventMetric normalizedProcessingTimePerItemDist =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/dns/per_item/processing_time_per_dns_update",
              "publishDnsUpdates Processing Time, divided by the batch size",
              "milliseconds",
              LABEL_DESCRIPTORS_FOR_COMMIT,
              EXPONENTIAL_FITTER);

  private static final EventMetric totalBatchSizePerItemDist =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/dns/per_item/batch_size",
              "Batch sizes for hosts and domains",
              "count",
              LABEL_DESCRIPTORS_FOR_COMMIT,
              FIBONACCI_FITTER);

  @Inject RegistryEnvironment registryEnvironment;
  @Inject @Parameter(PARAM_TLD) String tld;

  @Inject
  DnsMetrics() {}

  /**
   * Increment a monotonic counter that tracks calls to {@link
   * google.registry.dns.writer.DnsWriter#publishDomain(String)}, per TLD.
   */
  public void incrementPublishDomainRequests(long numRequests, PublishStatus status) {
    if (numRequests > 0) {
      publishDomainRequests.incrementBy(numRequests, tld, status.name());
    }
  }

  /**
   * Increment a monotonic counter that tracks calls to {@link
   * google.registry.dns.writer.DnsWriter#publishHost(String)}, per TLD.
   */
  public void incrementPublishHostRequests(long numRequests, PublishStatus status) {
    if (numRequests > 0) {
      publishHostRequests.incrementBy(numRequests, tld, status.name());
    }
  }

  /**
   * Measures information about the entire batched commit, per TLD.
   *
   * The information includes running times (per item and per commit), and batch sizes (per item and
   * per commit)
   *
   * This is to be used for load testing the system, and will not measure anything in prod.
   */
  void recordCommit(
      CommitStatus status,
      Duration processingDuration,
      int numberOfDomains,
      int numberOfHosts) {
    // We don't want to record all these metrics in production, as they are quite expensive
    if (registryEnvironment == RegistryEnvironment.PRODUCTION) {
      return;
    }
    int batchSize = numberOfDomains + numberOfHosts;

    processingTimePerCommitDist.record(processingDuration.getMillis(), tld, status.name());
    processingTimePerItemDist.record(
        processingDuration.getMillis(), batchSize, tld, status.name());

    if (batchSize > 0) {
      normalizedProcessingTimePerCommitDist.record(
          (double) processingDuration.getMillis() / batchSize,
          tld, status.name());
      normalizedProcessingTimePerItemDist.record(
          (double) processingDuration.getMillis() / batchSize,
          batchSize,
          tld, status.name());
    }

    totalBatchSizePerCommitDist.record(batchSize, tld, status.name());

    totalBatchSizePerItemDist.record(batchSize, batchSize, tld, status.name());

    commitCount.increment(tld, status.name());
    domainsCommittedCount.incrementBy(numberOfDomains, tld, status.name());
    hostsCommittedCount.incrementBy(numberOfHosts, tld, status.name());
  }
}
