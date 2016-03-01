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
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.EntityTestCase;
import com.google.domain.registry.model.billing.BillingEvent.Reason;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.reporting.HistoryEntry;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;

import org.joda.money.Money;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link BillingEvent}. */
public class BillingEventTest extends EntityTestCase {

  HistoryEntry historyEntry;
  DomainResource domain;
  BillingEvent oneTime;
  BillingEvent recurring;
  BillingEvent cancellation;
  BillingEvent modification;

  @Before
  public void setUp() throws Exception {
    createTld("tld");
    domain = persistActiveDomain("ratt.tld");
    historyEntry = persistResource(new HistoryEntry.Builder().setParent(domain).build());
    oneTime = persistResource(commonInit(
        new BillingEvent.OneTime.Builder()
            .setReason(Reason.CREATE)
            .setPeriodYears(2)
            .setCost(Money.of(USD, 1))
            .setBillingTime(START_OF_TIME)));
    recurring = persistResource(commonInit(
        new BillingEvent.Recurring.Builder()
            .setReason(Reason.AUTO_RENEW)
            .setRecurrenceEndTime(START_OF_TIME)));
    cancellation = persistResource(commonInit(
        new BillingEvent.Cancellation.Builder()
            .setReason(Reason.CREATE)
            .setBillingTime(START_OF_TIME)
            .setEventRef(Ref.create(oneTime))));
    modification = persistResource(commonInit(
        new BillingEvent.Modification.Builder()
            .setReason(Reason.CREATE)
            .setCost(Money.of(USD, 1))
            .setDescription("Something happened")
            .setEventRef(Ref.create((BillingEvent.OneTime) oneTime))));
  }

  private BillingEvent commonInit(BillingEvent.Builder<?, ?> builder) {
    return builder
        .setClientId("a registrar")
        .setFlags(ImmutableSet.of(BillingEvent.Flag.ANCHOR_TENANT))
        .setEventTime(START_OF_TIME)
        .setTargetId("foo")
        .setParent(historyEntry)
        .build();
  }

  @Test
  public void testPersistence() throws Exception {
    assertThat(ofy().load().entity(oneTime).now()).isEqualTo(oneTime);
    assertThat(ofy().load().entity(recurring).now()).isEqualTo(recurring);
    assertThat(ofy().load().entity(cancellation).now()).isEqualTo(cancellation);
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
        .containsExactly(cancellation);
    assertThat(ofy().load().type(BillingEvent.Modification.class).ancestor(domain).list())
        .containsExactly(modification);
    assertThat(ofy().load().type(BillingEvent.OneTime.class).ancestor(historyEntry).list())
        .containsExactly(oneTime);
    assertThat(ofy().load().type(BillingEvent.Recurring.class).ancestor(historyEntry).list())
        .containsExactly(recurring);
    assertThat(ofy().load().type(BillingEvent.Cancellation.class).ancestor(historyEntry).list())
        .containsExactly(cancellation);
    assertThat(ofy().load().type(BillingEvent.Modification.class).ancestor(historyEntry).list())
        .containsExactly(modification);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(oneTime, "clientId", "eventTime", "billingTime");
    verifyIndexing(
        recurring, "clientId", "eventTime", "recurrenceEndTime", "recurrenceTimeOfYear.timeString");
    verifyIndexing(cancellation, "clientId", "eventTime", "billingTime");
    verifyIndexing(modification, "clientId", "eventTime");
  }

  @Test
  public void testDeadCodeThatDeletedScrapCommandsReference() throws Exception {
    assertThat(recurring.getParentKey()).isEqualTo(Key.create(historyEntry));
    new BillingEvent.OneTime.Builder().setParent(Key.create(historyEntry));
  }
}
