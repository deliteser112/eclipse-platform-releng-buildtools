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

import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptors;

/**
 * Main class to stage a templated Dataflow pipeline which reads from Bigquery on GCS.
 *
 * <p>To stage this pipeline on GCS, run it with the following command line flags:
 * <ul>
 * <li>--runner=DataflowRunner
 * <li>--project=[YOUR PROJECT ID]
 * <li>--stagingLocation=gs://[WHERE PIPELINE JAR FILES SHOULD BE STAGED]
 * <li>--templateLocation=gs://[WHERE TEMPLATE.txt FILE SHOULD BE STAGED]
 * </ul>
 *
 * <p>Then, you can run the staged template via the API client library, gCloud or a raw REST call.
 *
 * @see <a href="https://cloud.google.com/dataflow/docs/templates/overview">Dataflow Templates</a>
 */
public class BigqueryTemplatePipeline {

  /** Custom command-line pipeline options for {@code BigqueryTemplatePipeline}. */
  public interface BigqueryTemplateOptions extends PipelineOptions {
    @Description("Bigquery query used to get the initial data for the pipeline.")
    @Default.String("SELECT * FROM `[YOUR_PROJECT].[DATASET].[TABLE]`")
    String getBigqueryQuery();
    void setBigqueryQuery(String value);

    @Description("The GCS bucket we output the result text file to.")
    @Default.String("[YOUR BUCKET HERE]")
    String getOutputBucket();
    void setOutputBucket(String value);
  }

  public static void main(String[] args) {
    // Parse standard arguments, as well as custom options
    BigqueryTemplateOptions options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(BigqueryTemplateOptions.class);

    // Create pipeline
    Pipeline p = Pipeline.create(options);
    p.apply(BigQueryIO.readTableRows().fromQuery(options.getBigqueryQuery()).usingStandardSql())
        .apply("Count request paths", new CountRequestPaths())
        .apply(TextIO.write().to(options.getOutputBucket()).withoutSharding().withHeader("HEADER"));
    p.run();
  }

  /** A composite {@code PTransform} that counts the number of times each request path appears. */
  static class CountRequestPaths extends PTransform<PCollection<TableRow>, PCollection<String>> {
    @Override
    public PCollection<String> expand(PCollection<TableRow> input) {
      return input
          .apply("Extract paths", ParDo.of(new ExtractRequestPathFn()))
          .apply("Count paths", Count.perElement())
          .apply(
              "Format results",
              MapElements.into(TypeDescriptors.strings())
                  .via(kv -> kv.getKey() + ": " + kv.getValue()));
    }
  }

  /** A {@code DoFn} that extracts the request path from a Bigquery {@code TableRow}. */
  static class ExtractRequestPathFn extends DoFn<TableRow, String> {
    @ProcessElement
    public void processElement(ProcessContext c) {
      c.output(String.valueOf(c.element().get("requestPath")));
    }
  }
}
