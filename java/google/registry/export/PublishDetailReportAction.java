// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.export;

import static com.google.common.base.MoreObjects.firstNonNull;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import google.registry.gcs.GcsUtils;
import google.registry.model.registrar.Registrar;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.request.JsonActionRunner;
import google.registry.request.JsonActionRunner.JsonAction;
import google.registry.storage.drive.DriveConnection;
import google.registry.util.FormattingLogger;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import javax.inject.Inject;

/** Publish a single registrar detail report from GCS to Drive. */
@Action(
    path = PublishDetailReportAction.PATH,
    method = Action.Method.POST,
    xsrfProtection = true,
    xsrfScope = "admin")
public final class PublishDetailReportAction implements Runnable, JsonAction {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** MIME type to use for deposited report files in Drive. */
  private static final MediaType REPORT_MIME_TYPE = MediaType.CSV_UTF_8;

  /** Endpoint to which JSON should be sent for this servlet.  See {@code web.xml}. */
  public static final String PATH = "/_dr/publishDetailReport";

  /** Name of parameter indicating the registrar for which this report will be published. */
  public static final String REGISTRAR_ID_PARAM = "registrar";

  /** Name of parameter providing a name for the report file placed in Drive (the base name). */
  public static final String DETAIL_REPORT_NAME_PARAM = "report";

  /**
   * Name of parameter giving the prefix of the GCS object name to use as the report contents.
   * Concatenating this value with the value of the "report" parameter gives the full object name.
   */
  public static final String GCS_FOLDER_PREFIX_PARAM = "gcsFolder";

  /** Name of parameter giving the GCS bucket name for the file to use as the report contents. */
  public static final String GCS_BUCKET_PARAM = "bucket";

  @Inject DriveConnection driveConnection;
  @Inject GcsUtils gcsUtils;
  @Inject JsonActionRunner runner;
  @Inject PublishDetailReportAction() {}

  @Override
  public void run() {
    runner.run(this);
  }

  /** Copy a detail report from Cloud Storage to Drive. */
  @Override
  public Map<String, Object> handleJsonRequest(Map<String, ?> json) {
    try {
      logger.infofmt("Publishing detail report for parameters: %s", json);
      String registrarId = getParam(json, REGISTRAR_ID_PARAM);
      Registrar registrar = checkArgumentNotNull(Registrar.loadByClientId(registrarId),
          "Registrar %s not found", registrarId);
      String driveFolderId = checkArgumentNotNull(registrar.getDriveFolderId(),
          "No drive folder associated with registrar " + registrarId);
      String gcsBucketName = getParam(json, GCS_BUCKET_PARAM);
      String gcsObjectName =
          getParam(json, GCS_FOLDER_PREFIX_PARAM) + getParam(json, DETAIL_REPORT_NAME_PARAM);
      try (InputStream input =
              gcsUtils.openInputStream(new GcsFilename(gcsBucketName, gcsObjectName))) {
        String driveId =
            driveConnection.createFile(
                getParam(json, DETAIL_REPORT_NAME_PARAM),
                REPORT_MIME_TYPE,
                driveFolderId,
                ByteStreams.toByteArray(input));
        logger.infofmt("Published detail report for %s to folder %s using GCS file gs://%s/%s.",
            registrarId,
            driveFolderId,
            gcsBucketName,
            gcsObjectName);
        return ImmutableMap.<String, Object>of("driveId", driveId);
      } catch (FileNotFoundException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    } catch (Throwable e) {
      logger.severe(e, e.toString());
      String message = firstNonNull(e.getMessage(), e.toString());
      throw e instanceof IllegalArgumentException
          ? new BadRequestException(message) : new InternalServerErrorException(message);
    }
  }

  private String getParam(Map<String, ?> json, String paramName) {
    return (String) checkArgumentNotNull(
        json.get(paramName),
        "Missing required parameter: %s", paramName);
  }
}
