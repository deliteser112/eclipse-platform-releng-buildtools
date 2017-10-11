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

package google.registry.reporting;

import static com.google.common.base.Strings.isNullOrEmpty;
import static google.registry.request.Action.Method.POST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.net.MediaType;
import google.registry.bigquery.BigqueryConnection;
import google.registry.bigquery.BigqueryUtils.TableType;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import google.registry.reporting.IcannReportingModule.ReportType;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;

/**
 * Action that generates monthly ICANN activity and transactions reports.
 *
 * <p> The reports are then uploaded to GCS under
 * gs://domain-registry-reporting/icann/monthly/YYYY-MM
 */
@Action(
    path = IcannReportingStagingAction.PATH,
    method = POST,
    auth = Auth.AUTH_INTERNAL_ONLY
)
public final class IcannReportingStagingAction implements Runnable {

  static final String PATH = "/_dr/task/icannReportingStaging";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @Config("icannReportingBucket") String reportingBucket;
  @Inject @Parameter(IcannReportingModule.PARAM_YEAR_MONTH) String yearMonth;
  @Inject @Parameter(IcannReportingModule.PARAM_SUBDIR) Optional<String> subdir;
  @Inject @Parameter(IcannReportingModule.PARAM_REPORT_TYPE) ReportType reportType;
  @Inject QueryBuilder queryBuilder;
  @Inject BigqueryConnection bigquery;
  @Inject GcsUtils gcsUtils;
  @Inject Response response;
  @Inject IcannReportingStagingAction() {}

  @Override
  public void run() {
    try {
      ImmutableMap<String, String> viewQueryMap = queryBuilder.getViewQueryMap();
      // Generate intermediary views
      for (Entry<String, String> entry : viewQueryMap.entrySet()) {
        createIntermediaryTableView(entry.getKey(), entry.getValue());
      }

      // Get an in-memory table of the aggregate query's result
      ImmutableTable<Integer, TableFieldSchema, Object> reportTable =
          bigquery.queryToLocalTableSync(queryBuilder.getReportQuery());

      // Get report headers from the table schema and convert into CSV format
      String headerRow = constructRow(getHeaders(reportTable.columnKeySet()));
      logger.infofmt("Headers: %s", headerRow);

      if (reportType == ReportType.ACTIVITY) {
        stageActivityReports(headerRow, reportTable.rowMap().values());
      } else {
        stageTransactionsReports(headerRow, reportTable.rowMap().values());
      }
      response.setStatus(SC_OK);
      response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
      response.setPayload("Completed staging action.");
    } catch (Exception e) {
      logger.warning(Throwables.getStackTraceAsString(e));
      response.setStatus(SC_INTERNAL_SERVER_ERROR);
      response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
      response.setPayload(
          String.format("Caught exception:\n%s\n%s", e.getMessage(),
              Arrays.toString(e.getStackTrace())));
    }
  }

  private void createIntermediaryTableView(String queryName, String query)
      throws ExecutionException, InterruptedException {
    // Later views depend on the results of earlier ones, so query everything synchronously
    bigquery.query(
        query,
        bigquery.buildDestinationTable(queryName)
            .description(String.format(
                "An intermediary view to generate %s reports for this month.", reportType))
            .type(TableType.VIEW)
            .build()
    ).get();
  }

  private Iterable<String> getHeaders(ImmutableSet<TableFieldSchema> fields) {
    return Iterables.transform(fields, schema -> schema.getName().replace('_', '-'));
  }

  private void stageActivityReports (
      String headerRow, ImmutableCollection<Map<TableFieldSchema, Object>> rows)
      throws IOException {
    // Create a report csv for each tld from query table, and upload to GCS
    for (Map<TableFieldSchema, Object> row : rows) {
      // Get the tld (first cell in each row)
      String tld = row.values().iterator().next().toString();
      if (isNullOrEmpty(tld)) {
        throw new RuntimeException("Found an empty row in the activity report table!");
      }
      ImmutableList<String> rowStrings = ImmutableList.of(constructRow(row.values()));
      // Create and upload the activity report with a single row
      uploadReport(tld, createReport(headerRow, rowStrings));
    }
  }

  private void stageTransactionsReports(
      String headerRow, ImmutableCollection<Map<TableFieldSchema, Object>> rows)
      throws IOException {
    // Map from tld to rows
    ListMultimap<String, String> tldToRows = ArrayListMultimap.create();
    for (Map<TableFieldSchema, Object> row : rows) {
      // Get the tld (first cell in each row)
      String tld = row.values().iterator().next().toString();
      if (isNullOrEmpty(tld)) {
        throw new RuntimeException("Found an empty row in the activity report table!");
      }
      tldToRows.put(tld, constructRow(row.values()));
    }
    // Create and upload a transactions report for each tld via its rows
    for (String tld : tldToRows.keySet()) {
      uploadReport(tld, createReport(headerRow, tldToRows.get(tld)));
    }
  }

  /**
   * Makes a row of the report by appending the string representation of all objects in an iterable
   * with commas separating individual fields.
   *
   * <p>This discards the first object, which is assumed to be the TLD field.
   * */
  private String constructRow(Iterable<? extends Object> iterable) {
    Iterator<? extends Object> rowIter = iterable.iterator();
    StringBuilder rowString = new StringBuilder();
    // Skip the TLD column
    rowIter.next();
    while (rowIter.hasNext()) {
      rowString.append(String.format("%s,", rowIter.next().toString()));
    }
    // Remove trailing comma
    rowString.deleteCharAt(rowString.length() - 1);
    return rowString.toString();
  }

  /**
   * Constructs a report given its headers and rows as a string.
   *
   * <p>Note that activity reports will only have one row, while transactions reports may have
   * multiple rows.
   */
  private String createReport(String headers, List<String> rows) {
    StringBuilder reportCsv = new StringBuilder(headers);
    for (String row : rows) {
      // Add CRLF between rows per ICANN specification
      reportCsv.append("\r\n");
      reportCsv.append(row);
    }
    logger.infofmt("Created %s report:\n%s", reportType, reportCsv.toString());
    return reportCsv.toString();
  }

  private void uploadReport(String tld, String reportCsv) throws IOException {
    // Upload resulting CSV file to GCS
    byte[] reportBytes = reportCsv.getBytes(UTF_8);
    String reportFilename =
        IcannReportingUploadAction.createFilename(tld, yearMonth, reportType);
    String reportBucketname =
        IcannReportingUploadAction.createReportingBucketName(reportingBucket, subdir, yearMonth);
    final GcsFilename gcsFilename = new GcsFilename(reportBucketname, reportFilename);
    try (OutputStream gcsOutput = gcsUtils.openOutputStream(gcsFilename)) {
      gcsOutput.write(reportBytes);
    }
    logger.infofmt(
        "Wrote %d bytes to file location %s",
        reportBytes.length,
        gcsFilename.toString());
  }
}
