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

import static com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Multimaps.filterKeys;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.model.server.ServerSecret.getServerSecret;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.newContactResource;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainApplication;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainResource;
import static com.google.domain.registry.testing.DatastoreHelper.newHostResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistPremiumList;
import static com.google.domain.registry.testing.DatastoreHelper.persistReservedList;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistResourceWithCommitLog;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static java.util.Arrays.asList;
import static org.joda.money.CurrencyUnit.USD;

import com.google.appengine.api.datastore.Entity;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.domain.registry.mapreduce.MapreduceAction;
import com.google.domain.registry.model.EntityClasses;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.annotations.VirtualEntity;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.billing.BillingEvent.Reason;
import com.google.domain.registry.model.billing.RegistrarBillingEntry;
import com.google.domain.registry.model.billing.RegistrarCredit;
import com.google.domain.registry.model.billing.RegistrarCredit.CreditType;
import com.google.domain.registry.model.billing.RegistrarCreditBalance;
import com.google.domain.registry.model.common.EntityGroupRoot;
import com.google.domain.registry.model.common.GaeUserIdConverter;
import com.google.domain.registry.model.export.LogsExportCursor;
import com.google.domain.registry.model.ofy.CommitLogCheckpoint;
import com.google.domain.registry.model.ofy.CommitLogCheckpointRoot;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.rde.RdeMode;
import com.google.domain.registry.model.rde.RdeRevision;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.RegistryCursor;
import com.google.domain.registry.model.registry.RegistryCursor.CursorType;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.model.server.Lock;
import com.google.domain.registry.model.smd.SignedMarkRevocationList;
import com.google.domain.registry.model.tmch.ClaimsListShard;
import com.google.domain.registry.model.tmch.TmchCrl;
import com.google.domain.registry.testing.mapreduce.MapreduceTestCase;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import com.googlecode.objectify.VoidWork;

import org.joda.money.Money;
import org.junit.Test;

public abstract class KillAllActionTestCase<T> extends MapreduceTestCase<T>{

  private final ImmutableSet<String> affectedKinds;

  private static final ImmutableSet<String> ALL_PERSISTED_KINDS = FluentIterable
      .from(EntityClasses.ALL_CLASSES)
      .filter(
          new Predicate<Class<?>>() {
            @Override
            public boolean apply(Class<?> clazz) {
              return !clazz.isAnnotationPresent(VirtualEntity.class);
            }})
      .transform(EntityClasses.CLASS_TO_KIND_FUNCTION)
      .toSet();


  public KillAllActionTestCase(ImmutableSet<String> affectedKinds) {
    this.affectedKinds = affectedKinds;
  }

