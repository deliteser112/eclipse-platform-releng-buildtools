// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.ui.server.admin;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.model.registry.Registries.getTlds;
import static com.google.domain.registry.util.DomainNameUtils.canonicalizeDomainName;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.util.SystemClock;

import com.googlecode.objectify.VoidWork;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Seconds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * RESTful CRUD bindings for Registry objects.
 */
public class RegistryServlet extends AdminResourceServlet {

  private static final SystemClock CLOCK = new SystemClock();

  @Override
  Map<String, Object> create(HttpServletRequest req, Map<String, ?> args) {
    final String tld = checkParseId(req);
    checkArgument(tld.equals(canonicalizeDomainName(tld)));
    try {
      ofy().transact(new VoidWork() {
          @Override
          public void vrun() {
            ofy().save().entity(new Registry.Builder().setTldStr(tld).build());
          }});
      return ImmutableMap.<String, Object>of("results", ImmutableList.of(tld + ": ok"));
    } catch (Exception e) {
      return ImmutableMap.<String, Object>of("results", ImmutableList.of(tld + ": " + e));
    }
  }

  @Override
  Map<String, Object> read(HttpServletRequest req, Map<String, ?> args) {
    String id = parseId(req);
    if (id != null) {
      return readTld(id);
    }
    // Collection request; if no item specified, return all.
    return ImmutableMap.<String, Object>of("set", readTlds(getTlds()));
  }

  Map<String, Object> readTld(String tld) {
    return ImmutableMap.<String, Object>of("item", toResultObject(
        checkExists(Registry.get(tld), "No registry exists for the given tld: " + tld)));
  }

  List<Map<String, ?>> readTlds(Set<String> tlds) {
    List<Map<String, ?>> registries = new ArrayList<>();
    for (String tld : tlds) {
      registries.add(toResultObject(Registry.get(tld)));
    }
    return registries;
  }

  Map<String, Object> toResultObject(Registry r) {
    return new ImmutableSortedMap.Builder<String, Object>(Ordering.natural())
    .put("name", r.getTld().toString())
    .put("state", r.getTldState(CLOCK.nowUtc()).toString())
    .put("tldStateTransitions", r.getTldStateTransitions().toString())
    .put("creationTime", Objects.toString(r.getCreationTime(), ""))
    .put("lastUpdateTime", Objects.toString(r.getUpdateAutoTimestamp().getTimestamp(), ""))
    .put("addGracePeriod", r.getAddGracePeriodLength().toStandardSeconds().toString())
    .put("autoRenewGracePeriod", r.getAutoRenewGracePeriodLength().toStandardSeconds().toString())
    .put("redemptionGracePeriod", r.getRedemptionGracePeriodLength().toStandardSeconds().toString())
    .put("renewGracePeriod", r.getRenewGracePeriodLength().toStandardSeconds().toString())
    .put("transferGracePeriod", r.getTransferGracePeriodLength().toStandardSeconds().toString())
    .put("automaticTransferLength", r.getAutomaticTransferLength().toStandardSeconds().toString())
    .put("pendingDeleteLength", r.getPendingDeleteLength().toStandardSeconds().toString())
    .build();
  }

  ImmutableSortedMap<DateTime, TldState> getTldStateTransitions(Map<String, ?> args) {
    ImmutableSortedMap.Builder<DateTime, TldState> builder = ImmutableSortedMap.naturalOrder();
    for (Map<String, ?> tldStateTransition
        : AdminResourceServlet.<Map<String, ?>>getParamList(args, "tldStateTransitions")) {
      builder.put(
          DateTime.parse(getValAsString(tldStateTransition, "transitionTime")),
          TldState.valueOf(getValAsString(tldStateTransition, "tldState")));
    }
    return builder.build();
  }

  Duration getValAsDuration(Map<String, ?> map, String identifier) {
    return Seconds.parseSeconds(getValAsString(map, identifier)).toStandardDuration();
  }

  @Override
  Map<String, Object> update(HttpServletRequest req, Map<String, ?> args) {
    String tld = checkParseId(req);

    ImmutableSortedMap<DateTime, TldState> tldStateTransitions =
        getTldStateTransitions(args);
    Duration addGracePeriodLength = getValAsDuration(args, "addGracePeriod");
    Duration autoRenewGracePeriodLength = getValAsDuration(args, "autoRenewGracePeriod");
    Duration redemptionGracePeriodLength = getValAsDuration(args, "redemptionGracePeriod");
    Duration renewGracePeriodLength = getValAsDuration(args, "renewGracePeriod");
    Duration transferGracePeriodLength = getValAsDuration(args, "transferGracePeriod");
    Duration automaticTransferLength = getValAsDuration(args, "automaticTransferLength");
    Duration pendingDeleteLength = getValAsDuration(args, "pendingDeleteLength");

    try {
      final Registry.Builder registry = Registry.get(tld).asBuilder();
      if (!tldStateTransitions.isEmpty()) {
        registry.setTldStateTransitions(tldStateTransitions);
      }
      if (!addGracePeriodLength.equals(Duration.ZERO)) {
        registry.setAddGracePeriodLength(addGracePeriodLength);
      }
      if (!autoRenewGracePeriodLength.equals(Duration.ZERO)) {
        registry.setAutoRenewGracePeriodLength(autoRenewGracePeriodLength);
      }
      if (!redemptionGracePeriodLength.equals(Duration.ZERO)) {
        registry.setRedemptionGracePeriodLength(redemptionGracePeriodLength);
      }
      if (!renewGracePeriodLength.equals(Duration.ZERO)) {
        registry.setRenewGracePeriodLength(renewGracePeriodLength);
      }
      if (!transferGracePeriodLength.equals(Duration.ZERO)) {
        registry.setTransferGracePeriodLength(transferGracePeriodLength);
      }
      if (!automaticTransferLength.equals(Duration.ZERO)) {
        registry.setAutomaticTransferLength(automaticTransferLength);
      }
      if (!pendingDeleteLength.equals(Duration.ZERO)) {
        registry.setPendingDeleteLength(pendingDeleteLength);
      }
      ofy().transact(new VoidWork(){
        @Override
        public void vrun() {
          ofy().save().entity(registry.build());
        }});
      return ImmutableMap.<String, Object>of("results", "OK");
    } catch (Exception e) {
      return ImmutableMap.<String, Object>of("results", e.toString());
    }
  }
}
