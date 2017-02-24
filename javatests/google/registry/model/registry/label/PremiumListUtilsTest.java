// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.model.registry.label;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.label.PremiumList.cachePremiumListEntries;
import static google.registry.model.registry.label.PremiumListUtils.deletePremiumList;
import static google.registry.model.registry.label.PremiumListUtils.doesPremiumListExist;
import static google.registry.model.registry.label.PremiumListUtils.getPremiumPrice;
import static google.registry.model.registry.label.PremiumListUtils.loadPremiumListEntries;
import static google.registry.model.registry.label.PremiumListUtils.savePremiumListAndEntries;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistPremiumList;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import google.registry.model.pricing.StaticPremiumListPricingEngine;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.PremiumList.PremiumListEntry;
import google.registry.model.registry.label.PremiumList.PremiumListRevision;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import java.util.Map;
import org.joda.money.Money;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PremiumListUtils}. */
@RunWith(JUnit4.class)
public class PremiumListUtilsTest {

  @Rule public final ExceptionRule thrown = new ExceptionRule();
  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Before
  public void before() throws Exception {
    // createTld() overwrites the premium list, so call it first.
    createTld("tld");
    PremiumList pl =
        persistPremiumList(
            "tld",
            "lol,USD 999 # yup",
            "rich,USD 1999 #tada",
            "icann,JPY 100",
            "johnny-be-goode,USD 20.50");
    persistResource(Registry.get("tld").asBuilder().setPremiumList(pl).build());
  }

  @Test
  public void testGetPremiumPrice_returnsNoPriceWhenNoPremiumListConfigured() throws Exception {
    createTld("ghost");
    persistResource(
        new Registry.Builder()
            .setTldStr("ghost")
            .setPremiumPricingEngine(StaticPremiumListPricingEngine.NAME)
            .build());
    assertThat(Registry.get("ghost").getPremiumList()).isNull();
    assertThat(getPremiumPrice("blah", Registry.get("ghost"))).isAbsent();
  }

  @Test
  public void testGetPremiumPrice_throwsExceptionWhenNonExistentPremiumListConfigured()
      throws Exception {
    deletePremiumList(PremiumList.get("tld").get());
    thrown.expect(IllegalStateException.class, "Could not load premium list 'tld'");
    getPremiumPrice("blah", Registry.get("tld"));
  }

  @Test
  public void testSave_largeNumberOfEntries_succeeds() throws Exception {
    PremiumList premiumList = persistHumongousPremiumList("tld", 2500);
    assertThat(loadPremiumListEntries(premiumList)).hasSize(2500);
    assertThat(getPremiumPrice("7", Registry.get("tld"))).hasValue(Money.parse("USD 100"));
  }

  @Test
  public void testSave_updateTime_isUpdatedOnEverySave() throws Exception {
    PremiumList pl =
        savePremiumListAndEntries(
            new PremiumList.Builder().setName("tld3").build(), ImmutableList.of("slime,USD 10"));
    PremiumList newPl =
        savePremiumListAndEntries(
            new PremiumList.Builder().setName(pl.getName()).build(),
            ImmutableList.of("mutants,USD 20"));
    assertThat(newPl.getLastUpdateTime()).isGreaterThan(pl.getLastUpdateTime());
  }

  @Test
  public void testSave_creationTime_onlyUpdatedOnFirstCreation() throws Exception {
    PremiumList pl = persistPremiumList("tld3", "sludge,JPY 1000");
    PremiumList newPl = savePremiumListAndEntries(pl, ImmutableList.of("sleighbells,CHF 2000"));
    assertThat(newPl.creationTime).isEqualTo(pl.creationTime);
  }

  @Test
  public void testExists() throws Exception {
    assertThat(doesPremiumListExist("tld")).isTrue();
    assertThat(doesPremiumListExist("nonExistentPremiumList")).isFalse();
  }

  @Test
  public void testGetPremiumPrice_comesFromBloomFilter() throws Exception {
    PremiumList pl = PremiumList.get("tld").get();
    PremiumListEntry entry =
        persistResource(
            new PremiumListEntry.Builder()
                .setParent(pl.getRevisionKey())
                .setLabel("missingno")
                .setPrice(Money.parse("USD 1000"))
                .build());
    // "missingno" shouldn't be in the bloom filter, thus it should return not premium without
    // attempting to load the entity that is actually present.
    assertThat(getPremiumPrice("missingno", Registry.get("tld"))).isAbsent();
    // However, if we manually query the cache to force an entity load, it should be found.
    assertThat(
            cachePremiumListEntries.get(
                Key.create(pl.getRevisionKey(), PremiumListEntry.class, "missingno")))
        .hasValue(entry);
  }


