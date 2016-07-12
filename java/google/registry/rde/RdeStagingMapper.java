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

package google.registry.rde;

import static com.google.common.base.Strings.nullToEmpty;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.googlecode.objectify.Result;
import google.registry.model.EppResource;
import google.registry.model.EppResourceUtils;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.model.rde.RdeMode;
import google.registry.model.registrar.Registrar;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateTime;

/** Mapper for {@link RdeStagingAction}. */
public final class RdeStagingMapper extends Mapper<EppResource, PendingDeposit, DepositFragment> {

  private static final long serialVersionUID = -1518185703789372524L;

  private final ImmutableSetMultimap<String, PendingDeposit> pendings;
  private final RdeMarshaller marshaller = new RdeMarshaller();

  RdeStagingMapper(ImmutableSetMultimap<String, PendingDeposit> pendings) {
    this.pendings = pendings;
  }

  @Override
  public final void map(final EppResource resource) {
    // The mapreduce has one special input that provides a null resource. This is used as a sentinel
    // to indicate that we should emit the Registrar objects on this map shard, as these need to be
    // added to every deposit. It is important that these be emitted as part of the mapreduce and
    // not added in a separate stage, because the reducer only runs if there is at least one value
    // emitted from the mapper. Without this, a cursor might never advance because no EppResource
    // entity exists at the watermark.
    if (resource == null) {
      for (Registrar registrar : Registrar.loadAll()) {
        DepositFragment fragment = marshaller.marshalRegistrar(registrar);
        for (PendingDeposit pending : pendings.values()) {
          emit(pending, fragment);
        }
      }
      return;
    }

    // Skip polymorphic entities that share datastore kind.
    if (!(resource instanceof ContactResource
        || resource instanceof DomainResource
        || resource instanceof HostResource)) {
      return;
    }

    // Skip prober data.
    if (nullToEmpty(resource.getCreationClientId()).startsWith("prober-")
        || nullToEmpty(resource.getCurrentSponsorClientId()).startsWith("prober-")
        || nullToEmpty(resource.getLastEppUpdateClientId()).startsWith("prober-")) {
      return;
    }

    // Contacts and hosts get emitted on all TLDs, even if domains don't reference them.
    boolean shouldEmitOnAllTlds = !(resource instanceof DomainResource);

    // Get the set of all TLDs to which this resource should be emitted.
    ImmutableSet<String> tlds =
        shouldEmitOnAllTlds
            ? pendings.keySet()
            : ImmutableSet.of(((DomainResource) resource).getTld());

    // Get the set of all point-in-time watermarks we need, to minimize rewinding.
    ImmutableSet<DateTime> dates =
        FluentIterable
            .from(shouldEmitOnAllTlds
                ? pendings.values()
                : pendings.get(((DomainResource) resource).getTld()))
            .transform(new Function<PendingDeposit, DateTime>() {
              @Override
              public DateTime apply(PendingDeposit pending) {
                return pending.watermark();
              }})
            .toSet();

    // Launch asynchronous fetches of point-in-time representations of resource.
    ImmutableMap<DateTime, Result<EppResource>> resourceAtTimes =
        ImmutableMap.copyOf(Maps.asMap(dates,
            new Function<DateTime, Result<EppResource>>() {
              @Override
              public Result<EppResource> apply(DateTime input) {
                return EppResourceUtils.loadAtPointInTime(resource, input);
              }}));

    // Convert resource to an XML fragment for each watermark/mode pair lazily and cache the result.
    Fragmenter fragmenter = new Fragmenter(resourceAtTimes);

    // Emit resource as an XML fragment for all TLDs and modes pending deposit.
    for (String tld : tlds) {
      for (PendingDeposit pending : pendings.get(tld)) {
        // Hosts and contacts don't get included in BRDA deposits.
        if (pending.mode() == RdeMode.THIN
            && (resource instanceof ContactResource
                || resource instanceof HostResource)) {
          continue;
        }
        for (DepositFragment fragment
            : fragmenter.marshal(pending.watermark(), pending.mode()).asSet()) {
          emit(pending, fragment);
        }
      }
    }

    // Avoid running out of memory.
    ofy().clearSessionCache();
  }

  /** Loading cache that turns a resource into XML for the various points in time and modes. */
  private class Fragmenter {
    private final Map<WatermarkModePair, Optional<DepositFragment>> cache = new HashMap<>();
    private final ImmutableMap<DateTime, Result<EppResource>> resourceAtTimes;

    Fragmenter(ImmutableMap<DateTime, Result<EppResource>> resourceAtTimes) {
      this.resourceAtTimes = resourceAtTimes;
    }

    Optional<DepositFragment> marshal(DateTime watermark, RdeMode mode) {
      Optional<DepositFragment> result = cache.get(WatermarkModePair.create(watermark, mode));
      if (result != null) {
        return result;
      }
      EppResource resource = resourceAtTimes.get(watermark).now();
      if (resource == null) {
        result = Optional.absent();
        cache.put(WatermarkModePair.create(watermark, RdeMode.FULL), result);
        cache.put(WatermarkModePair.create(watermark, RdeMode.THIN), result);
        return result;
      }
      if (resource instanceof DomainResource) {
        result = Optional.of(marshaller.marshalDomain((DomainResource) resource, mode));
        cache.put(WatermarkModePair.create(watermark, mode), result);
        return result;
      } else if (resource instanceof ContactResource) {
        result = Optional.of(marshaller.marshalContact((ContactResource) resource));
        cache.put(WatermarkModePair.create(watermark, RdeMode.FULL), result);
        cache.put(WatermarkModePair.create(watermark, RdeMode.THIN), result);
        return result;
      } else if (resource instanceof HostResource) {
        result = Optional.of(marshaller.marshalHost((HostResource) resource));
        cache.put(WatermarkModePair.create(watermark, RdeMode.FULL), result);
        cache.put(WatermarkModePair.create(watermark, RdeMode.THIN), result);
        return result;
      } else {
        throw new AssertionError(resource.toString());
      }
    }
  }

  /** Map key for {@link Fragmenter} cache. */
  @AutoValue
  abstract static class WatermarkModePair {
    abstract DateTime watermark();
    abstract RdeMode mode();

    static WatermarkModePair create(DateTime watermark, RdeMode mode) {
      return new AutoValue_RdeStagingMapper_WatermarkModePair(watermark, mode);
    }
  }
}
