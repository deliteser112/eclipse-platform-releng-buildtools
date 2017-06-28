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

import com.google.common.base.Optional;
import dagger.Module;
import dagger.Provides;
import google.registry.request.Parameter;
import javax.servlet.http.HttpServletRequest;

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
}
