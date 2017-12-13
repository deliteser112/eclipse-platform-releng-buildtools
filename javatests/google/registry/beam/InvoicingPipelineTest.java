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

import com.google.common.collect.ImmutableList;
import google.registry.beam.InvoicingPipeline.GenerateInvoiceRows;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.apache.beam.runners.direct.DirectRunner;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link InvoicingPipeline}. */
@RunWith(JUnit4.class)
public class InvoicingPipelineTest {

  private static PipelineOptions pipelineOptions;

  @BeforeClass
  public static void initializePipelineOptions() {
    pipelineOptions = PipelineOptionsFactory.create();
    pipelineOptions.setRunner(DirectRunner.class);
  }

  @Rule public final transient TestPipeline p = TestPipeline.fromOptions(pipelineOptions);

  @Test
  public void testGenerateInvoiceRowsFn() throws Exception {
    ImmutableList<BillingEvent> inputRows =
        ImmutableList.of(
            BillingEvent.create(
                1,
                ZonedDateTime.of(2017, 10, 4, 0, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2017, 10, 4, 0, 0, 0, 0, ZoneId.of("UTC")),
                "theRegistrar",
                "234",
                "test",
                "RENEW",
                "mydomain.test",
                "REPO-ID",
                3,
                "USD",
                20.5,
                ""),
            BillingEvent.create(
                1,
                ZonedDateTime.of(2017, 10, 4, 0, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2017, 10, 4, 0, 0, 0, 0, ZoneId.of("UTC")),
                "theRegistrar",
                "234",
                "test",
                "RENEW",
                "mydomain2.test",
                "REPO-ID",
                3,
                "USD",
                20.5,
                ""),
            BillingEvent.create(
                1,
                ZonedDateTime.of(2017, 10, 2, 0, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2017, 9, 29, 0, 0, 0, 0, ZoneId.of("UTC")),
                "theRegistrar",
                "234",
                "hello",
                "CREATE",
                "mydomain3.hello",
                "REPO-ID",
                5,
                "JPY",
                70.75,
                ""),
            BillingEvent.create(
                1,
                ZonedDateTime.of(2017, 10, 4, 0, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2017, 10, 4, 0, 0, 0, 0, ZoneId.of("UTC")),
                "googledomains",
                "456",
                "test",
                "RENEW",
                "mydomain4.test",
                "REPO-ID",
                1,
                "USD",
                20.5,
                ""));

    PCollection<BillingEvent> input = p.apply(Create.of(inputRows));
    PCollection<String> output = input.apply(new GenerateInvoiceRows());

    ImmutableList<String> outputStrings = ImmutableList.of(
        "2017-10-01,2020-09-30,234,41.00,USD,10125,1,PURCHASE,theRegistrar - test,2,"
            + "RENEW | TLD: test | TERM: 3-year,20.50,USD,",
        "2017-10-01,2022-09-30,234,70.75,JPY,10125,1,PURCHASE,theRegistrar - hello,1,"
            + "CREATE | TLD: hello | TERM: 5-year,70.75,JPY,",
        "2017-10-01,2018-09-30,456,20.50,USD,10125,1,PURCHASE,googledomains - test,1,"
            + "RENEW | TLD: test | TERM: 1-year,20.50,USD,");
    PAssert.that(output).containsInAnyOrder(outputStrings);
    p.run();
  }
}
