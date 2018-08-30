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

import static google.registry.request.RequestParameters.extractOptionalParameter;
import static google.registry.request.RequestParameters.extractRequiredParameter;

import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.dataflow.Dataflow;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.Parameter;
import google.registry.util.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.YearMonth;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/** Dagger module for injecting common settings for all reporting tasks. */
@Module
public class ReportingModule {

  private static final String CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";

  public static final String BEAM_QUEUE = "beam-reporting";
  /**
   * The request parameter name used by reporting actions that takes a year/month parameter, which
   * defaults to the last month.
   */
  public static final String PARAM_YEAR_MONTH = "yearMonth";
  /** The request parameter specifying the jobId for a running Dataflow pipeline. */
  public static final String PARAM_JOB_ID = "jobId";

  /** Provides the Cloud Dataflow jobId for a pipeline. */
  @Provides
  @Parameter(PARAM_JOB_ID)
  static String provideJobId(HttpServletRequest req) {
    return extractRequiredParameter(req, PARAM_JOB_ID);
  }

  /** Extracts an optional YearMonth in yyyy-MM format from the request. */
  @Provides
  @Parameter(PARAM_YEAR_MONTH)
  static Optional<YearMonth> provideYearMonthOptional(HttpServletRequest req) {
    DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM");
    Optional<String> optionalYearMonthStr = extractOptionalParameter(req, PARAM_YEAR_MONTH);
    try {
      return optionalYearMonthStr.map(s -> YearMonth.parse(s, formatter));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
          String.format(
              "yearMonth must be in yyyy-MM format, got %s instead",
              optionalYearMonthStr.orElse("UNSPECIFIED YEARMONTH")));
    }
  }

  /**
   * Provides the yearMonth in yyyy-MM format, if not specified in the request, defaults to one
   * month prior to run time.
   */
  @Provides
  static YearMonth provideYearMonth(
      @Parameter(PARAM_YEAR_MONTH) Optional<YearMonth> yearMonthOptional, Clock clock) {
    return yearMonthOptional.orElseGet(() -> new YearMonth(clock.nowUtc().minusMonths(1)));
  }

  /** Constructs a {@link Dataflow} API client with default settings. */
  @Provides
  static Dataflow provideDataflow(
      @Config("projectId") String projectId,
      HttpTransport transport,
      JsonFactory jsonFactory,
      Function<Set<String>, AppIdentityCredential> appIdentityCredentialFunc) {

    return new Dataflow.Builder(
        transport,
        jsonFactory,
        appIdentityCredentialFunc.apply(ImmutableSet.of(CLOUD_PLATFORM_SCOPE)))
        .setApplicationName(String.format("%s billing", projectId))
        .build();
  }

}
