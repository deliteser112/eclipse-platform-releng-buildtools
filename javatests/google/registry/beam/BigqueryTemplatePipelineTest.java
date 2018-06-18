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

package google.registry.beam;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.services.bigquery.model.TableRow;
import com.google.common.collect.ImmutableList;
import google.registry.beam.BigqueryTemplatePipeline.CountRequestPaths;
import google.registry.beam.BigqueryTemplatePipeline.ExtractRequestPathFn;
import java.util.List;
import org.apache.beam.runners.direct.DirectRunner;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFnTester;
import org.apache.beam.sdk.values.PCollection;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BigqueryTemplatePipeline}*/
@RunWith(JUnit4.class)
public class BigqueryTemplatePipelineTest {

  private static PipelineOptions pipelineOptions;

  @BeforeClass
  public static void initializePipelineOptions() {
    pipelineOptions = PipelineOptionsFactory.create();
    pipelineOptions.setRunner(DirectRunner.class);
  }

  @Rule public final transient TestPipeline p = TestPipeline.fromOptions(pipelineOptions);

  @Test
  public void testExtractRequestPathFn() throws Exception {
    ExtractRequestPathFn extractRequestPathFn = new ExtractRequestPathFn();
    DoFnTester<TableRow, String> fnTester = DoFnTester.of(extractRequestPathFn);
    TableRow emptyRow = new TableRow();
    TableRow hasRequestPathRow = new TableRow().set("requestPath", "a/path");
    TableRow hasOtherValueRow = new TableRow().set("anotherValue", "b/lah");
    List<String> outputs = fnTester.processBundle(emptyRow, hasRequestPathRow, hasOtherValueRow);
    assertThat(outputs).containsExactly("null", "a/path", "null");
  }

  @Test
  public void testEndToEndPipeline() {
    ImmutableList<TableRow> inputRows =
        ImmutableList.of(
            new TableRow(),
            new TableRow().set("requestPath", "a/path"),
            new TableRow().set("requestPath", "b/path"),
            new TableRow().set("requestPath", "b/path"),
            new TableRow().set("anotherValue", "b/path"));

    PCollection<TableRow> input = p.apply(Create.of(inputRows));
    PCollection<String> output = input.apply(new CountRequestPaths());

    ImmutableList<String> outputStrings = new ImmutableList.Builder<String>()
        .add("a/path: 1")
        .add("b/path: 2")
        .add("null: 2")
        .build();
    PAssert.that(output).containsInAnyOrder(outputStrings);
    p.run();
  }
}