  /** Create at least one of each type of entity in the schema. */
  void createData() {
    createTld("tld1");
    createTld("tld2");
    persistResource(CommitLogCheckpointRoot.create(START_OF_TIME.plusDays(1)));
    persistResource(
        CommitLogCheckpoint.create(
            START_OF_TIME.plusDays(1),
            ImmutableMap.of(1, START_OF_TIME.plusDays(2))));
    for (EppResource resource : asList(
        newDomainResource("foo.tld1"),
        newDomainResource("foo.tld2"),
        newDomainApplication("foo.tld1"),
        newDomainApplication("foo.tld2"),
        newContactResource("foo1"),
        newContactResource("foo2"),
        newHostResource("ns.foo.tld1"),
        newHostResource("ns.foo.tld2"))) {
      persistResourceWithCommitLog(resource);
      HistoryEntry history =
          persistResource(new HistoryEntry.Builder().setParent(resource).build());
      BillingEvent.OneTime oneTime = persistResource(new BillingEvent.OneTime.Builder()
          .setParent(history)
          .setBillingTime(START_OF_TIME)
          .setEventTime(START_OF_TIME)
          .setClientId("")
          .setTargetId("")
          .setReason(Reason.ERROR)
          .setCost(Money.of(USD, 1))
          .build());
      for (ImmutableObject descendant : asList(
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
          new BillingEvent.Cancellation.Builder()
              .setOneTimeEventRef(Ref.create(oneTime))
              .setParent(history)
              .setBillingTime(START_OF_TIME)
              .setEventTime(START_OF_TIME)
              .setClientId("")
              .setTargetId("")
              .setReason(Reason.ERROR)
              .build(),
          new BillingEvent.Modification.Builder()
              .setEventRef(Ref.create(oneTime))
              .setParent(history)
              .setEventTime(START_OF_TIME)
              .setClientId("")
              .setTargetId("")
              .setReason(Reason.ERROR)
              .setCost(Money.of(USD, 1))
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
    persistResource(new RegistrarBillingEntry.Builder()
        .setParent(Registrar.loadByClientId("TheRegistrar"))
        .setCreated(START_OF_TIME)
        .setDescription("description")
        .setAmount(Money.of(USD, 1))
        .build());
    RegistrarCredit credit = persistResource(new RegistrarCredit.Builder()
        .setParent(Registrar.loadByClientId("TheRegistrar"))
        .setCreationTime(START_OF_TIME)
        .setCurrency(USD)
        .setDescription("description")
        .setTld("tld1")
        .setType(CreditType.AUCTION)
        .build());
    persistResource(new RegistrarCreditBalance.Builder()
        .setParent(credit)
        .setAmount(Money.of(USD, 1))
        .setEffectiveTime(START_OF_TIME)
        .setWrittenTime(START_OF_TIME)
        .build());
    persistPremiumList("premium", "a,USD 100", "b,USD 200");
    persistReservedList("reserved", "lol,RESERVED_FOR_ANCHOR_TENANT,foobar1");
    getServerSecret();  // Forces persist.
    TmchCrl.set("crl content");
    persistResource(new LogsExportCursor.Builder().build());
    persistPremiumList("premium", "a,USD 100", "b,USD 200");
    SignedMarkRevocationList.create(
        START_OF_TIME, ImmutableMap.of("a", START_OF_TIME, "b", END_OF_TIME)).save();
    ClaimsListShard.create(START_OF_TIME, ImmutableMap.of("a", "1", "b", "2")).save();
    // These entities must be saved within a transaction.
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        RegistryCursor.save(Registry.get("tld1"), CursorType.BRDA, START_OF_TIME);
        RdeRevision.saveRevision("tld1", START_OF_TIME, RdeMode.FULL, 0);
      }});
    // These entities intentionally don't expose constructors or factory methods.
    getDatastoreService().put(new Entity(EntityGroupRoot.getCrossTldKey().getRaw()));
    getDatastoreService().put(new Entity(Key.getKind(GaeUserIdConverter.class), 1));
    getDatastoreService().put(new Entity(Key.getKind(Lock.class), 1));
  }

  abstract MapreduceAction createAction();

  @Test
  public void testKill() throws Exception {
    createData();
    ImmutableMultimap<String, Object> beforeContents = getDatastoreContents();
    assertThat(beforeContents.keySet()).named("persisted test data")
        .containsAllIn(ALL_PERSISTED_KINDS);
    MapreduceAction action = createAction();
    action.run();
    executeTasksUntilEmpty("mapreduce");
    ImmutableMultimap<String, Object> afterContents = getDatastoreContents();
    assertThat(afterContents.keySet()).containsNoneIn(affectedKinds);
    assertThat(afterContents)
        .containsExactlyEntriesIn(filterKeys(beforeContents, not(in(affectedKinds))));
  }

  private ImmutableMultimap<String, Object> getDatastoreContents() {
    ofy().clearSessionCache();
    ImmutableMultimap.Builder<String, Object> contentsBuilder = new ImmutableMultimap.Builder<>();
    // Filter out raw Entity objects created by the mapreduce.
    for (Object obj : Iterables.filter(ofy().load(), not(instanceOf(Entity.class)))) {
      contentsBuilder.put(Key.getKind(obj.getClass()), obj);
    }
    return contentsBuilder.build();
  }
}
