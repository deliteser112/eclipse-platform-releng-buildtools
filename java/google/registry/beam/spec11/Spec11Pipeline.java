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

import google.registry.beam.spec11.SafeBrowsingTransforms.EvaluateSafeBrowsingFn;
import google.registry.config.RegistryConfig.Config;
import java.io.Serializable;
import javax.inject.Inject;
import org.apache.beam.runners.dataflow.DataflowRunner;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Sample;
import org.apache.beam.sdk.transforms.ToString;
import org.apache.beam.sdk.values.PCollection;

/**
 * Definition of a Dataflow pipeline template, which generates a given month's spec11 report.
 *
 * <p>To stage this template on GCS, run the {@link
 * google.registry.tools.DeploySpec11PipelineCommand} Nomulus command.
 *
 * <p>Then, you can run the staged template via the API client library, gCloud or a raw REST call.
 *
 * @see <a href="https://cloud.google.com/dataflow/docs/templates/overview">Dataflow Templates</a>
 */
public class Spec11Pipeline implements Serializable {

  @Inject
  @Config("projectId")
  String projectId;

  @Inject
  @Config("beamStagingUrl")
  String beamStagingUrl;

  @Inject
  @Config("spec11TemplateUrl")
  String spec11TemplateUrl;

  @Inject
  @Config("spec11BucketUrl")
  String spec11BucketUrl;

  @Inject
  Spec11Pipeline() {}

  /** Custom options for running the spec11 pipeline. */
  interface Spec11PipelineOptions extends DataflowPipelineOptions {
    /** Returns the yearMonth we're generating the report for, in yyyy-MM format. */
    @Description("The yearMonth we generate the report for, in yyyy-MM format.")
    ValueProvider<String> getYearMonth();

    /**
     * Sets the yearMonth we generate invoices for.
     *
     * <p>This is implicitly set when executing the Dataflow template, by specifying the "yearMonth"
     * parameter.
     */
    void setYearMonth(ValueProvider<String> value);

    /** Returns the SafeBrowsing API key we use to evaluate subdomain health. */
    @Description("The API key we use to access the SafeBrowsing API.")
    ValueProvider<String> getSafeBrowsingApiKey();

    /**
     * Sets the SafeBrowsing API key we use.
     *
     * <p>This is implicitly set when executing the Dataflow template, by specifying the
     * "safeBrowsingApiKey" parameter.
     */
    void setSafeBrowsingApiKey(ValueProvider<String> value);
  }

  /** Deploys the spec11 pipeline as a template on GCS. */
  public void deploy() {
    // We can't store options as a member variable due to serialization concerns.
    Spec11PipelineOptions options = PipelineOptionsFactory.as(Spec11PipelineOptions.class);
    options.setProject(projectId);
    options.setRunner(DataflowRunner.class);
    // This causes p.run() to stage the pipeline as a template on GCS, as opposed to running it.
    options.setTemplateLocation(spec11TemplateUrl);
    options.setStagingLocation(beamStagingUrl);

    Pipeline p = Pipeline.create(options);
    PCollection<Subdomain> domains =
        p.apply(
            "Read active domains from BigQuery",
            BigQueryIO.read(Subdomain::parseFromRecord)
                .fromQuery(
                    // This query must be customized for your own use.
                    "SELECT * FROM YOUR_TABLE_HERE")
                .withCoder(SerializableCoder.of(Subdomain.class))
                .usingStandardSql()
                .withoutValidation()
                .withTemplateCompatibility());
    evaluateUrlHealth(domains, new EvaluateSafeBrowsingFn(options.getSafeBrowsingApiKey()));
    p.run();
  }

  /**
   * Evaluate each {@link Subdomain} URL via the SafeBrowsing API.
   *
   * <p>This is factored out to facilitate testing.
   */
  void evaluateUrlHealth(
      PCollection<Subdomain> domains, EvaluateSafeBrowsingFn evaluateSafeBrowsingFn) {
    domains
        // TODO(b/111545355): Remove this limiter once we're confident we won't go over quota.
        .apply(
            "Get just a few representative samples for now, don't want to overwhelm our quota",
            Sample.any(1000))
        .apply("Run through SafeBrowsingAPI", ParDo.of(evaluateSafeBrowsingFn))
        .apply("Convert results to string", ToString.elements())
        .apply(
            "Output to text file",
            TextIO.write()
                // TODO(b/111545355): Replace this with a templated directory based on yearMonth
                .to(spec11BucketUrl)
                .withoutSharding()
                .withHeader("HELLO WORLD"));
  }

}
