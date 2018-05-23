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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.monitoring.metrics.contrib.DistributionMetricSubject.assertThat;
import static com.google.monitoring.metrics.contrib.LongMetricSubject.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.label.DomainLabelMetrics.PremiumListCheckOutcome.BLOOM_FILTER_NEGATIVE;
import static google.registry.model.registry.label.DomainLabelMetrics.PremiumListCheckOutcome.CACHED_NEGATIVE;
import static google.registry.model.registry.label.DomainLabelMetrics.PremiumListCheckOutcome.CACHED_POSITIVE;
import static google.registry.model.registry.label.DomainLabelMetrics.PremiumListCheckOutcome.UNCACHED_NEGATIVE;
import static google.registry.model.registry.label.DomainLabelMetrics.PremiumListCheckOutcome.UNCACHED_POSITIVE;
import static google.registry.model.registry.label.DomainLabelMetrics.premiumListChecks;
import static google.registry.model.registry.label.DomainLabelMetrics.premiumListProcessingTime;
import static google.registry.model.registry.label.PremiumListUtils.deletePremiumList;
import static google.registry.model.registry.label.PremiumListUtils.doesPremiumListExist;
import static google.registry.model.registry.label.PremiumListUtils.getPremiumPrice;
import static google.registry.model.registry.label.PremiumListUtils.savePremiumListAndEntries;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.loadPremiumListEntries;
import static google.registry.testing.DatastoreHelper.persistPremiumList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;
import static org.joda.time.Duration.standardMinutes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import google.registry.dns.writer.VoidDnsWriter;
import google.registry.model.pricing.StaticPremiumListPricingEngine;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.PremiumList.PremiumListEntry;
import google.registry.model.registry.label.PremiumList.PremiumListRevision;
import google.registry.testing.AppEngineRule;
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

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Before
  public void before() throws Exception {
    // createTld() overwrites the premium list, so call it first.
    PremiumList.cachePremiumListEntries =
        PremiumList.createCachePremiumListEntries(standardMinutes(1));
    createTld("tld");
    PremiumList pl =
        persistPremiumList(
            "tld",
            "lol,USD 999 # yup",
            "rich,USD 1999 #tada",
            "icann,JPY 100",
            "johnny-be-goode,USD 20.50");
    persistResource(Registry.get("tld").asBuilder().setPremiumList(pl).build());
    premiumListChecks.reset();
    premiumListProcessingTime.reset();
  }

  void assertMetricOutcomeCount(int checkCount, DomainLabelMetrics.PremiumListCheckOutcome outcome)
      throws Exception {
    assertThat(premiumListChecks)
        .hasValueForLabels(checkCount, "tld", "tld", outcome.toString())
        .and()
        .hasNoOtherValues();
    assertThat(premiumListProcessingTime)
        .hasAnyValueForLabels("tld", "tld", outcome.toString())
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void testGetPremiumPrice_returnsNoPriceWhenNoPremiumListConfigured() throws Exception {
    createTld("ghost");
    persistResource(
        new Registry.Builder()
            .setTldStr("ghost")
            .setPremiumPricingEngine(StaticPremiumListPricingEngine.NAME)
            .setDnsWriters(ImmutableSet.of(VoidDnsWriter.NAME))
            .build());
    assertThat(Registry.get("ghost").getPremiumList()).isNull();
    assertThat(getPremiumPrice("blah", Registry.get("ghost"))).isEmpty();
    assertThat(premiumListChecks).hasNoOtherValues();
    assertThat(premiumListProcessingTime).hasNoOtherValues();
  }

  @Test
  public void testGetPremiumPrice_throwsExceptionWhenNonExistentPremiumListConfigured()
      throws Exception {
    deletePremiumList(PremiumList.getUncached("tld").get());
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> getPremiumPrice("blah", Registry.get("tld")));
    assertThat(thrown).hasMessageThat().contains("Could not load premium list 'tld'");
  }

  @Test
  public void testSave_largeNumberOfEntries_succeeds() throws Exception {
    PremiumList premiumList = persistHumongousPremiumList("tld", 2500);
    assertThat(loadPremiumListEntries(premiumList)).hasSize(2500);
    assertThat(getPremiumPrice("7", Registry.get("tld"))).hasValue(Money.parse("USD 100"));
    assertMetricOutcomeCount(1, UNCACHED_POSITIVE);
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
    PremiumList pl = PremiumList.getCached("tld").get();
    PremiumListEntry entry =
        persistResource(
            new PremiumListEntry.Builder()
                .setParent(pl.getRevisionKey())
                .setLabel("missingno")
                .setPrice(Money.parse("USD 1000"))
                .build());
    // "missingno" shouldn't be in the Bloom filter, thus it should return not premium without
    // attempting to load the entity that is actually present.
    assertThat(getPremiumPrice("missingno", Registry.get("tld"))).isEmpty();
    // However, if we manually query the cache to force an entity load, it should be found.
    assertThat(
            PremiumList.cachePremiumListEntries.get(
                Key.create(pl.getRevisionKey(), PremiumListEntry.class, "missingno")))
        .hasValue(entry);
    assertMetricOutcomeCount(1, BLOOM_FILTER_NEGATIVE);
  }

  @Test
  public void testGetPremiumPrice_cachedSecondTime() throws Exception {
    assertThat(getPremiumPrice("rich", Registry.get("tld"))).hasValue(Money.parse("USD 1999"));
    assertThat(getPremiumPrice("rich", Registry.get("tld"))).hasValue(Money.parse("USD 1999"));
    assertThat(premiumListChecks)
        .hasValueForLabels(1, "tld", "tld", UNCACHED_POSITIVE.toString())
        .and()
        .hasValueForLabels(1, "tld", "tld", CACHED_POSITIVE.toString())
        .and()
        .hasNoOtherValues();
    assertThat(premiumListProcessingTime)
        .hasAnyValueForLabels("tld", "tld", UNCACHED_POSITIVE.toString())
        .and()
        .hasAnyValueForLabels("tld", "tld", CACHED_POSITIVE.toString())
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void testGetPremiumPrice_bloomFilterFalsePositive() throws Exception {
    // Remove one of the premium list entries from behind the Bloom filter's back.
    ofy()
        .transactNew(
            new VoidWork() {
              @Override
              public void vrun() {
                ofy()
                    .delete()
                    .keys(
                        Key.create(
                            PremiumList.getCached("tld").get().getRevisionKey(),
                            PremiumListEntry.class,
                            "rich"));
              }
            });
    ofy().clearSessionCache();

    assertThat(getPremiumPrice("rich", Registry.get("tld"))).isEmpty();
    assertThat(getPremiumPrice("rich", Registry.get("tld"))).isEmpty();

    assertThat(premiumListChecks)
        .hasValueForLabels(1, "tld", "tld", UNCACHED_NEGATIVE.toString())
        .and()
        .hasValueForLabels(1, "tld", "tld", CACHED_NEGATIVE.toString())
        .and()
        .hasNoOtherValues();
    assertThat(premiumListProcessingTime)
        .hasAnyValueForLabels("tld", "tld", UNCACHED_NEGATIVE.toString())
        .and()
        .hasAnyValueForLabels("tld", "tld", CACHED_NEGATIVE.toString())
        .and()
        .hasNoOtherValues();
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
    savePremiumListAndEntries(pl, ImmutableList.of("genius,USD 10", "savant,USD 90"));
    assertThat(getPremiumPrice("genius", registry)).hasValue(Money.parse("USD 10"));
    assertThat(getPremiumPrice("savant", registry)).hasValue(Money.parse("USD 90"));
    assertThat(getPremiumPrice("dolt", registry)).isEmpty();
    // TODO(b/79888775): Assert that the old premium list is enqueued for later deletion.
    assertThat(premiumListChecks)
        .hasValueForLabels(4, "tld", "tld", UNCACHED_POSITIVE.toString())
        .and()
        .hasValueForLabels(1, "tld", "tld", BLOOM_FILTER_NEGATIVE.toString())
        .and()
        .hasNoOtherValues();
    assertThat(premiumListProcessingTime)
        .hasAnyValueForLabels("tld", "tld", UNCACHED_POSITIVE.toString())
        .and()
        .hasAnyValueForLabels("tld", "tld", BLOOM_FILTER_NEGATIVE.toString())
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void testGetPremiumPrice_allLabelsAreNonPremium_whenNotInList() throws Exception {
    assertThat(getPremiumPrice("blah", Registry.get("tld"))).isEmpty();
    assertThat(getPremiumPrice("slinge", Registry.get("tld"))).isEmpty();
    assertMetricOutcomeCount(2, BLOOM_FILTER_NEGATIVE);
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
    assertThat(getPremiumPrice("lol ", Registry.get("tld"))).isEmpty();
    ImmutableMap<String, PremiumListEntry> entries =
        loadPremiumListEntries(PremiumList.getUncached("tld2").get());
    assertThat(entries.keySet()).containsExactly("lol");
    assertThat(entries).doesNotContainKey("lol ");
    PremiumListEntry entry = entries.get("lol");
    assertThat(entry.comment).isEqualTo("yupper rooni");
    assertThat(entry.price).isEqualTo(Money.parse("USD 999"));
    assertThat(entry.label).isEqualTo("lol");
    assertThat(premiumListChecks)
        .hasValueForLabels(1, "tld", "tld2", UNCACHED_POSITIVE.toString())
        .and()
        .hasValueForLabels(1, "tld", "tld2", BLOOM_FILTER_NEGATIVE.toString())
        .and()
        .hasNoOtherValues();
    assertThat(premiumListProcessingTime)
        .hasAnyValueForLabels("tld", "tld2", UNCACHED_POSITIVE.toString())
        .and()
        .hasAnyValueForLabels("tld", "tld2", BLOOM_FILTER_NEGATIVE.toString())
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void test_saveAndUpdateEntriesTwice() throws Exception {
    PremiumList pl =
        savePremiumListAndEntries(
            new PremiumList.Builder().setName("pl").build(), ImmutableList.of("test,USD 1"));
    ImmutableMap<String, PremiumListEntry> entries = loadPremiumListEntries(pl);
    assertThat(entries.keySet()).containsExactly("test");
    assertThat(loadPremiumListEntries(PremiumList.getUncached("pl").get())).isEqualTo(entries);
    // Save again with no changes, and clear the cache to force a re-load from Datastore.
    PremiumList resaved = savePremiumListAndEntries(pl, ImmutableList.of("test,USD 1"));
    ofy().clearSessionCache();
    Map<String, PremiumListEntry> entriesReloaded =
        loadPremiumListEntries(PremiumList.getUncached("pl").get());
    assertThat(entriesReloaded).hasSize(1);
    assertThat(entriesReloaded).containsKey("test");
    assertThat(entriesReloaded.get("test").parent).isEqualTo(resaved.getRevisionKey());
  }

  @Test
  public void testDelete() throws Exception {
    persistPremiumList("gtld1", "trombone,USD 10");
    assertThat(PremiumList.getUncached("gtld1")).isPresent();
    Key<PremiumListRevision> parent = PremiumList.getUncached("gtld1").get().getRevisionKey();
    deletePremiumList(PremiumList.getUncached("gtld1").get());
    assertThat(PremiumList.getUncached("gtld1")).isEmpty();
    assertThat(ofy().load().type(PremiumListEntry.class).ancestor(parent).list()).isEmpty();
  }

  @Test
  public void testDelete_largeNumberOfEntries_succeeds() {
    persistHumongousPremiumList("ginormous", 2500);
    deletePremiumList(PremiumList.getUncached("ginormous").get());
    assertThat(PremiumList.getUncached("ginormous")).isEmpty();
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
