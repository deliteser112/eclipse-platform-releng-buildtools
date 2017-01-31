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

package google.registry.export.sheet;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import google.registry.model.server.Lock;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeResponse;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.joda.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SyncRegistrarsSheetAction}. */
@RunWith(JUnit4.class)
public class SyncRegistrarsSheetActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  private final FakeResponse response = new FakeResponse();
  private final SyncRegistrarsSheet syncRegistrarsSheet = mock(SyncRegistrarsSheet.class);

  private void runAction(@Nullable String idConfig, @Nullable String idParam) {
    SyncRegistrarsSheetAction action = new SyncRegistrarsSheetAction();
    action.response = response;
    action.syncRegistrarsSheet = syncRegistrarsSheet;
    action.idConfig = Optional.fromNullable(idConfig);
    action.idParam = Optional.fromNullable(idParam);
    action.timeout = Duration.standardHours(1);
    action.run();
  }

  @Test
  public void testPost_withoutParamsOrSystemProperty_dropsTask() throws Exception {
    runAction(null, null);
    assertThat(response.getPayload()).startsWith("MISSINGNO");
    verifyZeroInteractions(syncRegistrarsSheet);
  }

  @Test
  public void testPost_withoutParams_runsSyncWithDefaultIdAndChecksIfModified() throws Exception {
    when(syncRegistrarsSheet.wereRegistrarsModified()).thenReturn(true);
    runAction("jazz", null);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).startsWith("OK");
    verify(syncRegistrarsSheet).wereRegistrarsModified();
    verify(syncRegistrarsSheet).run(eq("jazz"));
    verifyNoMoreInteractions(syncRegistrarsSheet);
  }

  @Test
  public void testPost_noModificationsToRegistrarEntities_doesNothing() throws Exception {
    when(syncRegistrarsSheet.wereRegistrarsModified()).thenReturn(false);
    runAction("NewRegistrar", null);
    assertThat(response.getPayload()).startsWith("NOTMODIFIED");
    verify(syncRegistrarsSheet).wereRegistrarsModified();
    verifyNoMoreInteractions(syncRegistrarsSheet);
  }

  @Test
  public void testPost_overrideId_runsSyncWithCustomIdAndDoesNotCheckModified() throws Exception {
    runAction(null, "foobar");
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
        runAction(null, "foobar");
        return null;
      }
    }, SyncRegistrarsSheetAction.class, "", Duration.standardHours(1), lockName);
    assertThat(response.getPayload()).startsWith("LOCKED");
    verifyZeroInteractions(syncRegistrarsSheet);
  }
}
