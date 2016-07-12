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

package google.registry.tools;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.money.CurrencyUnit.USD;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.Range;
import com.googlecode.objectify.Key;
import google.registry.model.billing.RegistrarCredit;
import google.registry.model.billing.RegistrarCredit.CreditType;
import google.registry.model.billing.RegistrarCreditBalance;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link CreateCreditBalanceCommand}. */
public class CreateCreditBalanceCommandTest extends CommandTestCase<CreateCreditBalanceCommand> {

  private Registrar registrar;
  private RegistrarCredit credit;
  private long creditId;

  @Before
  public void setUp() {
    registrar = Registrar.loadByClientId("TheRegistrar");
    createTld("tld");
    assertThat(Registry.get("tld").getCurrency()).isEqualTo(USD);
    credit = persistResource(
        new RegistrarCredit.Builder()
            .setParent(registrar)
            .setType(CreditType.PROMOTION)
            .setTld("tld")
            .setCurrency(USD)
            .setCreationTime(Registry.get("tld").getCreationTime().plusMillis(1))
            .build());
    creditId = Key.create(credit).getId();
  }

  @Test
  public void testSuccess() throws Exception {
    DateTime before = DateTime.now(UTC);

    runCommandForced(
        "--registrar=TheRegistrar",
        "--credit_id=" + creditId,
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");

    RegistrarCreditBalance creditBalance =
        getOnlyElement(ofy().load().type(RegistrarCreditBalance.class).ancestor(credit));
    assertThat(creditBalance).isNotNull();
    assertThat(creditBalance.getParent().get()).isEqualTo(credit);
    assertThat(creditBalance.getEffectiveTime()).isEqualTo(DateTime.parse("2014-11-01T01:02:03Z"));
    assertThat(creditBalance.getWrittenTime()).isIn(Range.closed(before, DateTime.now(UTC)));
    assertThat(creditBalance.getAmount()).isEqualTo(Money.of(USD, 100));
  }

  @Test
  public void testFailure_nonexistentParentRegistrar() throws Exception {
    thrown.expect(NullPointerException.class, "FakeRegistrar");
    runCommandForced(
        "--registrar=FakeRegistrar",
        "--credit_id=" + creditId,
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_nonexistentCreditId() throws Exception {
    long badId = creditId + 1;
    thrown.expect(NullPointerException.class, "ID " + badId);
    runCommandForced(
        "--registrar=TheRegistrar",
        "--credit_id=" + badId,
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_negativeBalance() throws Exception {
    thrown.expect(IllegalArgumentException.class, "negative");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--credit_id=" + creditId,
        "--balance=\"USD -1\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_noRegistrar() throws Exception {
    thrown.expect(ParameterException.class, "--registrar");
    runCommandForced(
        "--credit_id=" + creditId,
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_noCreditId() throws Exception {
    thrown.expect(ParameterException.class, "--credit_id");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_noBalance() throws Exception {
    thrown.expect(ParameterException.class, "--balance");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--credit_id=" + creditId,
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_noEffectiveTime() throws Exception {
    thrown.expect(ParameterException.class, "--effective_time");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--credit_id=" + creditId,
        "--balance=\"USD 100\"");
  }
}
