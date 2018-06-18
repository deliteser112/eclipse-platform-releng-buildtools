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
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Utility class that produces SQL queries used to generate activity reports from Bigquery.
 */
public final class TransactionsReportingQueryBuilder implements QueryBuilder {

  @Inject @Config("projectId") String projectId;

  @Inject YearMonth yearMonth;

  @Inject TransactionsReportingQueryBuilder() {}

  static final String TRANSACTIONS_REPORT_AGGREGATION = "transactions_report_aggregation";
  static final String REGISTRAR_IANA_ID = "registrar_iana_id";
  static final String TOTAL_DOMAINS = "total_domains";
  static final String TOTAL_NAMESERVERS = "total_nameservers";
  static final String TRANSACTION_COUNTS = "transaction_counts";
  static final String TRANSACTION_TRANSFER_LOSING = "transaction_transfer_losing";
  static final String ATTEMPTED_ADDS = "attempted_adds";

  /** Returns the aggregate query which generates the transactions report from the saved view. */
  @Override
  public String getReportQuery() {
    return String.format(
        "#standardSQL\nSELECT * FROM `%s.%s.%s`",
        projectId,
        ICANN_REPORTING_DATA_SET,
        getTableName(TRANSACTIONS_REPORT_AGGREGATION));
  }

  /** Sets the month we're doing transactions reporting for, and returns the view query map. */
  @Override
  public ImmutableMap<String, String> getViewQueryMap() {
    // Set the earliest date to to yearMonth on day 1 at 00:00:00
    DateTime earliestReportTime = yearMonth.toLocalDate(1).toDateTime(new LocalTime(0, 0, 0));
    // Set the latest date to yearMonth on the last day at 23:59:59.999
    DateTime latestReportTime = earliestReportTime.plusMonths(1).minusMillis(1);
    return createQueryMap(earliestReportTime, latestReportTime);
  }

