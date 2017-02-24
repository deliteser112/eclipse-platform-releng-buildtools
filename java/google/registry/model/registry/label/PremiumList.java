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
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.partition;
import static com.google.common.hash.Funnels.unencodedCharsFunnel;
import static google.registry.config.RegistryConfig.getDomainLabelListCacheDuration;
import static google.registry.config.RegistryConfig.getSingletonCachePersistDuration;
import static google.registry.config.RegistryConfig.getStaticPremiumListMaxCachedEntries;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.allocateId;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.ofy.Ofy.RECOMMENDED_MEMCACHE_EXPIRATION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.BloomFilter;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;
import com.googlecode.objectify.cmd.Query;
import google.registry.model.Buildable;
import google.registry.model.ImmutableObject;
import google.registry.model.annotations.ReportedOn;
import google.registry.model.registry.Registry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * A premium list entity, persisted to Datastore, that is used to check domain label prices.
 */
@ReportedOn
@Entity
@Cache(expirationSeconds = RECOMMENDED_MEMCACHE_EXPIRATION)
public final class PremiumList extends BaseDomainLabelList<Money, PremiumList.PremiumListEntry> {

  /** The number of premium list entry entities that are created and deleted per batch. */
  private static final int TRANSACTION_BATCH_SIZE = 200;

  /** Stores the revision key for the set of currently used premium list entry entities. */
  Key<PremiumListRevision> revisionKey;

  /** Virtual parent entity for premium list entry entities associated with a single revision. */
  @ReportedOn
  @Entity
  @Cache(expirationSeconds = RECOMMENDED_MEMCACHE_EXPIRATION)
  public static class PremiumListRevision extends ImmutableObject {

    @Parent
    Key<PremiumList> parent;

    @Id
    long revisionId;

    /**
     * A bloom filter that is used to determine efficiently and quickly whether a label might be
     * premium.
     *
     * <p>If the label might be premium, then the premium list entry must be loaded by key and
     * checked for existence.  Otherwise, we know it's not premium, and no Datastore load is
     * required.
     */
    BloomFilter<String> probablePremiumLabels;

    /**
     * The maximum size of the bloom filter.
     *
     * <p>Trying to set it any larger will throw an error, as we know it won't fit into a Datastore
     * entity. We use 90% of the 1 MB Datastore limit to leave some wriggle room for the other
     * fields and miscellaneous entity serialization overhead.
     */
    private static final int MAX_BLOOM_FILTER_BYTES = 900000;

    /** Returns a new PremiumListRevision for the given key and premium list map. */
    @VisibleForTesting
    public static PremiumListRevision create(PremiumList parent, Set<String> premiumLabels) {
      PremiumListRevision revision = new PremiumListRevision();
      revision.parent = Key.create(parent);
      revision.revisionId = allocateId();
      // All premium list labels are already punycoded, so don't perform any further character
      // encoding on them.
      revision.probablePremiumLabels =
          BloomFilter.create(unencodedCharsFunnel(), premiumLabels.size());
      for (String label : premiumLabels) {
        revision.probablePremiumLabels.put(label);
      }
      try {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        revision.probablePremiumLabels.writeTo(bos);
        checkArgument(bos.size() <= MAX_BLOOM_FILTER_BYTES,
            "Too many premium labels were specified; bloom filter exceeds max entity size");
      } catch (IOException e) {
        throw new IllegalStateException("Could not serialize premium labels bloom filter", e);
      }
      return revision;
    }
  }

  /**
   * In-memory cache for premium lists.
   *
   * <p>This is cached for a shorter duration because we need to periodically reload this entity to
   * check if a new revision has been published, and if so, then use that.
   */
  private static final LoadingCache<String, PremiumList> cachePremiumLists =
      CacheBuilder.newBuilder()
          .expireAfterWrite(getDomainLabelListCacheDuration().getMillis(), MILLISECONDS)
          .build(new CacheLoader<String, PremiumList>() {
            @Override
            public PremiumList load(final String listName) {
              return ofy().doTransactionless(new Work<PremiumList>() {
                @Override
                public PremiumList run() {
                  return ofy().load()
                      .type(PremiumList.class)
                      .parent(getCrossTldKey())
                      .id(listName)
                      .now();
                }});
            }});

