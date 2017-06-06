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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static google.registry.config.RegistryConfig.getDomainLabelListCacheDuration;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.label.ReservationType.FULLY_BLOCKED;
import static google.registry.model.registry.label.ReservationType.NAMESERVER_RESTRICTED;
import static google.registry.model.registry.label.ReservationType.RESERVED_FOR_ANCHOR_TENANT;
import static google.registry.util.CollectionUtils.nullToEmpty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Embed;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Mapify;
import com.googlecode.objectify.mapper.Mapper;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.DomainLabelMetrics.MetricsReservedListMatch;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/**
 * A reserved list entity, persisted to Datastore, that is used to check domain label reservations.
 */
@Entity
public final class ReservedList
    extends BaseDomainLabelList<ReservationType, ReservedList.ReservedListEntry> {

  @Mapify(ReservedListEntry.LabelMapper.class)
  Map<String, ReservedListEntry> reservedListMap;

  boolean shouldPublish = true;

  /**
   * A reserved list entry entity, persisted to Datastore, that represents a single label and its
   * reservation type.
   */
  @Embed
  public static class ReservedListEntry
      extends DomainLabelEntry<ReservationType, ReservedListEntry> {

    ReservationType reservationType;

    /**
     * Contains the auth code necessary to register a domain with this label. Note that this field
     * will only ever be populated for entries with type RESERVED_FOR_ANCHOR_TENANT.
     */
    String authCode;

    /**
     * Contains a comma-delimited list of the fully qualified hostnames of the nameservers that can
     * be set on a domain with this label (only applicable to NAMESERVER_RESTRICTED).
     *
     * <p>A String field is persisted because Objectify 4 does not allow multi-dimensional
     * collections in embedded entities.
     *
     * @see <a
     *     href="https://github.com/objectify/objectify-legacy-wiki/blob/v4/Entities.wiki#embedding.">Embedding</a>
     */
    String allowedNameservers;

    /** Mapper for use with @Mapify */
    static class LabelMapper implements Mapper<String, ReservedListEntry> {
      @Override
      public String getKey(ReservedListEntry entry) {
        return entry.getLabel();
      }
    }

    /**
     * Creates a {@link ReservedListEntry} from label, reservation type, and optionally additional
     * restrictions
     *
     * <p>The additional restricitno can be the authCode for anchor tenant or the allowed
     * nameservers (in a colon-separated string) for nameserver-restricted domains.
     */
    public static ReservedListEntry create(
        String label,
        ReservationType reservationType,
        @Nullable String restrictions,
        String comment) {
      ReservedListEntry entry = new ReservedListEntry();
      if (restrictions != null) {
        checkArgument(
            reservationType == RESERVED_FOR_ANCHOR_TENANT
                || reservationType == NAMESERVER_RESTRICTED,
            "Only anchor tenant and nameserver restricted reservations "
                + "should have restrictions imposed");
        if (reservationType == RESERVED_FOR_ANCHOR_TENANT) {
          entry.authCode = restrictions;
        } else if (reservationType == NAMESERVER_RESTRICTED) {
          Set<String> allowedNameservers =
              ImmutableSet.copyOf(Splitter.on(':').trimResults().split(restrictions));
          checkNameserversAreValid(allowedNameservers);
          entry.allowedNameservers = Joiner.on(',').join(allowedNameservers);
        }
      } else {
        checkArgument(reservationType != RESERVED_FOR_ANCHOR_TENANT,
            "Anchor tenant reservations must have an auth code configured");
        checkArgument(
            reservationType != NAMESERVER_RESTRICTED,
            "Nameserver restricted reservations must have at least one nameserver configured");
      }
      entry.label = label;
      entry.comment = comment;
      entry.reservationType = reservationType;
      return entry;
    }

    private static void checkNameserversAreValid(Set<String> nameservers) {
      for (String nameserver : nameservers) {
        // A domain name with fewer than two parts cannot be a hostname, as a nameserver should be.
        checkArgument(
            InternetDomainName.from(nameserver).parts().size() >= 3,
            "%s is not a valid nameserver hostname",
            nameserver);
      }
    }

    @Override
    public ReservationType getValue() {
      return reservationType;
    }

    public String getAuthCode() {
      return authCode;
    }

    public ImmutableSet<String> getAllowedNameservers() {
      return ImmutableSet.copyOf(Splitter.on(',').splitToList(allowedNameservers));
    }
  }

  @Override
  protected boolean refersToKey(Registry registry, Key<? extends BaseDomainLabelList<?, ?>> key) {
    return registry.getReservedLists().contains(key);
  }

  /** Determines whether the ReservedList is in use on any Registry */
  public boolean isInUse() {
    return !getReferencingTlds().isEmpty();
  }

  /**
   * Returns whether this reserved list is included in the concatenated list of reserved terms
   * published to Google Drive for viewing by registrars.
   */
  public boolean getShouldPublish() {
    return shouldPublish;
  }

  public ImmutableMap<String, ReservedListEntry> getReservedListEntries() {
    return ImmutableMap.copyOf(nullToEmpty(reservedListMap));
  }

  /**
   * Gets a ReservedList by name using the caching layer.
   *
   * @return An Optional<ReservedList> that has a value if a reserved list exists by the given name,
   *     or absent if not.
   * @throws UncheckedExecutionException if some other error occurs while trying to load the
   *     ReservedList from the cache or Datastore.
   */
  public static Optional<ReservedList> get(String listName) {
    return getFromCache(listName, cache);
  }

  /** Loads a ReservedList from its Objectify key. */
  public static Optional<ReservedList> load(Key<ReservedList> key) {
    return get(key.getName());
  }

  /**
   * Queries the set of all reserved lists associated with the specified TLD and returns the
   * reservation types of the label.
   *
   * <p>If the label is in none of the lists, it returns an empty set.
   */
  public static ImmutableSet<ReservationType> getReservationTypes(String label, String tld) {
    checkNotNull(label, "label");
    if (label.length() == 0) {
      return ImmutableSet.of(FULLY_BLOCKED);
    }
    return FluentIterable.from(getReservedListEntries(label, tld))
        .transform(
            new Function<ReservedListEntry, ReservationType>() {
              @Override
              public ReservationType apply(ReservedListEntry reservedListEntry) {
                return reservedListEntry.reservationType;
              }
            })
        .toSet();
  }

  /**
   * Returns true if the given label and TLD is reserved for an anchor tenant, and the given auth
   * code matches the one set on the reservation. If there are multiple anchor tenant entries fo
   * this label, all the auth codes need to be the same and match the given one, otherwise an
   * exception is thrown.
   */
  public static boolean matchesAnchorTenantReservation(
      InternetDomainName domainName, String authCode) {
    ImmutableSet<ReservedListEntry> entries =
        getReservedListEntries(domainName.parts().get(0), domainName.parent().toString());

    Set<String> domainAuthCodes = new HashSet<>();
    for (ReservedListEntry entry : entries) {
      if (entry.reservationType == RESERVED_FOR_ANCHOR_TENANT) {
        domainAuthCodes.add(entry.getAuthCode());
      }
    }
    checkState(
        domainAuthCodes.size() <= 1, "There are conflicting auth codes for domain: %s", domainName);

    return !domainAuthCodes.isEmpty() && getOnlyElement(domainAuthCodes).equals(authCode);
  }

  /**
   * Returns the set of nameservers that can be set on the given domain.
   *
   * <p>The allowed nameservers are the intersection of all allowed nameservers for the given domain
   * across all reserved lists. Returns an empty set if not applicable, i. e. the label for the
   * domain is not set with {@code NAMESERVER_RESTRICTED} reservation type.
   */
  public static ImmutableSet<String> getAllowedNameservers(InternetDomainName domainName) {
    HashSet<String> allowedNameservers = new HashSet<>();
    boolean foundFirstNameserverRestricted = false;
    for (ReservedListEntry entry :
        getReservedListEntries(domainName.parts().get(0), domainName.parent().toString())) {
      if (entry.reservationType == NAMESERVER_RESTRICTED) {
        if (foundFirstNameserverRestricted) {
          allowedNameservers.retainAll(entry.getAllowedNameservers());
        } else {
          allowedNameservers = new HashSet<String>(entry.getAllowedNameservers());
          foundFirstNameserverRestricted = true;
        }
      }
    }
    return ImmutableSet.copyOf(allowedNameservers);
  }


  /**
   * Helper function to retrieve the entries associated with this label and TLD, or an empty set if
   * no such entry exists.
   */
  private static ImmutableSet<ReservedListEntry> getReservedListEntries(String label, String tld) {
    DateTime startTime = DateTime.now(UTC);
    Registry registry = Registry.get(checkNotNull(tld, "tld"));
    ImmutableSet<Key<ReservedList>> reservedLists = registry.getReservedLists();
    ImmutableSet<ReservedList> lists = loadReservedLists(reservedLists);
    ImmutableSet.Builder<ReservedListEntry> entriesBuilder = new ImmutableSet.Builder<>();
    ImmutableSet.Builder<MetricsReservedListMatch> metricMatchesBuilder =
        new ImmutableSet.Builder<>();

    // Loop through all reservation lists and add each of them.
    for (ReservedList rl : lists) {
      if (rl.getReservedListEntries().containsKey(label)) {
        ReservedListEntry entry = rl.getReservedListEntries().get(label);
        entriesBuilder.add(entry);
        metricMatchesBuilder.add(
            MetricsReservedListMatch.create(rl.getName(), entry.reservationType));
      }
    }
    ImmutableSet<ReservedListEntry> entries = entriesBuilder.build();
    DomainLabelMetrics.recordReservedListCheckOutcome(
        tld, metricMatchesBuilder.build(), DateTime.now(UTC).getMillis() - startTime.getMillis());
    return entries;
  }

  private static ImmutableSet<ReservedList> loadReservedLists(
      ImmutableSet<Key<ReservedList>> reservedListKeys) {
    ImmutableSet.Builder<ReservedList> builder = new ImmutableSet.Builder<>();
    for (Key<ReservedList> listKey : reservedListKeys) {
      try {
        builder.add(cache.get(listKey.getName()));
      } catch (ExecutionException e) {
        throw new UncheckedExecutionException(String.format(
            "Could not load the reserved list '%s' from the cache", listKey.getName()), e);
      }
    }

    return builder.build();
  }

  private static LoadingCache<String, ReservedList> cache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(getDomainLabelListCacheDuration().getMillis(), MILLISECONDS)
          .build(
              new CacheLoader<String, ReservedList>() {
                @Override
                public ReservedList load(String listName) {
                  return ofy()
                      .load()
                      .type(ReservedList.class)
                      .parent(getCrossTldKey())
                      .id(listName)
                      .now();
                }});

  /**
   * Gets the {@link ReservationType} of a label in a single ReservedList, or returns an absent
   * Optional if none exists in the list.
   *
   * <p>Note that this logic is significantly less complicated than the {@link #getReservationTypes}
   * methods, which are applicable to an entire Registry, and need to check across multiple reserved
   * lists.
   */
  public Optional<ReservationType> getReservationInList(String label) {
    ReservedListEntry entry = getReservedListEntries().get(label);
    return Optional.fromNullable(entry == null ? null : entry.reservationType);
  }

  @Override
  @Nullable
  ReservedListEntry createFromLine(String originalLine) {
    List<String> lineAndComment = splitOnComment(originalLine);
    if (lineAndComment.isEmpty()) {
      return null;
    }
    String line = lineAndComment.get(0);
    String comment = lineAndComment.get(1);
    List<String> parts = Splitter.on(',').trimResults().splitToList(line);
    checkArgument(parts.size() == 2 || parts.size() == 3,
        "Could not parse line in reserved list: %s", originalLine);
    String label = parts.get(0);
    ReservationType reservationType = ReservationType.valueOf(parts.get(1));
    String restrictions = (parts.size() > 2) ? parts.get(2) : null;
    return ReservedListEntry.create(label, reservationType, restrictions, comment);
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /**
   * A builder for constructing {@link ReservedList} objects, since they are immutable.
   */
  public static class Builder extends BaseDomainLabelList.Builder<ReservedList, Builder> {
    public Builder() {}

    private Builder(ReservedList instance) {
      super(instance);
    }

    public Builder setReservedListMap(ImmutableMap<String, ReservedListEntry> reservedListMap) {
      getInstance().reservedListMap = reservedListMap;
      return this;
    }

    public Builder setShouldPublish(boolean shouldPublish) {
      getInstance().shouldPublish = shouldPublish;
      return this;
    }

    /**
     * Updates the reservedListMap from input lines.
     *
     * @throws IllegalArgumentException if the lines cannot be parsed correctly.
     */
    public Builder setReservedListMapFromLines(Iterable<String> lines) {
      return setReservedListMap(getInstance().parse(lines));
    }
  }
}
