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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.partition;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.label.PremiumList.cachePremiumListEntries;
import static google.registry.model.registry.label.PremiumList.cachePremiumListRevisions;
import static google.registry.model.registry.label.PremiumList.cachePremiumLists;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.PremiumList.PremiumListEntry;
import google.registry.model.registry.label.PremiumList.PremiumListRevision;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.joda.money.Money;
import org.joda.time.DateTime;

/** Static helper methods for working with {@link PremiumList}s. */
public final class PremiumListUtils {

  /** The number of premium list entry entities that are created and deleted per batch. */
  static final int TRANSACTION_BATCH_SIZE = 200;

  /**
   * Returns the premium price for the specified label and registry, or absent if the label is not
   * premium.
   */
  public static Optional<Money> getPremiumPrice(String label, Registry registry) {
    // If the registry has no configured premium list, then no labels are premium.
    if (registry.getPremiumList() == null) {
      return Optional.<Money> absent();
    }
    String listName = registry.getPremiumList().getName();
    Optional<PremiumList> optionalPremiumList = PremiumList.get(listName);
    checkState(optionalPremiumList.isPresent(), "Could not load premium list '%s'", listName);
    PremiumList premiumList = optionalPremiumList.get();
    PremiumListRevision revision;
    try {
      revision = cachePremiumListRevisions.get(premiumList.getRevisionKey());
    } catch (InvalidCacheLoadException | ExecutionException e) {
      throw new RuntimeException(
          "Could not load premium list revision " + premiumList.getRevisionKey(), e);
    }
    checkState(
        revision.probablePremiumLabels != null,
        "Probable premium labels bloom filter is null on revision '%s'",
        premiumList.getRevisionKey());

    if (revision.probablePremiumLabels.mightContain(label)) {
      Key<PremiumListEntry> entryKey =
          Key.create(premiumList.getRevisionKey(), PremiumListEntry.class, label);
      try {
        Optional<PremiumListEntry> entry = cachePremiumListEntries.get(entryKey);
        return (entry.isPresent()) ? Optional.of(entry.get().getValue()) : Optional.<Money>absent();
      } catch (InvalidCacheLoadException | ExecutionException e) {
        throw new RuntimeException("Could not load premium list entry " + entryKey, e);
      }
    } else {
      return Optional.<Money>absent();
    }
  }

  /**
   * Persists a new or updated PremiumList object and its descendant entities to Datastore.
   *
   * <p>The flow here is: save the new premium list entries parented on that revision entity,
   * save/update the PremiumList, and then delete the old premium list entries associated with the
   * old revision.
   *
   * <p>This is the only valid way to save these kinds of entities!
   */
  public static PremiumList savePremiumListAndEntries(
      final PremiumList premiumList,
      ImmutableMap<String, PremiumListEntry> premiumListEntries) {
    final Optional<PremiumList> oldPremiumList = PremiumList.get(premiumList.getName());

    // Create the new revision (with its bloom filter) and parent the entries on it.
    final PremiumListRevision newRevision =
        PremiumListRevision.create(premiumList, premiumListEntries.keySet());
    final Key<PremiumListRevision> newRevisionKey = Key.create(newRevision);
    ImmutableSet<PremiumListEntry> parentedEntries =
        parentPremiumListEntriesOnRevision(premiumListEntries.values(), newRevisionKey);

    // Save the new child entities in a series of transactions.
    for (final List<PremiumListEntry> batch :
        partition(parentedEntries, TRANSACTION_BATCH_SIZE)) {
      ofy().transactNew(new VoidWork() {
        @Override
        public void vrun() {
          ofy().save().entities(batch);
        }});
    }

    // Save the new PremiumList and revision itself.
    PremiumList updated = ofy().transactNew(new Work<PremiumList>() {
        @Override
        public PremiumList run() {
          DateTime now = ofy().getTransactionTime();
          // Assert that the premium list hasn't been changed since we started this process.
          PremiumList existing = ofy().load()
              .type(PremiumList.class)
              .parent(getCrossTldKey())
              .id(premiumList.getName())
              .now();
          checkState(
              Objects.equals(existing, oldPremiumList.orNull()),
              "PremiumList was concurrently edited");
          PremiumList newList = premiumList.asBuilder()
              .setLastUpdateTime(now)
              .setCreationTime(
                  oldPremiumList.isPresent() ? oldPremiumList.get().creationTime : now)
              .setRevision(newRevisionKey)
              .build();
          ofy().save().entities(newList, newRevision);
          return newList;
        }});
    // Update the cache.
    cachePremiumLists.put(premiumList.getName(), updated);
    // Delete the entities under the old PremiumList.
    if (oldPremiumList.isPresent()) {
      deleteRevisionAndEntriesOfPremiumList(oldPremiumList.get());
    }
    return updated;
  }

  public static PremiumList savePremiumListAndEntries(
      PremiumList premiumList, Iterable<String> premiumListLines) {
    return savePremiumListAndEntries(premiumList, premiumList.parse(premiumListLines));
  }

  /** Re-parents the given {@link PremiumListEntry}s on the given {@link PremiumListRevision}. */
  @VisibleForTesting
  public static ImmutableSet<PremiumListEntry> parentPremiumListEntriesOnRevision(
      Iterable<PremiumListEntry> entries, final Key<PremiumListRevision> revisionKey) {
    return FluentIterable.from(entries)
        .transform(
            new Function<PremiumListEntry, PremiumListEntry>() {
              @Override
              public PremiumListEntry apply(PremiumListEntry entry) {
                return entry.asBuilder().setParent(revisionKey).build();
              }
            })
        .toSet();
  }

  /** Deletes the PremiumList and all of its child entities. */
  public static void deletePremiumList(final PremiumList premiumList) {
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().entity(premiumList);
      }});
    deleteRevisionAndEntriesOfPremiumList(premiumList);
    cachePremiumLists.invalidate(premiumList.getName());
  }

  static void deleteRevisionAndEntriesOfPremiumList(final PremiumList premiumList) {
    if (premiumList.getRevisionKey() == null) {
      return;
    }
    for (final List<Key<PremiumListEntry>> batch : partition(
        ofy().load().type(PremiumListEntry.class).ancestor(premiumList.revisionKey).keys(),
        TRANSACTION_BATCH_SIZE)) {
      ofy().transactNew(new VoidWork() {
        @Override
        public void vrun() {
          ofy().delete().keys(batch);
        }});
    }
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().key(premiumList.getRevisionKey());
      }});
  }

  /** Returns whether a PremiumList of the given name exists, bypassing the cache. */
  public static boolean doesPremiumListExist(String name) {
    return ofy().load().key(Key.create(getCrossTldKey(), PremiumList.class, name)).now() != null;
  }

  private PremiumListUtils() {}
}
