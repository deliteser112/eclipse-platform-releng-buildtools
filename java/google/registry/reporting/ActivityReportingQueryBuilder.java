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
import google.registry.util.ResourceUtils;
import google.registry.util.SqlTemplate;
import java.io.IOException;
import java.net.URL;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Utility class that produces SQL queries used to generate activity reports from Bigquery.
 */
public final class ActivityReportingQueryBuilder {

  // Names for intermediary tables for overall activity reporting query.
  static final String ACTIVITY_REPORTING = "activity_reporting";
  static final String MONTHLY_LOGS = "monthly_logs";
  static final String REGISTRAR_OPERATING_STATUS = "registrar_operating_status";
  static final String DNS_COUNTS = "dns_counts";
  static final String EPP_METRICS = "epp_metrics";
  static final String WHOIS_COUNTS = "whois_counts";

  /** Sets the month we're doing activity reporting for, and initializes the query map. */
  static ImmutableMap<String, String> getQueryMap(DateTime reportingMonth) throws IOException {
    // Convert the DateTime reportingMonth into YYYY-MM-01 format for start and end of month
    DateTimeFormatter formatter = DateTimeFormat.forPattern("YYYY-MM-01");
    String startOfMonth = formatter.print(reportingMonth);
    String endOfMonth = formatter.print(reportingMonth.plusMonths(1));
    return createQueryMap(startOfMonth, endOfMonth);
  }

  private static ImmutableMap<String, String> createQueryMap(
      String startOfMonth, String endOfMonth) throws IOException {

    ImmutableMap.Builder<String, String> queriesBuilder = ImmutableMap.builder();
    String operationalRegistrarsQuery =
        SqlTemplate.create(getQueryFromFile("registrar_operating_status.sql"))
            .put("REGISTRAR_DATA_SET", "registrar_data")
            .put("REGISTRAR_STATUS_TABLE", "registrar_status")
            .build();
    queriesBuilder.put(REGISTRAR_OPERATING_STATUS, operationalRegistrarsQuery);

    // TODO(b/62626209): Make this use the CloudDNS counts instead.
    String dnsCountsQuery =
        SqlTemplate.create(getQueryFromFile("dns_counts.sql")).build();
    queriesBuilder.put(DNS_COUNTS, dnsCountsQuery);

    // The monthly logs query is a shared dependency for epp counts and whois metrics
    String monthlyLogsQuery =
        SqlTemplate.create(getQueryFromFile("monthly_logs.sql"))
            .put("START_OF_MONTH", startOfMonth).put("END_OF_MONTH", endOfMonth)
            .put("APPENGINE_LOGS_DATA_SET", "appengine_logs")
            .put("REQUEST_TABLE", "appengine_googleapis_com_request_log_")
            .build();
    queriesBuilder.put("monthly_logs", monthlyLogsQuery);

    String eppQuery =
        SqlTemplate.create(getQueryFromFile("epp_metrics.sql"))
            .put("MONTHLY_LOGS_DATA_SET", MONTHLY_LOGS)
            .put("MONTHLY_LOGS_TABLE", MONTHLY_LOGS + "_table")
            .build();
    queriesBuilder.put(EPP_METRICS, eppQuery);

    String whoisQuery =
        SqlTemplate.create(getQueryFromFile("whois_counts.sql"))
            .put("MONTHLY_LOGS_DATA_SET", MONTHLY_LOGS)
            .put("MONTHLY_LOGS_TABLE", MONTHLY_LOGS + "_table")
            .build();
    queriesBuilder.put(WHOIS_COUNTS, whoisQuery);

    String activityQuery =
        SqlTemplate.create(getQueryFromFile("activity_report_aggregation.sql"))
            .put("ACTIVITY_REPORTING_DATA_SET", ACTIVITY_REPORTING)
            .put("REGISTRAR_OPERATING_STATUS_TABLE", REGISTRAR_OPERATING_STATUS)
            .put("DNS_COUNTS_TABLE", DNS_COUNTS)
            .put("EPP_METRICS_TABLE", EPP_METRICS)
            .put("WHOIS_COUNTS_TABLE", WHOIS_COUNTS)
            .put("LATEST_SNAPSHOT_DATA_SET", "latest_snapshot")
            .put("REGISTRY_TABLE", "Registry")
            .build();
    queriesBuilder.put("activity_report_aggregation", activityQuery);

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
