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

package google.registry.rde;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.Duration.standardDays;
import static org.joda.time.Duration.standardSeconds;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import google.registry.model.common.Cursor;
import google.registry.model.common.Cursor.CursorType;
import google.registry.model.registry.Registry;
import google.registry.model.server.Lock;
import google.registry.rde.EscrowTaskRunner.EscrowTask;
import google.registry.request.HttpException.NoContentException;
import google.registry.request.HttpException.ServiceUnavailableException;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import java.util.concurrent.Callable;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link EscrowTaskRunner}. */
@RunWith(JUnit4.class)
public class EscrowTaskRunnerTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  private final EscrowTask task = mock(EscrowTask.class);
  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));

  private EscrowTaskRunner runner;
  private Registry registry;

  @Before
  public void before() throws Exception {
    createTld("lol");
    registry = Registry.get("lol");
    runner = new EscrowTaskRunner();
    runner.clock = clock;
    runner.tld = "lol";
    DateTimeZone.setDefault(DateTimeZone.forID("America/New_York"));  // Make sure UTC stuff works.
  }

  @Test
  public void testRun_cursorIsToday_advancesCursorToTomorrow() throws Exception {
    clock.setTo(DateTime.parse("2006-06-06T00:30:00Z"));
    persistResource(
        Cursor.create(CursorType.RDE_STAGING, DateTime.parse("2006-06-06TZ"), registry));
    runner.lockRunAndRollForward(
        task, registry, standardSeconds(30), CursorType.RDE_STAGING, standardDays(1));
    verify(task).runWithLock(DateTime.parse("2006-06-06TZ"));
    ofy().clearSessionCache();
    Cursor cursor = ofy().load().key(Cursor.createKey(CursorType.RDE_STAGING, registry)).now();
    assertThat(cursor.getCursorTime()).isEqualTo(DateTime.parse("2006-06-07TZ"));
  }

  @Test
  public void testRun_cursorMissing_assumesTodayAndAdvancesCursorToTomorrow() throws Exception {
    clock.setTo(DateTime.parse("2006-06-06T00:30:00Z"));
    runner.lockRunAndRollForward(
        task, registry, standardSeconds(30), CursorType.RDE_STAGING, standardDays(1));
    verify(task).runWithLock(DateTime.parse("2006-06-06TZ"));
    Cursor cursor =
        ofy().load().key(Cursor.createKey(CursorType.RDE_STAGING, Registry.get("lol"))).now();
    assertThat(cursor.getCursorTime()).isEqualTo(DateTime.parse("2006-06-07TZ"));
  }

  @Test
  public void testRun_cursorInTheFuture_doesNothing() throws Exception {
    clock.setTo(DateTime.parse("2006-06-06T00:30:00Z"));
    persistResource(
        Cursor.create(CursorType.RDE_STAGING, DateTime.parse("2006-06-07TZ"), registry));
    thrown.expect(NoContentException.class, "Already completed");
    runner.lockRunAndRollForward(
        task, registry, standardSeconds(30), CursorType.RDE_STAGING, standardDays(1));
  }

  @Test
  public void testRun_lockIsntAvailable_throws503() throws Exception {
    String lockName = task.getClass().getSimpleName() + " lol";
    clock.setTo(DateTime.parse("2006-06-06T00:30:00Z"));
    persistResource(
        Cursor.create(CursorType.RDE_STAGING, DateTime.parse("2006-06-06TZ"), registry));
    thrown.expect(ServiceUnavailableException.class, "Lock in use: " + lockName);
    Lock.executeWithLocks(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            runner.lockRunAndRollForward(
                task, registry, standardSeconds(30), CursorType.RDE_STAGING, standardDays(1));
            return null;
          }},
        null,
        "lol",
        standardSeconds(30),
        lockName);
  }
}
