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

package google.registry.reporting.icann;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import google.registry.bigquery.BigqueryJobFailureException;
import google.registry.reporting.icann.IcannReportingModule.ReportType;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.FakeSleeper;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.util.Retrier;
import org.joda.time.YearMonth;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link google.registry.reporting.icann.IcannReportingStagingAction}. */
@RunWith(JUnit4.class)
public class IcannReportingStagingActionTest {

  FakeResponse response = new FakeResponse();
  IcannReportingStager stager = mock(IcannReportingStager.class);
  ReportingEmailUtils emailUtils = mock(ReportingEmailUtils.class);

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withLocalModules()
      .withTaskQueue()
      .build();

  @Before
  public void setUp() throws Exception {
    when(stager.stageReports(ReportType.ACTIVITY)).thenReturn(ImmutableList.of("a", "b"));
    when(stager.stageReports(ReportType.TRANSACTIONS)).thenReturn(ImmutableList.of("c", "d"));
  }

  private static void assertUploadTaskEnqueued(String subDir) throws Exception {
    TaskMatcher matcher =
        new TaskMatcher()
            .url("/_dr/task/icannReportingUpload")
            .method("POST")
            .param("subdir", subDir);
    assertTasksEnqueued("retryable-cron-tasks", matcher);
  }

  private IcannReportingStagingAction createAction(ImmutableList<ReportType> reportingMode) {
    IcannReportingStagingAction action = new IcannReportingStagingAction();
    action.yearMonth = new YearMonth(2017, 6);
    action.subdir = "default/dir";
    action.reportTypes = reportingMode;
    action.response = response;
    action.stager = stager;
    action.retrier = new Retrier(new FakeSleeper(new FakeClock()), 3);
    action.emailUtils = emailUtils;
    return action;
  }

  @Test
  public void testActivityReportingMode_onlyStagesActivityReports() throws Exception {
    IcannReportingStagingAction action = createAction(ImmutableList.of(ReportType.ACTIVITY));
    action.run();
    verify(stager).stageReports(ReportType.ACTIVITY);
    verify(stager).createAndUploadManifest(ImmutableList.of("a", "b"));
    verify(emailUtils)
        .emailResults(
            "ICANN Monthly report staging summary [SUCCESS]",
            "Completed staging the following 2 ICANN reports:\na\nb");
    assertUploadTaskEnqueued("default/dir");
  }

  @Test
  public void testAbsentReportingMode_stagesBothReports() throws Exception {
    IcannReportingStagingAction action =
        createAction(ImmutableList.of(ReportType.ACTIVITY, ReportType.TRANSACTIONS));
    action.run();
    verify(stager).stageReports(ReportType.ACTIVITY);
    verify(stager).stageReports(ReportType.TRANSACTIONS);
    verify(stager).createAndUploadManifest(ImmutableList.of("a", "b", "c", "d"));
    verify(emailUtils)
        .emailResults(
            "ICANN Monthly report staging summary [SUCCESS]",
            "Completed staging the following 4 ICANN reports:\na\nb\nc\nd");
    assertUploadTaskEnqueued("default/dir");
  }

  @Test
  public void testRetryOnBigqueryException() throws Exception {
    IcannReportingStagingAction action =
        createAction(ImmutableList.of(ReportType.ACTIVITY, ReportType.TRANSACTIONS));
    when(stager.stageReports(ReportType.TRANSACTIONS))
        .thenThrow(new BigqueryJobFailureException("Expected failure", null, null, null))
        .thenReturn(ImmutableList.of("c", "d"));
    action.run();
    verify(stager, times(2)).stageReports(ReportType.ACTIVITY);
    verify(stager, times(2)).stageReports(ReportType.TRANSACTIONS);
    verify(stager).createAndUploadManifest(ImmutableList.of("a", "b", "c", "d"));
    verify(emailUtils)
        .emailResults(
            "ICANN Monthly report staging summary [SUCCESS]",
            "Completed staging the following 4 ICANN reports:\na\nb\nc\nd");
    assertUploadTaskEnqueued("default/dir");
  }

  @Test
  public void testEmailEng_onMoreThanRetriableFailure() throws Exception {
    IcannReportingStagingAction action =
        createAction(ImmutableList.of(ReportType.ACTIVITY));
    when(stager.stageReports(ReportType.ACTIVITY))
        .thenThrow(new BigqueryJobFailureException("Expected failure", null, null, null));
    RuntimeException thrown = assertThrows(RuntimeException.class, action::run);
    assertThat(thrown).hasCauseThat().isInstanceOf(BigqueryJobFailureException.class);
    assertThat(thrown).hasMessageThat().isEqualTo("Staging action failed.");
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("Expected failure");
    verify(stager, times(3)).stageReports(ReportType.ACTIVITY);
    verify(emailUtils)
        .emailResults(
            "ICANN Monthly report staging summary [FAILURE]",
            "Staging failed due to BigqueryJobFailureException: Expected failure,"
                + " check logs for more details.");
    // Assert no upload task enqueued
    assertNoTasksEnqueued("retryable-cron-tasks");
  }
}
