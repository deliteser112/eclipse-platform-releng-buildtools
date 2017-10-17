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

package google.registry.model.server;

import com.google.common.collect.ImmutableSet;
import google.registry.model.server.Lock.LockState;
import google.registry.monitoring.metrics.IncrementableMetric;
import google.registry.monitoring.metrics.LabelDescriptor;
import google.registry.monitoring.metrics.MetricRegistryImpl;
import javax.annotation.Nullable;

/** Metrics for lock contention. */
class LockMetrics {

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "TLD"),
          LabelDescriptor.create("resource", "resource name"),
          LabelDescriptor.create(
              "state", "The existing lock state (before attempting to acquire)."));

  private static final IncrementableMetric lockRequestsMetric =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/lock/acquire_lock_requests",
              "Count of lock acquisition attempts",
              "count",
              LABEL_DESCRIPTORS);

  void record(String resourceName, @Nullable String tld, LockState state) {
    lockRequestsMetric.increment(String.valueOf(tld), resourceName, state.name());
  }
}
