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
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.money.CurrencyUnit.JPY;
import static org.joda.money.CurrencyUnit.USD;

import google.registry.model.EntityTestCase;
import google.registry.model.billing.RegistrarCredit.CreditType;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.testing.ExceptionRule;
import org.joda.money.CurrencyUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link RegistrarCredit}. */
public class RegistrarCreditTest extends EntityTestCase {

  @Rule
  public ExceptionRule thrown = new ExceptionRule();

  private RegistrarCredit auctionCredit;
  private RegistrarCredit promoCredit;

  @Before
  public void setUp() throws Exception {
    createTld("tld");
    Registrar theRegistrar = ofy().load()
        .type(Registrar.class)
        .parent(getCrossTldKey())
        .id("TheRegistrar").now();
    auctionCredit = persistResource(
        new RegistrarCredit.Builder()
            .setParent(theRegistrar)
            .setType(CreditType.AUCTION)
            .setCurrency(CurrencyUnit.USD)
            .setTld("tld")
            .setCreationTime(clock.nowUtc())
            .build());
    promoCredit = persistResource(
        new RegistrarCredit.Builder()
            .setParent(theRegistrar)
            .setType(CreditType.PROMOTION)
            .setCurrency(CurrencyUnit.USD)
            .setTld("tld")
            .setCreationTime(clock.nowUtc())
            .build());
  }

  @Test
  public void testPersistence() throws Exception {
    assertThat(ofy().load().entity(auctionCredit).now()).isEqualTo(auctionCredit);
    assertThat(ofy().load().entity(promoCredit).now()).isEqualTo(promoCredit);
  }

  @Test
  public void testIndexing() throws Exception {
    // No indexing needed, so we don't expect any indices.
    verifyIndexing(auctionCredit);
    verifyIndexing(promoCredit);
  }

  @Test
  public void testFailure_missingTld() throws Exception {
    thrown.expect(NullPointerException.class, "tld");
    promoCredit.asBuilder().setTld(null).build();
  }

  @Test
  public void testFailure_NonexistentTld() throws Exception {
    thrown.expect(IllegalArgumentException.class, "example");
    promoCredit.asBuilder().setTld("example").build();
  }

  @Test
  public void testFailure_CurrencyDoesNotMatchTldCurrency() throws Exception {
    thrown.expect(IllegalArgumentException.class, "currency");
    assertThat(Registry.get("tld").getCurrency()).isEqualTo(USD);
    promoCredit.asBuilder().setTld("tld").setCurrency(JPY).build();
  }
}
