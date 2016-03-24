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

package com.google.domain.registry.flows.domain;

import static com.google.domain.registry.model.eppoutput.CheckData.DomainCheck.create;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistDeletedDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistPremiumList;
import static com.google.domain.registry.testing.DatastoreHelper.persistReservedList;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.flows.ResourceCheckFlow.TooManyResourceChecksException;
import com.google.domain.registry.flows.ResourceCheckFlowTestCase;
import com.google.domain.registry.flows.domain.DomainCheckFlow.OnlyCheckedNamesCanBeFeeCheckedException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.BadDomainNameCharacterException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.BadDomainNamePartsCountException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.BadPeriodUnitException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.CurrencyUnitMismatchException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.DashesInThirdAndFourthException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.DomainLabelTooLongException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.EmptyDomainNamePartException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.FeeChecksDontSupportPhasesException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.InvalidIdnDomainLabelException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.InvalidPunycodeException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.LeadingDashException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.RestoresAreAlwaysForOneYearException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.TldDoesNotExistException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.TrailingDashException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.UnknownFeeCommandException;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.model.registry.label.ReservedList;
import com.google.domain.registry.testing.DatastoreHelper;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainCheckFlow}. */
public class DomainCheckFlowTest
    extends ResourceCheckFlowTestCase<DomainCheckFlow, DomainResource> {

  public DomainCheckFlowTest() {
    setEppInput("domain_check_one_tld.xml");
  }

  private ReservedList createReservedList() throws Exception {
      return persistReservedList(
          "tld-reserved",
          "reserved,FULLY_BLOCKED",
          "anchor,RESERVED_FOR_ANCHOR_TENANT,foo2BAR",
          "allowedinsunrise,ALLOWED_IN_SUNRISE",
          "collision,NAME_COLLISION",
          "premiumcollision,NAME_COLLISION");
  }

  @Before
  public void initCheckTest() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistResource(Registry.get("tld").asBuilder().setReservedLists(createReservedList()).build());
  }

  @Test
  public void testNothingExists() throws Exception {
    doCheckTest(
        create(true, "example1.tld", null),
        create(true, "example2.tld", null),
        create(true, "example3.tld", null));
  }

  @Test
  public void testOneExists() throws Exception {
    persistActiveDomain("example1.tld");
    doCheckTest(
        create(false, "example1.tld", "In use"),
        create(true, "example2.tld", null),
        create(true, "example3.tld", null));
  }

  @Test
  public void testOneReserved() throws Exception {
    setEppInput("domain_check_one_tld_reserved.xml");
    doCheckTest(
        create(false, "reserved.tld", "Reserved"),
        create(false, "allowedinsunrise.tld", "Reserved for non-sunrise"),
        create(true, "example2.tld", null),
        create(true, "example3.tld", null));
  }

  @Test
  public void testAnchorTenantReserved() throws Exception {
    setEppInput("domain_check_anchor.xml");
    doCheckTest(create(false, "anchor.tld", "Reserved"));
  }

  @Test
  public void testOneReserved_multipartTld() throws Exception {
    createTld("tld.foo");
    persistResource(
        Registry.get("tld.foo")
            .asBuilder()
            .setReservedLists(persistReservedList(
                "tld.foo", "reserved,FULLY_BLOCKED", "allowedinsunrise,ALLOWED_IN_SUNRISE"))
            .build());
    setEppInput("domain_check_one_multipart_tld_reserved.xml");
    doCheckTest(
        create(false, "reserved.tld.foo", "Reserved"),
        create(false, "allowedinsunrise.tld.foo", "Reserved for non-sunrise"),
        create(true, "example2.tld.foo", null),
        create(true, "example3.tld.foo", null));
  }

  @Test
  public void testOneExistsButWasDeleted() throws Exception {
    persistDeletedDomain("example1.tld", clock.nowUtc());
    doCheckTest(
        create(true, "example1.tld", null),
        create(true, "example2.tld", null),
        create(true, "example3.tld", null));
  }

  @Test
  public void testDuplicatesAllowed() throws Exception {
    setEppInput("domain_check_duplicates.xml");
    doCheckTest(
        create(true, "example1.tld", null),
        create(true, "example1.tld", null));
  }

  @Test
  public void testXmlMatches() throws Exception {
    persistActiveDomain("example2.tld");
    runFlowAssertResponse(readFile("domain_check_one_tld_response.xml"));
  }

  @Test
  public void test50IdsAllowed() throws Exception {
    // Make sure we don't have a regression that reduces the number of allowed checks.
    setEppInput("domain_check_50.xml");
    runFlow();
  }

  @Test
  public void testTooManyIds() throws Exception {
    setEppInput("domain_check_51.xml");
    thrown.expect(TooManyResourceChecksException.class);
    runFlow();
  }

  @Test
  public void testWrongTld() throws Exception {
    setEppInput("domain_check.xml");
    thrown.expect(TldDoesNotExistException.class);
    runFlow();
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    thrown.expect(NotAuthorizedForTldException.class);
    DatastoreHelper.persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    runFlow();
  }

  private void doFailingBadLabelTest(String label, Class<? extends Exception> expectedException)
      throws Exception {
    setEppInput("domain_check_template.xml", ImmutableMap.of("LABEL", label));
    thrown.expect(expectedException);
    runFlow();
  }

  @Test
  public void testFailure_uppercase() throws Exception {
    doFailingBadLabelTest("FOO.tld", BadDomainNameCharacterException.class);
  }

  @Test
  public void testFailure_badCharacter() throws Exception {
    doFailingBadLabelTest("test_example.tld", BadDomainNameCharacterException.class);
  }

  @Test
  public void testFailure_leadingDash() throws Exception {
    doFailingBadLabelTest("-example.tld", LeadingDashException.class);
  }

  @Test
  public void testFailure_trailingDash() throws Exception {
    doFailingBadLabelTest("example-.tld", TrailingDashException.class);
  }

  @Test
  public void testFailure_tooLong() throws Exception {
    doFailingBadLabelTest(Strings.repeat("a", 64) + ".tld", DomainLabelTooLongException.class);
  }

  @Test
  public void testFailure_leadingDot() throws Exception {
    doFailingBadLabelTest(".example.tld", EmptyDomainNamePartException.class);
  }

  @Test
  public void testFailure_leadingDotTld() throws Exception {
    doFailingBadLabelTest("foo..tld", EmptyDomainNamePartException.class);
  }

  @Test
  public void testFailure_tooManyParts() throws Exception {
    doFailingBadLabelTest("foo.example.tld", BadDomainNamePartsCountException.class);
  }

  @Test
  public void testFailure_tooFewParts() throws Exception {
    doFailingBadLabelTest("tld", BadDomainNamePartsCountException.class);
  }

  @Test
  public void testFailure_invalidPunycode() throws Exception {
    doFailingBadLabelTest("xn--abcdefg.tld", InvalidPunycodeException.class);
  }

  @Test
  public void testFailure_dashesInThirdAndFourthPosition() throws Exception {
    doFailingBadLabelTest("ab--cdefg.tld", DashesInThirdAndFourthException.class);
  }

  @Test
  public void testFailure_tldDoesNotExist() throws Exception {
    doFailingBadLabelTest("foo.nosuchtld", TldDoesNotExistException.class);
  }

  @Test
  public void testFailure_invalidIdnCodePoints() throws Exception {
    // ❤☀☆☂☻♞☯.tld
    doFailingBadLabelTest("xn--k3hel9n7bxlu1e.tld", InvalidIdnDomainLabelException.class);
  }

  @Test
  public void testAvailExtension() throws Exception {
    persistActiveDomain("example1.tld");
    setEppInput("domain_check_avail.xml");
    doCheckTest(
        create(false, "example1.tld", "In use"),
        create(true, "example2.tld", null),
        create(true, "example3.tld", null));
  }

  /**
   * Test that premium names are shown as unavailable if the premium pricing extension is not
   * declared at login.
   */
  @Test
  public void testAvailExtension_premiumDomainsAreUnavailableWithoutExtension() throws Exception {
    sessionMetadata.setServiceExtensionUris(ImmutableSet.<String>of());
    createTld("example");
    setEppInput("domain_check_premium.xml");
    doCheckTest(create(false, "rich.example", "Premium names require EPP ext."));
  }

  /**
   * Test that premium names are always shown as available if the TLD does not require the premium
   * pricing extension to register premium names.
   */
  @Test
  public void testAvailExtension_premiumDomainsAvailableIfNotRequiredByTld() throws Exception {
    sessionMetadata.setServiceExtensionUris(ImmutableSet.<String>of());
    createTld("example");
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    setEppInput("domain_check_premium.xml");
    doCheckTest(create(true, "rich.example", null));
  }


  /** Test multiyear periods and explicitly correct currency and that the avail extension is ok. */
  @Test
  public void testFeeExtension() throws Exception {
    persistActiveDomain("example1.tld");
    setEppInput("domain_check_fee.xml");
    runFlowAssertResponse(readFile("domain_check_fee_response.xml"));
  }

  /** Test commands for create, renew, transfer and restore with implicit period and currency. */
  @Test
  public void testFeeExtension_multipleCommands() throws Exception {
    setEppInput("domain_check_fee_multiple_commands.xml");
    runFlowAssertResponse(readFile("domain_check_fee_multiple_commands_response.xml"));
  }

  /** Test the same as {@link #testFeeExtension_multipleCommands} with premium labels. */
  @Test
  public void testFeeExtension_premiumLabels() throws Exception {
    createTld("example");
    setEppInput("domain_check_fee_premium.xml");
    runFlowAssertResponse(readFile("domain_check_fee_premium_response.xml"));
  }

  @Test
  public void testFeeExtension_fractionalCost() throws Exception {
    // Note that the response xml expects to see "11.10" with two digits after the decimal point.
    // This works because Money.getAmount(), used in the flow, returns a BigDecimal that is set to
    // display the number of digits that is conventional for the given currency.
    persistResource(Registry.get("tld").asBuilder()
        .setCreateBillingCost(Money.of(CurrencyUnit.USD, 11.1))
        .build());
    setEppInput("domain_check_fee_fractional.xml");
    runFlowAssertResponse(readFile("domain_check_fee_fractional_response.xml"));
  }

  /** Test that create fees are properly omitted/classed on names on reserved lists. */
  @Test
  public void testFeeExtension_reservedName() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistResource(Registry.get("tld").asBuilder()
        .setReservedLists(createReservedList())
        .setPremiumList(persistPremiumList("tld", "premiumcollision,USD 70"))
        .build());
    setEppInput("domain_check_fee_reserved.xml");
    runFlowAssertResponse(readFile("domain_check_fee_reserved_response.xml"));
  }

  @Test
  public void testFeeExtension_feesNotOmittedOnReservedNamesInSunrise() throws Exception {
    createTld("tld", TldState.SUNRISE);
    persistResource(Registry.get("tld").asBuilder()
        .setReservedLists(createReservedList())
        .setPremiumList(persistPremiumList("tld", "premiumcollision,USD 70"))
        .build());
    setEppInput("domain_check_fee_reserved.xml");
    runFlowAssertResponse(readFile("domain_check_fee_reserved_sunrise_response.xml"));
  }

  @Test
  public void testFeeExtension_wrongCurrency() throws Exception {
    thrown.expect(CurrencyUnitMismatchException.class);
    setEppInput("domain_check_fee_euro.xml");
    runFlow();
  }

  @Test
  public void testFeeExtension_periodNotInYears() throws Exception {
    thrown.expect(BadPeriodUnitException.class);
    setEppInput("domain_check_fee_bad_period.xml");
    runFlow();
  }

  @Test
  public void testFeeExtension_commandWithPhase() throws Exception {
    thrown.expect(FeeChecksDontSupportPhasesException.class);
    setEppInput("domain_check_fee_command_phase.xml");
    runFlow();
  }

  @Test
  public void testFeeExtension_commandSubphase() throws Exception {
    thrown.expect(FeeChecksDontSupportPhasesException.class);
    setEppInput("domain_check_fee_command_subphase.xml");
    runFlow();
  }

  @Test
  public void testFeeExtension_feeCheckNotInAvailabilityCheck() throws Exception {
    thrown.expect(OnlyCheckedNamesCanBeFeeCheckedException.class);
    setEppInput("domain_check_fee_not_in_avail.xml");
    runFlow();
  }

  @Test
  public void testFeeExtension_multiyearRestore() throws Exception {
    thrown.expect(RestoresAreAlwaysForOneYearException.class);
    setEppInput("domain_check_fee_multiyear_restore.xml");
    runFlow();
  }

  @Test
  public void testFeeExtension_unknownCommand() throws Exception {
    thrown.expect(UnknownFeeCommandException.class);
    setEppInput("domain_check_fee_unknown_command.xml");
    runFlow();
  }

  @Test
  public void testFeeExtension_invalidCommand() throws Exception {
    thrown.expect(UnknownFeeCommandException.class);
    setEppInput("domain_check_fee_invalid_command.xml");
    runFlow();
  }
}
