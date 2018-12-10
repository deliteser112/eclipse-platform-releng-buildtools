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
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.MediaType;
import google.registry.config.RegistryConfig.Config;
import google.registry.reporting.ReportingModule;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import org.joda.time.LocalDate;
import org.json.JSONException;

/**
 * Retries until a {@code Dataflow} job with a given {@code jobId} completes, continuing the Spec11
 * pipeline accordingly.
 *
 * <p>This calls {@link Spec11EmailUtils#emailSpec11Reports(String, String, List)} ()} on success or
 * {@link Spec11EmailUtils#sendAlertEmail(String, String)} on failure.
 */
@Action(path = PublishSpec11ReportAction.PATH, method = POST, auth = Auth.AUTH_INTERNAL_OR_ADMIN)
public class PublishSpec11ReportAction implements Runnable {

  static final String PATH = "/_dr/task/publishSpec11";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String JOB_DONE = "JOB_STATE_DONE";
  private static final String JOB_FAILED = "JOB_STATE_FAILED";

  private final String projectId;
  private final String spec11EmailBodyTemplate;
  private final String jobId;
  private final Spec11EmailUtils emailUtils;
  private final Spec11RegistrarThreatMatchesParser spec11RegistrarThreatMatchesParser;
  private final Dataflow dataflow;
  private final Response response;
  private final LocalDate date;

  @Inject
  PublishSpec11ReportAction(
      @Config("projectId") String projectId,
      @Config("spec11EmailBodyTemplate") String spec11EmailBodyTemplate,
      @Parameter(ReportingModule.PARAM_JOB_ID) String jobId,
      Spec11EmailUtils emailUtils,
      Spec11RegistrarThreatMatchesParser spec11RegistrarThreatMatchesParser,
      Dataflow dataflow,
      Response response,
      LocalDate date) {
    this.projectId = projectId;
    this.spec11EmailBodyTemplate = spec11EmailBodyTemplate;
    this.jobId = jobId;
    this.emailUtils = emailUtils;
    this.spec11RegistrarThreatMatchesParser = spec11RegistrarThreatMatchesParser;
    this.dataflow = dataflow;
    this.response = response;
    this.date = date;
  }

  @Override
  public void run() {
    try {
      logger.atInfo().log("Starting publish job.");
      Job job = dataflow.projects().jobs().get(projectId, jobId).execute();
      String state = job.getCurrentState();
      switch (state) {
        case JOB_DONE:
          logger.atInfo().log(
              "Dataflow job %s finished successfully, publishing results if appropriate.", jobId);
          response.setStatus(SC_OK);
          if (shouldSendSpec11Email()) {
            ImmutableList<RegistrarThreatMatches> matchesList =
                spec11RegistrarThreatMatchesParser.getRegistrarThreatMatches();
            String subject = String.format("Google Registry Monthly Threat Detector [%s]", date);
            emailUtils.emailSpec11Reports(spec11EmailBodyTemplate, subject, matchesList);
          }
          break;
        case JOB_FAILED:
          logger.atSevere().log("Dataflow job %s finished unsuccessfully.", jobId);
          response.setStatus(SC_NO_CONTENT);
          emailUtils.sendAlertEmail(
              String.format("Spec11 Dataflow Pipeline Failure %s", date),
              String.format("Spec11 %s job %s ended in status failure.", date, jobId));
          break;
        default:
          logger.atInfo().log("Job in non-terminal state %s, retrying:", state);
          response.setStatus(SC_NOT_MODIFIED);
          break;
      }
    } catch (IOException | JSONException e) {
      logger.atSevere().withCause(e).log("Failed to publish Spec11 reports.");
      emailUtils.sendAlertEmail(
          String.format("Spec11 Publish Failure %s", date),
          String.format("Spec11 %s publish action failed due to %s", date, e.getMessage()));
      response.setStatus(SC_INTERNAL_SERVER_ERROR);
      response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
      response.setPayload(String.format("Template launch failed: %s", e.getMessage()));
    }
  }

  private boolean shouldSendSpec11Email() {
    // TODO(b/120496893): send emails every day with the diff content
    return date.getDayOfMonth() == 2;
  }
}
