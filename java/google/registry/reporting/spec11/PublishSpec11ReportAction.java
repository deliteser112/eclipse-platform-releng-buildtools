// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.reporting.spec11;

import static google.registry.request.Action.Method.POST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.api.services.dataflow.Dataflow;
import com.google.api.services.dataflow.model.Job;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.MediaType;
import google.registry.config.RegistryConfig.Config;
import google.registry.reporting.ReportingModule;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import java.io.IOException;
import javax.inject.Inject;
import org.joda.time.YearMonth;

/**
 * Retries until a {@code Dataflow} job with a given {@code jobId} completes, continuing the Spec11
 * pipeline accordingly.
 *
 * <p>This calls {@link Spec11EmailUtils#emailSpec11Reports()} on success or {@link
 * Spec11EmailUtils#sendFailureAlertEmail(String)} on failure.
 */
@Action(path = PublishSpec11ReportAction.PATH, method = POST, auth = Auth.AUTH_INTERNAL_OR_ADMIN)
public class PublishSpec11ReportAction implements Runnable {

  static final String PATH = "/_dr/task/publishSpec11";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String JOB_DONE = "JOB_STATE_DONE";
  private static final String JOB_FAILED = "JOB_STATE_FAILED";

  private final String projectId;
  private final String jobId;
  private final Spec11EmailUtils emailUtils;
  private final Dataflow dataflow;
  private final Response response;
  private final YearMonth yearMonth;

  @Inject
  PublishSpec11ReportAction(
      @Config("projectId") String projectId,
      @Parameter(ReportingModule.PARAM_JOB_ID) String jobId,
      Spec11EmailUtils emailUtils,
      Dataflow dataflow,
      Response response,
      YearMonth yearMonth) {
    this.projectId = projectId;
    this.jobId = jobId;
    this.emailUtils = emailUtils;
    this.dataflow = dataflow;
    this.response = response;
    this.yearMonth = yearMonth;
  }

  @Override
  public void run() {
    try {
      logger.atInfo().log("Starting publish job.");
      Job job = dataflow.projects().jobs().get(projectId, jobId).execute();
      String state = job.getCurrentState();
      switch (state) {
        case JOB_DONE:
          logger.atInfo().log("Dataflow job %s finished successfully, publishing results.", jobId);
          response.setStatus(SC_OK);
          emailUtils.emailSpec11Reports();
          break;
        case JOB_FAILED:
          logger.atSevere().log("Dataflow job %s finished unsuccessfully.", jobId);
          response.setStatus(SC_NO_CONTENT);
          emailUtils.sendFailureAlertEmail(
              String.format(
                  "Spec11 %s job %s ended in status failure.", yearMonth.toString(), jobId));
          break;
        default:
          logger.atInfo().log("Job in non-terminal state %s, retrying:", state);
          response.setStatus(SC_NOT_MODIFIED);
          break;
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Failed to publish Spec11 reports.");
      emailUtils.sendFailureAlertEmail(
          String.format(
              "Spec11 %s publish action failed due to %s", yearMonth.toString(), e.getMessage()));
      response.setStatus(SC_INTERNAL_SERVER_ERROR);
      response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
      response.setPayload(String.format("Template launch failed: %s", e.getMessage()));
    }
  }
}
