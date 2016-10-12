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

package google.registry.tools;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.registry.label.ReservedListTest.GET_NAME_FUNCTION;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistPremiumList;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.JPY;
import static org.joda.money.CurrencyUnit.USD;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Duration.standardMinutes;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.Range;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link CreateTldCommand}. */
public class CreateTldCommandTest extends CommandTestCase<CreateTldCommand> {

  private static final String DATETIME_FORMAT = "YYYY-MM-dd'T'HH:mm:ssZZ";

  @Before
  public void init() {
    persistReservedList("common_abuse", "baa,FULLY_BLOCKED");
    persistReservedList("xn--q9jyb4c_abuse", "lamb,FULLY_BLOCKED");
    persistReservedList("tld_banned", "kilo,FULLY_BLOCKED", "lima,MISTAKEN_PREMIUM");
    persistReservedList("soy_expurgated", "fireflies,FULLY_BLOCKED");
    persistPremiumList("xn--q9jyb4c", "minecraft,USD 1000");
  }

  @Test
  public void testSuccess() throws Exception {
    DateTime before = DateTime.now(UTC);
    runCommandForced("xn--q9jyb4c", "--roid_suffix=Q9JYB4C");
    DateTime after = DateTime.now(UTC);

    Registry registry = Registry.get("xn--q9jyb4c");
    assertThat(registry).isNotNull();
    assertThat(registry.getTldState(registry.getCreationTime())).isEqualTo(TldState.PREDELEGATION);
    assertThat(registry.getAddGracePeriodLength()).isEqualTo(Registry.DEFAULT_ADD_GRACE_PERIOD);
    assertThat(registry.getRedemptionGracePeriodLength())
        .isEqualTo(Registry.DEFAULT_REDEMPTION_GRACE_PERIOD);
    assertThat(registry.getPendingDeleteLength()).isEqualTo(Registry.DEFAULT_PENDING_DELETE_LENGTH);
    assertThat(registry.getCreationTime()).isIn(Range.closed(before, after));
  }

