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

package google.registry.model.billing;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.collect.ImmutableMap;
import google.registry.model.EntityTestCase;
import google.registry.model.billing.RegistrarCredit.CreditType;
import google.registry.model.billing.RegistrarCreditBalance.BalanceMap;
import google.registry.model.registrar.Registrar;
import google.registry.testing.ExceptionRule;
import java.util.Map;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link RegistrarCreditBalance}. */
public class RegistrarCreditBalanceTest extends EntityTestCase {

  @Rule
  public ExceptionRule thrown = new ExceptionRule();

  private DateTime then = clock.nowUtc().plusDays(1);

  private Registrar theRegistrar;
  private RegistrarCredit unpersistedCredit;
  private RegistrarCredit credit;
  private RegistrarCreditBalance balance;

  Map<DateTime, ? extends Map<DateTime, Money>> rawBalanceMap;

  @Before
  public void setUp() throws Exception {
    createTld("tld");
    theRegistrar = Registrar.loadByClientId("TheRegistrar");
    unpersistedCredit = makeCredit(theRegistrar, clock.nowUtc());
    credit = persistResource(makeCredit(theRegistrar, clock.nowUtc()));
    balance = persistResource(
        makeBalance(credit, Money.parse("USD 90"), clock.nowUtc(), clock.nowUtc()));
    persistResource(
        makeBalance(credit, Money.parse("USD 80"), clock.nowUtc(), clock.nowUtc().plusMillis(1)));
    persistResource(
        makeBalance(credit, Money.parse("USD 70"), clock.nowUtc(), clock.nowUtc().plusMillis(2)));
    persistResource(
        makeBalance(credit, Money.parse("USD 40"), then, then));
    persistResource(
        makeBalance(credit, Money.parse("USD 30"), then, then.plusMillis(1)));
    persistResource(
        makeBalance(credit, Money.parse("USD 20"), then, then.plusMillis(2)));
    rawBalanceMap = ImmutableMap.of(
          clock.nowUtc(),
          ImmutableMap.of(
              clock.nowUtc(), Money.parse("USD 90"),
              clock.nowUtc().plusMillis(1), Money.parse("USD 80"),
              clock.nowUtc().plusMillis(2), Money.parse("USD 70")),
          then,
          ImmutableMap.of(
              then, Money.parse("USD 40"),
              then.plusMillis(1), Money.parse("USD 30"),
              then.plusMillis(2), Money.parse("USD 20")));
  }

  @Test
  public void testPersistence() throws Exception {
    assertThat(ofy().load().entity(balance).now()).isEqualTo(balance);
  }

  @Test
  public void testIndexing() throws Exception {
    // No indexing needed, so we don't expect any indices.
    verifyIndexing(balance);
  }
  @Test
  public void testSuccess_balanceWithUnpersistedCredit() throws Exception {
    balance.asBuilder().setParent(unpersistedCredit).build();
  }

  @Test
  public void testFailure_balanceNotInCreditCurrency() throws Exception {
    thrown.expect(IllegalStateException.class);
    balance.asBuilder()
        .setAmount(Money.parse("JPY 1"))
        .build();
  }

  @Test
  public void testFailure_balanceNotInCreditCurrencyWithUnpersistedCredit() throws Exception {
    thrown.expect(IllegalStateException.class);
    balance.asBuilder()
        .setParent(unpersistedCredit)
        .setAmount(Money.parse("JPY 1"))
        .build();
  }

  @Test
  public void testSuccess_balanceMap_createForCredit() throws Exception {
    assertThat(BalanceMap.createForCredit(credit)).isEqualTo(rawBalanceMap);
  }

  @Test
  public void testSuccess_balanceMap_createForEmptyCredit() throws Exception {
    assertThat(BalanceMap.createForCredit(makeCredit(theRegistrar, clock.nowUtc()))).isEmpty();
  }

