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

package google.registry.monitoring.whitebox;

import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;

import com.google.common.base.Suppliers;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link Metrics}. */
@RunWith(MockitoJUnitRunner.class)
public class MetricsTest {

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withLocalModules()
      .withTaskQueue()
      .build();

  private final FakeClock clock = new FakeClock(DateTime.parse("1984-12-18TZ"));

  @Before
  public void before() throws Exception {
    inject.setStaticField(Metrics.class, "clock", clock);
    inject.setStaticField(Metrics.class, "idGenerator", Suppliers.ofInstance("laffo"));
  }

  @Test
  public void testExport() throws Exception {
    class TestMetric extends Metrics {}
    Metrics metrics = new TestMetric();
    clock.advanceOneMilli();
    metrics.setTableId("test");
    metrics.export();
    assertTasksEnqueued("bigquery-streaming-metrics",
        new TaskMatcher()
            .url("/_dr/task/metrics")
            .header("Host", "1.backend.test.localhost")
            .param("tableId", "test")
            .param("startTime", "472176000.000000")
            .param("endTime", "472176000.001000")
            .param("insertId", "laffo"));
  }
}
