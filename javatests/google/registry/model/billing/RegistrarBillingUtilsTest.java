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
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static java.util.Arrays.asList;
import static org.joda.money.CurrencyUnit.JPY;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableSortedMap;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import java.util.Map;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RegistrarBillingUtils}. */
@RunWith(JUnit4.class)
public final class RegistrarBillingUtilsTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Rule
  public InjectRule inject = new InjectRule();

  private final FakeClock clock = new FakeClock(DateTime.parse("1984-12-18TZ"));
  private Registrar registrar;

  @Before
  public void before() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    registrar = Registrar.loadByClientId("NewRegistrar");
    createTlds("xn--q9jyb4c", "com", "net");
    persistResource(
        Registry.get("xn--q9jyb4c").asBuilder()
            .setCurrency(JPY)
            .setRenewBillingCostTransitions(
                ImmutableSortedMap.of(START_OF_TIME, Money.parse("JPY 110")))
            .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.parse("JPY 0")))
            .setCreateBillingCost(Money.parse("JPY 130"))
            .setRestoreBillingCost(Money.parse("JPY 170"))
            .setServerStatusChangeBillingCost(Money.parse("JPY 190"))
            .build());
  }

  @Test
  public void testGetCurrencies_returnsAllCurrenciesEnabledOnRegistrySorted() {
    assertThat(RegistrarBillingUtils.getCurrencies())
        .containsExactly(JPY, USD)
        .inOrder();
  }

  @Test
  public void testLoadBalance_noHistory_returnsZeroes() {
    Map<CurrencyUnit, Money> balance = RegistrarBillingUtils.loadBalance(registrar);
    assertThat(balance).hasSize(2);
    assertThat(balance).containsEntry(USD, Money.parse("USD 0.00"));
    assertThat(balance).containsEntry(JPY, Money.parse("JPY 0"));
  }

  @Test
  public void testLoadBalance_oneCurrency_hasTwoEntriesWithSumAndZero() {
    RegistrarBillingEntry entry1 = new RegistrarBillingEntry.Builder()
        .setPrevious(null)
        .setParent(registrar)
        .setCreated(clock.nowUtc())
        .setDescription("USD Invoice for July")
        .setAmount(Money.parse("USD 10.00"))
        .build();
    clock.advanceBy(Duration.standardDays(30));
    RegistrarBillingEntry entry2 = new RegistrarBillingEntry.Builder()
        .setPrevious(entry1)
        .setParent(registrar)
        .setCreated(clock.nowUtc())
        .setDescription("USD Invoice for August")
        .setAmount(Money.parse("USD 23.00"))
        .build();
    persistSimpleResources(asList(entry1, entry2));
    Map<CurrencyUnit, Money> balance = RegistrarBillingUtils.loadBalance(registrar);
    assertThat(balance).hasSize(2);
    assertThat(balance).containsEntry(USD, Money.parse("USD 33.00"));
    assertThat(balance).containsEntry(JPY, Money.parse("JPY 0"));
  }

  @Test
  public void testLoadBalance_twoCurrencies_hasTwoEntriesWithSum() {
    RegistrarBillingEntry entry1 = new RegistrarBillingEntry.Builder()
        .setPrevious(null)
        .setParent(registrar)
        .setCreated(clock.nowUtc())
        .setDescription("USD Invoice for July")
        .setAmount(Money.parse("USD 10.00"))
        .build();
    clock.advanceBy(Duration.standardDays(30));
    RegistrarBillingEntry entry2 = new RegistrarBillingEntry.Builder()
        .setPrevious(entry1)
        .setParent(registrar)
        .setCreated(clock.nowUtc())
        .setDescription("USD Invoice for August")
        .setAmount(Money.parse("USD 3.50"))
        .build();
    RegistrarBillingEntry entry3 = new RegistrarBillingEntry.Builder()
        .setPrevious(null)
        .setParent(registrar)
        .setCreated(clock.nowUtc())
        .setDescription("JPY Invoice for August")
        .setAmount(Money.parse("JPY 666"))
        .build();
    persistSimpleResources(asList(entry1, entry2, entry3));
    Map<CurrencyUnit, Money> balance = RegistrarBillingUtils.loadBalance(registrar);
    assertThat(balance).hasSize(2);
    assertThat(balance).containsEntry(USD, Money.parse("USD 13.50"));
    assertThat(balance).containsEntry(JPY, Money.parse("JPY 666"));
  }
}
