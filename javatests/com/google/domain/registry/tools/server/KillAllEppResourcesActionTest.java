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

package com.google.domain.registry.tools.server;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Multimaps.filterKeys;
import static com.google.common.collect.Sets.difference;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.EntityClasses.CLASS_TO_KIND_FUNCTION;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static java.util.Arrays.asList;

import com.google.appengine.api.datastore.Entity;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.billing.BillingEvent.Reason;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DomainBase;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.index.DomainApplicationIndex;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyContactIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyDomainIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex.ForeignKeyHostIndex;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.mapreduce.MapreduceTestCase;

import com.googlecode.objectify.Key;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;

/** Tests for {@link KillAllEppResourcesAction}.*/
@RunWith(JUnit4.class)
public class KillAllEppResourcesActionTest extends MapreduceTestCase<KillAllEppResourcesAction> {

  static final Set<String> AFFECTED_KINDS = FluentIterable
      .from(asList(
          EppResourceIndex.class,
          ForeignKeyContactIndex.class,
          ForeignKeyDomainIndex.class,
          ForeignKeyHostIndex.class,
          DomainApplicationIndex.class,
          DomainBase.class,
          ContactResource.class,
          HostResource.class,
          HistoryEntry.class,
          PollMessage.class,
          BillingEvent.OneTime.class,
          BillingEvent.Recurring.class))
      .transform(CLASS_TO_KIND_FUNCTION)
      .toSet();

  private void runMapreduce() throws Exception {
    action = new KillAllEppResourcesAction();
    action.mrRunner = new MapreduceRunner(Optional.<Integer>absent(), Optional.<Integer>absent());
    action.response = new FakeResponse();
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void testKill() throws Exception {
    createTld("tld1");
    createTld("tld2");
    for (EppResource resource : asList(
        persistActiveDomain("foo.tld1"),
        persistActiveDomain("foo.tld2"),
        persistActiveDomainApplication("foo.tld1"),
        persistActiveDomainApplication("foo.tld2"),
        persistActiveContact("foo"),
        persistActiveContact("foo"),
        persistActiveHost("ns.foo.tld1"),
        persistActiveHost("ns.foo.tld2"))) {
      HistoryEntry history = new HistoryEntry.Builder().setParent(resource).build();
      for (ImmutableObject descendant : asList(
          history,
          new PollMessage.OneTime.Builder()
              .setParent(history)
              .setClientId("")
              .setEventTime(START_OF_TIME)
              .build(),
          new PollMessage.Autorenew.Builder()
              .setParent(history)
              .setClientId("")
              .setEventTime(START_OF_TIME)
              .build(),
          new BillingEvent.OneTime.Builder()
              .setParent(history)
              .setBillingTime(START_OF_TIME)
              .setEventTime(START_OF_TIME)
              .setClientId("")
              .setTargetId("")
              .setReason(Reason.ERROR)
              .setCost(Money.of(CurrencyUnit.USD, 1))
              .build(),
          new BillingEvent.Recurring.Builder()
              .setParent(history)
              .setEventTime(START_OF_TIME)
              .setClientId("")
              .setTargetId("")
              .setReason(Reason.ERROR)
              .build())) {
        persistResource(descendant);
      }
    }
    ImmutableMultimap<String, Object> beforeContents = getDatastoreContents();
    assertThat(beforeContents.keySet()).containsAllIn(AFFECTED_KINDS);
    assertThat(difference(beforeContents.keySet(), AFFECTED_KINDS)).isNotEmpty();
    runMapreduce();
    ofy().clearSessionCache();
    ImmutableMultimap<String, Object> afterContents = getDatastoreContents();
    assertThat(afterContents.keySet()).containsNoneIn(AFFECTED_KINDS);
    assertThat(afterContents)
        .containsExactlyEntriesIn(filterKeys(beforeContents, not(in(AFFECTED_KINDS))));
  }

  private ImmutableMultimap<String, Object> getDatastoreContents() {
    ImmutableMultimap.Builder<String, Object> contentsBuilder = new ImmutableMultimap.Builder<>();
    // Filter out raw Entity objects created by the mapreduce.
    for (Object obj : Iterables.filter(ofy().load(), not(instanceOf(Entity.class)))) {
      contentsBuilder.put(Key.getKind(obj.getClass()), obj);
    }
    return contentsBuilder.build();
  }
}
