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

package google.registry.pricing;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import google.registry.model.domain.fee.Fee;
import google.registry.model.ofy.Ofy;
import google.registry.model.registry.Registry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TldSpecificLogicProxyTest {

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  final FakeClock clock = new FakeClock(DateTime.parse("2010-01-01T10:00:00Z"));

  Money basicCreateCost;

  @Before
  public void before() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    createTld("tld");

    createTld("eap");
    DateTime a = clock.nowUtc().minusDays(1);
    DateTime b = clock.nowUtc().plusDays(1);
    persistResource(
        Registry.get("eap")
            .asBuilder()
            .setEapFeeSchedule(
                ImmutableSortedMap.of(
                    START_OF_TIME, Money.of(USD, 0),
                    a, Money.of(USD, 100),
                    b, Money.of(USD, 50)))
            .build());

    basicCreateCost =
        PricingEngineProxy.getPricesForDomainName("example.tld", clock.nowUtc()).getCreateCost();
  }

  @Test
  public void testTldSpecificLogicEngine() {
    TldSpecificLogicProxy.EppCommandOperations createPrice =
        TldSpecificLogicProxy.getCreatePrice(
            Registry.get("tld"), "example.tld", clock.nowUtc(), 1);
    assertThat(createPrice.getTotalCost()).isEqualTo(basicCreateCost);
    assertThat(createPrice.getFees()).hasSize(1);
  }

  @Test
  public void testTldSpecificLogicEngineEap() {
    TldSpecificLogicProxy.EppCommandOperations createPrice =
        TldSpecificLogicProxy.getCreatePrice(
            Registry.get("eap"), "example.eap", clock.nowUtc(), 1);
    assertThat(createPrice.getTotalCost()).isEqualTo(basicCreateCost.plus(Money.of(USD, 100)));
    assertThat(createPrice.getCurrency()).isEqualTo(USD);
    assertThat(createPrice.getFees().get(0))
        .isEqualTo(Fee.create(basicCreateCost.getAmount(), "create"));
    assertThat(createPrice.getFees().get(1))
        .isEqualTo(
            Fee.create(
                Money.of(USD, 100).getAmount(),
                "Early Access Period, fee expires: " + clock.nowUtc().plusDays(1)));
    assertThat(createPrice.getFees())
        .isEqualTo(
            ImmutableList.of(
                Fee.create(basicCreateCost.getAmount(), "create"),
                Fee.create(
                    Money.of(USD, 100).getAmount(),
                    "Early Access Period, fee expires: " + clock.nowUtc().plusDays(1))));
  }
}
