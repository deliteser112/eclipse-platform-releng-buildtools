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
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.config.RegistryConfig.Config;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import javax.inject.Inject;
import org.joda.time.Duration;
import org.joda.time.YearMonth;

/**
 * Invokes the {@code InvoicingPipeline} beam template via the REST api, and enqueues the {@link
 * PublishInvoicesAction} to publish the subsequent output.
 *
 * <p>This action runs the {@link google.registry.beam.InvoicingPipeline} beam template, staged at
 * gs://<projectId>-beam/templates/invoicing. The pipeline then generates invoices for the month and
 * stores them on GCS.
 */
@Action(path = GenerateInvoicesAction.PATH, method = POST, auth = Auth.AUTH_INTERNAL_ONLY)
public class GenerateInvoicesAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  static final String PATH = "/_dr/task/generateInvoices";

  private final String projectId;
  private final String beamBucketUrl;
  private final YearMonth yearMonth;
  private final Dataflow dataflow;
  private final Response response;

  @Inject
  GenerateInvoicesAction(
      @Config("projectId") String projectId,
      @Config("apacheBeamBucketUrl") String beamBucketUrl,
      YearMonth yearMonth,
      Dataflow dataflow,
      Response response) {
    this.projectId = projectId;
    this.beamBucketUrl = beamBucketUrl;
    this.yearMonth = yearMonth;
    this.dataflow = dataflow;
    this.response = response;
  }

  @Override
  public void run() {
    logger.infofmt("Launching invoicing pipeline for %s", yearMonth);
    try {
      LaunchTemplateParameters params =
          new LaunchTemplateParameters()
              .setJobName(String.format("invoicing-%s", yearMonth))
              .setEnvironment(
                  new RuntimeEnvironment()
                      .setZone("us-east1-c")
                      .setTempLocation(beamBucketUrl + "/temporary"))
              .setParameters(ImmutableMap.of("yearMonth", yearMonth.toString("yyyy-MM")));
      LaunchTemplateResponse launchResponse =
          dataflow
              .projects()
              .templates()
              .launch(projectId, params)
              .setGcsPath(beamBucketUrl + "/templates/invoicing")
              .execute();
      logger.infofmt("Got response: %s", launchResponse.getJob().toPrettyString());
      String jobId = launchResponse.getJob().getId();
      enqueuePublishTask(jobId);
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

  private void enqueuePublishTask(String jobId) {
    TaskOptions publishTask =
        TaskOptions.Builder.withUrl(PublishInvoicesAction.PATH)
            .method(TaskOptions.Method.POST)
            // Dataflow jobs tend to take about 10 minutes to complete.
            .countdownMillis(Duration.standardMinutes(10).getMillis())
            .param(BillingModule.PARAM_JOB_ID, jobId);
    QueueFactory.getQueue(BillingModule.BILLING_QUEUE).add(publishTask);
  }
}
