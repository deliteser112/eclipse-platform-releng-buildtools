// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.export;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.InjectRule;
import com.google.domain.registry.testing.TaskQueueHelper.TaskMatcher;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Unit tests for {@link CheckSnapshotServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class CheckSnapshotServletTest {

  static final DateTime START_TIME = DateTime.parse("2014-08-01T01:02:03Z");
  static final DateTime COMPLETE_TIME = START_TIME.plus(Duration.standardMinutes(30));

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withTaskQueue()
      .build();

  @Mock
  private HttpServletRequest req;

  @Mock
  private HttpServletResponse rsp;

  private DatastoreBackupInfo backupInfo;

  @Mock
  private DatastoreBackupService backupService;

  @Mock
  private LoadSnapshotServlet loadSnapshotServlet;

  private final FakeClock clock = new FakeClock(COMPLETE_TIME.plusMillis(1000));
  private final StringWriter httpOutput = new StringWriter();
  private final CheckSnapshotServlet servlet = new CheckSnapshotServlet();

  @Before
  public void before() throws Exception {
    inject.setStaticField(CheckSnapshotServlet.class, "backupService", backupService);
    inject.setStaticField(CheckSnapshotServlet.class, "loadSnapshotServlet", loadSnapshotServlet);
    inject.setStaticField(DatastoreBackupInfo.class, "clock", clock);

    when(rsp.getWriter()).thenReturn(new PrintWriter(httpOutput));

    servlet.init(mock(ServletConfig.class));
    when(req.getMethod()).thenReturn("POST");

    backupInfo = new DatastoreBackupInfo(
        "some_backup",
        START_TIME,
        Optional.of(COMPLETE_TIME),
        ImmutableSet.of("one", "two", "three"),
        Optional.of("gs://somebucket/some_backup_20140801.backup_info"));
  }

  private void setPendingBackup() {
    backupInfo = new DatastoreBackupInfo(
        backupInfo.getName(),
        backupInfo.getStartTime(),
        Optional.<DateTime>absent(),
        backupInfo.getKinds(),
        backupInfo.getGcsFilename());
  }

  @Test
  public void testSuccess_enqueuePollTask() throws Exception {
    servlet.enqueuePollTask("some_snapshot_name", ImmutableSet.of("one", "two", "three"));
    assertTasksEnqueued(CheckSnapshotServlet.QUEUE,
        new TaskMatcher()
            .url(CheckSnapshotServlet.PATH)
            .param(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM, "some_snapshot_name")
            .param(CheckSnapshotServlet.SNAPSHOT_KINDS_TO_LOAD_PARAM, "one,two,three")
            .method("POST"));
  }

  @Test
  public void testPost_forPendingBackup_returnsNotModified() throws Exception {
    setPendingBackup();
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM)).thenReturn("some_backup");
    when(backupService.findByName("some_backup")).thenReturn(backupInfo);

    servlet.service(req, rsp);
    verify(rsp).sendError(SC_NOT_MODIFIED, "Datastore backup some_backup still pending");
  }

  @Test
  public void testPost_forStalePendingBackupBackup_returnsAccepted() throws Exception {
    setPendingBackup();
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM)).thenReturn("some_backup");
    when(backupService.findByName("some_backup")).thenReturn(backupInfo);
    clock.setTo(START_TIME
        .plus(Duration.standardHours(20))
        .plus(Duration.standardMinutes(3))
        .plus(Duration.millis(1234)));

    servlet.service(req, rsp);
    verify(rsp).sendError(SC_ACCEPTED,
        "Datastore backup some_backup abandoned - "
        + "not complete after 20 hours, 3 minutes and 1 second");
  }

  @Test
  public void testPost_forCompleteBackup_enqueuesLoadTask() throws Exception {
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM)).thenReturn("some_backup");
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_KINDS_TO_LOAD_PARAM)).thenReturn("one,two");
    when(backupService.findByName("some_backup")).thenReturn(backupInfo);

    servlet.service(req, rsp);
    verify(rsp).setStatus(SC_OK);
    verify(loadSnapshotServlet).enqueueLoadTask(
        "20140801_010203",
        "gs://somebucket/some_backup_20140801.backup_info",
        ImmutableSet.of("one", "two"));
  }

  @Test
  public void testPost_forCompleteAutoBackup_enqueuesLoadTask_usingBackupName() throws Exception {
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM))
        .thenReturn("auto_snapshot_somestring");
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_KINDS_TO_LOAD_PARAM)).thenReturn("one,two");
    when(backupService.findByName("auto_snapshot_somestring")).thenReturn(backupInfo);

    servlet.service(req, rsp);
    verify(rsp).setStatus(SC_OK);
    verify(loadSnapshotServlet).enqueueLoadTask(
        "somestring",
        "gs://somebucket/some_backup_20140801.backup_info",
        ImmutableSet.of("one", "two"));
  }

  @Test
  public void testPost_forCompleteBackup_missingKindsToLoad_enqueuesLoadTask() throws Exception {
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM)).thenReturn("some_backup");
    when(backupService.findByName("some_backup")).thenReturn(backupInfo);

    servlet.service(req, rsp);
    verify(rsp).setStatus(SC_OK);
    verify(loadSnapshotServlet).enqueueLoadTask(
        "20140801_010203",
        "gs://somebucket/some_backup_20140801.backup_info",
        ImmutableSet.of("one", "two", "three"));
  }

  @Test
  public void testPost_forCompleteBackup_withExtraKindsToLoad_enqueuesLoadTask() throws Exception {
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM)).thenReturn("some_backup");
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_KINDS_TO_LOAD_PARAM)).thenReturn("one,foo");
    when(backupService.findByName("some_backup")).thenReturn(backupInfo);

    servlet.service(req, rsp);
    verify(rsp).setStatus(SC_OK);
    verify(loadSnapshotServlet).enqueueLoadTask(
        "20140801_010203",
        "gs://somebucket/some_backup_20140801.backup_info",
        ImmutableSet.of("one"));
  }

