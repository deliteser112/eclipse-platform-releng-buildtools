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

package google.registry.monitoring.metrics;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractScheduledService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Engine to write metrics to a {@link MetricWriter} on a regular periodic basis.
 *
 * <p>In the Producer/Consumer pattern, this class is the Producer and {@link MetricExporter} is the
 * consumer.
 */
public class MetricReporter extends AbstractScheduledService {

  private static final Logger logger = Logger.getLogger(MetricReporter.class.getName());

  private final long writeInterval;
  private final MetricRegistry metricRegistry;
  private final BlockingQueue<Optional<ImmutableList<MetricPoint<?>>>> writeQueue;
  private final MetricExporter metricExporter;

  /**
   * Returns a new MetricReporter.
   *
   * @param metricWriter {@link MetricWriter} implementation to write metrics to.
   * @param writeInterval time period between metric writes, in seconds.
   */
  public MetricReporter(MetricWriter metricWriter, long writeInterval) {
    this(
        metricWriter,
        writeInterval,
        MetricRegistryImpl.getDefault(),
        new ArrayBlockingQueue<Optional<ImmutableList<MetricPoint<?>>>>(1000));
  }

  @VisibleForTesting
  MetricReporter(
      MetricWriter metricWriter,
      long writeInterval,
      MetricRegistry metricRegistry,
      BlockingQueue<Optional<ImmutableList<MetricPoint<?>>>> writeQueue) {
    checkArgument(writeInterval > 0, "writeInterval must be greater than zero");

    this.writeInterval = writeInterval;
    this.metricRegistry = metricRegistry;
    this.writeQueue = writeQueue;
    this.metricExporter = new MetricExporter(writeQueue, metricWriter);
  }

  @Override
  protected void runOneIteration() {
    logger.info("Running background metric push");
    ImmutableList.Builder<MetricPoint<?>> points = new ImmutableList.Builder<>();

    /*
    TODO(shikhman): Right now timestamps are recorded for each datapoint, which may use more storage
    on the backend than if one timestamp were recorded for a batch. This should be configurable.
     */
    for (Metric<?> metric : metricRegistry.getRegisteredMetrics()) {
      points.addAll(metric.getTimestampedValues());
      logger.fine(String.format("Enqueued metric %s", metric));
      MetricMetrics.pushedPoints.incrementBy(
          1, metric.getMetricSchema().kind().name(), metric.getValueClass().toString());
    }

    if (!writeQueue.offer(Optional.of(points.build()))) {
      logger.warning("writeQueue full, dropped a reporting interval of points");
    }

    MetricMetrics.pushIntervals.incrementBy(1);
  }

  @Override
  protected void shutDown() {
    // Make sure to run one iteration on shutdown so that short-lived programs still report at
    // least once.
    runOneIteration();

    writeQueue.offer(Optional.<ImmutableList<MetricPoint<?>>>absent());
    metricExporter.stopAsync().awaitTerminated();
  }

  @Override
  protected void startUp() {
    metricExporter.startAsync().awaitRunning();
  }

  @Override
  protected Scheduler scheduler() {
    // Start writing after waiting for one writeInterval.
    return Scheduler.newFixedDelaySchedule(writeInterval, writeInterval, TimeUnit.SECONDS);
  }
}
