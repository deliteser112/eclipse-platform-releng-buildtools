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
import org.apache.beam.sdk.transforms.Count;
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
     * <p>This is implicitly set when executing the Dataflow template, by specifying the 'yearMonth
     * parameter.
     */
    void setYearMonth(ValueProvider<String> value);
  }

  /** Deploys the spec11 pipeline as a template on GCS, for a given projectID and GCS bucket. */
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
    countDomainsAndOutputResults(domains);
    p.run();
  }

  /** Globally count the number of elements and output the results to GCS. */
  void countDomainsAndOutputResults(PCollection<Subdomain> domains) {
    // TODO(b/111545355): Actually process each domain with the SafeBrowsing API
    domains
        .apply("Count number of subdomains", Count.globally())
        .apply("Convert global count to string", ToString.elements())
        .apply(
            "Output to text file",
            TextIO.write()
                // TODO(b/111545355): Replace this with a templated directory based on yearMonth
                .to(spec11BucketUrl)
                .withoutSharding()
                .withHeader("HELLO WORLD"));
  }
}
