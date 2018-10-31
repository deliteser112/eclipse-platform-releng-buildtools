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

package google.registry.flows.async;

import static com.google.monitoring.metrics.contrib.DistributionMetricSubject.assertThat;
import static com.google.monitoring.metrics.contrib.LongMetricSubject.assertThat;
import static google.registry.flows.async.AsyncFlowMetrics.OperationResult.SUCCESS;
import static google.registry.flows.async.AsyncFlowMetrics.OperationType.CONTACT_AND_HOST_DELETE;

import com.google.common.collect.ImmutableSet;
import google.registry.testing.FakeClock;
import google.registry.testing.ShardableTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AsyncFlowMetrics}. */
@RunWith(JUnit4.class)
public class AsyncFlowMetricsTest extends ShardableTestCase {

  private final FakeClock clock = new FakeClock();
  private final AsyncFlowMetrics asyncFlowMetrics = new AsyncFlowMetrics(clock);

  @Test
  public void testRecordAsyncFlowResult_calculatesDurationMillisCorrectly() {
    asyncFlowMetrics.recordAsyncFlowResult(
        CONTACT_AND_HOST_DELETE,
        SUCCESS,
        clock.nowUtc().minusMinutes(10).minusSeconds(5).minusMillis(566));
    assertThat(AsyncFlowMetrics.asyncFlowOperationCounts)
        .hasValueForLabels(1, "contactAndHostDelete", "success")
        .and()
        .hasNoOtherValues();
    assertThat(AsyncFlowMetrics.asyncFlowOperationProcessingTime)
        .hasDataSetForLabels(ImmutableSet.of(605566.0), "contactAndHostDelete", "success")
        .and()
        .hasNoOtherValues();
  }
}
