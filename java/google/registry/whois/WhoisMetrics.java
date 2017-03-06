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

package google.registry.whois;

import static google.registry.monitoring.metrics.EventMetric.DEFAULT_FITTER;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import google.registry.monitoring.metrics.EventMetric;
import google.registry.monitoring.metrics.IncrementableMetric;
import google.registry.monitoring.metrics.LabelDescriptor;
import google.registry.monitoring.metrics.MetricRegistryImpl;
import google.registry.util.Clock;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * Instrumentation for WHOIS requests.
 *
 * @see WhoisServer
 */
public class WhoisMetrics {

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS =
      ImmutableSet.of(
          LabelDescriptor.create("command_name", "The name of the WHOIS command."),
          LabelDescriptor.create(
              "num_results", "The number of results returned by the WHOIS command."),
          LabelDescriptor.create("status", "The return status of the WHOIS command."));

  private static final IncrementableMetric whoisRequests =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/whois/requests", "Count of WHOIS Requests", "count", LABEL_DESCRIPTORS);

  private static final EventMetric processingTime =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/whois/processing_time",
              "WHOIS Processing Time",
              "milliseconds",
              LABEL_DESCRIPTORS,
              DEFAULT_FITTER);

  @Inject
  public WhoisMetrics() {}

  /**
   * Increment a counter which tracks WHOIS requests.
   *
   * @see WhoisServer
   */
  public void incrementWhoisRequests(WhoisMetric metric) {
    whoisRequests.increment(
        metric.commandName(),
        metric.numResults().toString(),
        metric.status());
  }

  /** Record the server-side processing time for a WHOIS request. */
  public void recordProcessingTime(WhoisMetric metric) {
    processingTime.record(
        metric.endTimestamp().getMillis() - metric.startTimestamp().getMillis(),
        metric.commandName(),
        metric.numResults().toString(),
        metric.status());
  }

  /** A value class for recording attributes of a WHOIS metric. */
  @AutoValue
  public abstract static class WhoisMetric {

    public abstract String commandName();

    public abstract Integer numResults();

    public abstract String status();

    public abstract DateTime startTimestamp();

    public abstract DateTime endTimestamp();

    /**
     * Create a {@link WhoisMetric.Builder} for a request context, with the start and end timestamps
     * taken from the given clock.
     *
     * <p>The start timestamp is recorded now, and the end timestamp at {@code build()}.
     */
    public static WhoisMetric.Builder builderForRequest(Clock clock) {
      return WhoisMetric.builder().setStartTimestamp(clock.nowUtc()).setClock(clock);
    }

    /** Create a {@link WhoisMetric.Builder}. */
    public static Builder builder() {
      return new AutoValue_WhoisMetrics_WhoisMetric.Builder();
    }

    /** A builder to create instances of {@link WhoisMetric}. */
    @AutoValue.Builder
    public abstract static class Builder {

      /** Builder-only clock to support automatic recording of endTimestamp on {@link #build()}. */
      private Clock clock = null;

      public abstract Builder setCommandName(String commandName);

      public abstract Builder setNumResults(Integer numResults);

      public abstract Builder setStatus(String status);

      abstract Builder setStartTimestamp(DateTime startTimestamp);

      abstract Builder setEndTimestamp(DateTime endTimestamp);

      Builder setClock(Clock clock) {
        this.clock = clock;
        return this;
      }

      /**
       * Build an instance of {@link WhoisMetric} using this builder.
       *
       * <p>If a clock was provided with {@code setClock()}, the end timestamp will be set to the
       * current timestamp of the clock; otherwise end timestamp must have been previously set.
       */
      public WhoisMetric build() {
        if (clock != null) {
          setEndTimestamp(clock.nowUtc());
        }
        return autoBuild();
      }

      abstract WhoisMetric autoBuild();
    }
  }
}
