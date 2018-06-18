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

package google.registry.tools.server;

import static com.google.appengine.tools.pipeline.PipelineServiceFactory.newPipelineService;
import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.mapreduce.Counters;
import com.google.appengine.tools.mapreduce.MapReduceResult;
import com.google.appengine.tools.pipeline.Job0;
import com.google.appengine.tools.pipeline.JobInfo.State;
import com.google.appengine.tools.pipeline.Value;
import google.registry.testing.FakeJsonResponse;
import google.registry.testing.mapreduce.MapreduceTestCase;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PollMapreduceAction}.*/
@RunWith(JUnit4.class)
public class PollMapreduceActionTest extends MapreduceTestCase<PollMapreduceAction> {

  @Before
  public void init() {
    action = new PollMapreduceAction();
  }

  @Test
  public void testPollUntilSuccess() throws Exception {
    action.jobId = newPipelineService().startNewPipeline(new SuccessfulJob());
    assertThat(poll()).containsExactly("state", State.RUNNING.toString());
    executeTasksUntilEmpty("default");
    assertThat(poll()).containsExactly(
        "state", State.COMPLETED_SUCCESSFULLY.toString(), "output", "foobar");
  }

  @Test
  public void testPollUntilFailure() throws Exception {
    action.jobId = newPipelineService().startNewPipeline(new ThrowingJob());
    assertThat(poll()).containsExactly("state", State.RUNNING.toString());
    executeTasksUntilEmpty("default");
    assertThat(poll()).containsExactly("state", State.STOPPED_BY_ERROR.toString());
  }

  Map<String, ?> poll() {
    action.response = new FakeJsonResponse();
    action.run();
    return ((FakeJsonResponse) action.response).getResponseMap();
  }

  /** A job that returns a string. */
  static class SuccessfulJob extends Job0<MapReduceResult<String>> {
    @Override
    public Value<MapReduceResult<String>> run() {
      MapReduceResult<String> result =
          new MapReduceResult<String>() {
            @Override
            public Counters getCounters() {
              throw new UnsupportedOperationException();
            }

            @Override
            public String getOutputResult() {
              return "foobar";
            }};
      return immediate(result);
    }
  }

  /** A job that throws a RunTimeException when run. */
  static class ThrowingJob extends Job0<Void> {
    @Override
    public Value<Void> run() {
      throw new RuntimeException("expected");
    }
  }
}
