// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package com.google.domain.registry.rde;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.Duration.standardDays;
import static org.joda.time.Duration.standardSeconds;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.RegistryCursor;
import com.google.domain.registry.model.registry.RegistryCursor.CursorType;
import com.google.domain.registry.model.server.Lock;
import com.google.domain.registry.rde.EscrowTaskRunner.EscrowTask;
import com.google.domain.registry.request.HttpException.NoContentException;
import com.google.domain.registry.request.HttpException.ServiceUnavailableException;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.testing.FakeClock;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Callable;

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
        RegistryCursor.create(registry, CursorType.RDE_STAGING, DateTime.parse("2006-06-06TZ")));
    runner.lockRunAndRollForward(
        task, registry, standardSeconds(30), CursorType.RDE_STAGING, standardDays(1));
    verify(task).runWithLock(DateTime.parse("2006-06-06TZ"));
    ofy().clearSessionCache();
    assertThat(RegistryCursor.load(registry, CursorType.RDE_STAGING))
        .hasValue(DateTime.parse("2006-06-07TZ"));
  }

  @Test
  public void testRun_cursorMissing_assumesTodayAndAdvancesCursorToTomorrow() throws Exception {
    clock.setTo(DateTime.parse("2006-06-06T00:30:00Z"));
    runner.lockRunAndRollForward(
        task, registry, standardSeconds(30), CursorType.RDE_STAGING, standardDays(1));
    verify(task).runWithLock(DateTime.parse("2006-06-06TZ"));
    assertThat(RegistryCursor.load(Registry.get("lol"), CursorType.RDE_STAGING))
        .hasValue(DateTime.parse("2006-06-07TZ"));
  }

  @Test
  public void testRun_cursorInTheFuture_doesNothing() throws Exception {
    clock.setTo(DateTime.parse("2006-06-06T00:30:00Z"));
    persistResource(
        RegistryCursor.create(registry, CursorType.RDE_STAGING, DateTime.parse("2006-06-07TZ")));
    thrown.expect(NoContentException.class, "Already completed");
    runner.lockRunAndRollForward(
        task, registry, standardSeconds(30), CursorType.RDE_STAGING, standardDays(1));
  }

  @Test
  public void testRun_lockIsntAvailable_throws503() throws Exception {
    String lockName = task.getClass().getSimpleName() + " lol";
    thrown.expect(ServiceUnavailableException.class, "Lock in use: " + lockName);
    clock.setTo(DateTime.parse("2006-06-06T00:30:00Z"));
    persistResource(
        RegistryCursor.create(registry, CursorType.RDE_STAGING, DateTime.parse("2006-06-06TZ")));
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
