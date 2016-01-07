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

package google.registry.model.billing;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableMap;
import com.googlecode.objectify.Key;
import google.registry.model.EntityTestCase;
import google.registry.model.registrar.Registrar;
import google.registry.testing.ExceptionRule;
import org.joda.money.CurrencyMismatchException;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RegistrarBillingEntry}. */
@RunWith(JUnit4.class)
public final class RegistrarBillingEntryTest extends EntityTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(
        persistResource(
            new RegistrarBillingEntry.Builder()
                .setPrevious(null)
                .setParent(Registrar.loadByClientId("NewRegistrar"))
                .setCreated(DateTime.parse("1984-12-18TZ"))
                .setTransactionId("goblin-market")
                .setDescription("USD Invoice for December 1984")
                .setAmount(Money.parse("USD 10.00"))
                .build()),
        "currency",
        "created");
  }

  @Test
  public void testGetters() throws Exception {
    RegistrarBillingEntry entry =
        new RegistrarBillingEntry.Builder()
            .setPrevious(null)
            .setParent(Registrar.loadByClientId("NewRegistrar"))
            .setCreated(DateTime.parse("1984-12-18TZ"))
            .setTransactionId("goblin-market")
            .setDescription("USD Invoice for December 1984")
            .setAmount(Money.parse("USD 10.00"))
            .build();
    assertThat(entry.getId()).isEqualTo(1L);
    assertThat(entry.getParent()).isEqualTo(Key.create(Registrar.loadByClientId("NewRegistrar")));
    assertThat(entry.getCreated()).isEqualTo(DateTime.parse("1984-12-18TZ"));
    assertThat(entry.getTransactionId()).isEqualTo("goblin-market");
    assertThat(entry.getDescription()).isEqualTo("USD Invoice for December 1984");
    assertThat(entry.getAmount()).isEqualTo(Money.parse("USD 10.00"));
    assertThat(entry.getBalance()).isEqualTo(Money.parse("USD 10.00"));
  }

  @Test
  public void testToJsonMap() throws Exception {
    assertThat(
            new RegistrarBillingEntry.Builder()
                .setPrevious(null)
                .setParent(Registrar.loadByClientId("NewRegistrar"))
                .setCreated(DateTime.parse("1984-12-18TZ"))
                .setTransactionId("goblin-market")
                .setDescription("USD Invoice for December 1984")
                .setAmount(Money.parse("USD 10.00"))
                .build()
                .toJsonMap())
        .isEqualTo(
            new ImmutableMap.Builder<String, Object>()
                .put("id", 1L)
                .put("transactionId", "goblin-market")
                .put("created", "1984-12-18T00:00:00.000Z")
                .put("description", "USD Invoice for December 1984")
                .put("currency", "USD")
                .put("amount", "10.00")
                .put("balance", "10.00")
                .build());
  }

  @Test
  public void testBadTimeOrdering_causesError() throws Exception {
    thrown.expect(IllegalStateException.class, "Created timestamp not after previous");
    new RegistrarBillingEntry.Builder()
        .setPrevious(
            new RegistrarBillingEntry.Builder()
                .setPrevious(null)
                .setParent(Registrar.loadByClientId("NewRegistrar"))
                .setCreated(DateTime.parse("1984-12-18TZ"))
                .setDescription("USD Invoice for December")
                .setAmount(Money.parse("USD 10.00"))
                .build())
        .setParent(Registrar.loadByClientId("NewRegistrar"))
        .setCreated(DateTime.parse("1984-12-17TZ"))
        .setTransactionId("goblin")
        .setDescription("USD Invoice for August")
        .setAmount(Money.parse("USD 3.50"))
        .build();
  }

  @Test
  public void testRegistrarMismatch_causesError() throws Exception {
    thrown.expect(IllegalStateException.class, "Parent not same as previous");
    new RegistrarBillingEntry.Builder()
        .setPrevious(
            new RegistrarBillingEntry.Builder()
                .setPrevious(null)
                .setParent(Registrar.loadByClientId("NewRegistrar"))
                .setCreated(DateTime.parse("1984-12-18TZ"))
                .setDescription("USD Invoice for December")
                .setAmount(Money.parse("USD 10.00"))
                .build())
        .setParent(Registrar.loadByClientId("TheRegistrar"))
        .setCreated(DateTime.parse("1984-12-17TZ"))
        .setTransactionId("goblin")
        .setDescription("USD Invoice for August")
        .setAmount(Money.parse("USD 3.50"))
        .build();
  }

  @Test
  public void testCurrencyMismatch_causesError() throws Exception {
    thrown.expect(CurrencyMismatchException.class);
    new RegistrarBillingEntry.Builder()
        .setPrevious(
            new RegistrarBillingEntry.Builder()
                .setPrevious(null)
                .setParent(Registrar.loadByClientId("NewRegistrar"))
                .setCreated(DateTime.parse("1984-12-18TZ"))
                .setDescription("USD Invoice for December")
                .setAmount(Money.parse("USD 10.00"))
                .build())
        .setParent(Registrar.loadByClientId("NewRegistrar"))
        .setCreated(DateTime.parse("1984-12-17TZ"))
        .setTransactionId("goblin")
        .setDescription("JPY Invoice for August")
        .setAmount(Money.parse("JPY 350"))
        .build();
  }

  @Test
  public void testZeroAmount_causesError() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Amount can't be zero");
    new RegistrarBillingEntry.Builder()
        .setPrevious(null)
        .setParent(Registrar.loadByClientId("NewRegistrar"))
        .setCreated(DateTime.parse("1984-12-18TZ"))
        .setDescription("USD Invoice for December")
        .setAmount(Money.zero(USD))
        .build();
  }

  @Test
  public void testEmptyTransactionId_becomeNull() {
    assertThat(
        new RegistrarBillingEntry.Builder()
            .setPrevious(null)
            .setParent(Registrar.loadByClientId("NewRegistrar"))
            .setTransactionId("")
            .setCreated(DateTime.parse("1984-12-18TZ"))
            .setDescription("USD Invoice for December 1984")
            .setAmount(Money.parse("USD 10.00"))
            .build()
            .getTransactionId())
        .isNull();
  }
}
