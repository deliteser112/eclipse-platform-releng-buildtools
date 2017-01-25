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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.export.CheckSnapshotAction.CHECK_SNAPSHOT_KINDS_TO_LOAD_PARAM;
import static google.registry.export.CheckSnapshotAction.CHECK_SNAPSHOT_NAME_PARAM;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static org.mockito.Mockito.verify;

import com.google.common.base.Joiner;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link ExportSnapshotAction}. */
@RunWith(MockitoJUnitRunner.class)
public class ExportSnapshotActionTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withTaskQueue().build();

  @Mock private DatastoreBackupService backupService;

  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2014-08-01T01:02:03Z"));
  private final ExportSnapshotAction action = new ExportSnapshotAction();

  @Before
  public void before() throws Exception {
    action.clock = clock;
    action.backupService = backupService;
    action.response = response;
  }

  @Test
  public void testPost_launchesBackup_andEnqueuesPollTask() throws Exception {
    action.run();
    verify(backupService)
        .launchNewBackup(
            ExportSnapshotAction.QUEUE,
            "auto_snapshot_20140801_010203",
            "domain-registry-snapshots",
            ExportConstants.getBackupKinds());
    assertTasksEnqueued(
        CheckSnapshotAction.QUEUE,
        new TaskMatcher()
            .url(CheckSnapshotAction.PATH)
            .param(CHECK_SNAPSHOT_NAME_PARAM, "auto_snapshot_20140801_010203")
            .param(
                CHECK_SNAPSHOT_KINDS_TO_LOAD_PARAM,
                Joiner.on(",").join(ExportConstants.getReportingKinds()))
            .method("POST"));
    assertThat(response.getPayload())
        .isEqualTo("Datastore backup started with name: auto_snapshot_20140801_010203");
  }
}
