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

import static google.registry.request.RequestParameters.extractOptionalEnumParameter;
import static google.registry.request.RequestParameters.extractOptionalParameter;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.Module;
import dagger.Provides;
import google.registry.bigquery.BigqueryConnection;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.Parameter;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.Optional;
import javax.inject.Qualifier;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.Duration;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;

/** Module for dependencies required by ICANN monthly transactions/activity reporting. */
@Module
public final class IcannReportingModule {

  /** Enum determining the type of report to generate or upload. */
  public enum ReportType {
    TRANSACTIONS,
    ACTIVITY
  }

  static final String PARAM_SUBDIR = "subdir";
  static final String PARAM_REPORT_TYPE = "reportType";
  static final String ICANN_REPORTING_DATA_SET = "icann_reporting";
  static final String DATASTORE_EXPORT_DATA_SET = "latest_datastore_export";
  static final String MANIFEST_FILE_NAME = "MANIFEST.txt";
  private static final String DEFAULT_SUBDIR = "icann/monthly";

  /** Provides an optional subdirectory to store/upload reports to, extracted from the request. */
  @Provides
  @Parameter(PARAM_SUBDIR)
  static Optional<String> provideSubdirOptional(HttpServletRequest req) {
    return extractOptionalParameter(req, PARAM_SUBDIR);
  }

  /** Provides the subdirectory to store/upload reports to, defaults to icann/monthly/yearMonth. */
  @Provides
  @ReportingSubdir
  static String provideSubdir(
      @Parameter(PARAM_SUBDIR) Optional<String> subdirOptional, YearMonth yearMonth) {
    String subdir =
        subdirOptional.orElse(
            String.format(
                "%s/%s", DEFAULT_SUBDIR, DateTimeFormat.forPattern("yyyy-MM").print(yearMonth)));
    if (subdir.startsWith("/") || subdir.endsWith("/")) {
      throw new BadRequestException(
          String.format("subdir must not start or end with a \"/\", got %s instead.", subdir));
    }
    return subdir;
  }

  /** Provides an optional reportType to store/upload reports to, extracted from the request. */
  @Provides
  @Parameter(PARAM_REPORT_TYPE)
  static Optional<ReportType> provideReportTypeOptional(HttpServletRequest req) {
    return extractOptionalEnumParameter(req, ReportType.class, PARAM_REPORT_TYPE);
  }

  /** Provides a list of reportTypes specified. If absent, we default to both report types. */
  @Provides
  static ImmutableList<ReportType> provideReportTypes(
      @Parameter(PARAM_REPORT_TYPE) Optional<ReportType> reportTypeOptional) {
    return reportTypeOptional.map(ImmutableList::of)
        .orElseGet(() -> ImmutableList.of(ReportType.ACTIVITY, ReportType.TRANSACTIONS));
  }

  /**
   * Constructs a BigqueryConnection with default settings.
   *
   * <p>We use Bigquery to generate ICANN monthly reports via large aggregate SQL queries.
   *
   * @see ActivityReportingQueryBuilder
   * @see google.registry.tools.BigqueryParameters for justifications of defaults.
   */
  @Provides
  static BigqueryConnection provideBigqueryConnection(
      BigqueryConnection.Builder bigQueryConnectionBuilder) {
    try {
      BigqueryConnection connection =
          bigQueryConnectionBuilder
              .setExecutorService(MoreExecutors.newDirectExecutorService())
              .setDatasetId(ICANN_REPORTING_DATA_SET)
              .setOverwrite(true)
              .setPollInterval(Duration.standardSeconds(1))
              .build();
      return connection;
    } catch (Throwable e) {
      throw new RuntimeException("Could not initialize BigqueryConnection!", e);
    }
  }

  /** Dagger qualifier for the subdirectory we stage to/upload from. */
  @Qualifier
  @Documented
  @Retention(RUNTIME)
  public @interface ReportingSubdir {}
}

