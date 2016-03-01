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

package com.google.domain.registry.export.sheet;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.domain.registry.model.server.Lock;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeResponse;

import org.joda.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;

/** Unit tests for {@link SyncRegistrarsSheetTask}. */
@RunWith(JUnit4.class)
public class SyncRegistrarsSheetTaskTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  private final FakeResponse response = new FakeResponse();
  private final SyncRegistrarsSheet syncRegistrarsSheet = mock(SyncRegistrarsSheet.class);

  private void runTask(@Nullable String idConfig, @Nullable String idParam) {
    SyncRegistrarsSheetTask task = new SyncRegistrarsSheetTask();
    task.response = response;
    task.syncRegistrarsSheet = syncRegistrarsSheet;
    task.idConfig = Optional.fromNullable(idConfig);
    task.idParam = Optional.fromNullable(idParam);
    task.interval = Duration.standardHours(1);
    task.timeout = Duration.standardHours(1);
    task.run();
  }

  @Test
  public void testPost_withoutParamsOrSystemProperty_dropsTask() throws Exception {
    runTask(null, null);
    assertThat(response.getPayload()).startsWith("MISSINGNO");
    verifyZeroInteractions(syncRegistrarsSheet);
  }

  @Test
  public void testPost_withoutParams_runsSyncWithDefaultIdAndChecksIfModified() throws Exception {
    when(syncRegistrarsSheet.wasRegistrarsModifiedInLast(any(Duration.class))).thenReturn(true);
    runTask("jazz", null);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).startsWith("OK");
    verify(syncRegistrarsSheet).wasRegistrarsModifiedInLast(any(Duration.class));
    verify(syncRegistrarsSheet).run(eq("jazz"));
    verifyNoMoreInteractions(syncRegistrarsSheet);
  }

  @Test
  public void testPost_noModificationsToRegistrarEntities_doesNothing() throws Exception {
    when(syncRegistrarsSheet.wasRegistrarsModifiedInLast(any(Duration.class))).thenReturn(false);
    runTask("NewRegistrar", null);
    assertThat(response.getPayload()).startsWith("NOTMODIFIED");
    verify(syncRegistrarsSheet).wasRegistrarsModifiedInLast(any(Duration.class));
    verifyNoMoreInteractions(syncRegistrarsSheet);
  }

  @Test
  public void testPost_overrideId_runsSyncWithCustomIdAndDoesNotCheckModified() throws Exception {
    runTask(null, "foobar");
    assertThat(response.getPayload()).startsWith("OK");
    verify(syncRegistrarsSheet).run(eq("foobar"));
    verifyNoMoreInteractions(syncRegistrarsSheet);
  }

  @Test
  public void testPost_failToAquireLock_servletDoesNothingAndReturns() throws Exception {
    String lockName = "Synchronize registrars sheet: foobar";
    Lock.executeWithLocks(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        runTask(null, "foobar");
        return null;
      }
    }, SyncRegistrarsSheetTask.class, "", Duration.standardHours(1), lockName);
    assertThat(response.getPayload()).startsWith("LOCKED");
    verifyZeroInteractions(syncRegistrarsSheet);
  }
}
