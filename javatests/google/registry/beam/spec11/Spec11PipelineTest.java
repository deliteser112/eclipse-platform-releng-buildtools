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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import google.registry.beam.spec11.SafeBrowsingTransforms.EvaluateSafeBrowsingFn;
import google.registry.util.ResourceUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Supplier;
import org.apache.beam.runners.direct.DirectRunner;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider.StaticValueProvider;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.stubbing.Answer;

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
    ImmutableList.Builder<Subdomain> subdomainsBuilder = new ImmutableList.Builder<>();
    // Put in 2 batches worth (490 < max < 490*2) to get one positive and one negative example.
    for (int i = 0; i < 510; i++) {
      subdomainsBuilder.add(
          Subdomain.create(
              String.format("%s.com", i),
              ZonedDateTime.of(2017, 9, 29, 0, 0, 0, 0, ZoneId.of("UTC")),
              "OK"));
    }
    return subdomainsBuilder.build();
  }

  /**
   * Tests the end-to-end Spec11 pipeline with mocked out API calls.
   *
   * <p>We suppress the (Serializable & Supplier) dual-casted lambda warnings because the supplier
   * produces an explicitly serializable mock, which is safe to cast.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testEndToEndPipeline_generatesExpectedFiles() throws Exception {
    // Establish mocks for testing
    ImmutableList<Subdomain> inputRows = getInputDomains();
    CloseableHttpClient httpClient = mock(CloseableHttpClient.class, withSettings().serializable());
    CloseableHttpResponse negativeResponse =
        mock(CloseableHttpResponse.class, withSettings().serializable());
    CloseableHttpResponse positiveResponse =
        mock(CloseableHttpResponse.class, withSettings().serializable());

    // Tailor the fake API's response based on whether or not it contains the "bad url" 111.com
    when(httpClient.execute(any(HttpPost.class)))
        .thenAnswer(
            (Answer & Serializable)
                (i) -> {
                  String request =
                      CharStreams.toString(
                          new InputStreamReader(
                              ((HttpPost) i.getArguments()[0]).getEntity().getContent(), UTF_8));
                  if (request.contains("http://111.com")) {
                    return positiveResponse;
                  } else {
                    return negativeResponse;
                  }
                });
    when(negativeResponse.getStatusLine())
        .thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "Done"));
    when(negativeResponse.getEntity()).thenReturn(new FakeHttpEntity("{}"));
    when(positiveResponse.getStatusLine())
        .thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "Done"));
    when(positiveResponse.getEntity())
        .thenReturn(new FakeHttpEntity(getBadUrlMatch("http://111.com")));
    EvaluateSafeBrowsingFn evalFn =
        new EvaluateSafeBrowsingFn(
           StaticValueProvider.of("apikey"), (Serializable & Supplier) () -> httpClient);

    // Apply input and evaluation transforms
    PCollection<Subdomain> input = p.apply(Create.of(inputRows));
    spec11Pipeline.evaluateUrlHealth(input, evalFn);
    p.run();

    // Verify output of text file
    ImmutableList<String> generatedReport = resultFileContents();
    // TODO(b/80524726): Rigorously test this output once the pipeline output is finalized.
    assertThat(generatedReport).hasSize(2);
    assertThat(generatedReport.get(1)).contains("http://111.com");

  }

  /** Returns the text contents of a file under the beamBucket/results directory. */
  private ImmutableList<String> resultFileContents() throws Exception {
    File resultFile = new File(String.format("%s/results", tempFolder.getRoot().getAbsolutePath()));
    return ImmutableList.copyOf(
        ResourceUtils.readResourceUtf8(resultFile.toURI().toURL()).split("\n"));
  }

  /** Returns a filled-in template for threat detected at a given url. */
  private static String getBadUrlMatch(String url) {
    return "{\n"
        + "  \"matches\": [{\n"
        + "    \"threatType\":      \"MALWARE\",\n"
        + "    \"platformType\":    \"WINDOWS\",\n"
        + "    \"threatEntryType\": \"URL\",\n"
        + String.format("    \"threat\":          {\"url\": \"%s\"},\n", url)
        + "    \"threatEntryMetadata\": {\n"
        + "      \"entries\": [{\n"
        + "        \"key\": \"malware_threat_type\",\n"
        + "        \"value\": \"landing\"\n"
        + "     }]\n"
        + "    },\n"
        + "    \"cacheDuration\": \"300.000s\"\n"
        + "  },"
        + "]\n"
        + "}";
  }

  /** A serializable HttpEntity fake that returns {@link String} content. */
  private static class FakeHttpEntity extends BasicHttpEntity implements Serializable {

    private static final long serialVersionUID = 105738294571L;

    private String content;

    private void writeObject(ObjectOutputStream oos) throws IOException {
      oos.defaultWriteObject();
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      super.setContent(new ByteArrayInputStream(this.content.getBytes(UTF_8)));
    }

    FakeHttpEntity(String content) {
      this.content = content;
    }
  }
}
