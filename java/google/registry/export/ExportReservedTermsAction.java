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

package google.registry.export;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static google.registry.export.ExportUtils.exportReservedTerms;
import static google.registry.request.Action.Method.POST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.net.MediaType;
import google.registry.model.registry.Registry;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.RequestParameters;
import google.registry.request.Response;
import google.registry.storage.drive.DriveConnection;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;

/** Action that exports the publicly viewable reserved terms list for a TLD to Google Drive. */
@Action(path = "/_dr/task/exportReservedTerms", method = POST)
public class ExportReservedTermsAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();
  static final MediaType EXPORT_MIME_TYPE = MediaType.PLAIN_TEXT_UTF_8;
  static final String RESERVED_TERMS_FILENAME = "reserved_terms.txt";

  @Inject DriveConnection driveConnection;
  @Inject @Parameter(RequestParameters.PARAM_TLD) String tld;
  @Inject Response response;
  @Inject ExportReservedTermsAction() {}

  /**
   * Exports the reserved terms for the TLD specified via the "tld" param to a newline-delimited
   * UTF-8-formatted CSV file (with one column) named "reserved_terms.txt" in the Google Drive
   * folder with the id specified for that TLD.
   *
   * <p>This servlet prints the ID of the file in GoogleDrive that was created/updated.
   */
  @Override
  public void run() {
    response.setContentType(PLAIN_TEXT_UTF_8);
    try {
      Registry registry = Registry.get(tld);
      String resultMsg;
      if (registry.getReservedLists().isEmpty() && isNullOrEmpty(registry.getDriveFolderId())) {
        resultMsg = "No reserved lists configured";
        logger.infofmt("No reserved terms to export for TLD %s", tld);
      } else {
        checkNotNull(registry.getDriveFolderId(), "No drive folder associated with this TLD");
        resultMsg = driveConnection.createOrUpdateFile(
            RESERVED_TERMS_FILENAME,
            EXPORT_MIME_TYPE,
            registry.getDriveFolderId(),
            exportReservedTerms(registry).getBytes(UTF_8));
        logger.infofmt("Exporting reserved terms succeeded for TLD %s, response was: %s",
            tld, resultMsg);
      }
      response.setStatus(SC_OK);
      response.setPayload(resultMsg);
    } catch (Throwable e) {
      response.setStatus(SC_INTERNAL_SERVER_ERROR);
      response.setPayload(e.getMessage());
      throw new RuntimeException(
          String.format("Exception occurred while exporting reserved terms for TLD %s.", tld), e);
    }
  }
}