  /**
   * In-memory cache for {@link PremiumListRevision}s, used for retrieving bloom filters quickly.
   *
   * <p>This is cached for a long duration (essentially indefinitely) because a given
   * {@link PremiumListRevision} is immutable and cannot ever be changed once created, so its cache
   * need not ever expire.
   */
  private static final LoadingCache<Key<PremiumListRevision>, PremiumListRevision>
      cachePremiumListRevisions =
          CacheBuilder.newBuilder()
              .expireAfterWrite(getSingletonCachePersistDuration().getMillis(), MILLISECONDS)
              .build(
                  new CacheLoader<Key<PremiumListRevision>, PremiumListRevision>() {
                    @Override
                    public PremiumListRevision load(final Key<PremiumListRevision> revisionKey) {
                      return ofy()
                          .doTransactionless(
                              new Work<PremiumListRevision>() {
                                @Override
                                public PremiumListRevision run() {
                                  return ofy().load().key(revisionKey).now();
                                }});
                    }});

  /**
   * In-memory cache for {@link PremiumListEntry}s for a given label and {@link PremiumListRevision}
   *
   * <p>Because the PremiumList itself makes up part of the PremiumListRevision's key, this is
   * specific to a given premium list. Premium list entries might not be present, as indicated by
   * the Optional wrapper, and we want to cache that as well.
   *
   * <p>This is cached for a long duration (essentially indefinitely) because a given {@link
   * PremiumListRevision} and its child {@link PremiumListEntry}s are immutable and cannot ever be
   * changed once created, so the cache need not ever expire.
   *
   * <p>A maximum size is set here on the cache because it can potentially grow too big to fit in
   * memory if there are a very large number of premium list entries in the system. The least-
   * accessed entries will be evicted first.
   */
  @VisibleForTesting
  static final LoadingCache<Key<PremiumListEntry>, Optional<PremiumListEntry>>
      cachePremiumListEntries =
          CacheBuilder.newBuilder()
              .expireAfterWrite(getSingletonCachePersistDuration().getMillis(), MILLISECONDS)
              .maximumSize(getStaticPremiumListMaxCachedEntries())
              .build(
                  new CacheLoader<Key<PremiumListEntry>, Optional<PremiumListEntry>>() {
                    @Override
                    public Optional<PremiumListEntry> load(final Key<PremiumListEntry> entryKey) {
                      return ofy()
                          .doTransactionless(
                              new Work<Optional<PremiumListEntry>>() {
                                @Override
                                public Optional<PremiumListEntry> run() {
                                  return Optional.fromNullable(ofy().load().key(entryKey).now());
                                }});
                    }});

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
    Optional<PremiumList> optionalPremiumList = get(listName);
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
   * Loads and returns the entire premium list map.
   *
   * <p>This load operation is quite expensive for large premium lists because each premium list
   * entry is a separate Datastore entity, and loading them this way bypasses the in-memory caches.
   * Do not use this method if all you need to do is check the price of a small number of labels!
   */
  @VisibleForTesting
  public Map<String, PremiumListEntry> loadPremiumListEntries() {
    try {
      ImmutableMap.Builder<String, PremiumListEntry> entriesMap = new ImmutableMap.Builder<>();
      if (revisionKey != null) {
        for (PremiumListEntry entry : queryEntriesForCurrentRevision()) {
          entriesMap.put(entry.getLabel(), entry);
        }
      }
      return entriesMap.build();
    } catch (Exception e) {
      throw new RuntimeException("Could not retrieve entries for premium list " + name, e);
    }
  }

  @VisibleForTesting
  public Key<PremiumListRevision> getRevisionKey() {
    return revisionKey;
  }

  /** Returns the PremiumList with the specified name. */
  public static Optional<PremiumList> get(String name) {
    try {
      return Optional.of(cachePremiumLists.get(name));
    } catch (InvalidCacheLoadException e) {
      return Optional.<PremiumList> absent();
    } catch (ExecutionException e) {
      throw new UncheckedExecutionException("Could not retrieve premium list named " + name, e);
    }
  }

  /** Returns whether a PremiumList of the given name exists, bypassing the cache. */
  public static boolean exists(String name) {
    return ofy().load().key(Key.create(getCrossTldKey(), PremiumList.class, name)).now() != null;
  }

