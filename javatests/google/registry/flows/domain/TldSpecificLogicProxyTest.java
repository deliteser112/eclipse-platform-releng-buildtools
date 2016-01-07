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

package google.registry.flows.domain;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Range;
import com.googlecode.objectify.Key;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.TestExtraLogicManager;
import google.registry.model.domain.fee.BaseFee.FeeType;
import google.registry.model.domain.fee.Fee;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.ofy.Ofy;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.pricing.PricingEngineProxy;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.testing.ShardableTestCase;
import java.math.BigDecimal;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TldSpecificLogicProxyTest extends ShardableTestCase {

  @Rule public final InjectRule inject = new InjectRule();

  @Rule public final ExceptionRule thrown = new ExceptionRule();

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  final FakeClock clock = new FakeClock(DateTime.parse("2010-01-01T10:00:00Z"));

  Money basicCreateCost;

  @Before
  public void before() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    createTlds("tld", "eap", "test");
    RegistryExtraFlowLogicProxy.setOverride("test", TestExtraLogicManager.class);
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
  public void test_basicCreatePrice() throws Exception {
    TldSpecificLogicProxy.EppCommandOperations createPrice =
        TldSpecificLogicProxy.getCreatePrice(
            Registry.get("tld"), "example.tld", "clientIdentifer", clock.nowUtc(), 1, null);
    assertThat(createPrice.getTotalCost()).isEqualTo(basicCreateCost);
    assertThat(createPrice.getFees()).hasSize(1);
  }

  @Test
  public void test_eap() throws Exception {
    TldSpecificLogicProxy.EppCommandOperations createPrice =
        TldSpecificLogicProxy.getCreatePrice(
            Registry.get("eap"), "example.eap", "clientId", clock.nowUtc(), 1, null);
    Range<DateTime> eapValidPeriod =
        Range.closedOpen(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1));
    assertThat(createPrice.getTotalCost()).isEqualTo(basicCreateCost.plus(Money.of(USD, 100)));
    assertThat(createPrice.getCurrency()).isEqualTo(USD);
    assertThat(createPrice.getFees().get(0))
        .isEqualTo(Fee.create(basicCreateCost.getAmount(), FeeType.CREATE));
    assertThat(createPrice.getFees().get(1))
        .isEqualTo(
            Fee.create(
                new BigDecimal("100.00"),
                FeeType.EAP,
                eapValidPeriod,
                clock.nowUtc().plusDays(1)));
    assertThat(createPrice.getFees())
        .containsExactly(
            Fee.create(basicCreateCost.getAmount(), FeeType.CREATE),
            Fee.create(
                new BigDecimal("100.00"),
                FeeType.EAP,
                eapValidPeriod,
                clock.nowUtc().plusDays(1)))
        .inOrder();
  }

  @Test
  public void test_extraLogic_createPrice() throws Exception {
    TldSpecificLogicProxy.EppCommandOperations price =
        TldSpecificLogicProxy.getCreatePrice(
            Registry.get("test"), "create-54.test", "clientId", clock.nowUtc(), 3, null);
    assertThat(price.getCurrency()).isEqualTo(CurrencyUnit.USD);
    assertThat(price.getFees()).hasSize(1);
    assertThat(price.getFees().get(0).getCost()).isEqualTo(new BigDecimal(54));
    assertThat(price.getFees().get(0).getDescription()).isEqualTo("create");
    assertThat(price.getCredits()).isEmpty();
  }

  @Test
  public void test_extraLogic_renewPrice() throws Exception {
    persistActiveDomain("renew--13.test");
    TldSpecificLogicProxy.EppCommandOperations price =
        TldSpecificLogicProxy.getRenewPrice(
            Registry.get("test"), "renew--13.test", "clientId", clock.nowUtc(), 1, null);
    assertThat(price.getCurrency()).isEqualTo(CurrencyUnit.USD);
    assertThat(price.getFees()).isEmpty();
    assertThat(price.getCredits()).hasSize(1);
    assertThat(price.getCredits().get(0).getCost()).isEqualTo(new BigDecimal(-13));
    assertThat(price.getCredits().get(0).getDescription()).isEqualTo("renew");
  }

  @Test
  public void test_extraLogic_renewPrice_noDomain() throws Exception {
    thrown.expect(ResourceDoesNotExistException.class);
    TldSpecificLogicProxy.getRenewPrice(
        Registry.get("test"), "renew--13.test", "clientId", clock.nowUtc(), 1, null);
  }

  void persistPendingDeleteDomain(String domainName, DateTime now) throws Exception {
    DomainResource domain = newDomainResource(domainName);
    HistoryEntry historyEntry =
        persistResource(
            new HistoryEntry.Builder()
                .setType(HistoryEntry.Type.DOMAIN_DELETE)
                .setParent(domain)
                .build());
    domain =
        persistResource(
            domain
                .asBuilder()
                .setRegistrationExpirationTime(now.plusYears(5).plusDays(45))
                .setDeletionTime(now.plusDays(35))
                .addGracePeriod(
                    GracePeriod.create(GracePeriodStatus.REDEMPTION, now.plusDays(1), "foo", null))
                .setStatusValues(ImmutableSet.of(StatusValue.PENDING_DELETE))
                .setDeletePollMessage(
                    Key.create(
                        persistResource(
                            new PollMessage.OneTime.Builder()
                                .setClientId("TheRegistrar")
                                .setEventTime(now.plusDays(5))
                                .setParent(historyEntry)
                                .build())))
                .build());
    clock.advanceOneMilli();
  }

  @Test
  public void test_extraLogic_restorePrice() throws Exception {
    persistPendingDeleteDomain("renew-13.test", clock.nowUtc());
    TldSpecificLogicProxy.EppCommandOperations price =
        TldSpecificLogicProxy.getRestorePrice(
            Registry.get("test"), "renew-13.test", "clientId", clock.nowUtc(), null);
    assertThat(price.getCurrency()).isEqualTo(CurrencyUnit.USD);
    assertThat(price.getFees()).hasSize(2);
    assertThat(price.getFees().get(0).getCost()).isEqualTo(new BigDecimal(13));
    assertThat(price.getFees().get(0).getDescription()).isEqualTo("renew");
    assertThat(price.getFees().get(1).getDescription()).isEqualTo("restore");
    assertThat(price.getCredits()).isEmpty();
  }

  @Test
  public void test_extraLogic_restorePrice_noDomain() throws Exception {
    thrown.expect(ResourceDoesNotExistException.class);
    TldSpecificLogicProxy.getRestorePrice(
        Registry.get("test"), "renew-13.test", "clientId", clock.nowUtc(), null);
  }

  @Test
  public void test_extraLogic_transferPrice() throws Exception {
    persistActiveDomain("renew-26.test");
    TldSpecificLogicProxy.EppCommandOperations price =
        TldSpecificLogicProxy.getTransferPrice(
            Registry.get("test"), "renew-26.test", "clientId", clock.nowUtc(), 2, null);
    assertThat(price.getCurrency()).isEqualTo(CurrencyUnit.USD);
    assertThat(price.getFees()).hasSize(1);
    assertThat(price.getFees().get(0).getCost()).isEqualTo(new BigDecimal(26));
    assertThat(price.getFees().get(0).getDescription()).isEqualTo("renew");
    assertThat(price.getCredits()).isEmpty();
  }

  @Test
  public void test_extraLogic_updatePrice() throws Exception {
    persistActiveDomain("update-13.test");
    TldSpecificLogicProxy.EppCommandOperations price =
        TldSpecificLogicProxy.getUpdatePrice(
            Registry.get("test"), "update-13.test", "clientId", clock.nowUtc(), null);
    assertThat(price.getCurrency()).isEqualTo(CurrencyUnit.USD);
    assertThat(price.getFees()).hasSize(1);
    assertThat(price.getFees().get(0).getCost()).isEqualTo(new BigDecimal(13));
    assertThat(price.getFees().get(0).getDescription()).isEqualTo("update");
    assertThat(price.getCredits()).isEmpty();
  }
}