  @Test
  public void testSave_removedPremiumListEntries_areNoLongerInDatastore() throws Exception {
    Registry registry = Registry.get("tld");
    PremiumList pl = persistPremiumList("tld", "genius,USD 10", "dolt,JPY 1000");
    assertThat(getPremiumPrice("genius", registry)).hasValue(Money.parse("USD 10"));
    assertThat(getPremiumPrice("dolt", registry)).hasValue(Money.parse("JPY 1000"));
    assertThat(ofy()
            .load()
            .type(PremiumListEntry.class)
            .parent(pl.getRevisionKey())
            .id("dolt")
            .now()
            .price)
        .isEqualTo(Money.parse("JPY 1000"));
    PremiumList pl2 =
        savePremiumListAndEntries(pl, ImmutableList.of("genius,USD 10", "savant,USD 90"));
    assertThat(getPremiumPrice("genius", registry)).hasValue(Money.parse("USD 10"));
    assertThat(getPremiumPrice("savant", registry)).hasValue(Money.parse("USD 90"));
    assertThat(getPremiumPrice("dolt", registry)).isAbsent();
    assertThat(ofy()
            .load()
            .type(PremiumListEntry.class)
            .parent(pl.getRevisionKey())
            .id("dolt")
            .now())
        .isNull();
    assertThat(ofy()
            .load()
            .type(PremiumListEntry.class)
            .parent(pl2.getRevisionKey())
            .id("dolt")
            .now())
        .isNull();
  }

  @Test
  public void testGetPremiumPrice_allLabelsAreNonPremium_whenNotInList() throws Exception {
    assertThat(getPremiumPrice("blah", Registry.get("tld"))).isAbsent();
    assertThat(getPremiumPrice("slinge", Registry.get("tld"))).isAbsent();
  }

  @Test
  public void testSave_simple() throws Exception {
    PremiumList pl =
        savePremiumListAndEntries(
            new PremiumList.Builder().setName("tld2").build(),
            ImmutableList.of("lol , USD 999 # yupper rooni "));
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setPremiumList(pl).build());
    assertThat(getPremiumPrice("lol", Registry.get("tld"))).hasValue(Money.parse("USD 999"));
    assertThat(getPremiumPrice("lol ", Registry.get("tld"))).isAbsent();
    Map<String, PremiumListEntry> entries = loadPremiumListEntries(PremiumList.get("tld2").get());
    assertThat(entries.keySet()).containsExactly("lol");
    assertThat(entries).doesNotContainKey("lol ");
    PremiumListEntry entry = entries.get("lol");
    assertThat(entry.comment).isEqualTo("yupper rooni");
    assertThat(entry.price).isEqualTo(Money.parse("USD 999"));
    assertThat(entry.label).isEqualTo("lol");
  }

  @Test
  public void test_saveAndUpdateEntriesTwice() throws Exception {
    PremiumList pl =
        savePremiumListAndEntries(
            new PremiumList.Builder().setName("pl").build(), ImmutableList.of("test,USD 1"));
    Map<String, PremiumListEntry> entries = loadPremiumListEntries(pl);
    assertThat(entries.keySet()).containsExactly("test");
    assertThat(loadPremiumListEntries(PremiumList.get("pl").get())).isEqualTo(entries);
    // Save again with no changes, and clear the cache to force a re-load from Datastore.
    PremiumList resaved = savePremiumListAndEntries(pl, ImmutableList.of("test,USD 1"));
    ofy().clearSessionCache();
    Map<String, PremiumListEntry> entriesReloaded =
        loadPremiumListEntries(PremiumList.get("pl").get());
    assertThat(entriesReloaded).hasSize(1);
    assertThat(entriesReloaded).containsKey("test");
    assertThat(entriesReloaded.get("test").parent).isEqualTo(resaved.getRevisionKey());
  }

  @Test
  public void testDelete() throws Exception {
    persistPremiumList("gtld1", "trombone,USD 10");
    assertThat(PremiumList.get("gtld1")).isPresent();
    Key<PremiumListRevision> parent = PremiumList.get("gtld1").get().getRevisionKey();
    deletePremiumList(PremiumList.get("gtld1").get());
    assertThat(PremiumList.get("gtld1")).isAbsent();
    assertThat(ofy().load().type(PremiumListEntry.class).ancestor(parent).list()).isEmpty();
  }

  @Test
  public void testDelete_largeNumberOfEntries_succeeds() {
    persistHumongousPremiumList("ginormous", 2500);
    deletePremiumList(PremiumList.get("ginormous").get());
    assertThat(PremiumList.get("ginormous")).isAbsent();
  }

  @Test
  public void testDelete_failsWhenListDoesntExist() throws Exception {
    thrown.expect(IllegalStateException.class);
    deletePremiumList(PremiumList.get("tld-premium-blah").get());
  }

  /** Persists a premium list with a specified number of nonsense entries. */
  private PremiumList persistHumongousPremiumList(String name, int size) {
    String[] entries = new String[size];
    for (int i = 0; i < size; i++) {
      entries[i] = String.format("%d,USD 100 # blahz", i);
    }
    return persistPremiumList(name, entries);
  }
}
