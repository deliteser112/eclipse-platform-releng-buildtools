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

package google.registry.export;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.export.CheckSnapshotAction.CHECK_SNAPSHOT_KINDS_TO_LOAD_PARAM;
import static google.registry.export.CheckSnapshotAction.CHECK_SNAPSHOT_NAME_PARAM;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.request.Action.Method;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NoContentException;
import google.registry.request.HttpException.NotModifiedException;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link CheckSnapshotAction}. */
@RunWith(MockitoJUnitRunner.class)
public class CheckSnapshotActionTest {

  static final DateTime START_TIME = DateTime.parse("2014-08-01T01:02:03Z");
  static final DateTime COMPLETE_TIME = START_TIME.plus(Duration.standardMinutes(30));

  @Rule public final InjectRule inject = new InjectRule();
  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withTaskQueue().build();
  @Rule public final ExceptionRule thrown = new ExceptionRule();

  @Mock private DatastoreBackupService backupService;

  private DatastoreBackupInfo backupInfo;

  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(COMPLETE_TIME.plusMillis(1000));
  private final CheckSnapshotAction action = new CheckSnapshotAction();

  @Before
  public void before() throws Exception {
    inject.setStaticField(DatastoreBackupInfo.class, "clock", clock);
    action.requestMethod = Method.POST;
    action.snapshotName = "some_backup";
    action.kindsToLoadParam = "one,two";
    action.response = response;
    action.backupService = backupService;

    backupInfo =
        new DatastoreBackupInfo(
            "some_backup",
            START_TIME,
            Optional.of(COMPLETE_TIME),
            ImmutableSet.of("one", "two", "three"),
            Optional.of("gs://somebucket/some_backup_20140801.backup_info"));

    when(backupService.findByName("some_backup")).thenReturn(backupInfo);
  }

  private void setPendingBackup() {
    backupInfo =
        new DatastoreBackupInfo(
            backupInfo.getName(),
            backupInfo.getStartTime(),
            Optional.<DateTime>absent(),
            backupInfo.getKinds(),
            backupInfo.getGcsFilename());

    when(backupService.findByName("some_backup")).thenReturn(backupInfo);
  }

  private static void assertLoadTaskEnqueued(String id, String file, String kinds)
      throws Exception {
    assertTasksEnqueued(
        "export-snapshot",
        new TaskMatcher()
            .url("/_dr/task/loadSnapshot")
            .method("POST")
            .param("id", id)
            .param("file", file)
            .param("kinds", kinds));
  }

  @Test
  public void testSuccess_enqueuePollTask() throws Exception {
    CheckSnapshotAction.enqueuePollTask(
        "some_snapshot_name", ImmutableSet.of("one", "two", "three"));
    assertTasksEnqueued(
        CheckSnapshotAction.QUEUE,
        new TaskMatcher()
            .url(CheckSnapshotAction.PATH)
            .param(CHECK_SNAPSHOT_NAME_PARAM, "some_snapshot_name")
            .param(CHECK_SNAPSHOT_KINDS_TO_LOAD_PARAM, "one,two,three")
            .method("POST"));
  }

  @Test
  public void testPost_forPendingBackup_returnsNotModified() throws Exception {
    setPendingBackup();

    thrown.expect(NotModifiedException.class, "Datastore backup some_backup still pending");
    action.run();
  }

  @Test
  public void testPost_forStalePendingBackupBackup_returnsNoContent() throws Exception {
    setPendingBackup();

    when(backupService.findByName("some_backup")).thenReturn(backupInfo);

    clock.setTo(
        START_TIME
            .plus(Duration.standardHours(20))
            .plus(Duration.standardMinutes(3))
            .plus(Duration.millis(1234)));

    thrown.expect(
        NoContentException.class,
        "Datastore backup some_backup abandoned - "
            + "not complete after 20 hours, 3 minutes and 1 second");

    action.run();
  }

  @Test
  public void testPost_forCompleteBackup_enqueuesLoadTask() throws Exception {

    action.run();
    assertLoadTaskEnqueued(
        "20140801_010203", "gs://somebucket/some_backup_20140801.backup_info", "one,two");
  }

  @Test
  public void testPost_forCompleteAutoBackup_enqueuesLoadTask_usingBackupName() throws Exception {
    action.snapshotName = "auto_snapshot_somestring";
    when(backupService.findByName("auto_snapshot_somestring")).thenReturn(backupInfo);

    action.run();
    assertLoadTaskEnqueued(
        "somestring", "gs://somebucket/some_backup_20140801.backup_info", "one,two");
  }

  @Test
  public void testPost_forCompleteBackup_withExtraKindsToLoad_enqueuesLoadTask() throws Exception {
    action.kindsToLoadParam = "one,foo";

    action.run();
    assertLoadTaskEnqueued(
        "20140801_010203", "gs://somebucket/some_backup_20140801.backup_info", "one");
  }

  @Test
  public void testPost_forCompleteBackup_withEmptyKindsToLoad_skipsLoadTask() throws Exception {
    action.kindsToLoadParam = "";

    action.run();
    assertNoTasksEnqueued("export-snapshot");
  }

  @Test
  public void testPost_forBadBackup_returnsBadRequest() throws Exception {
    when(backupService.findByName("some_backup"))
        .thenThrow(new IllegalArgumentException("No backup found"));

    thrown.expect(BadRequestException.class, "Bad backup name some_backup: No backup found");

    action.run();
  }

  @Test
  public void testGet_returnsInformation() throws Exception {
    action.requestMethod = Method.GET;

    action.run();
    assertThat(response.getPayload())
        .isEqualTo(
            Joiner.on("\n")
                .join(
                    ImmutableList.of(
                        "Backup name: some_backup",
                        "Status: COMPLETE",
                        "Started: 2014-08-01T01:02:03.000Z",
                        "Ended: 2014-08-01T01:32:03.000Z",
                        "Duration: 30m",
                        "GCS: gs://somebucket/some_backup_20140801.backup_info",
                        "Kinds: [one, two, three]",
                        "")));
  }

  @Test
  public void testGet_forBadBackup_returnsError() throws Exception {
    action.requestMethod = Method.GET;
    when(backupService.findByName("some_backup"))
        .thenThrow(new IllegalArgumentException("No backup found"));

    thrown.expect(BadRequestException.class, "Bad backup name some_backup: No backup found");

    action.run();
  }
}
