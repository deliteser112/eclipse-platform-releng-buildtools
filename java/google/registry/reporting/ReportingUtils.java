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

import com.google.common.base.Ascii;
import google.registry.reporting.IcannReportingModule.ReportType;

/** Static utils for reporting. */
public final class ReportingUtils {

  /** Generates a report filename in accord with ICANN's specifications. */
  static String createFilename(String tld, String yearMonth, ReportType reportType) {
    // Report files use YYYYMM naming instead of standard YYYY-MM, per ICANN requirements.
    return String.format(
        "%s-%s-%s.csv", tld, Ascii.toLowerCase(reportType.toString()), yearMonth.replace("-", ""));
  }

  /** Constructs the bucket name to store/upload reports to. */
  static String createReportingBucketName(String reportingBucket, String subdir) {
    return String.format("%s/%s", reportingBucket, subdir);
  }
}
