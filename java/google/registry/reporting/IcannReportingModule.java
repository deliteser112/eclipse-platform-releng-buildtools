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

import static google.registry.request.RequestParameters.extractEnumParameter;
import static google.registry.request.RequestParameters.extractOptionalParameter;
import static google.registry.request.RequestParameters.extractRequiredParameter;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import google.registry.bigquery.BigqueryConnection;
import google.registry.request.Parameter;
import java.util.Optional;
import java.util.concurrent.Executors;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.Duration;

/** Module for dependencies required by ICANN monthly transactions/activity reporting. */
@Module
public final class IcannReportingModule {

  /** Enum determining the type of report to generate or upload. */
  public enum ReportType {
    TRANSACTIONS,
    ACTIVITY
  }

  static final String PARAM_YEAR_MONTH = "yearMonth";
  static final String PARAM_REPORT_TYPE = "reportType";
  static final String PARAM_SUBDIR = "subdir";
  static final String ICANN_REPORTING_DATA_SET = "icann_reporting";
  static final String DATASTORE_EXPORT_DATA_SET = "latest_datastore_export";
  private static final String BIGQUERY_SCOPE =  "https://www.googleapis.com/auth/bigquery";

  @Provides
  @Parameter(PARAM_YEAR_MONTH)
  static String provideYearMonth(HttpServletRequest req) {
    return extractRequiredParameter(req, PARAM_YEAR_MONTH);
  }

  @Provides
  @Parameter(PARAM_REPORT_TYPE)
  static ReportType provideReportType(HttpServletRequest req) {
    return extractEnumParameter(req, ReportType.class, PARAM_REPORT_TYPE);
  }

  @Provides
  @Parameter(PARAM_SUBDIR)
  static Optional<String> provideSubdir(HttpServletRequest req) {
    return extractOptionalParameter(req, PARAM_SUBDIR);
  }

  @Provides
  static QueryBuilder provideQueryBuilder(
      @Parameter(PARAM_REPORT_TYPE) ReportType reportType,
      ActivityReportingQueryBuilder activityBuilder,
      TransactionsReportingQueryBuilder transactionsBuilder) {
    return reportType == ReportType.ACTIVITY ? activityBuilder : transactionsBuilder;
  }

  /**
   * Constructs a BigqueryConnection with default settings.
   *
   * <p> We use Bigquery to generate activity reports via large aggregate SQL queries.
   *
   * @see ActivityReportingQueryBuilder
   * @see google.registry.tools.BigqueryParameters for justifications of defaults.
   */
  @Provides
  static BigqueryConnection provideBigqueryConnection(HttpTransport transport) {
    try {
      GoogleCredential credential = GoogleCredential
          .getApplicationDefault(transport, new JacksonFactory());
      BigqueryConnection connection = new BigqueryConnection.Builder()
          .setExecutorService(Executors.newFixedThreadPool(20))
          .setCredential(credential.createScoped(ImmutableList.of(BIGQUERY_SCOPE)))
          .setDatasetId(ICANN_REPORTING_DATA_SET)
          .setOverwrite(true)
          .setPollInterval(Duration.standardSeconds(1))
          .build();
      connection.initialize();
      return connection;
    } catch (Throwable e) {
      throw new RuntimeException("Could not initialize BigqueryConnection!", e);
    }
  }
}
