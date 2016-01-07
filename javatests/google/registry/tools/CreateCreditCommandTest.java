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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static org.joda.money.CurrencyUnit.USD;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.Range;
import google.registry.model.billing.RegistrarCredit;
import google.registry.model.billing.RegistrarCredit.CreditType;
import google.registry.model.billing.RegistrarCreditBalance;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link CreateCreditCommand}. */
public class CreateCreditCommandTest extends CommandTestCase<CreateCreditCommand> {

  private Registrar registrar;

  @Before
  public void setUp() {
    createTld("tld");
    registrar = Registrar.loadByClientId("TheRegistrar");
    assertThat(Registry.get("tld").getCurrency()).isEqualTo(USD);
  }

  @Test
  public void testSuccess() throws Exception {
    DateTime before = DateTime.now(UTC);
    runCommandForced(
        "--registrar=TheRegistrar",
        "--type=PROMOTION",
        "--tld=tld",
        "--description=\"Some kind of credit\"",
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");

    RegistrarCredit credit =
        getOnlyElement(ofy().load().type(RegistrarCredit.class).ancestor(registrar));
    assertThat(credit).isNotNull();
    assertThat(ofy().load().key(credit.getParent()).now()).isEqualTo(registrar);
    assertThat(credit.getType()).isEqualTo(CreditType.PROMOTION);
    assertThat(credit.getTld()).isEqualTo("tld");
    assertThat(credit.getDescription()).isEqualTo("Some kind of credit");
    assertThat(credit.getCurrency()).isEqualTo(USD);
    assertThat(credit.getCreationTime()).isIn(Range.closed(before, DateTime.now(UTC)));

    RegistrarCreditBalance creditBalance =
        getOnlyElement(ofy().load().type(RegistrarCreditBalance.class).ancestor(credit));
    assertThat(creditBalance).isNotNull();
    assertThat(ofy().load().key(creditBalance.getParent()).now()).isEqualTo(credit);
    assertThat(creditBalance.getEffectiveTime()).isEqualTo(DateTime.parse("2014-11-01T01:02:03Z"));
    assertThat(creditBalance.getWrittenTime()).isEqualTo(credit.getCreationTime());
    assertThat(creditBalance.getAmount()).isEqualTo(Money.of(USD, 100));
  }

  @Test
  public void testSuccess_defaultDescription() throws Exception {
    runCommandForced(
        "--registrar=TheRegistrar",
        "--type=PROMOTION",
        "--tld=tld",
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");

    RegistrarCredit credit =
        getOnlyElement(ofy().load().type(RegistrarCredit.class).ancestor(registrar));
    assertThat(credit).isNotNull();
    assertThat(credit.getDescription()).isEqualTo("Promotional Credit for .tld");
  }

  @Test
  public void testFailure_nonexistentParentRegistrar() throws Exception {
    thrown.expect(NullPointerException.class, "FakeRegistrar");
    runCommandForced(
        "--registrar=FakeRegistrar",
        "--type=PROMOTION",
        "--tld=tld",
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_nonexistentTld() throws Exception {
    thrown.expect(IllegalArgumentException.class, "faketld");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--type=PROMOTION",
        "--tld=faketld",
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_nonexistentType() throws Exception {
    thrown.expect(ParameterException.class, "Invalid value for --type");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--type=BADTYPE",
        "--tld=tld",
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_negativeBalance() throws Exception {
    thrown.expect(IllegalArgumentException.class, "negative");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--type=PROMOTION",
        "--tld=tld",
        "--balance=\"USD -1\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_noRegistrar() throws Exception {
    thrown.expect(ParameterException.class, "--registrar");
    runCommandForced(
        "--type=PROMOTION",
        "--tld=tld",
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_noType() throws Exception {
    thrown.expect(ParameterException.class, "--type");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--tld=tld",
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_noTld() throws Exception {
    thrown.expect(ParameterException.class, "--tld");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--type=PROMOTION",
        "--balance=\"USD 100\"",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_noBalance() throws Exception {
    thrown.expect(ParameterException.class, "--balance");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--type=PROMOTION",
        "--tld=tld",
        "--effective_time=2014-11-01T01:02:03Z");
  }

  @Test
  public void testFailure_noEffectiveTime() throws Exception {
    thrown.expect(ParameterException.class, "--effective_time");
    runCommandForced(
        "--registrar=TheRegistrar",
        "--type=PROMOTION",
        "--tld=tld",
        "--balance=\"USD 100\"");
  }
}