  /** Returns a map from view name to its associated SQL query. */
  private ImmutableMap<String, String> createQueryMap(
      DateTime earliestReportTime, DateTime latestReportTime) {

    ImmutableMap.Builder<String, String> queriesBuilder = ImmutableMap.builder();
    String registrarIanaIdQuery =
        SqlTemplate.create(getQueryFromFile("registrar_iana_id.sql"))
            .put("PROJECT_ID", projectId)
            .put("DATASTORE_EXPORT_DATA_SET", DATASTORE_EXPORT_DATA_SET)
            .put("REGISTRAR_TABLE", "Registrar")
            .build();
    queriesBuilder.put(getTableName(REGISTRAR_IANA_ID), registrarIanaIdQuery);

    String totalDomainsQuery =
        SqlTemplate.create(getQueryFromFile("total_domains.sql"))
            .put("PROJECT_ID", projectId)
            .put("DATASTORE_EXPORT_DATA_SET", DATASTORE_EXPORT_DATA_SET)
            .put("DOMAINBASE_TABLE", "DomainBase")
            .put("REGISTRAR_TABLE", "Registrar")
            .build();
    queriesBuilder.put(getTableName(TOTAL_DOMAINS), totalDomainsQuery);

    DateTimeFormatter timestampFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String totalNameserversQuery =
        SqlTemplate.create(getQueryFromFile("total_nameservers.sql"))
            .put("PROJECT_ID", projectId)
            .put("DATASTORE_EXPORT_DATA_SET", DATASTORE_EXPORT_DATA_SET)
            .put("HOSTRESOURCE_TABLE", "HostResource")
            .put("DOMAINBASE_TABLE", "DomainBase")
            .put("REGISTRAR_TABLE", "Registrar")
            .put("LATEST_REPORT_TIME", timestampFormatter.print(latestReportTime))
            .build();
    queriesBuilder.put(getTableName(TOTAL_NAMESERVERS), totalNameserversQuery);

    String transactionCountsQuery =
        SqlTemplate.create(getQueryFromFile("transaction_counts.sql"))
            .put("PROJECT_ID", projectId)
            .put("DATASTORE_EXPORT_DATA_SET", DATASTORE_EXPORT_DATA_SET)
            .put("REGISTRAR_TABLE", "Registrar")
            .put("HISTORYENTRY_TABLE", "HistoryEntry")
            .put("EARLIEST_REPORT_TIME", timestampFormatter.print(earliestReportTime))
            .put("LATEST_REPORT_TIME", timestampFormatter.print(latestReportTime))
            .put("CLIENT_ID", "clientId")
            .put("OTHER_CLIENT_ID", "otherClientId")
            .put("TRANSFER_SUCCESS_FIELD", "TRANSFER_GAINING_SUCCESSFUL")
            .put("TRANSFER_NACKED_FIELD", "TRANSFER_GAINING_NACKED")
            .put("DEFAULT_FIELD", "field")
            .build();
    queriesBuilder.put(getTableName(TRANSACTION_COUNTS), transactionCountsQuery);

    String transactionTransferLosingQuery =
        SqlTemplate.create(getQueryFromFile("transaction_counts.sql"))
            .put("PROJECT_ID", projectId)
            .put("DATASTORE_EXPORT_DATA_SET", DATASTORE_EXPORT_DATA_SET)
            .put("REGISTRAR_TABLE", "Registrar")
            .put("HISTORYENTRY_TABLE", "HistoryEntry")
            .put("EARLIEST_REPORT_TIME", timestampFormatter.print(earliestReportTime))
            .put("LATEST_REPORT_TIME", timestampFormatter.print(latestReportTime))
            // Roles are reversed for losing queries
            .put("CLIENT_ID", "otherClientId")
            .put("OTHER_CLIENT_ID", "clientId")
            .put("TRANSFER_SUCCESS_FIELD", "TRANSFER_LOSING_SUCCESSFUL")
            .put("TRANSFER_NACKED_FIELD", "TRANSFER_LOSING_NACKED")
            .put("DEFAULT_FIELD", "NULL")
            .build();
    queriesBuilder.put(getTableName(TRANSACTION_TRANSFER_LOSING), transactionTransferLosingQuery);

    // App Engine log table suffixes use YYYYMMDD format
    DateTimeFormatter logTableFormatter = DateTimeFormat.forPattern("yyyyMMdd");
    String attemptedAddsQuery =
        SqlTemplate.create(getQueryFromFile("attempted_adds.sql"))
            .put("PROJECT_ID", projectId)
            .put("DATASTORE_EXPORT_DATA_SET", DATASTORE_EXPORT_DATA_SET)
            .put("REGISTRAR_TABLE", "Registrar")
            .put("APPENGINE_LOGS_DATA_SET", "appengine_logs")
            .put("REQUEST_TABLE", "appengine_googleapis_com_request_log_")
            .put("FIRST_DAY_OF_MONTH", logTableFormatter.print(earliestReportTime))
            .put("LAST_DAY_OF_MONTH", logTableFormatter.print(latestReportTime))
            // All metadata logs for reporting come from google.registry.flows.FlowReporter.
            .put(
                "METADATA_LOG_PREFIX",
                "google.registry.flows.FlowReporter recordToLogs: FLOW-LOG-SIGNATURE-METADATA")
            .build();
    queriesBuilder.put(getTableName(ATTEMPTED_ADDS), attemptedAddsQuery);

    String aggregateQuery =
        SqlTemplate.create(getQueryFromFile("transactions_report_aggregation.sql"))
            .put("PROJECT_ID", projectId)
            .put("DATASTORE_EXPORT_DATA_SET", DATASTORE_EXPORT_DATA_SET)
            .put("REGISTRY_TABLE", "Registry")
            .put("ICANN_REPORTING_DATA_SET", ICANN_REPORTING_DATA_SET)
            .put("REGISTRAR_IANA_ID_TABLE", getTableName(REGISTRAR_IANA_ID))
            .put("TOTAL_DOMAINS_TABLE", getTableName(TOTAL_DOMAINS))
            .put("TOTAL_NAMESERVERS_TABLE", getTableName(TOTAL_NAMESERVERS))
            .put("TRANSACTION_COUNTS_TABLE", getTableName(TRANSACTION_COUNTS))
            .put("TRANSACTION_TRANSFER_LOSING_TABLE", getTableName(TRANSACTION_TRANSFER_LOSING))
            .put("ATTEMPTED_ADDS_TABLE", getTableName(ATTEMPTED_ADDS))
            .build();
    queriesBuilder.put(getTableName(TRANSACTIONS_REPORT_AGGREGATION), aggregateQuery);

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
    return Resources.getResource(
        ActivityReportingQueryBuilder.class, "sql/" + filename);
  }
}

