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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import google.registry.reporting.IcannReportingModule.ReportType;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link google.registry.reporting.IcannReportingStagingAction}.
 */
@RunWith(JUnit4.class)
public class IcannReportingStagingActionTest {

  FakeResponse response = new FakeResponse();
  IcannReportingStager stager = mock(IcannReportingStager.class);

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withLocalModules()
      .build();

  @Before
  public void setUp() throws Exception {
    when(stager.stageReports(ReportType.ACTIVITY)).thenReturn(ImmutableList.of("a", "b"));
    when(stager.stageReports(ReportType.TRANSACTIONS)).thenReturn(ImmutableList.of("c", "d"));
  }

  private IcannReportingStagingAction createAction(ImmutableList<ReportType> reportingMode) {
    IcannReportingStagingAction action = new IcannReportingStagingAction();
    action.reportTypes = reportingMode;
    action.response = response;
    action.stager = stager;
    return action;
  }

  @Test
  public void testActivityReportingMode_onlyStagesActivityReports() throws Exception {
    IcannReportingStagingAction action = createAction(ImmutableList.of(ReportType.ACTIVITY));
    action.run();
    verify(stager).stageReports(ReportType.ACTIVITY);
    verify(stager).createAndUploadManifest(ImmutableList.of("a", "b"));
  }

  @Test
  public void testAbsentReportingMode_stagesBothReports() throws Exception {
    IcannReportingStagingAction action =
        createAction(ImmutableList.of(ReportType.ACTIVITY, ReportType.TRANSACTIONS));
    action.run();
    verify(stager).stageReports(ReportType.ACTIVITY);
    verify(stager).stageReports(ReportType.TRANSACTIONS);
    verify(stager).createAndUploadManifest(ImmutableList.of("a", "b", "c", "d"));
  }
}

