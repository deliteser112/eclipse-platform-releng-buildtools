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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AtomicLongMap;
import google.registry.monitoring.metrics.MetricSchema.Kind;
import java.util.Map.Entry;
import javax.annotation.concurrent.ThreadSafe;
import org.joda.time.Instant;

/**
 * A metric which stores Long values. It is stateful and can be changed in increments.
 *
 * <p>This metric is generally suitable for counters, such as requests served or errors generated.
 */
@ThreadSafe
public final class Counter extends AbstractMetric<Long>
    implements SettableMetric<Long>, IncrementableMetric {

  private static final String LABEL_COUNT_ERROR =
      "The count of labelValues must be equal to the underlying "
          + "MetricDescriptor's count of labels.";

  private final AtomicLongMap<ImmutableList<String>> values = AtomicLongMap.create();

  Counter(
      String name,
      String description,
      String valueDisplayName,
      ImmutableSet<LabelDescriptor> labels) {
    super(name, description, valueDisplayName, Kind.CUMULATIVE, labels, Long.class);
  }

  @VisibleForTesting
  void incrementBy(long offset, ImmutableList<String> labelValues) {
    values.addAndGet(labelValues, offset);
  }

  @Override
  public final void incrementBy(long offset, String... labelValues) {
    checkArgument(labelValues.length == this.getMetricSchema().labels().size(), LABEL_COUNT_ERROR);
    checkArgument(offset >= 0, "The offset provided must be non-negative");

    incrementBy(offset, ImmutableList.copyOf(labelValues));
  }

  @Override
  public final void increment(String... labelValues) {
    checkArgument(labelValues.length == this.getMetricSchema().labels().size(), LABEL_COUNT_ERROR);

    incrementBy(1L, ImmutableList.copyOf(labelValues));
  }

  /**
   * Returns a snapshot of the metric's values. The timestamp of each {@link MetricPoint} will be
   * the last modification time for that tuple of label values.
   */
  @Override
  public final ImmutableList<MetricPoint<Long>> getTimestampedValues() {
    return getTimestampedValues(Instant.now());
  }

  @Override
  public final int getCardinality() {
    return values.size();
  }

  @VisibleForTesting
  final ImmutableList<MetricPoint<Long>> getTimestampedValues(Instant timestamp) {
    ImmutableList.Builder<MetricPoint<Long>> timestampedValues = new ImmutableList.Builder<>();
    for (Entry<ImmutableList<String>, Long> entry : values.asMap().entrySet()) {
      timestampedValues.add(MetricPoint.create(this, entry.getKey(), timestamp, entry.getValue()));
    }
    return timestampedValues.build();
  }

  @VisibleForTesting
  final void set(Long value, ImmutableList<String> labelValues) {
    this.values.put(labelValues, value);
  }

  @Override
  public final void set(Long value, String... labelValues) {
    checkArgument(labelValues.length == this.getMetricSchema().labels().size(), LABEL_COUNT_ERROR);

    set(value, ImmutableList.copyOf(labelValues));
  }
}