  /**
   * A premium list entry entity, persisted to Datastore.  Each instance represents the price of a
   * single label on a given TLD.
   */
  @ReportedOn
  @Entity
  @Cache(expirationSeconds = RECOMMENDED_MEMCACHE_EXPIRATION)
  public static class PremiumListEntry extends DomainLabelEntry<Money, PremiumListEntry>
      implements Buildable {

    @Parent
    Key<PremiumListRevision> parent;

    Money price;

    @Override
    public Money getValue() {
      return price;
    }

    @Override
    public Builder asBuilder() {
      return new Builder(clone(this));
    }

    /**
     * A builder for constructing {@link PremiumList} objects, since they are immutable.
     */
    public static class Builder extends DomainLabelEntry.Builder<PremiumListEntry, Builder> {
      public Builder() {}

      private Builder(PremiumListEntry instance) {
        super(instance);
      }

      public Builder setParent(Key<PremiumListRevision> parentKey) {
        getInstance().parent = parentKey;
        return this;
      }

      public Builder setPrice(Money price) {
        getInstance().price = price;
        return this;
      }
    }
  }

  @Override
  @Nullable
  PremiumListEntry createFromLine(String originalLine) {
    List<String> lineAndComment = splitOnComment(originalLine);
    if (lineAndComment.isEmpty()) {
      return null;
    }
    String line = lineAndComment.get(0);
    String comment = lineAndComment.get(1);
    List<String> parts = Splitter.on(',').trimResults().splitToList(line);
    checkArgument(parts.size() == 2, "Could not parse line in premium list: %s", originalLine);
    return new PremiumListEntry.Builder()
        .setLabel(parts.get(0))
        .setPrice(Money.parse(parts.get(1)))
        .setComment(comment)
        .build();
  }

  public static PremiumList saveWithEntries(
      PremiumList premiumList, Iterable<String> premiumListLines) {
    return saveWithEntries(premiumList, premiumList.parse(premiumListLines));
  }

  /** Re-parents the given {@link PremiumListEntry}s on the given {@link PremiumListRevision}. */
  public static ImmutableSet<PremiumListEntry> parentEntriesOnRevision(
      Iterable<PremiumListEntry> entries, final Key<PremiumListRevision> revisionKey) {
    return FluentIterable.from(firstNonNull(entries, ImmutableSet.of()))
        .transform(
            new Function<PremiumListEntry, PremiumListEntry>() {
              @Override
              public PremiumListEntry apply(PremiumListEntry entry) {
                return entry.asBuilder().setParent(revisionKey).build();
              }
            })
        .toSet();
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
  public static PremiumList saveWithEntries(
      final PremiumList premiumList, ImmutableMap<String, PremiumListEntry> premiumListEntries) {
    final Optional<PremiumList> oldPremiumList = get(premiumList.getName());

    // Create the new revision (with its bloom filter) and parent the entries on it.
    final PremiumListRevision newRevision =
        PremiumListRevision.create(premiumList, premiumListEntries.keySet());
    final Key<PremiumListRevision> newRevisionKey = Key.create(newRevision);
    ImmutableSet<PremiumListEntry> parentedEntries =
        parentEntriesOnRevision(
            firstNonNull(premiumListEntries.values(), ImmutableSet.of()), newRevisionKey);

    // Save the new child entities in a series of transactions.
    for (final List<PremiumListEntry> batch : partition(parentedEntries, TRANSACTION_BATCH_SIZE)) {
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
      oldPremiumList.get().deleteRevisionAndEntries();
    }
    return updated;
  }

  @Override
  public boolean refersToKey(Registry registry, Key<? extends BaseDomainLabelList<?, ?>> key) {
    return Objects.equals(registry.getPremiumList(), key);
  }

  /** Deletes the PremiumList and all of its child entities. */
  public void delete() {
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().entity(PremiumList.this);
      }});
    deleteRevisionAndEntries();
    cachePremiumLists.invalidate(name);
  }

  private void deleteRevisionAndEntries() {
    if (revisionKey == null) {
      return;
    }
    for (final List<Key<PremiumListEntry>> batch : partition(
        queryEntriesForCurrentRevision().keys(),
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
        ofy().delete().key(revisionKey);
      }});
  }

  private Query<PremiumListEntry> queryEntriesForCurrentRevision() {
    return ofy().load().type(PremiumListEntry.class).ancestor(revisionKey);
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for constructing {@link PremiumList} objects, since they are immutable.  */
  public static class Builder extends BaseDomainLabelList.Builder<PremiumList, Builder> {

    public Builder() {}

    private Builder(PremiumList instance) {
      super(instance);
    }

    public Builder setRevision(Key<PremiumListRevision> revision) {
      getInstance().revisionKey = revision;
      return this;
    }

    @Override
    public PremiumList build() {
      return super.build();
    }
  }
}
