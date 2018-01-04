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
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.api.services.dataflow.Dataflow;
import com.google.api.services.dataflow.model.Job;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.common.net.MediaType;
import google.registry.config.RegistryConfig.Config;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import javax.inject.Inject;

/**
 * Uploads the results of the {@link google.registry.beam.InvoicingPipeline}.
 *
 * <p>This relies on the retry semantics in {@code queue.xml} to ensure proper upload, in spite of
 * fluctuations in generation timing.
 *
 * @see <a href=https://cloud.google.com/dataflow/docs/reference/rest/v1b3/projects.jobs#Job.JobState>
 *   Job States</a>
 */
@Action(path = PublishInvoicesAction.PATH, method = POST, auth = Auth.AUTH_INTERNAL_OR_ADMIN)
public class PublishInvoicesAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();
  private static final String JOB_DONE = "JOB_STATE_DONE";
  private static final String JOB_FAILED = "JOB_STATE_FAILED";

  private final String projectId;
  private final String jobId;
  private final BillingEmailUtils emailUtils;
  private final Dataflow dataflow;
  private final Response response;

  @Inject
  PublishInvoicesAction(
      @Config("projectId") String projectId,
      @Parameter(BillingModule.PARAM_JOB_ID) String jobId,
      BillingEmailUtils emailUtils,
      Dataflow dataflow,
      Response response) {
    this.projectId = projectId;
    this.jobId = jobId;
    this.emailUtils = emailUtils;
    this.dataflow = dataflow;
    this.response = response;
  }

  static final String PATH = "/_dr/task/publishInvoices";

  @Override
  public void run() {
    try {
      logger.info("Starting publish job.");
      Job job = dataflow.projects().jobs().get(projectId, jobId).execute();
      String state = job.getCurrentState();
      switch (state) {
        case JOB_DONE:
          logger.infofmt("Dataflow job %s finished successfully, publishing results.", jobId);
          response.setStatus(SC_OK);
          enqueueCopyDetailReportsTask();
          emailUtils.emailInvoiceLink();
          break;
        case JOB_FAILED:
          logger.severefmt("Dataflow job %s finished unsuccessfully.", jobId);
          response.setStatus(SC_NO_CONTENT);
          // TODO(larryruili): Email failure message
          break;
        default:
          logger.infofmt("Job in non-terminal state %s, retrying:", state);
          response.setStatus(SC_NOT_MODIFIED);
          break;
      }
    } catch (IOException e) {
      response.setStatus(SC_INTERNAL_SERVER_ERROR);
      response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
      response.setPayload(String.format("Template launch failed: %s", e.getMessage()));
    }
  }

  private static void enqueueCopyDetailReportsTask() {
    TaskOptions copyDetailTask =
        TaskOptions.Builder.withUrl(CopyDetailReportsAction.PATH)
            .method(TaskOptions.Method.POST)
            .param(
                BillingModule.PARAM_DIRECTORY_PREFIX, BillingModule.RESULTS_DIRECTORY_PREFIX);
    QueueFactory.getQueue(BillingModule.CRON_QUEUE).add(copyDetailTask);
  }
}
