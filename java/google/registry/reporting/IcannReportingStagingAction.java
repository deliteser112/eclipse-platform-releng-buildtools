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

import static google.registry.request.Action.Method.POST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.net.MediaType;
import google.registry.bigquery.BigqueryJobFailureException;
import google.registry.reporting.IcannReportingModule.ReportType;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import google.registry.util.Retrier;
import javax.inject.Inject;

/**
 * Action that generates monthly ICANN activity and transactions reports.
 *
 * <p>The reports are stored in GCS under gs://[project-id]-reporting/[subdir]. We also store a
 * MANIFEST.txt file that contains a list of filenames generated, to facilitate subsequent uploads.
 *
 * <p>Parameters:
 *
 * <p>yearMonth: the reporting month in yyyy-MM format. Defaults to the previous month at runtime
 * (i.e. a run on 2017-09-01 defaults to 2017-08's reports).
 *
 * <p>subdir: the subdirectory of gs://[project-id]-reporting/ to upload to. For example:
 * "manual/dir" means reports will be stored under gs://[project-id]-reporting/manual/dir. Defaults
 * to "icann/monthly/[yearMonth]".
 *
 * <p>reportTypes: the type of reports to generate. You can specify either 'activity' or
 * 'transactions'. Defaults to generating both.
 */
@Action(path = IcannReportingStagingAction.PATH, method = POST, auth = Auth.AUTH_INTERNAL_ONLY)
public final class IcannReportingStagingAction implements Runnable {

  static final String PATH = "/_dr/task/icannReportingStaging";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject ImmutableList<ReportType> reportTypes;
  @Inject IcannReportingStager stager;
  @Inject Retrier retrier;
  @Inject Response response;
  @Inject ReportingEmailUtils emailUtils;
  @Inject IcannReportingStagingAction() {}

  @Override
  public void run() {
    retrier.callWithRetry(
        () -> {
          ImmutableList.Builder<String> manifestedFilesBuilder = new ImmutableList.Builder<>();
          for (ReportType reportType : reportTypes) {
            manifestedFilesBuilder.addAll(stager.stageReports(reportType));
          }
          ImmutableList<String> manifestedFiles = manifestedFilesBuilder.build();
          stager.createAndUploadManifest(manifestedFiles);

          logger.infofmt("Completed staging %d report files.", manifestedFiles.size());
          emailUtils.emailResults(
              "ICANN Monthly report staging summary [SUCCESS]",
              String.format(
                  "Completed staging the following %d ICANN reports:\n%s",
                  manifestedFiles.size(), Joiner.on('\n').join(manifestedFiles)));

          response.setStatus(SC_OK);
          response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
          response.setPayload("Completed staging action.");
          return null;
        },
        new Retrier.FailureReporter() {
          @Override
          public void beforeRetry(Throwable thrown, int failures, int maxAttempts) {}

          @Override
          public void afterFinalFailure(Throwable thrown, int failures) {
            emailUtils.emailResults(
                "ICANN Monthly report staging summary [FAILURE]",
                String.format(
                    "Staging failed due to %s, check logs for more details.", thrown.toString()));
            logger.severefmt("Staging action failed due to %s", thrown.toString());
            response.setStatus(SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.PLAIN_TEXT_UTF_8);
            response.setPayload(String.format("Staging failed due to %s", thrown.toString()));
          }
        },
        BigqueryJobFailureException.class);
  }
}
