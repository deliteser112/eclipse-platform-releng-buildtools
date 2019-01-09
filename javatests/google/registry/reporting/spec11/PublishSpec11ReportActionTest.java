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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.reporting.spec11.Spec11RegistrarThreatMatchesParserTest.sampleThreatMatches;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.api.services.dataflow.Dataflow;
import com.google.api.services.dataflow.Dataflow.Projects;
import com.google.api.services.dataflow.Dataflow.Projects.Jobs;
import com.google.api.services.dataflow.Dataflow.Projects.Jobs.Get;
import com.google.api.services.dataflow.model.Job;
import com.google.common.net.MediaType;
import google.registry.testing.FakeResponse;
import java.io.IOException;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PublishSpec11ReportAction}. */
@RunWith(JUnit4.class)
public class PublishSpec11ReportActionTest {

  private final String spec11BodyTemplate = "{LIST_OF_THREATS}\n{REPLY_TO_EMAIL}";
  private final LocalDate date = new LocalDate(2018, 6, 5);

  private Dataflow dataflow;
  private Projects projects;
  private Jobs jobs;
  private Get get;
  private Spec11EmailUtils emailUtils;
  private Spec11RegistrarThreatMatchesParser parser;

  private Job expectedJob;
  private FakeResponse response;
  private PublishSpec11ReportAction publishAction;

  @Before
  public void setUp() throws Exception {
    dataflow = mock(Dataflow.class);
    projects = mock(Projects.class);
    jobs = mock(Jobs.class);
    get = mock(Get.class);
    when(dataflow.projects()).thenReturn(projects);
    when(projects.jobs()).thenReturn(jobs);
    when(jobs.get("test-project", "12345")).thenReturn(get);
    expectedJob = new Job();
    when(get.execute()).thenReturn(expectedJob);
    emailUtils = mock(Spec11EmailUtils.class);
    response = new FakeResponse();
    parser = mock(Spec11RegistrarThreatMatchesParser.class);
    when(parser.getRegistrarThreatMatches()).thenReturn(sampleThreatMatches());
    publishAction =
        new PublishSpec11ReportAction(
            "test-project",
            spec11BodyTemplate,
            "12345",
            emailUtils,
            mock(Spec11RegistrarThreatMatchesParser.class),
            dataflow,
            response,
            date);
  }

  @Test
  public void testJobDone_emailsResultsOnSecondOfMonth() throws Exception {
    expectedJob.setCurrentState("JOB_STATE_DONE");
    publishAction =
        new PublishSpec11ReportAction(
            "test-project",
            spec11BodyTemplate,
            "12345",
            emailUtils,
            parser,
            dataflow,
            response,
            date.withDayOfMonth(2));
    publishAction.run();
    assertThat(response.getStatus()).isEqualTo(SC_OK);
    verify(emailUtils)
        .emailSpec11Reports(
            spec11BodyTemplate,
            "Google Registry Monthly Threat Detector [2018-06-02]",
            sampleThreatMatches());
  }

  @Test
  public void testJobFailed_returnsNonRetriableResponse() {
    expectedJob.setCurrentState("JOB_STATE_FAILED");
    publishAction.run();
    assertThat(response.getStatus()).isEqualTo(SC_NO_CONTENT);
    verify(emailUtils)
        .sendAlertEmail(
            "Spec11 Dataflow Pipeline Failure 2018-06-05",
            "Spec11 2018-06-05 job 12345 ended in status failure.");
  }

  @Test
  public void testJobIndeterminate_returnsRetriableResponse() {
    expectedJob.setCurrentState("JOB_STATE_RUNNING");
    publishAction.run();
    assertThat(response.getStatus()).isEqualTo(SC_NOT_MODIFIED);
    verifyNoMoreInteractions(emailUtils);
  }

  @Test
  public void testIOException_returnsFailureMessage() throws IOException {
    when(get.execute()).thenThrow(new IOException("expected"));
    publishAction.run();
    assertThat(response.getStatus()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.getContentType()).isEqualTo(MediaType.PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("Template launch failed: expected");
    verify(emailUtils)
        .sendAlertEmail(
            "Spec11 Publish Failure 2018-06-05",
            "Spec11 2018-06-05 publish action failed due to expected");
  }

  @Test
  public void testJobDone_doesNotEmailResults() {
    expectedJob.setCurrentState("JOB_STATE_DONE");
    publishAction.run();
    assertThat(response.getStatus()).isEqualTo(SC_OK);
    verifyNoMoreInteractions(emailUtils);
  }
}
