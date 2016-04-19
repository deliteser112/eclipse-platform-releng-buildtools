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

package com.google.domain.registry.model.registry;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.domain.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.model.registry.label.ReservedListTest.GET_NAME_FUNCTION;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistPremiumList;
import static com.google.domain.registry.testing.DatastoreHelper.persistReservedList;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.EUR;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.domain.registry.model.EntityTestCase;
import com.google.domain.registry.model.registry.Registry.RegistryNotFoundException;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.model.registry.label.PremiumList;
import com.google.domain.registry.model.registry.label.ReservedList;
import com.google.domain.registry.testing.ExceptionRule;

import com.googlecode.objectify.Key;

import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link Registry}. */
public class RegistryTest extends EntityTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  Registry registry;

  @Before
  public void setup() throws Exception {
    createTld("tld");
    registry = Registry.get("tld");
  }

  @Test
  public void testPersistence() throws Exception {
    assertWithMessage("Registry not found").that(Registry.get("tld")).isNotNull();
    assertThat(ofy().load().type(Registry.class).parent(getCrossTldKey()).id("tld").now())
        .isEqualTo(Registry.get("tld"));
  }

  @Test
  public void testFailure_registryNotFound() throws Exception {
    createTld("foo");
    thrown.expect(RegistryNotFoundException.class);
    Registry.get("baz");
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(Registry.get("tld"));
  }

  @Test
  public void testSettingEscrowEnabled_null() {
    assertThat(Registry.get("tld").asBuilder().setEscrowEnabled(true).build().getEscrowEnabled())
        .isTrue();
    assertThat(Registry.get("tld").asBuilder().setEscrowEnabled(false).build().getEscrowEnabled())
        .isFalse();
  }

  @Test
  public void testSettingCreateBillingCost() {
    Registry registry =
        Registry.get("tld").asBuilder().setCreateBillingCost(Money.of(USD, 42)).build();
    assertThat(registry.getStandardCreateCost()).isEqualTo(Money.of(USD, 42));
    // The default value of 17 is set in createTld().
    assertThat(registry.getStandardRestoreCost()).isEqualTo(Money.of(USD, 17));
  }

  @Test
  public void testSettingRestoreBillingCost() {
    Registry registry =
        Registry.get("tld").asBuilder().setRestoreBillingCost(Money.of(USD, 42)).build();
    // The default value of 13 is set in createTld().
    assertThat(registry.getStandardCreateCost()).isEqualTo(Money.of(USD, 13));
    assertThat(registry.getStandardRestoreCost()).isEqualTo(Money.of(USD, 42));
  }

  @Test
  public void testSetReservedList_doesntMutateExistingRegistry() {
    ReservedList rl15 = persistReservedList(
        "tld-reserved15",
        "potato,FULLY_BLOCKED",
        "phone,FULLY_BLOCKED");
    ReservedList rl16 = persistReservedList(
        "tld-reserved16",
        "port,FULLY_BLOCKED",
        "manteau,FULLY_BLOCKED");
    Registry registry1 = new Registry.Builder()
        .setTldStr("propter")
        .setReservedLists(ImmutableSet.of(rl15)).build();
    assertThat(registry1.getReservedLists()).hasSize(1);
    Registry registry2 = registry1.asBuilder()
        .setReservedLists(ImmutableSet.of(rl15, rl16))
        .build();
    assertThat(registry1.getReservedLists()).hasSize(1);
    assertThat(registry2.getReservedLists()).hasSize(2);
  }

  @Test
  public void testGetReservedLists_doesntReturnNullWhenUninitialized() throws Exception {
    Registry registry = new Registry.Builder().setTldStr("foo").build();
    assertThat(registry.getReservedLists()).isNotNull();
    assertThat(registry.getReservedLists()).isEmpty();
  }

  @Test
  public void testSetReservedLists() {
    ReservedList rl5 = persistReservedList(
        "tld-reserved5",
        "lol,FULLY_BLOCKED",
        "cat,FULLY_BLOCKED");
    ReservedList rl6 = persistReservedList(
        "tld-reserved6",
        "hammock,FULLY_BLOCKED",
        "mouse,FULLY_BLOCKED");
    Registry r = Registry.get("tld")
        .asBuilder().setReservedLists(ImmutableSet.of(rl5, rl6)).build();
    assertThat(transform(r.getReservedLists(), GET_NAME_FUNCTION))
        .containsExactly("tld-reserved5", "tld-reserved6");
    r = Registry.get("tld").asBuilder().setReservedLists(ImmutableSet.<ReservedList> of()).build();
    assertThat(r.getReservedLists()).isEmpty();
  }

  @Test
  public void testSetReservedListsByName() {
    persistReservedList(
        "tld-reserved24",
        "lol,FULLY_BLOCKED",
        "cat,FULLY_BLOCKED");
    persistReservedList(
        "tld-reserved25",
        "mit,FULLY_BLOCKED",
        "tim,FULLY_BLOCKED");
    Registry r = Registry
        .get("tld")
        .asBuilder()
        .setReservedListsByName(ImmutableSet.of("tld-reserved24", "tld-reserved25"))
        .build();
    assertThat(transform(r.getReservedLists(), GET_NAME_FUNCTION))
        .containsExactly("tld-reserved24", "tld-reserved25");
    r = Registry.get("tld").asBuilder().setReservedListsByName(ImmutableSet.<String> of()).build();
    assertThat(r.getReservedLists()).isEmpty();
  }

  @Test
  public void testSetPremiumList() {
    PremiumList pl2 = persistPremiumList("tld2", "lol,USD 50", "cat,USD 700");
    Registry registry = Registry.get("tld").asBuilder().setPremiumList(pl2).build();
    Key<PremiumList> plKey = registry.getPremiumList();
    assertThat(plKey).isNotNull();
    PremiumList stored = PremiumList.get(plKey.getName()).get();
    assertThat(stored.getName()).isEqualTo("tld2");
  }

  @Test
  public void testSettingServerStatusChangeBillingCost() {
    Registry registry =
        Registry.get("tld").asBuilder().setServerStatusChangeBillingCost(Money.of(USD, 42)).build();
    assertThat(registry.getServerStatusChangeCost()).isEqualTo(Money.of(USD, 42));
  }

  @Test
  public void testSettingLordnUsername() {
    Registry registry = Registry.get("tld").asBuilder().setLordnUsername("username").build();
    assertThat(registry.getLordnUsername()).isEqualTo("username");
  }

  @Test
  public void testPdtLooksLikeGa() {
    Registry registry = Registry.get("tld").asBuilder()
        .setTldStateTransitions(ImmutableSortedMap.of(START_OF_TIME, TldState.PDT))
        .build();
    assertThat(registry.getTldState(START_OF_TIME)).isEqualTo(TldState.GENERAL_AVAILABILITY);
  }

  @Test
  public void testTldStateTransitionTimes() {
    Registry registry = Registry.get("tld").asBuilder()
        .setTldStateTransitions(ImmutableSortedMap.<DateTime, TldState>naturalOrder()
            .put(START_OF_TIME, TldState.PREDELEGATION)
            .put(clock.nowUtc().plusMonths(1), TldState.SUNRISE)
            .put(clock.nowUtc().plusMonths(2), TldState.SUNRUSH)
            .put(clock.nowUtc().plusMonths(3), TldState.LANDRUSH)
            .put(clock.nowUtc().plusMonths(4), TldState.QUIET_PERIOD)
            .put(clock.nowUtc().plusMonths(5), TldState.GENERAL_AVAILABILITY)
            .build())
        .build();
    assertThat(registry.getTldState(clock.nowUtc())).isEqualTo(TldState.PREDELEGATION);
    assertThat(registry.getTldState(clock.nowUtc().plusMillis(1)))
        .isEqualTo(TldState.PREDELEGATION);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(1).minusMillis(1)))
        .isEqualTo(TldState.PREDELEGATION);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(1))).isEqualTo(TldState.SUNRISE);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(1).plusMillis(1)))
        .isEqualTo(TldState.SUNRISE);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(2).minusMillis(1)))
        .isEqualTo(TldState.SUNRISE);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(2))).isEqualTo(TldState.SUNRUSH);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(2).plusMillis(1)))
        .isEqualTo(TldState.SUNRUSH);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(3).minusMillis(1)))
        .isEqualTo(TldState.SUNRUSH);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(3))).isEqualTo(TldState.LANDRUSH);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(3).plusMillis(1)))
        .isEqualTo(TldState.LANDRUSH);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(4).minusMillis(1)))
        .isEqualTo(TldState.LANDRUSH);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(4)))
        .isEqualTo(TldState.QUIET_PERIOD);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(4).plusMillis(1)))
        .isEqualTo(TldState.QUIET_PERIOD);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(5).minusMillis(1)))
        .isEqualTo(TldState.QUIET_PERIOD);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(5)))
        .isEqualTo(TldState.GENERAL_AVAILABILITY);
    assertThat(registry.getTldState(clock.nowUtc().plusMonths(5).plusMillis(1)))
        .isEqualTo(TldState.GENERAL_AVAILABILITY);
    assertThat(registry.getTldState(END_OF_TIME)).isEqualTo(TldState.GENERAL_AVAILABILITY);
  }

  @Test
  public void testQuietPeriodCanAppearMultipleTimesAnywhere() throws Exception {
    Registry.get("tld").asBuilder()
        .setTldStateTransitions(ImmutableSortedMap.<DateTime, TldState>naturalOrder()
            .put(START_OF_TIME, TldState.PREDELEGATION)
            .put(clock.nowUtc().plusMonths(1), TldState.QUIET_PERIOD)
            .put(clock.nowUtc().plusMonths(2), TldState.SUNRISE)
            .put(clock.nowUtc().plusMonths(3), TldState.QUIET_PERIOD)
            .put(clock.nowUtc().plusMonths(4), TldState.LANDRUSH)
            .put(clock.nowUtc().plusMonths(5), TldState.QUIET_PERIOD)
            .put(clock.nowUtc().plusMonths(6), TldState.GENERAL_AVAILABILITY)
            .build())
        .build();
  }

  @Test
  public void testRenewBillingCostTransitionTimes() {
    Registry registry = Registry.get("tld").asBuilder()
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(
            START_OF_TIME, Money.of(USD, 8),
            clock.nowUtc(), Money.of(USD, 1),
            clock.nowUtc().plusMonths(1), Money.of(USD, 2),
            clock.nowUtc().plusMonths(2), Money.of(USD, 3))).build();
    assertThat(registry.getStandardRenewCost(START_OF_TIME)).isEqualTo(Money.of(USD, 8));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().minusMillis(1)))
        .isEqualTo(Money.of(USD, 8));
    assertThat(registry.getStandardRenewCost(clock.nowUtc())).isEqualTo(Money.of(USD, 1));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().plusMillis(1)))
        .isEqualTo(Money.of(USD, 1));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().plusMonths(1).minusMillis(1)))
        .isEqualTo(Money.of(USD, 1));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().plusMonths(1)))
        .isEqualTo(Money.of(USD, 2));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().plusMonths(1).plusMillis(1)))
        .isEqualTo(Money.of(USD, 2));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().plusMonths(2).minusMillis(1)))
        .isEqualTo(Money.of(USD, 2));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().plusMonths(2)))
        .isEqualTo(Money.of(USD, 3));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().plusMonths(2).plusMillis(1)))
        .isEqualTo(Money.of(USD, 3));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().plusMonths(3).minusMillis(1)))
        .isEqualTo(Money.of(USD, 3));
    assertThat(registry.getStandardRenewCost(END_OF_TIME)).isEqualTo(Money.of(USD, 3));
  }

  @Test
  public void testRenewBillingCostNoTransitions() {
    Registry registry = Registry.get("tld");
    // The default value of 11 is set in createTld().
    assertThat(registry.getStandardRenewCost(START_OF_TIME)).isEqualTo(Money.of(USD, 11));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().minusMillis(1)))
        .isEqualTo(Money.of(USD, 11));
    assertThat(registry.getStandardRenewCost(clock.nowUtc())).isEqualTo(Money.of(USD, 11));
    assertThat(registry.getStandardRenewCost(clock.nowUtc().plusMillis(1)))
        .isEqualTo(Money.of(USD, 11));
    assertThat(registry.getStandardRenewCost(END_OF_TIME)).isEqualTo(Money.of(USD, 11));
  }

  @Test
  public void testIsPremiumDomain() throws Exception {
    createTld("example");
    Registry registry = Registry.get("example");
    assertThat(registry.isPremiumName("poor.example")).isFalse();
    assertThat(registry.isPremiumName("rich.example")).isTrue();
    assertThat(registry.isPremiumName("richer.example")).isTrue();
  }

  public void testGetDomainCreateCost() throws Exception {
    // The example tld has a premium price for "rich".
    createTld("example");
    Registry registry = Registry.get("example");
    // The default value of 17 is set in createTld().
    assertThat(registry.getDomainCreateCost("poor.example", 1)).isEqualTo(Money.of(USD, 13));
    assertThat(registry.getDomainCreateCost("poor.example", 2)).isEqualTo(Money.of(USD, 26));
    assertThat(registry.getDomainCreateCost("rich.example", 1)).isEqualTo(Money.of(USD, 100));
    assertThat(registry.getDomainCreateCost("rich.example", 2)).isEqualTo(Money.of(USD, 200));
  }

  @Test
  public void testGetDomainRenewCost() throws Exception {
    // The example tld has a premium price for "rich".
    createTld("example");
    Registry registry = Registry.get("example").asBuilder()
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(
            START_OF_TIME, Money.of(USD, 8),
            clock.nowUtc(), Money.of(USD, 10)))
        .build();
    assertThat(registry.getDomainRenewCost("poor.example", 1, START_OF_TIME))
        .isEqualTo(Money.of(USD, 8));
    assertThat(registry.getDomainRenewCost("poor.example", 2, START_OF_TIME))
        .isEqualTo(Money.of(USD, 16));
    assertThat(registry.getDomainRenewCost("poor.example", 1, clock.nowUtc()))
        .isEqualTo(Money.of(USD, 10));
    assertThat(registry.getDomainRenewCost("poor.example", 2, clock.nowUtc()))
        .isEqualTo(Money.of(USD, 20));
    assertThat(registry.getDomainRenewCost("rich.example", 1, START_OF_TIME))
        .isEqualTo(Money.of(USD, 100));
    assertThat(registry.getDomainRenewCost("rich.example", 2, START_OF_TIME))
        .isEqualTo(Money.of(USD, 200));
    assertThat(registry.getDomainRenewCost("rich.example", 1, clock.nowUtc()))
        .isEqualTo(Money.of(USD, 100));
    assertThat(registry.getDomainRenewCost("rich.example", 2, clock.nowUtc()))
        .isEqualTo(Money.of(USD, 200));
  }

  @Test
  public void testFailure_tldNeverSet() {
    thrown.expect(IllegalArgumentException.class, "No registry TLD specified.");
    new Registry.Builder().build();
  }

  @Test
  public void testFailure_setTldStr_null() {
    thrown.expect(IllegalArgumentException.class, "TLD must not be null.");
    new Registry.Builder().setTldStr(null);
  }

  @Test
  public void testFailure_setTldStr_invalidTld() {
    thrown.expect(
        IllegalArgumentException.class,
        "Cannot create registry for TLD that is not a valid, canonical domain name");
    new Registry.Builder().setTldStr(".tld").build();
  }

  @Test
  public void testFailure_setTldStr_nonCanonicalTld() {
    thrown.expect(
        IllegalArgumentException.class,
        "Cannot create registry for TLD that is not a valid, canonical domain name");
    new Registry.Builder().setTldStr("TLD").build();
  }

  @Test
  public void testFailure_tldStatesOutOfOrder() {
    thrown.expect(IllegalArgumentException.class);
    Registry.get("tld").asBuilder()
        .setTldStateTransitions(ImmutableSortedMap.of(
            clock.nowUtc(), TldState.SUNRUSH, clock.nowUtc().plusMonths(1), TldState.SUNRISE))
        .build();
  }

  @Test
  public void testFailure_duplicateTldState() {
    thrown.expect(IllegalArgumentException.class);
    Registry.get("tld").asBuilder()
        .setTldStateTransitions(ImmutableSortedMap.of(
            clock.nowUtc(), TldState.SUNRUSH, clock.nowUtc().plusMonths(1), TldState.SUNRUSH))
        .build();
  }

  @Test
  public void testFailure_negativeRenewBillingCostTransitionValue() {
    thrown.expect(IllegalArgumentException.class, "billing cost cannot be negative");
    Registry.get("tld").asBuilder()
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(USD, -42)));
  }

  @Test
  public void testFailure_negativeCreateBillingCost() {
    thrown.expect(IllegalArgumentException.class, "billing cost cannot be negative");
    Registry.get("tld").asBuilder().setCreateBillingCost(Money.of(USD, -42));
  }

  @Test
  public void testFailure_negativeRestoreBillingCost() {
    thrown.expect(IllegalArgumentException.class, "billing cost cannot be negative");
    Registry.get("tld").asBuilder().setRestoreBillingCost(Money.of(USD, -42));
  }

  @Test
  public void testFailure_negativeServerStatusChangeBillingCost() {
    thrown.expect(IllegalArgumentException.class, "billing cost cannot be negative");
    Registry.get("tld").asBuilder().setServerStatusChangeBillingCost(Money.of(USD, -42));
  }

  @Test
  public void testFailure_renewBillingCostTransitionValue_wrongCurrency() {
    thrown.expect(IllegalArgumentException.class, "cost must be in the registry's currency");
    Registry.get("tld").asBuilder()
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 42)))
        .build();
  }

  @Test
  public void testFailure_createBillingCost_wrongCurrency() {
    thrown.expect(IllegalArgumentException.class, "cost must be in the registry's currency");
    Registry.get("tld").asBuilder().setCreateBillingCost(Money.of(EUR, 42)).build();
  }

  @Test
  public void testFailure_restoreBillingCost_wrongCurrency() {
    thrown.expect(IllegalArgumentException.class, "cost must be in the registry's currency");
    Registry.get("tld").asBuilder().setRestoreBillingCost(Money.of(EUR, 42)).build();
  }

  @Test
  public void testFailure_serverStatusChangeBillingCost_wrongCurrency() {
    thrown.expect(IllegalArgumentException.class, "cost must be in the registry's currency");
    Registry.get("tld").asBuilder().setServerStatusChangeBillingCost(Money.of(EUR, 42)).build();
  }

  @Test
  public void testFailure_isPremiumNameForSldNotUnderTld() {
    thrown.expect(IllegalArgumentException.class);
    Registry.get("tld").isPremiumName("test.example");
  }

  @Test
  public void testFailure_isPremiumNameForSldSubdomain() throws Exception {
    createTld("example");
    thrown.expect(IllegalArgumentException.class);
    Registry.get("example").isPremiumName("rich.sld.example");
  }

  @Test
  public void testFailure_getCreateCostForSldNotUnderTld() {
    thrown.expect(IllegalArgumentException.class);
    Registry.get("tld").getDomainCreateCost("test.example", 1);
  }

  @Test
  public void testFailure_getCreateCostForSldSubdomain() throws Exception {
    createTld("example");
    thrown.expect(IllegalArgumentException.class);
    Registry.get("example").getDomainCreateCost("rich.sld.example", 1);
  }

  @Test
  public void testFailure_getRenewCostForSldNotUnderTld() {
    thrown.expect(IllegalArgumentException.class);
    Registry.get("tld").getDomainRenewCost("test.example", 1, clock.nowUtc());
  }

  @Test
  public void testFailure_getRenewCostForSldSubdomain() throws Exception {
    createTld("example");
    thrown.expect(IllegalArgumentException.class);
    Registry.get("example").getDomainRenewCost("rich.sld.example", 1, clock.nowUtc());
  }
}