@Test
  public void testPost_forCompleteBackup_withEmptyKindsToLoad_skipsLoadTask() throws Exception {
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM)).thenReturn("some_backup");
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_KINDS_TO_LOAD_PARAM)).thenReturn("");
    when(backupService.findByName("some_backup")).thenReturn(backupInfo);

    servlet.service(req, rsp);
    verify(rsp).setStatus(SC_OK);
    verifyZeroInteractions(loadSnapshotServlet);
  }

  @Test
  public void testPost_forBadBackup_returnsBadRequest() throws Exception {
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM)).thenReturn("some_backup");
    when(backupService.findByName("some_backup")).thenThrow(
        new IllegalArgumentException("No backup found"));

    servlet.service(req, rsp);
    verify(rsp).sendError(SC_BAD_REQUEST, "Bad backup name some_backup: No backup found");
  }

  @Test
  public void testPost_noBackupSpecified_returnsError() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    servlet.service(req, rsp);
    verify(rsp).sendError(SC_BAD_REQUEST, "Missing required parameter: name");
  }

  @Test
  public void testGet_returnsInformation() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM)).thenReturn("some_backup");
    when(backupService.findByName("some_backup")).thenReturn(backupInfo);

    servlet.service(req, rsp);
    verify(rsp).setStatus(SC_OK);
    assertThat(httpOutput.toString()).isEqualTo("OK\n\n" + Joiner.on("\n").join(ImmutableList.of(
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
    when(req.getMethod()).thenReturn("GET");
    when(req.getParameter(CheckSnapshotServlet.SNAPSHOT_NAME_PARAM)).thenReturn("some_backup");
    when(backupService.findByName("some_backup")).thenThrow(
        new IllegalArgumentException("No backup found"));

    servlet.service(req, rsp);
    verify(rsp).sendError(SC_BAD_REQUEST, "No backup found");
  }

  @Test
  public void testGet_noBackupSpecified_returnsError() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    servlet.service(req, rsp);
    verify(rsp).sendError(SC_BAD_REQUEST, "Missing required parameter: name");
  }
}
