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

package com.google.domain.registry.rde;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.model.rde.RdeMode.FULL;
import static com.google.domain.registry.model.rde.RdeMode.THIN;
import static com.google.domain.registry.model.registry.RegistryCursor.CursorType.BRDA;
import static com.google.domain.registry.model.registry.RegistryCursor.CursorType.RDE_STAGING;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeConstants.TUESDAY;
import static org.joda.time.Duration.standardDays;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.RegistryCursor;
import com.google.domain.registry.model.registry.RegistryCursor.CursorType;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.InjectRule;

import com.googlecode.objectify.VoidWork;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PendingDepositChecker}. */
@RunWith(JUnit4.class)
public class PendingDepositCheckerTest {

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  private final FakeClock clock = new FakeClock();
  private final PendingDepositChecker checker = new PendingDepositChecker();

  @Before
  public void before() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    checker.brdaDayOfWeek = TUESDAY;
    checker.brdaInterval = standardDays(7);
    checker.clock = clock;
    checker.rdeInterval = standardDays(1);
  }

  @Test
  public void testMethod_noTldsWithEscrowEnabled_returnsEmpty() throws Exception {
    createTld("pal");
    createTld("fun");
    assertThat(checker.getTldsAndWatermarksPendingDepositForRdeAndBrda()).isEmpty();
  }

  @Test
  public void testMethod_firstDeposit_depositsRdeTodayAtMidnight() throws Exception {
    clock.setTo(DateTime.parse("2000-01-01T08:00Z"));  // Saturday
    createTldWithEscrowEnabled("lol");
    clock.advanceOneMilli();
    assertThat(checker.getTldsAndWatermarksPendingDepositForRdeAndBrda()).isEqualTo(
        ImmutableSetMultimap.of(
            "lol", PendingDeposit.create(
                "lol", DateTime.parse("2000-01-01TZ"), FULL, RDE_STAGING, standardDays(1))));
  }

  @Test
  @Ignore("TODO(b/23791350): Causes TimestampInversionException")
  public void testMethod_firstDepositOnBrdaDay_depositsBothRdeAndBrda() throws Exception {
    clock.setTo(DateTime.parse("2000-01-04T08:00Z"));  // Tuesday
    createTldWithEscrowEnabled("lol");
    clock.advanceOneMilli();
    assertThat(checker.getTldsAndWatermarksPendingDepositForRdeAndBrda()).isEqualTo(
        ImmutableSetMultimap.of(
            "lol", PendingDeposit.create(
                "lol", DateTime.parse("2000-01-04TZ"), FULL, RDE_STAGING, standardDays(1)),
            "lol", PendingDeposit.create(
                "lol", DateTime.parse("2000-01-04TZ"), THIN, BRDA, standardDays(7))));
  }

  @Test
  public void testMethod_firstRdeDeposit_initializesCursorToMidnightToday() throws Exception {
    clock.setTo(DateTime.parse("2000-01-01TZ"));  // Saturday
    createTldWithEscrowEnabled("lol");
    clock.advanceOneMilli();
    Registry registry = Registry.get("lol");
    assertThat(RegistryCursor.load(registry, RDE_STAGING)).isAbsent();
    checker.getTldsAndWatermarksPendingDepositForRdeAndBrda();
    assertThat(RegistryCursor.load(registry, RDE_STAGING))
        .hasValue(DateTime.parse("2000-01-01TZ"));
  }

  @Test
  public void testMethod_subsequentRdeDeposit_doesntMutateCursor() throws Exception {
    clock.setTo(DateTime.parse("2000-01-01TZ"));  // Saturday
    createTldWithEscrowEnabled("lol");
    clock.advanceOneMilli();
    DateTime yesterday = DateTime.parse("1999-12-31TZ");
    setCursor(Registry.get("lol"), RDE_STAGING, yesterday);
    clock.advanceOneMilli();
    checker.getTldsAndWatermarksPendingDepositForRdeAndBrda();
    assertThat(RegistryCursor.load(Registry.get("lol"), RDE_STAGING)).hasValue(yesterday);
  }

  @Test
  public void testMethod_firstBrdaDepositButNotOnBrdaDay_doesntInitializeCursor()
      throws Exception {
    clock.setTo(DateTime.parse("2000-01-01TZ"));  // Saturday
    createTldWithEscrowEnabled("lol");
    Registry registry = Registry.get("lol");
    clock.advanceOneMilli();
    setCursor(registry, RDE_STAGING, DateTime.parse("2000-01-02TZ")); // assume rde is already done
    clock.advanceOneMilli();
    assertThat(RegistryCursor.load(registry, BRDA)).isAbsent();
    assertThat(checker.getTldsAndWatermarksPendingDepositForRdeAndBrda()).isEmpty();
    assertThat(RegistryCursor.load(registry, BRDA)).isAbsent();
  }

  @Test
  public void testMethod_backloggedTwoDays_onlyWantsLeastRecentDay() throws Exception {
    clock.setTo(DateTime.parse("2000-01-01TZ"));
    createTldWithEscrowEnabled("lol");
    clock.advanceOneMilli();
    setCursor(Registry.get("lol"), RDE_STAGING, DateTime.parse("1999-12-30TZ"));
    clock.advanceOneMilli();
    assertThat(checker.getTldsAndWatermarksPendingDepositForRdeAndBrda()).isEqualTo(
        ImmutableSetMultimap.of(
            "lol", PendingDeposit.create(
                "lol", DateTime.parse("1999-12-30TZ"), FULL, RDE_STAGING, standardDays(1))));
  }

  @Test
  @Ignore("TODO(b/23791350): Causes TimestampInversionException")
  public void testMethod_multipleTldsWithEscrowEnabled_depositsBoth() throws Exception {
    clock.setTo(DateTime.parse("2000-01-01TZ"));  // Saturday
    createTldWithEscrowEnabled("pal");
    clock.advanceOneMilli();
    createTldWithEscrowEnabled("fun");
    clock.advanceOneMilli();
    assertThat(checker.getTldsAndWatermarksPendingDepositForRdeAndBrda()).isEqualTo(
        ImmutableSetMultimap.of(
            "pal", PendingDeposit.create(
                "pal", DateTime.parse("2000-01-01TZ"), FULL, RDE_STAGING, standardDays(1)),
            "fun", PendingDeposit.create(
                "fun", DateTime.parse("2000-01-01TZ"), FULL, RDE_STAGING, standardDays(1))));
  }

  private static void setCursor(
      final Registry registry, final CursorType cursorType, final DateTime value) {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        RegistryCursor.save(registry, cursorType, value);
      }});
  }

  private static void createTldWithEscrowEnabled(final String tld) {
    createTld(tld);
    persistResource(Registry.get(tld).asBuilder().setEscrowEnabled(true).build());
  }
}

