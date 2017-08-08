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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import google.registry.config.RegistryConfig.Config;
import google.registry.request.Parameter;
import google.registry.util.ResourceUtils;
import google.registry.util.SqlTemplate;
import java.io.IOException;
import java.net.URL;
import javax.inject.Inject;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Utility class that produces SQL queries used to generate activity reports from Bigquery.
 */
public final class ActivityReportingQueryBuilder {

  // Names for intermediary tables for overall activity reporting query.
  static final String ICANN_REPORTING_DATA_SET = "icann_reporting";
  static final String ACTIVITY_REPORT_AGGREGATION = "activity_report_aggregation";
  static final String MONTHLY_LOGS = "monthly_logs";
  static final String REGISTRAR_OPERATING_STATUS = "registrar_operating_status";
  static final String DNS_COUNTS = "dns_counts";
  static final String EPP_METRICS = "epp_metrics";
  static final String WHOIS_COUNTS = "whois_counts";

  @Inject @Config("projectId") String projectId;
  @Inject @Parameter(IcannReportingModule.PARAM_YEAR_MONTH) String yearMonth;
  @Inject ActivityReportingQueryBuilder() {}

  /** Returns the aggregate query which generates the activity report from the saved view. */
  String getActivityReportQuery() throws IOException {
    return String.format(
        "#standardSQL\nSELECT * FROM `%s.%s.%s`",
        projectId,
        ICANN_REPORTING_DATA_SET,
        getTableName(ACTIVITY_REPORT_AGGREGATION));
  }

  /** Returns the table name of the query, suffixed with the yearMonth in _YYYYMM format. */
  private String getTableName(String queryName) {
    return String.format("%s_%s", queryName, yearMonth.replace("-", ""));
  }

  /** Sets the month we're doing activity reporting for, and returns the view query map. */
  ImmutableMap<String, String> getViewQueryMap() throws IOException {
    LocalDate reportDate = DateTimeFormat.forPattern("yyyy-MM").parseLocalDate(yearMonth);
    // Convert reportingMonth into YYYYMM01 format for Bigquery table partition pattern-matching.
    DateTimeFormatter formatter = DateTimeFormat.forPattern("YYYYMM01");
    String startOfMonth = formatter.print(reportDate);
    String endOfMonth = formatter.print(reportDate.plusMonths(1));
    return createQueryMap(startOfMonth, endOfMonth);
  }

  /** Returns a map from view name to its associated SQL query. */
  private ImmutableMap<String, String> createQueryMap(
      String startOfMonth, String endOfMonth) throws IOException {

    ImmutableMap.Builder<String, String> queriesBuilder = ImmutableMap.builder();
    String operationalRegistrarsQuery =
        SqlTemplate.create(getQueryFromFile("registrar_operating_status.sql"))
            .put("PROJECT_ID", projectId)
            .put("REGISTRAR_DATA_SET", "registrar_data")
            .put("REGISTRAR_STATUS_TABLE", "registrar_status")
            .build();
    queriesBuilder.put(getTableName(REGISTRAR_OPERATING_STATUS), operationalRegistrarsQuery);

    // TODO(b/62626209): Make this use the CloudDNS counts instead.
    String dnsCountsQuery =
        SqlTemplate.create(getQueryFromFile("dns_counts.sql")).build();
    queriesBuilder.put(getTableName(DNS_COUNTS), dnsCountsQuery);

    // The monthly logs query is a shared dependency for epp counts and whois metrics
    String monthlyLogsQuery =
        SqlTemplate.create(getQueryFromFile("monthly_logs.sql"))
            .put("PROJECT_ID", projectId)
            .put("APPENGINE_LOGS_DATA_SET", "appengine_logs")
            .put("REQUEST_TABLE", "appengine_googleapis_com_request_log_")
            .put("START_OF_MONTH", startOfMonth)
            .put("END_OF_MONTH", endOfMonth)
            .build();
    queriesBuilder.put(getTableName(MONTHLY_LOGS), monthlyLogsQuery);

    String eppQuery =
        SqlTemplate.create(getQueryFromFile("epp_metrics.sql"))
            .put("PROJECT_ID", projectId)
            .put("ICANN_REPORTING_DATA_SET", ICANN_REPORTING_DATA_SET)
            .put("MONTHLY_LOGS_TABLE", getTableName(MONTHLY_LOGS))
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
            // TODO(larryruili): Change to "latest_datastore_export" when cl/163124895 in prod.
            .put("LATEST_DATASTORE_EXPORT", "latest_datastore_views")
            .put("REGISTRY_TABLE", "Registry")
            .build();
    queriesBuilder.put(getTableName(ACTIVITY_REPORT_AGGREGATION), aggregateQuery);

    return queriesBuilder.build();
  }

  /** Returns {@link String} for file in {@code reporting/sql/} directory. */
  private static String getQueryFromFile(String filename) throws IOException {
    return ResourceUtils.readResourceUtf8(getUrl(filename));
  }

  private static URL getUrl(String filename) {
    return Resources.getResource(ActivityReportingQueryBuilder.class, "sql/" + filename);
  }
}

