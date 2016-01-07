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

package google.registry.model.transfer;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainResource;
import google.registry.model.poll.PollMessage;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferData.TransferServerApproveEntity;
import google.registry.testing.AppEngineRule;
import google.registry.testing.DatastoreHelper;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TransferData}. */
@RunWith(JUnit4.class)
public class TransferDataTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  protected final DateTime now = DateTime.now(UTC);

  HistoryEntry historyEntry;
  TransferData transferData;
  BillingEvent.OneTime transferBillingEvent;
  BillingEvent.OneTime nonTransferBillingEvent;
  BillingEvent.OneTime otherTransferBillingEvent;
  BillingEvent.Recurring recurringBillingEvent;

  @Before
  public void setUp() {
    createTld("tld");
    DomainResource domain = DatastoreHelper.persistActiveDomain("tat.tld");
    historyEntry = persistResource(new HistoryEntry.Builder().setParent(domain).build());
    transferBillingEvent = persistResource(makeBillingEvent());

    nonTransferBillingEvent = persistResource(
        makeBillingEvent().asBuilder().setReason(Reason.CREATE).build());

    otherTransferBillingEvent = persistResource(
        makeBillingEvent().asBuilder().setCost(Money.of(USD, 33)).build());

    recurringBillingEvent = persistResource(
        new BillingEvent.Recurring.Builder()
            .setReason(Reason.RENEW)
            .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
            .setClientId("TheRegistrar")
            .setTargetId("foo.tld")
            .setEventTime(now)
            .setRecurrenceEndTime(END_OF_TIME)
            .setParent(historyEntry)
            .build());
  }

  private BillingEvent.OneTime makeBillingEvent() {
    return new BillingEvent.OneTime.Builder()
        .setReason(Reason.TRANSFER)
        .setClientId("TheRegistrar")
        .setTargetId("foo.tld")
        .setEventTime(now)
        .setBillingTime(now.plusDays(5))
        .setCost(Money.of(USD, 42))
        .setPeriodYears(3)
        .setParent(historyEntry)
        .build();
  }

  @SafeVarargs
  private static TransferData makeTransferDataWithEntities(
      Key<? extends TransferServerApproveEntity>... entityKeys) {
    ImmutableSet<Key<? extends TransferServerApproveEntity>> entityKeysSet =
        ImmutableSet.copyOf(entityKeys);
    return new TransferData.Builder().setServerApproveEntities(entityKeysSet).build();
  }

  @Test
  public void testSuccess_FindBillingEventNoEntities() throws Exception {
    transferData = makeTransferDataWithEntities();
    assertThat(transferData.serverApproveBillingEvent).isNull();
    assertThat(transferData.getServerApproveBillingEvent()).isNull();
  }

  @Test
  public void testSuccess_FindBillingEventOtherEntities() throws Exception {
    transferData = makeTransferDataWithEntities(
        Key.create(nonTransferBillingEvent),
        Key.create(recurringBillingEvent),
        Key.create(PollMessage.OneTime.class, 1));
    assertThat(transferData.serverApproveBillingEvent).isNull();
    assertThat(transferData.getServerApproveBillingEvent()).isNull();
  }

  @Test
  public void testSuccess_GetStoredBillingEventNoEntities() throws Exception {
    transferData = new TransferData.Builder()
        .setServerApproveBillingEvent(Key.create(transferBillingEvent))
        .build();
    assertThat(ofy().load().key(transferData.serverApproveBillingEvent).now())
        .isEqualTo(transferBillingEvent);
    assertThat(ofy().load().key(transferData.getServerApproveBillingEvent()).now())
        .isEqualTo(transferBillingEvent);
  }

  @Test
  public void testSuccess_GetStoredBillingEventMultipleEntities() throws Exception {
    transferData = makeTransferDataWithEntities(
        Key.create(otherTransferBillingEvent),
        Key.create(nonTransferBillingEvent),
        Key.create(recurringBillingEvent),
        Key.create(PollMessage.OneTime.class, 1));
    transferData = transferData.asBuilder()
        .setServerApproveBillingEvent(Key.create(transferBillingEvent))
        .build();
    assertThat(ofy().load().key(transferData.serverApproveBillingEvent).now())
        .isEqualTo(transferBillingEvent);
    assertThat(ofy().load().key(transferData.getServerApproveBillingEvent()).now())
        .isEqualTo(transferBillingEvent);
  }
}
