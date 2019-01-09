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

package google.registry.reporting.icann;

import static google.registry.reporting.icann.IcannReportingModule.DATASTORE_EXPORT_DATA_SET;
import static google.registry.reporting.icann.IcannReportingModule.ICANN_REPORTING_DATA_SET;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import google.registry.config.RegistryConfig.Config;
import google.registry.util.ResourceUtils;
import google.registry.util.SqlTemplate;
import java.net.URL;
import javax.inject.Inject;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/** Utility class that produces SQL queries used to generate activity reports from Bigquery. */
public final class ActivityReportingQueryBuilder implements QueryBuilder {

  // Names for intermediary tables for overall activity reporting query.
  static final String REGISTRAR_OPERATING_STATUS = "registrar_operating_status";
  static final String DNS_COUNTS = "dns_counts";
  static final String MONTHLY_LOGS = "monthly_logs";
  static final String EPP_METRICS = "epp_metrics";
  static final String WHOIS_COUNTS = "whois_counts";
  static final String ACTIVITY_REPORT_AGGREGATION = "activity_report_aggregation";

  @Inject
  @Config("projectId")
  String projectId;

  @Inject YearMonth yearMonth;

  @Inject DnsCountQueryCoordinator dnsCountQueryCoordinator;

  @Inject
  ActivityReportingQueryBuilder() {}

  /** Returns the aggregate query which generates the activity report from the saved view. */
  @Override
  public String getReportQuery() {
    return String.format(
        "#standardSQL\nSELECT * FROM `%s.%s.%s`",
        projectId, ICANN_REPORTING_DATA_SET, getTableName(ACTIVITY_REPORT_AGGREGATION));
  }

  /** Sets the month we're doing activity reporting for, and returns the view query map. */
  @Override
  public ImmutableMap<String, String> getViewQueryMap() {
    LocalDate firstDayOfMonth = yearMonth.toLocalDate(1);
    // The pattern-matching is inclusive, so we subtract 1 day to only report that month's data.
    LocalDate lastDayOfMonth = yearMonth.toLocalDate(1).plusMonths(1).minusDays(1);
    return createQueryMap(firstDayOfMonth, lastDayOfMonth);
  }

  public void prepareForQuery() throws Exception {
    dnsCountQueryCoordinator.prepareForQuery();
  }

  /** Returns a map from view name to its associated SQL query. */
  private ImmutableMap<String, String> createQueryMap(
      LocalDate firstDayOfMonth, LocalDate lastDayOfMonth) {

    ImmutableMap.Builder<String, String> queriesBuilder = ImmutableMap.builder();
    String operationalRegistrarsQuery =
        SqlTemplate.create(getQueryFromFile("registrar_operating_status.sql"))
            .put("PROJECT_ID", projectId)
            .put("DATASTORE_EXPORT_DATA_SET", DATASTORE_EXPORT_DATA_SET)
            .put("REGISTRAR_TABLE", "Registrar")
            .build();
    queriesBuilder.put(getTableName(REGISTRAR_OPERATING_STATUS), operationalRegistrarsQuery);

    String dnsCountsQuery = dnsCountQueryCoordinator.createQuery();
    queriesBuilder.put(getTableName(DNS_COUNTS), dnsCountsQuery);

    // Convert reportingMonth into YYYYMMDD format for Bigquery table partition pattern-matching.
    DateTimeFormatter logTableFormatter = DateTimeFormat.forPattern("yyyyMMdd");
    // The monthly logs are a shared dependency for epp counts and whois metrics
    String monthlyLogsQuery =
        SqlTemplate.create(getQueryFromFile("monthly_logs.sql"))
            .put("PROJECT_ID", projectId)
            .put("APPENGINE_LOGS_DATA_SET", "appengine_logs")
            .put("REQUEST_TABLE", "appengine_googleapis_com_request_log_")
            .put("FIRST_DAY_OF_MONTH", logTableFormatter.print(firstDayOfMonth))
            .put("LAST_DAY_OF_MONTH", logTableFormatter.print(lastDayOfMonth))
            .build();
    queriesBuilder.put(getTableName(MONTHLY_LOGS), monthlyLogsQuery);

    String eppQuery =
        SqlTemplate.create(getQueryFromFile("epp_metrics.sql"))
            .put("PROJECT_ID", projectId)
            .put("ICANN_REPORTING_DATA_SET", ICANN_REPORTING_DATA_SET)
            .put("MONTHLY_LOGS_TABLE", getTableName(MONTHLY_LOGS))
            // All metadata logs for reporting come from google.registry.flows.FlowReporter.
            .put(
                "METADATA_LOG_PREFIX",
                "google.registry.flows.FlowReporter recordToLogs: FLOW-LOG-SIGNATURE-METADATA")
            .build();
    queriesBuilder.put(getTableName(EPP_METRICS), eppQuery);

    String whoisQuery =
        SqlTemplate.create(getQueryFromFile("whois_counts.sql"))
            .put("PROJECT_ID", projectId)
            .put("ICANN_REPORTING_DATA_SET", ICANN_REPORTING_DATA_SET)
            .put("MONTHLY_LOGS_TABLE", getTableName(MONTHLY_LOGS))
            .build();
    queriesBuilder.put(getTableName(WHOIS_COUNTS), whoisQuery);

    String aggregateQuery =
        SqlTemplate.create(getQueryFromFile("activity_report_aggregation.sql"))
            .put("PROJECT_ID", projectId)
            .put("ICANN_REPORTING_DATA_SET", ICANN_REPORTING_DATA_SET)
            .put("REGISTRAR_OPERATING_STATUS_TABLE", getTableName(REGISTRAR_OPERATING_STATUS))
            .put("DNS_COUNTS_TABLE", getTableName(DNS_COUNTS))
            .put("EPP_METRICS_TABLE", getTableName(EPP_METRICS))
            .put("WHOIS_COUNTS_TABLE", getTableName(WHOIS_COUNTS))
            .put("DATASTORE_EXPORT_DATA_SET", DATASTORE_EXPORT_DATA_SET)
            .put("REGISTRY_TABLE", "Registry")
            .build();
    queriesBuilder.put(getTableName(ACTIVITY_REPORT_AGGREGATION), aggregateQuery);

    return queriesBuilder.build();
  }

  /** Returns the table name of the query, suffixed with the yearMonth in _yyyyMM format. */
  private String getTableName(String queryName) {
    return String.format("%s_%s", queryName, DateTimeFormat.forPattern("yyyyMM").print(yearMonth));
  }

  /** Returns {@link String} for file in {@code reporting/sql/} directory. */
  private static String getQueryFromFile(String filename) {
    return ResourceUtils.readResourceUtf8(getUrl(filename));
  }

  private static URL getUrl(String filename) {
    return Resources.getResource(ActivityReportingQueryBuilder.class, "sql/" + filename);
  }
}
