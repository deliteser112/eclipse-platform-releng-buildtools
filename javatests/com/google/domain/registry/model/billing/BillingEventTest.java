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

package com.google.domain.registry.model.billing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.EntityTestCase;
import com.google.domain.registry.model.billing.BillingEvent.Reason;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.GracePeriod;
import com.google.domain.registry.model.domain.rgp.GracePeriodStatus;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.testing.ExceptionRule;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;

import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link BillingEvent}. */
public class BillingEventTest extends EntityTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final DateTime now = DateTime.now(UTC);

  HistoryEntry historyEntry;
  HistoryEntry historyEntry2;
  DomainResource domain;
  BillingEvent.OneTime oneTime;
  BillingEvent.Recurring recurring;
  BillingEvent.Cancellation cancellationOneTime;
  BillingEvent.Cancellation cancellationRecurring;
  BillingEvent.Modification modification;

  @Before
  public void setUp() throws Exception {
    createTld("tld");
    domain = persistActiveDomain("foo.tld");
    historyEntry = persistResource(
        new HistoryEntry.Builder()
            .setParent(domain)
            .setModificationTime(now)
            .build());
    historyEntry2 = persistResource(
        new HistoryEntry.Builder()
        .setParent(domain)
        .setModificationTime(now.plusDays(1))
        .build());

    oneTime = persistResource(commonInit(
        new BillingEvent.OneTime.Builder()
            .setParent(historyEntry)
            .setReason(Reason.CREATE)
            .setFlags(ImmutableSet.of(BillingEvent.Flag.ANCHOR_TENANT))
            .setPeriodYears(2)
            .setCost(Money.of(USD, 1))
            .setEventTime(now)
            .setBillingTime(now.plusDays(5))));
    recurring = persistResource(commonInit(
        new BillingEvent.Recurring.Builder()
            .setParent(historyEntry)
            .setReason(Reason.AUTO_RENEW)
            .setEventTime(now.plusYears(1))
            .setRecurrenceEndTime(END_OF_TIME)));
    cancellationOneTime = persistResource(commonInit(
        new BillingEvent.Cancellation.Builder()
            .setParent(historyEntry2)
            .setReason(Reason.CREATE)
            .setEventTime(now.plusDays(1))
            .setBillingTime(now.plusDays(5))
            .setOneTimeEventRef(Ref.create(oneTime))));
    cancellationRecurring = persistResource(commonInit(
        new BillingEvent.Cancellation.Builder()
            .setParent(historyEntry2)
            .setReason(Reason.AUTO_RENEW)
            .setEventTime(now.plusDays(1))
            .setBillingTime(now.plusYears(1).plusDays(45))
            .setRecurringEventRef(Ref.create(recurring))));
    modification = persistResource(commonInit(
        new BillingEvent.Modification.Builder()
            .setParent(historyEntry2)
            .setReason(Reason.CREATE)
            .setCost(Money.of(USD, 1))
            .setDescription("Something happened")
            .setEventTime(now.plusDays(1))
            .setEventRef(Ref.create(oneTime))));
  }

  private <E extends BillingEvent, B extends BillingEvent.Builder<E, B>> E commonInit(B builder) {
    return builder
        .setClientId("a registrar")
        .setTargetId("foo.tld")
        .build();
  }

  @Test
  public void testPersistence() throws Exception {
    assertThat(ofy().load().entity(oneTime).now()).isEqualTo(oneTime);
    assertThat(ofy().load().entity(recurring).now()).isEqualTo(recurring);
    assertThat(ofy().load().entity(cancellationOneTime).now()).isEqualTo(cancellationOneTime);
    assertThat(ofy().load().entity(cancellationRecurring).now()).isEqualTo(cancellationRecurring);
    assertThat(ofy().load().entity(modification).now()).isEqualTo(modification);
  }

  @Test
  public void testParenting() throws Exception {
    // Note that these are all tested separately because BillingEvent is an abstract base class that
    // lacks the @Entity annotation, and thus we cannot call .type(BillingEvent.class)
    assertThat(ofy().load().type(BillingEvent.OneTime.class).ancestor(domain).list())
        .containsExactly(oneTime);
    assertThat(ofy().load().type(BillingEvent.Recurring.class).ancestor(domain).list())
        .containsExactly(recurring);
    assertThat(ofy().load().type(BillingEvent.Cancellation.class).ancestor(domain).list())
        .containsExactly(cancellationOneTime, cancellationRecurring);
    assertThat(ofy().load().type(BillingEvent.Modification.class).ancestor(domain).list())
        .containsExactly(modification);
    assertThat(ofy().load().type(BillingEvent.OneTime.class).ancestor(historyEntry).list())
        .containsExactly(oneTime);
    assertThat(ofy().load().type(BillingEvent.Recurring.class).ancestor(historyEntry).list())
        .containsExactly(recurring);
    assertThat(ofy().load().type(BillingEvent.Cancellation.class).ancestor(historyEntry2).list())
        .containsExactly(cancellationOneTime, cancellationRecurring);
    assertThat(ofy().load().type(BillingEvent.Modification.class).ancestor(historyEntry2).list())
        .containsExactly(modification);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(oneTime, "clientId", "eventTime", "billingTime");
    verifyIndexing(
        recurring, "clientId", "eventTime", "recurrenceEndTime", "recurrenceTimeOfYear.timeString");
    verifyIndexing(cancellationOneTime, "clientId", "eventTime", "billingTime");
    verifyIndexing(modification, "clientId", "eventTime");
  }

  @Test
  public void testSuccess_cancellation_forGracePeriod_withOneTime() {
    BillingEvent.Cancellation newCancellation = BillingEvent.Cancellation.forGracePeriod(
        GracePeriod.forBillingEvent(GracePeriodStatus.ADD, oneTime),
        historyEntry2,
        "foo.tld");
    // Set ID to be the same to ignore for the purposes of comparison.
    newCancellation = newCancellation.asBuilder().setId(cancellationOneTime.getId()).build();
    assertThat(newCancellation).isEqualTo(cancellationOneTime);
  }

  @Test
  public void testSuccess_cancellation_forGracePeriod_withRecurring() {
    BillingEvent.Cancellation newCancellation = BillingEvent.Cancellation.forGracePeriod(
        GracePeriod.createForRecurring(
            GracePeriodStatus.AUTO_RENEW,
            now.plusYears(1).plusDays(45),
            "a registrar",
            Ref.create(recurring)),
        historyEntry2,
        "foo.tld");
    // Set ID to be the same to ignore for the purposes of comparison.
    newCancellation = newCancellation.asBuilder().setId(cancellationRecurring.getId()).build();
    assertThat(newCancellation).isEqualTo(cancellationRecurring);
  }

  @Test
  public void testFailure_cancellation_forGracePeriodWithoutBillingEvent() {
    thrown.expect(IllegalArgumentException.class, "grace period without billing event");
    BillingEvent.Cancellation.forGracePeriod(
        GracePeriod.createWithoutBillingEvent(
            GracePeriodStatus.REDEMPTION,
            now.plusDays(1),
            "a registrar"),
        historyEntry,
        "foo.tld");
  }

  @Test
  public void testFailure_cancellationWithNoBillingEvent() {
    thrown.expect(IllegalStateException.class, "exactly one billing event");
    cancellationOneTime.asBuilder().setOneTimeEventRef(null).setRecurringEventRef(null).build();
  }

  @Test
  public void testFailure_cancellationWithBothBillingEvents() {
    thrown.expect(IllegalStateException.class, "exactly one billing event");
    cancellationOneTime.asBuilder()
        .setOneTimeEventRef(Ref.create(oneTime))
        .setRecurringEventRef(Ref.create(recurring))
        .build();
  }

  @Test
  public void testDeadCodeThatDeletedScrapCommandsReference() throws Exception {
    assertThat(recurring.getParentKey()).isEqualTo(Key.create(historyEntry));
    new BillingEvent.OneTime.Builder().setParent(Key.create(historyEntry));
  }
}
