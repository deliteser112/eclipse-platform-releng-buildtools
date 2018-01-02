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

import static google.registry.flows.async.AsyncFlowMetrics.OperationResult.SUCCESS;
import static google.registry.flows.async.AsyncFlowMetrics.OperationType.CONTACT_AND_HOST_DELETE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.monitoring.metrics.EventMetric;
import com.google.monitoring.metrics.IncrementableMetric;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.testing.ShardableTestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AsyncFlowMetrics}. */
@RunWith(JUnit4.class)
public class AsyncFlowMetricsTest extends ShardableTestCase {

  @Rule public final InjectRule inject = new InjectRule();

  private final IncrementableMetric asyncFlowOperationCounts = mock(IncrementableMetric.class);
  private final EventMetric asyncFlowOperationProcessingTime = mock(EventMetric.class);
  private final EventMetric asyncFlowBatchSize = mock(EventMetric.class);
  private AsyncFlowMetrics asyncFlowMetrics;
  private FakeClock clock;

  @Before
  public void setUp() {
    clock = new FakeClock();
    asyncFlowMetrics = new AsyncFlowMetrics(clock);
    inject.setStaticField(
        AsyncFlowMetrics.class, "asyncFlowOperationCounts", asyncFlowOperationCounts);
    inject.setStaticField(
        AsyncFlowMetrics.class,
        "asyncFlowOperationProcessingTime",
        asyncFlowOperationProcessingTime);
    inject.setStaticField(AsyncFlowMetrics.class, "asyncFlowBatchSize", asyncFlowBatchSize);
  }

  @Test
  public void testRecordAsyncFlowResult_calculatesDurationMillisCorrectly() {
    asyncFlowMetrics.recordAsyncFlowResult(
        CONTACT_AND_HOST_DELETE,
        SUCCESS,
        clock.nowUtc().minusMinutes(10).minusSeconds(5).minusMillis(566));
    verify(asyncFlowOperationCounts).increment("contactAndHostDelete", "success");
    verify(asyncFlowOperationProcessingTime).record(605566.0, "contactAndHostDelete", "success");
    verifyNoMoreInteractions(asyncFlowOperationCounts);
    verifyNoMoreInteractions(asyncFlowOperationProcessingTime);
  }
}
