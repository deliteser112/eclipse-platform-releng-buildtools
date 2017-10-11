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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.request.Action.Method.POST;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.io.ByteStreams;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import google.registry.reporting.IcannReportingModule.ReportType;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.RequestParameters;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import google.registry.util.Retrier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Action that uploads the monthly transaction and activity reports from Cloud Storage to ICANN via
 * an HTTP PUT.
 *
 */
@Action(
  path = IcannReportingUploadAction.PATH,
  method = POST,
  auth = Auth.AUTH_INTERNAL_OR_ADMIN
)
public final class IcannReportingUploadAction implements Runnable {

  static final String PATH = "/_dr/task/icannReportingUpload";
  static final String DEFAULT_SUBDIR = "icann/monthly";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @Config("icannReportingBucket") String icannReportingBucket;
  @Inject @Parameter(RequestParameters.PARAM_TLD) String tld;
  @Inject @Parameter(IcannReportingModule.PARAM_YEAR_MONTH) String yearMonth;
  @Inject @Parameter(IcannReportingModule.PARAM_REPORT_TYPE) ReportType reportType;
  @Inject @Parameter(IcannReportingModule.PARAM_SUBDIR) Optional<String> subdir;
  @Inject GcsUtils gcsUtils;
  @Inject IcannHttpReporter icannReporter;
  @Inject Response response;
  @Inject Retrier retrier;

  @Inject
  IcannReportingUploadAction() {}

  @Override
  public void run() {
    validateParams();
    String reportFilename = createFilename(tld, yearMonth, reportType);
    String reportBucketname = createReportingBucketName(icannReportingBucket, subdir, yearMonth);
    logger.infofmt("Reading ICANN report %s from bucket %s", reportFilename, reportBucketname);
    final GcsFilename gcsFilename = new GcsFilename(reportBucketname, reportFilename);
    checkState(
        gcsUtils.existsAndNotEmpty(gcsFilename),
        "ICANN report object %s in bucket %s not found",
        gcsFilename.getObjectName(),
        gcsFilename.getBucketName());

    retrier.callWithRetry(
        () -> {
          final byte[] payload = readReportFromGcs(gcsFilename);
          icannReporter.send(payload, tld, yearMonth, reportType);
          response.setContentType(PLAIN_TEXT_UTF_8);
          response.setPayload(
              String.format("OK, sending: %s", new String(payload, StandardCharsets.UTF_8)));
          return null;
        },
        IOException.class);
  }

  private byte[] readReportFromGcs(GcsFilename reportFilename) throws IOException {
    try (InputStream gcsInput = gcsUtils.openInputStream(reportFilename)) {
      return ByteStreams.toByteArray(gcsInput);
    }
  }

  static String createFilename(String tld, String yearMonth, ReportType reportType) {
    // Report files use YYYYMM naming instead of standard YYYY-MM, per ICANN requirements.
    String fileYearMonth = yearMonth.substring(0, 4) + yearMonth.substring(5, 7);
    return String.format("%s-%s-%s.csv", tld, reportType.toString().toLowerCase(), fileYearMonth);
  }

  static String createReportingBucketName(
      String reportingBucket, Optional<String> subdir, String yearMonth) {
    return subdir.isPresent()
        ? String.format("%s/%s", reportingBucket, subdir.get())
        : String.format("%s/%s/%s", reportingBucket, DEFAULT_SUBDIR, yearMonth);

  }

  private void validateParams() {
    assertTldExists(tld);
    checkState(
        yearMonth.matches("[0-9]{4}-[0-9]{2}"),
        "yearMonth must be in YYYY-MM format, got %s instead.",
        yearMonth);
    if (subdir.isPresent()) {
      checkState(
          !subdir.get().startsWith("/") && !subdir.get().endsWith("/"),
          "subdir must not start or end with a \"/\", got %s instead.",
          subdir.get());
    }
  }
}
