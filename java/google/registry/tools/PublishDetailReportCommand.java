// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkNotNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableMap;
import google.registry.export.PublishDetailReportAction;
import google.registry.model.registrar.Registrar;
import java.util.Map;

/**
 * Command to publish a given billing detail report to a registrar's Drive folder.
 */
@Parameters(separators = " =", commandDescription = "Publish detail report for a registrar")
public class PublishDetailReportCommand extends ConfirmingCommand
    implements ServerSideCommand {

  @Parameter(
      names = "--registrar_id",
      description = "Client identifier of the registrar to publish the report for",
      required = true)
  private String registrarId;

  @Parameter(
      names = "--report_name",
      description = "Name of the detail report (without directory prefixes)",
      required = true)
  private String reportName;

  @Parameter(
      names = "--gcs_bucket",
      description = "Name of the GCS bucket that holds billing output files.")
  private String gcsBucket = "domain-registry-billing";

  @Parameter(
      names = "--gcs_folder",
      description = "GCS folder under which report was exported.",
      required = true)
  private String gcsFolder;

  private static final String DRIVE_FOLDER_URL_TEMPLATE =
      "https://drive.google.com/corp/drive/#folders/%s";

  private String gcsFolderPrefix;
  private String driveFolderUrl;

  private Connection connection;

  @Override
  public void setConnection(Connection connection) {
    this.connection = connection;
  }

  @Override
  protected void init() throws Exception {
    // Append a trailing slash to the GCS folder (if there is one, and if it doesn't already end
    // in a slash) to get the "folder prefix" version.
    // TODO(b/18611424): Fix PublishDetailReportAction to take fewer error-prone parameters.
    gcsFolderPrefix =
        (gcsFolder.isEmpty() || gcsFolder.endsWith("/")) ? gcsFolder : gcsFolder + "/";
    Registrar registrar = checkNotNull(
        Registrar.loadByClientId(registrarId), "Registrar with ID %s not found", registrarId);
    driveFolderUrl = String.format(DRIVE_FOLDER_URL_TEMPLATE, registrar.getDriveFolderId());
  }

  @Override
  protected String prompt() {
    String gcsFile = String.format("gs://%s/%s%s", gcsBucket, gcsFolderPrefix, reportName);
    return "Publish detail report:\n"
        + " - Registrar: " + registrarId + "\n"
        + " - Drive folder: " + driveFolderUrl + "\n"
        + " - GCS file: " + gcsFile;
  }

  @Override
  protected String execute() throws Exception {
    final ImmutableMap<String, String> params = ImmutableMap.of(
        PublishDetailReportAction.REGISTRAR_ID_PARAM, registrarId,
        PublishDetailReportAction.DETAIL_REPORT_NAME_PARAM, reportName,
        PublishDetailReportAction.GCS_FOLDER_PREFIX_PARAM, gcsFolderPrefix,
        PublishDetailReportAction.GCS_BUCKET_PARAM, gcsBucket);
    Map<String, Object> response =
        connection.sendJson(PublishDetailReportAction.PATH, params);
    return "Success! Published report with drive file ID: " + response.get("driveId");
  }
}