  @Test
  public void testSuccess_balanceMap_getActiveBalance_emptyMap() throws Exception {
    BalanceMap map = new BalanceMap(ImmutableMap.<DateTime, Map<DateTime, Money>>of());
    assertThat(map.getActiveBalanceAtTime(START_OF_TIME)).isAbsent();
    assertThat(map.getActiveBalanceAtTime(clock.nowUtc())).isAbsent();
    assertThat(map.getActiveBalanceAtTime(END_OF_TIME)).isAbsent();
    assertThat(map.getActiveBalanceBeforeTime(START_OF_TIME)).isAbsent();
    assertThat(map.getActiveBalanceBeforeTime(clock.nowUtc())).isAbsent();
    assertThat(map.getActiveBalanceBeforeTime(END_OF_TIME)).isAbsent();
  }

  @Test
  public void testSuccess_balanceMap_getActiveBalanceAtTime() throws Exception {
    BalanceMap map = new BalanceMap(rawBalanceMap);
    assertThat(map.getActiveBalanceAtTime(START_OF_TIME)).isAbsent();
    assertThat(map.getActiveBalanceAtTime(clock.nowUtc().minusMillis(1))).isAbsent();
    assertThat(map.getActiveBalanceAtTime(clock.nowUtc()).get()).isEqualTo(Money.parse("USD 70"));
    assertThat(map.getActiveBalanceAtTime(clock.nowUtc().plusMillis(1)).get())
        .isEqualTo(Money.parse("USD 70"));
    assertThat(map.getActiveBalanceAtTime(then.minusMillis(1)).get())
        .isEqualTo(Money.parse("USD 70"));
    assertThat(map.getActiveBalanceAtTime(then).get()).isEqualTo(Money.parse("USD 20"));
    assertThat(map.getActiveBalanceAtTime(then.plusMillis(1)).get())
        .isEqualTo(Money.parse("USD 20"));
    assertThat(map.getActiveBalanceAtTime(END_OF_TIME).get()).isEqualTo(Money.parse("USD 20"));
  }

  @Test
  public void testSuccess_balanceMap_getActiveBalanceBeforeTime() throws Exception {
    BalanceMap map = new BalanceMap(rawBalanceMap);
    assertThat(map.getActiveBalanceBeforeTime(START_OF_TIME)).isAbsent();
    assertThat(map.getActiveBalanceBeforeTime(clock.nowUtc().minusMillis(1))).isAbsent();
    assertThat(map.getActiveBalanceBeforeTime(clock.nowUtc())).isAbsent();
    assertThat(map.getActiveBalanceBeforeTime(clock.nowUtc().plusMillis(1)).get())
        .isEqualTo(Money.parse("USD 70"));
    assertThat(map.getActiveBalanceBeforeTime(then.minusMillis(1)).get())
        .isEqualTo(Money.parse("USD 70"));
    assertThat(map.getActiveBalanceBeforeTime(then).get()).isEqualTo(Money.parse("USD 70"));
    assertThat(map.getActiveBalanceBeforeTime(then.plusMillis(1)).get())
        .isEqualTo(Money.parse("USD 20"));
    assertThat(map.getActiveBalanceBeforeTime(END_OF_TIME).get()).isEqualTo(Money.parse("USD 20"));
  }

  private static RegistrarCredit makeCredit(Registrar parent, DateTime creationTime) {
    return new RegistrarCredit.Builder()
        .setParent(parent)
        .setType(CreditType.PROMOTION)
        .setCurrency(CurrencyUnit.USD)
        .setTld("tld")
        .setCreationTime(creationTime)
        .build();
  }

  private static RegistrarCreditBalance makeBalance(
      RegistrarCredit parent, Money amount, DateTime effectiveTime, DateTime writtenTime) {
    return new RegistrarCreditBalance.Builder()
        .setParent(parent)
        .setEffectiveTime(effectiveTime)
        .setAmount(amount)
        .setWrittenTime(writtenTime)
        .build();
  }
}
