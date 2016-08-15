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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import org.joda.time.Instant;

/**
 * Value type class to store a point-in-time snapshot of a {@link Metric} value for a given label
 * value tuple.
 */
@AutoValue
public abstract class MetricPoint<V> {

  private static final String LABEL_COUNT_ERROR =
      "The count of labelsValues must be equal to the underlying "
          + "MetricDescriptor's count of labels.";

  MetricPoint() {}

  /**
   * Returns a new {@link MetricPoint}. Callers should insure that the count of {@code labelValues}
   * matches the count of labels for the given metric.
   */
  static <V> MetricPoint<V> create(
      Metric<V> metric, ImmutableList<String> labelValues, Instant timestamp, V value) {
    checkArgument(
        labelValues.size() == metric.getMetricSchema().labels().size(), LABEL_COUNT_ERROR);
    return new AutoValue_MetricPoint<>(metric, labelValues, timestamp, value);
  }

  public abstract Metric<V> metric();

  public abstract ImmutableList<String> labelValues();

  public abstract Instant timestamp();

  public abstract V value();
}
