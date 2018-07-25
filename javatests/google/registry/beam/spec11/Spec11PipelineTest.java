// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.beam.spec11;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import google.registry.util.ResourceUtils;
import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.apache.beam.runners.direct.DirectRunner;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Spec11Pipeline}. */
@RunWith(JUnit4.class)
public class Spec11PipelineTest {

  private static PipelineOptions pipelineOptions;

  @BeforeClass
  public static void initializePipelineOptions() {
    pipelineOptions = PipelineOptionsFactory.create();
    pipelineOptions.setRunner(DirectRunner.class);
  }

  @Rule public final transient TestPipeline p = TestPipeline.fromOptions(pipelineOptions);
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Spec11Pipeline spec11Pipeline;

  @Before
  public void initializePipeline() throws IOException {
    spec11Pipeline = new Spec11Pipeline();
    spec11Pipeline.projectId = "test-project";
    spec11Pipeline.spec11BucketUrl = tempFolder.getRoot().getAbsolutePath() + "/results";
    File beamTempFolder = tempFolder.newFolder();
    spec11Pipeline.beamStagingUrl = beamTempFolder.getAbsolutePath() + "/staging";
    spec11Pipeline.spec11TemplateUrl = beamTempFolder.getAbsolutePath() + "/templates/invoicing";
  }

  private ImmutableList<Subdomain> getInputDomains() {
    return ImmutableList.of(
        Subdomain.create(
            "a.com", ZonedDateTime.of(2017, 9, 29, 0, 0, 0, 0, ZoneId.of("UTC")), "OK"),
        Subdomain.create(
            "b.com", ZonedDateTime.of(2017, 9, 29, 0, 0, 0, 0, ZoneId.of("UTC")), "OK"),
        Subdomain.create(
            "c.com", ZonedDateTime.of(2017, 9, 29, 0, 0, 0, 0, ZoneId.of("UTC")), "OK"));
  }

  @Test
  public void testEndToEndPipeline_generatesExpectedFiles() throws Exception {
    ImmutableList<Subdomain> inputRows = getInputDomains();
    PCollection<Subdomain> input = p.apply(Create.of(inputRows));
    spec11Pipeline.countDomainsAndOutputResults(input);
    p.run();

    ImmutableList<String> generatedReport = resultFileContents();
    assertThat(generatedReport.get(0)).isEqualTo("HELLO WORLD");
    assertThat(generatedReport.get(1)).isEqualTo("3");
  }

  /** Returns the text contents of a file under the beamBucket/results directory. */
  private ImmutableList<String> resultFileContents() throws Exception {
    File resultFile = new File(String.format("%s/results", tempFolder.getRoot().getAbsolutePath()));
    return ImmutableList.copyOf(
        ResourceUtils.readResourceUtf8(resultFile.toURI().toURL()).split("\n"));
  }
}
