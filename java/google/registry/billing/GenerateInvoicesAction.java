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

package google.registry.billing;

import static google.registry.request.Action.Method.POST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.api.services.dataflow.Dataflow;
import com.google.api.services.dataflow.model.LaunchTemplateParameters;
import com.google.api.services.dataflow.model.LaunchTemplateResponse;
import com.google.api.services.dataflow.model.RuntimeEnvironment;
import com.google.common.net.MediaType;
import google.registry.config.RegistryConfig.Config;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import javax.inject.Inject;


/**
 * Generates invoices for the month and stores them on GCS.
 *
 * <p>Currently this is just a simplified runner that verifies we can deploy dataflow jobs from App
 * Engine.
 */
@Action(
    path = GenerateInvoicesAction.PATH,
    method = POST,
    auth = Auth.AUTH_INTERNAL_ONLY
)
public class GenerateInvoicesAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @Config("projectId") String projectId;
  @Inject @Config("apacheBeamBucketUrl") String beamBucketUrl;
  @Inject Dataflow dataflow;
  @Inject Response response;
  @Inject GenerateInvoicesAction() {}

  static final String PATH = "/_dr/task/generateInvoices";

  @Override
  public void run() {
    logger.info("Launching dataflow job");
    try {
      LaunchTemplateParameters params =
          new LaunchTemplateParameters()
              .setJobName("test-bigquerytemplate1")
              .setEnvironment(
                  new RuntimeEnvironment()
                      .setZone("us-east1-c")
                      .setTempLocation(beamBucketUrl + "/temp"));
      LaunchTemplateResponse launchResponse =
          dataflow
              .projects()
              .templates()
              .launch(projectId, params)
              .setGcsPath(beamBucketUrl + "/templates/bigquery1")
              .execute();
      logger.infofmt("Got response: %s", launchResponse.getJob().toPrettyString());
    } catch (IOException e) {
      logger.warningfmt("Template Launch failed due to: %s", e.getMessage());
      response.setStatus(SC_INTERNAL_SERVER_ERROR);
      response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
      response.setPayload(String.format("Template launch failed: %s", e.getMessage()));
      return;
    }
    response.setStatus(SC_OK);
    response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
    response.setPayload("Launched dataflow template.");
  }
}
