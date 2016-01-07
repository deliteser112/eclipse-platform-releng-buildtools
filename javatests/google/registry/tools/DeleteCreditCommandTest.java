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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.money.CurrencyUnit.USD;

import com.beust.jcommander.ParameterException;
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

/** Unit tests for {@link DeleteCreditCommand}. */
public class DeleteCreditCommandTest extends CommandTestCase<DeleteCreditCommand> {

  private Registrar registrar;
  private RegistrarCredit creditA;
  private RegistrarCredit creditB;
  private RegistrarCreditBalance balanceA1;
  private RegistrarCreditBalance balanceA2;
  private RegistrarCreditBalance balanceB1;
  private long creditAId;
  private long creditBId;

  @Before
  public void setUp() {
    registrar = Registrar.loadByClientId("TheRegistrar");
    createTld("tld");
    assertThat(Registry.get("tld").getCurrency()).isEqualTo(USD);
    DateTime creditCreationTime = Registry.get("tld").getCreationTime().plusMillis(1);
    creditA = persistResource(
        new RegistrarCredit.Builder()
            .setParent(registrar)
            .setType(CreditType.PROMOTION)
            .setTld("tld")
            .setCurrency(USD)
            .setCreationTime(creditCreationTime)
            .build());
    creditB = persistResource(
        new RegistrarCredit.Builder()
            .setParent(registrar)
            .setType(CreditType.AUCTION)
            .setTld("tld")
            .setCurrency(USD)
            .setCreationTime(creditCreationTime)
            .build());
    balanceA1 = persistResource(
        new RegistrarCreditBalance.Builder()
            .setParent(creditA)
            .setEffectiveTime(DateTime.parse("2014-11-01T00:00:00Z"))
            .setAmount(Money.of(USD, 100))
            .setWrittenTime(creditCreationTime)
            .build());
    balanceA2 = persistResource(
        new RegistrarCreditBalance.Builder()
            .setParent(creditA)
            .setEffectiveTime(DateTime.parse("2014-12-01T00:00:00Z"))
            .setAmount(Money.of(USD, 50))
            .setWrittenTime(creditCreationTime)
            .build());
    balanceB1 = persistResource(
        new RegistrarCreditBalance.Builder()
            .setParent(creditB)
            .setEffectiveTime(DateTime.parse("2014-11-01T00:00:00Z"))
            .setAmount(Money.of(USD, 42))
            .setWrittenTime(creditCreationTime)
            .build());
    creditAId = Key.create(creditA).getId();
    creditBId = Key.create(creditB).getId();
  }

  @Test
  public void testSuccess() throws Exception {
    assertThat(RegistrarCredit.loadAllForRegistrar(registrar)).containsAllOf(creditA, creditB);
    assertThat(ofy().load().type(RegistrarCreditBalance.class).ancestor(getCrossTldKey()))
        .containsAllOf(balanceA1, balanceA2, balanceB1);

    runCommandForced("--registrar=TheRegistrar", "--credit_id=" + creditAId);

    assertThat(RegistrarCredit.loadAllForRegistrar(registrar)).doesNotContain(creditA);
    assertThat(RegistrarCredit.loadAllForRegistrar(registrar)).contains(creditB);
    assertThat(ofy().load().type(RegistrarCreditBalance.class).ancestor(getCrossTldKey()))
        .containsNoneOf(balanceA1, balanceA2);
    assertThat(ofy().load().type(RegistrarCreditBalance.class).ancestor(getCrossTldKey()))
        .contains(balanceB1);
  }

  @Test
  public void testFailure_nonexistentParentRegistrar() throws Exception {
    thrown.expect(NullPointerException.class, "FakeRegistrar");
    runCommandForced("--registrar=FakeRegistrar", "--credit_id=" + creditAId);
  }

  @Test
  public void testFailure_nonexistentCreditId() throws Exception {
    long badId = creditAId + creditBId + 1;
    thrown.expect(NullPointerException.class, "ID " + badId);
    runCommandForced("--registrar=TheRegistrar", "--credit_id=" + badId);
  }

  @Test
  public void testFailure_noRegistrar() throws Exception {
    thrown.expect(ParameterException.class, "--registrar");
    runCommandForced("--credit_id=" + creditAId);
  }

  @Test
  public void testFailure_noCreditId() throws Exception {
    thrown.expect(ParameterException.class, "--credit_id");
    runCommandForced("--registrar=TheRegistrar");
  }
}