  @Test
  public void testFailure_multipleArguments() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Can't create more than one TLD at a time");
    runCommandForced("--roid_suffix=blah", "xn--q9jyb4c", "test");
  }

  @Test
  public void testSuccess_initialTldStateFlag() throws Exception {
    runCommandForced(
        "--initial_tld_state=GENERAL_AVAILABILITY", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getTldState(DateTime.now(UTC)))
        .isEqualTo(TldState.GENERAL_AVAILABILITY);
  }

  @Test
  public void testSuccess_initialRenewBillingCostFlag() throws Exception {
    runCommandForced(
        "--initial_renew_billing_cost=\"USD 42.42\"", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getStandardRenewCost(DateTime.now(UTC)))
        .isEqualTo(Money.of(USD, 42.42));
  }

  @Test
  public void testSuccess_eapFeeSchedule() throws Exception {
    DateTime now = DateTime.now(UTC);
    DateTime tomorrow = now.plusDays(1);
    runCommandForced(
        String.format(
            "--eap_fee_schedule=\"%s=USD 0.00,%s=USD 50.00,%s=USD 10.00\"",
            START_OF_TIME.toString(DATETIME_FORMAT),
            now.toString(DATETIME_FORMAT),
            tomorrow.toString(DATETIME_FORMAT)),
        "--roid_suffix=Q9JYB4C",
        "xn--q9jyb4c");

    Registry registry = Registry.get("xn--q9jyb4c");
    assertThat(registry.getEapFeeFor(now.minusHours(1)).getCost())
        .isEqualTo(BigDecimal.ZERO.setScale(2));
    assertThat(registry.getEapFeeFor(now.plusHours(1)).getCost())
        .isEqualTo(new BigDecimal("50.00"));
    assertThat(registry.getEapFeeFor(now.plusDays(1).plusHours(1)).getCost())
        .isEqualTo(new BigDecimal("10.00"));
  }

  @Test
  public void testSuccess_addGracePeriodFlag() throws Exception {
    runCommandForced("--add_grace_period=PT300S", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getAddGracePeriodLength()).isEqualTo(standardMinutes(5));
  }

  @Test
  public void testSuccess_roidSuffixWorks() throws Exception {
    runCommandForced("--roid_suffix=RSUFFIX", "tld");
    assertThat(Registry.get("tld").getRoidSuffix()).isEqualTo("RSUFFIX");
  }

  @Test
  public void testSuccess_escrow() throws Exception {
    runCommandForced("--escrow=true", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getEscrowEnabled()).isTrue();
  }

  @Test
  public void testSuccess_noEscrow() throws Exception {
    runCommandForced("--escrow=false", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getEscrowEnabled()).isFalse();
  }

  @Test
  public void testSuccess_redemptionGracePeriodFlag() throws Exception {
    runCommandForced("--redemption_grace_period=PT300S", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getRedemptionGracePeriodLength())
        .isEqualTo(standardMinutes(5));
  }

  @Test
  public void testSuccess_pendingDeleteLengthFlag() throws Exception {
    runCommandForced("--pending_delete_length=PT300S", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getPendingDeleteLength()).isEqualTo(standardMinutes(5));
  }

  @Test
  public void testSuccess_automaticTransferLengthFlag() throws Exception {
    runCommandForced("--automatic_transfer_length=PT300S", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getAutomaticTransferLength())
        .isEqualTo(standardMinutes(5));
  }

  @Test
  public void testSuccess_createBillingCostFlag() throws Exception {
    runCommandForced("--create_billing_cost=\"USD 42.42\"", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getStandardCreateCost()).isEqualTo(Money.of(USD, 42.42));
  }

  @Test
  public void testSuccess_restoreBillingCostFlag() throws Exception {
    runCommandForced(
        "--restore_billing_cost=\"USD 42.42\"", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getStandardRestoreCost())
        .isEqualTo(Money.of(USD, 42.42));
  }

  @Test
  public void testSuccess_serverStatusChangeCostFlag() throws Exception {
    runCommandForced(
        "--server_status_change_cost=\"USD 42.42\"", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getServerStatusChangeCost())
        .isEqualTo(Money.of(USD, 42.42));
  }

  @Test
  public void testSuccess_nonUsdBillingCostFlag() throws Exception {
    runCommandForced(
        "--create_billing_cost=\"JPY 12345\"",
        "--restore_billing_cost=\"JPY 67890\"",
        "--initial_renew_billing_cost=\"JPY 101112\"",
        "--server_status_change_cost=\"JPY 97865\"",
        "--roid_suffix=Q9JYB4C",
        "xn--q9jyb4c");
    Registry registry = Registry.get("xn--q9jyb4c");
    assertThat(registry.getStandardCreateCost()).isEqualTo(Money.ofMajor(JPY, 12345));
    assertThat(registry.getStandardRestoreCost()).isEqualTo(Money.ofMajor(JPY, 67890));
    assertThat(registry.getStandardRenewCost(START_OF_TIME)).isEqualTo(Money.ofMajor(JPY, 101112));
  }

  @Test
  public void testSuccess_multipartTld() throws Exception {
    runCommandForced("co.uk", "--roid_suffix=COUK");

    Registry registry = Registry.get("co.uk");
    assertThat(registry.getTldState(new DateTime())).isEqualTo(TldState.PREDELEGATION);
    assertThat(registry.getAddGracePeriodLength()).isEqualTo(Registry.DEFAULT_ADD_GRACE_PERIOD);
    assertThat(registry.getRedemptionGracePeriodLength())
        .isEqualTo(Registry.DEFAULT_REDEMPTION_GRACE_PERIOD);
    assertThat(registry.getPendingDeleteLength()).isEqualTo(Registry.DEFAULT_PENDING_DELETE_LENGTH);
  }

  @Test
  public void testSuccess_setReservedLists() throws Exception {
    runCommandForced(
        "--reserved_lists=xn--q9jyb4c_abuse,common_abuse", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(transform(Registry.get("xn--q9jyb4c").getReservedLists(), GET_NAME_FUNCTION))
        .containsExactly("xn--q9jyb4c_abuse", "common_abuse");
  }

  @Test
  public void testSuccess_addLrpPeriod() throws Exception {
    runCommandForced(
        "--lrp_period=2004-06-09T12:30:00Z/2004-07-10T13:30:00Z",
        "--roid_suffix=Q9JYB4C",
        "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getLrpPeriod()).isEqualTo(
        new Interval(
            DateTime.parse("2004-06-09T12:30:00Z"), DateTime.parse("2004-07-10T13:30:00Z")));
  }

  @Test
  public void testSuccess_setPremiumPriceAckRequired() throws Exception {
    runCommandForced("--premium_price_ack_required=true", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getPremiumPriceAckRequired()).isTrue();
  }

  @Test
  public void testFailure_invalidAddGracePeriod() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--add_grace_period=5m", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidRedemptionGracePeriod() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--redemption_grace_period=5m", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidPendingDeleteLength() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--pending_delete_length=5m", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidTldState() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--initial_tld_state=INVALID_STATE", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_bothTldStateFlags() throws Exception {
    DateTime now = DateTime.now(UTC);
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        String.format("--tld_state_transitions=%s=PREDELEGATION,%s=SUNRISE", now, now.plus(1)),
        "--initial_tld_state=GENERAL_AVAILABILITY",
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_negativeInitialRenewBillingCost() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        "--initial_renew_billing_cost=USD -42", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidEapCurrency() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        String.format(
            "--eap_fee_schedule=\"%s=JPY 123456\"", START_OF_TIME.toString(DATETIME_FORMAT)),
        "--roid_suffix=Q9JYB4C",
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_noTldName() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced();
  }

  @Test
  public void testFailure_duplicateArguments() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("foo", "xn--q9jyb4c", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_alreadyExists() throws Exception {
    createTld("xn--q9jyb4c");
    thrown.expect(IllegalStateException.class);
    runCommandForced("--roid_suffix=NOTDUPE", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_tldStartsWithDigit() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("1foo", "--roid_suffix=1FOO");
  }

  @Test
  public void testSuccess_setAllowedRegistrants() throws Exception {
    runCommandForced("--allowed_registrants=alice,bob", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getAllowedRegistrantContactIds())
        .containsExactly("alice", "bob");
  }

  @Test
  public void testSuccess_setAllowedNameservers() throws Exception {
    runCommandForced(
        "--allowed_nameservers=ns1.example.com,ns2.example.com",
        "--roid_suffix=Q9JYB4C",
        "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getAllowedFullyQualifiedHostNames())
        .containsExactly("ns1.example.com", "ns2.example.com");
  }

  @Test
  public void testSuccess_setCommonReservedListOnTld() throws Exception {
    runSuccessfulReservedListsTest("common_abuse");
  }

  @Test
  public void testSuccess_setTldSpecificReservedListOnTld() throws Exception {
    runSuccessfulReservedListsTest("xn--q9jyb4c_abuse");
  }

  @Test
  public void testSuccess_setCommonReservedListAndTldSpecificReservedListOnTld() throws Exception {
    runSuccessfulReservedListsTest("common_abuse,xn--q9jyb4c_abuse");
  }

  @Test
  public void testFailure_setReservedListFromOtherTld() throws Exception {
    runFailureReservedListsTest(
        "tld_banned",
        IllegalArgumentException.class,
        "The reserved list(s) tld_banned cannot be applied to the tld xn--q9jyb4c");
  }

  @Test
  public void testSuccess_setReservedListFromOtherTld_withOverride() throws Exception {
    runReservedListsTestOverride("tld_banned");
  }

  @Test
  public void testFailure_setCommonAndReservedListFromOtherTld() throws Exception {
    runFailureReservedListsTest(
        "common_abuse,tld_banned",
        IllegalArgumentException.class,
        "The reserved list(s) tld_banned cannot be applied to the tld xn--q9jyb4c");
  }

  @Test
  public void testSuccess_setCommonAndReservedListFromOtherTld_withOverride() throws Exception {
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errContent));
    runReservedListsTestOverride("common_abuse,tld_banned");
    String errMsg =
        "Error overriden: The reserved list(s) tld_banned cannot be applied to the tld xn--q9jyb4c";
    assertThat(errContent.toString()).contains(errMsg);
    System.setOut(null);
  }

  @Test
  public void testFailure_setMultipleReservedListsFromOtherTld() throws Exception {
    runFailureReservedListsTest(
        "tld_banned,soy_expurgated",
        IllegalArgumentException.class,
        "The reserved list(s) tld_banned, soy_expurgated cannot be applied to the tld xn--q9jyb4c");
  }

  @Test
  public void testSuccess_setMultipleReservedListsFromOtherTld_withOverride() throws Exception {
    runReservedListsTestOverride("tld_banned,soy_expurgated");
  }

  @Test
  public void testFailure_setNonExistentReservedLists() throws Exception {
    runFailureReservedListsTest(
        "xn--q9jyb4c_asdf,common_asdsdgh",
        IllegalStateException.class,
        "Could not find reserved list xn--q9jyb4c_asdf to add to the tld");
  }

  @Test
  public void testSuccess_setPremiumList() throws Exception {
    runCommandForced("--premium_list=xn--q9jyb4c", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getPremiumList().getName()).isEqualTo("xn--q9jyb4c");
  }

  @Test
  public void testSuccess_setDriveFolderIdToValue() throws Exception {
    runCommandForced("--drive_folder_id=madmax2030", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getDriveFolderId()).isEqualTo("madmax2030");
  }

  @Test
  public void testSuccess_setDriveFolderIdToNull() throws Exception {
    runCommandForced("--drive_folder_id=null", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getDriveFolderId()).isNull();
  }

  @Test
  public void testFailure_setPremiumListThatDoesntExist() throws Exception {
    thrown.expect(IllegalArgumentException.class, "The premium list 'phonies' doesn't exist");
    runCommandForced("--premium_list=phonies", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_roidSuffixAlreadyInUse() throws Exception {
    createTld("foo", "BLAH");
    thrown.expect(IllegalArgumentException.class, "The roid suffix BLAH is already in use");
    runCommandForced("--roid_suffix=BLAH", "randomtld");
  }

  @Test
  public void testFailure_addLrpPeriod_backwardsInterval() throws Exception {
    thrown.expect(
        ParameterException.class,
        "--lrp_period=2005-06-09T12:30:00Z/2004-07-10T13:30:00Z not an ISO-8601 interval");
    runCommandForced(
        "--lrp_period=2005-06-09T12:30:00Z/2004-07-10T13:30:00Z",
        "--roid_suffix=Q9JYB4C",
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_addLrpPeriod_badInterval() throws Exception {
    thrown.expect(ParameterException.class, "--lrp_period=foobar not an ISO-8601 interval");
    runCommandForced("--lrp_period=foobar", "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
  }

  private void runSuccessfulReservedListsTest(String reservedLists) throws Exception {
    runCommandForced("--reserved_lists", reservedLists, "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
  }

  private void runReservedListsTestOverride(String reservedLists) throws Exception {
    runCommandForced(
        "--override_reserved_list_rules",
        "--reserved_lists",
        reservedLists,
        "--roid_suffix=Q9JYB4C",
        "xn--q9jyb4c");
  }

  private void runFailureReservedListsTest(
      String reservedLists, Class<? extends Exception> errorClass, String errorMsg)
      throws Exception {
    thrown.expect(errorClass, errorMsg);
    runCommandForced("--reserved_lists", reservedLists, "--roid_suffix=Q9JYB4C", "xn--q9jyb4c");
  }
}
