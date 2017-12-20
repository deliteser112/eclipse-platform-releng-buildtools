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

import google.registry.beam.BillingEvent.InvoiceGroupingKey;
import google.registry.beam.BillingEvent.InvoiceGroupingKey.InvoiceGroupingKeyCoder;
import google.registry.config.RegistryConfig.Config;
import javax.inject.Inject;
import org.apache.beam.runners.dataflow.DataflowRunner;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.io.DefaultFilenamePolicy.Params;
import org.apache.beam.sdk.io.FileBasedSink;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.sdk.values.TypeDescriptors;

/**
 * Definition of a Dataflow pipeline template, which generates a given month's invoices.
 *
 * <p>To stage this template on GCS, run the {@link
 * google.registry.tools.DeployInvoicingPipelineCommand} Nomulus command.
 *
 * <p>Then, you can run the staged template via the API client library, gCloud or a raw REST call.
 * For an example using the API client library, see {@link
 * google.registry.billing.GenerateInvoicesAction}.
 *
 * @see <a href="https://cloud.google.com/dataflow/docs/templates/overview">Dataflow Templates</a>
 */
public class InvoicingPipeline {

  @Inject @Config("projectId") String projectId;
  @Inject @Config("apacheBeamBucketUrl") String beamBucket;
  @Inject InvoicingPipeline() {}

  /** Custom options for running the invoicing pipeline. */
  interface InvoicingPipelineOptions extends DataflowPipelineOptions {
    /** Returns the yearMonth we're generating invoices for, in yyyy-MM format. */
    @Description("The yearMonth we generate invoices for, in yyyy-MM format.")
    ValueProvider<String> getYearMonth();
    /** Sets the yearMonth we generate invoices for. */
    void setYearMonth(ValueProvider<String> value);
  }

  /** Deploys the invoicing pipeline as a template on GCS, for a given projectID and GCS bucket. */
  public void deploy() {
    InvoicingPipelineOptions options = PipelineOptionsFactory.as(InvoicingPipelineOptions.class);
    options.setProject(projectId);
    options.setRunner(DataflowRunner.class);
    options.setStagingLocation(beamBucket + "/staging");
    options.setTemplateLocation(beamBucket + "/templates/invoicing");

    Pipeline p = Pipeline.create(options);

    PCollection<BillingEvent> billingEvents =
        p.apply(
            "Read BillingEvents from Bigquery",
            BigQueryIO.read(BillingEvent::parseFromRecord)
                .fromQuery(InvoicingUtils.makeQueryProvider(options.getYearMonth(), projectId))
                .withCoder(SerializableCoder.of(BillingEvent.class))
                .usingStandardSql()
                .withoutValidation());

    applyTerminalTransforms(billingEvents);
    p.run();
  }

  /**
   * Applies output transforms to the {@code BillingEvent} source collection.
   *
   * <p>This is factored out purely to facilitate testing.
   */
  void applyTerminalTransforms(PCollection<BillingEvent> billingEvents) {
    billingEvents.apply(
        "Write events to separate CSVs keyed by registrarId_tld pair", writeDetailReports());

    billingEvents
        .apply("Generate overall invoice rows", new GenerateInvoiceRows())
        .apply("Write overall invoice to CSV", writeInvoice());
  }

  /** Transform that converts a {@code BillingEvent} into an invoice CSV row. */
  private static class GenerateInvoiceRows
      extends PTransform<PCollection<BillingEvent>, PCollection<String>> {
    @Override
    public PCollection<String> expand(PCollection<BillingEvent> input) {
      return input
          .apply(
              "Map to invoicing key",
              MapElements.into(TypeDescriptor.of(InvoiceGroupingKey.class))
                  .via(BillingEvent::getInvoiceGroupingKey))
          .setCoder(new InvoiceGroupingKeyCoder())
          .apply("Count occurrences", Count.perElement())
          .apply(
              "Format as CSVs",
              MapElements.into(TypeDescriptors.strings())
                  .via((KV<InvoiceGroupingKey, Long> kv) -> kv.getKey().toCsv(kv.getValue())));
    }
  }

  /** Returns an IO transform that writes the overall invoice to a single CSV file. */
  private TextIO.Write writeInvoice() {
    return TextIO.write()
        .to(beamBucket + "/results/overall_invoice")
        .withHeader(InvoiceGroupingKey.invoiceHeader())
        .withoutSharding()
        .withSuffix(".csv");
  }

  /** Returns an IO transform that writes detail reports to registrar-tld keyed CSV files. */
  private TextIO.TypedWrite<BillingEvent, Params> writeDetailReports() {
    return TextIO.<BillingEvent>writeCustomType()
        .to(
            InvoicingUtils.makeDestinationFunction(beamBucket + "/results"),
            InvoicingUtils.makeEmptyDestinationParams(beamBucket + "/results"))
        .withFormatFunction(BillingEvent::toCsv)
        .withoutSharding()
        .withTempDirectory(FileBasedSink.convertToFileResourceIfPossible(beamBucket + "/temporary"))
        .withHeader(BillingEvent.getHeader())
        .withSuffix(".csv");
  }
}
