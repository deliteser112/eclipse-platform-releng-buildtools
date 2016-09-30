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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobConfiguration;
import com.google.api.services.bigquery.model.JobConfigurationExtract;
import com.google.api.services.bigquery.model.JobConfigurationQuery;
import com.google.common.collect.ImmutableList;
import google.registry.bigquery.BigqueryConnection;
import google.registry.util.SqlTemplate;
import java.nio.file.Path;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/** Command for creating a registrar activity report and saving it to cloud storage. */
@Parameters(separators = " =", commandDescription = "Generates a registrar activity report.")
final class RegistrarActivityReportCommand implements Command {

  @ParametersDelegate
  private final BigqueryParameters bigqueryParameters = new BigqueryParameters();

  @Parameter(
      names = {"-t", "--tld"},
      description = "Name of TLD (in ASCII) on which to run report.",
      required = true)
  private String tld;

  @Parameter(
      names = {"-r", "--registrar"},
      description = "Registrar clientId for which we're running this report.",
      required = true)
  private String registrarClientId;

  @Parameter(
      names = {"-s", "--start"},
      description = "Start time for report.",
      required = true)
  private DateTime start;

  @Parameter(
      names = {"-e", "--end"},
      description = "End time for report.",
      required = true)
  private DateTime end;

  @Parameter(
      names = {"-o", "--output"},
      description = "GCS output filename, e.g. gs://bucket/blah.csv",
      required = true)
  private Path output;

  @Override
  public void run() throws Exception {
    checkArgument(output.toUri().getScheme().equals("gs"),
        "Not a valid cloud storage URI: %s", output);
    checkArgument(end.isAfter(start), "End time must be after start time");
    try (BigqueryConnection bq = bigqueryParameters.newConnection()) {
      bq.ensureTable(bq.getTable("ReportingIdentifiers"),
          getSql("ReportingIdentifiers.sql")
              .build());
      bq.ensureTable(bq.getTable("ReportingHistory"),
          getSql("ReportingHistory.sql")
              .build());
      Job job = bq.runJob(new Job()
          .setConfiguration(new JobConfiguration()
              .setQuery(new JobConfigurationQuery()
                  .setDefaultDataset(bq.getDataset())
                  .setQuery(getSql("registrar_activity_report.sql")
                      .put("STARTTIME", BIGQUERY_TIMESTAMP_FORMATTER.print(start))
                      .put("ENDTIME", BIGQUERY_TIMESTAMP_FORMATTER.print(end))
                      .put("REGISTRAR", registrarClientId)
                      .put("TLD", tld)
                      .build()))));
      bq.runJob(new Job()
          .setConfiguration(new JobConfiguration()
              .setExtract(new JobConfigurationExtract()
                  .setPrintHeader(true)
                  .setDestinationFormat("CSV")
                  .setDestinationUris(ImmutableList.of(output.toUri().toString()))
                  .setSourceTable(job.getConfiguration().getQuery().getDestinationTable()))));
    }
  }

  private static SqlTemplate getSql(String filename) {
    return SqlTemplate.create(
        readResourceUtf8(RegistrarActivityReportCommand.class, "sql/" + filename));
  }

  /** Bigquery timestamps silently fail if you put the {@code T} in there. */
  private static final DateTimeFormatter BIGQUERY_TIMESTAMP_FORMATTER =
      DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss").withZoneUTC();
}
