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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static google.registry.reporting.IcannReportingModule.MANIFEST_FILE_NAME;
import static google.registry.request.Action.Method.POST;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import google.registry.util.Retrier;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Inject;

/**
 * Action that uploads the monthly activity/transactions reports from GCS to ICANN via an HTTP PUT.
 *
 * <p>This should be run after {@link IcannReportingStagingAction}, which writes out the month's
 * reports and a MANIFEST.txt file. This action reads the filenames from the MANIFEST.txt, and
 * attempts to upload every file in the manifest to ICANN's endpoint.
 *
 * <p>Parameters:
 *
 * <p>subdir: the subdirectory of gs://[project-id]-reporting/ to retrieve reports from. For
 * example: "manual/dir" means reports will be stored under gs://[project-id]-reporting/manual/dir.
 * Defaults to "icann/monthly/[last month in yyyy-MM format]".
 */
@Action(path = IcannReportingUploadAction.PATH, method = POST, auth = Auth.AUTH_INTERNAL_OR_ADMIN)
public final class IcannReportingUploadAction implements Runnable {

  static final String PATH = "/_dr/task/icannReportingUpload";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject
  @Config("icannReportingBucket")
  String reportingBucket;

  @Inject
  @Parameter(IcannReportingModule.PARAM_SUBDIR)
  String subdir;

  @Inject GcsUtils gcsUtils;
  @Inject IcannHttpReporter icannReporter;
  @Inject Retrier retrier;
  @Inject Response response;

  @Inject
  IcannReportingUploadAction() {}

  @Override
  public void run() {
    String reportBucketname = ReportingUtils.createReportingBucketName(reportingBucket, subdir);
    ImmutableList<String> manifestedFiles = getManifestedFiles(reportBucketname);
    // Report on all manifested files
    for (String reportFilename : manifestedFiles) {
      logger.infofmt("Reading ICANN report %s from bucket %s", reportFilename, reportBucketname);
      final GcsFilename gcsFilename = new GcsFilename(reportBucketname, reportFilename);
      verifyFileExists(gcsFilename);
      retrier.callWithRetry(
          () -> {
            final byte[] payload = readBytesFromGcs(gcsFilename);
            icannReporter.send(payload, reportFilename);
            response.setContentType(PLAIN_TEXT_UTF_8);
            response.setPayload(String.format("OK, sending: %s", new String(payload, UTF_8)));
            return null;
          },
          IOException.class);
    }
  }

  private ImmutableList<String> getManifestedFiles(String reportBucketname) {
    GcsFilename manifestFilename = new GcsFilename(reportBucketname, MANIFEST_FILE_NAME);
    verifyFileExists(manifestFilename);
    return retrier.callWithRetry(
        () ->
            ImmutableList.copyOf(
                Splitter.on('\n')
                    .omitEmptyStrings()
                    .split(new String(readBytesFromGcs(manifestFilename), UTF_8))),
        IOException.class);
  }

  private byte[] readBytesFromGcs(GcsFilename reportFilename) throws IOException {
    try (InputStream gcsInput = gcsUtils.openInputStream(reportFilename)) {
      return ByteStreams.toByteArray(gcsInput);
    }
  }

  private void verifyFileExists(GcsFilename gcsFilename) {
    checkArgument(
        gcsUtils.existsAndNotEmpty(gcsFilename),
        "Object %s in bucket %s not found",
        gcsFilename.getObjectName(),
        gcsFilename.getBucketName());
  }
}
