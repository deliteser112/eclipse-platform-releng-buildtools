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

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistDeletedDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistSimpleResource;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.billing.BillingEvent.Reason;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.ForeignKeyIndex;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.Registry.TldType;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.mapreduce.MapreduceTestCase;

import com.googlecode.objectify.Key;

import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;

/** Unit tests for {@link DeleteProberDataAction}. */
@RunWith(JUnit4.class)
public class DeleteProberDataActionTest extends MapreduceTestCase<DeleteProberDataAction> {

  private static final DateTime DELETION_TIME = DateTime.parse("2010-01-01T00:00:00.000Z");

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Before
  public void init() {
    // Entities in these two should not be touched.
    createTld("tld", "TLD");
    // Since "example" doesn't end with .test, its entities won't be deleted even though it is of
    // TEST type.
    createTld("example", "EXAMPLE");
    persistResource(Registry.get("example").asBuilder().setTldType(TldType.TEST).build());

    // Entities in these two should be deleted.
    createTld("ib-any.test", "IBANYT");
    persistResource(Registry.get("ib-any.test").asBuilder().setTldType(TldType.TEST).build());
    createTld("oa-canary.test", "OACANT");
    persistResource(Registry.get("oa-canary.test").asBuilder().setTldType(TldType.TEST).build());

    action = new DeleteProberDataAction();
    action.mrRunner = new MapreduceRunner(Optional.<Integer>absent(), Optional.<Integer>absent());
    action.response = new FakeResponse();
    action.isDryRun = false;
  }

  private void runMapreduce() throws Exception {
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void test_deletesAllAndOnlyProberData() throws Exception {
    Set<ImmutableObject> tldEntities = persistLotsOfDomains("tld");
    Set<ImmutableObject> exampleEntities = persistLotsOfDomains("example");
    Set<ImmutableObject> ibEntities = persistLotsOfDomains("ib-any.test");
    Set<ImmutableObject> oaEntities = persistLotsOfDomains("oa-canary.test");
    runMapreduce();
    assertNotDeleted(tldEntities);
    assertNotDeleted(exampleEntities);
    assertDeleted(ibEntities);
    assertDeleted(oaEntities);
  }

  @Test
  public void testSuccess_doesntDeleteNicDomainForProbers() throws Exception {
    DomainResource nic = persistActiveDomain("nic.ib-any.test");
    ForeignKeyIndex<DomainResource> fkiNic =
        ForeignKeyIndex.load(DomainResource.class, "nic.ib-any.test", START_OF_TIME);
    Set<ImmutableObject> ibEntities = persistLotsOfDomains("ib-any.test");
    runMapreduce();
    assertDeleted(ibEntities);
    assertNotDeleted(ImmutableSet.<ImmutableObject>of(nic, fkiNic));
  }

  @Test
  public void testSuccess_dryRunDoesntDeleteData() throws Exception {
    Set<ImmutableObject> tldEntities = persistLotsOfDomains("tld");
    Set<ImmutableObject> oaEntities = persistLotsOfDomains("oa-canary.test");
    action.isDryRun = true;
    assertNotDeleted(tldEntities);
    assertNotDeleted(oaEntities);
  }

  /**
   * Persists and returns a domain and a descendant history entry, billing event, and poll message,
   * along with the ForeignKeyIndex and EppResourceIndex.
   */
  private static Set<ImmutableObject> persistDomainAndDescendants(String fqdn) {
    DomainResource domain = persistDeletedDomain(fqdn, DELETION_TIME);
    HistoryEntry historyEntry = persistSimpleResource(
        new HistoryEntry.Builder()
            .setParent(domain)
            .setType(HistoryEntry.Type.DOMAIN_CREATE)
            .build());
    BillingEvent.OneTime billingEvent = persistSimpleResource(
        new BillingEvent.OneTime.Builder()
            .setParent(historyEntry)
            .setBillingTime(DELETION_TIME.plusYears(1))
            .setCost(Money.parse("USD 10"))
            .setPeriodYears(1)
            .setReason(Reason.CREATE)
            .setClientId("TheRegistrar")
            .setEventTime(DELETION_TIME)
            .setTargetId(fqdn)
            .build());
    PollMessage.OneTime pollMessage = persistSimpleResource(
        new PollMessage.OneTime.Builder()
            .setParent(historyEntry)
            .setEventTime(DELETION_TIME)
            .setClientId("TheRegistrar")
            .setMsg("Domain registered")
            .build());
    ForeignKeyIndex<DomainResource> fki =
        ForeignKeyIndex.load(DomainResource.class, fqdn, START_OF_TIME);
    EppResourceIndex eppIndex =
        ofy().load().entity(EppResourceIndex.create(Key.create(domain))).now();
    return ImmutableSet.<ImmutableObject>of(
        domain, historyEntry, billingEvent, pollMessage, fki, eppIndex);
  }

  private static Set<ImmutableObject> persistLotsOfDomains(String tld) {
    ImmutableSet.Builder<ImmutableObject> persistedObjects = new ImmutableSet.Builder<>();
    for (int i = 0; i < 20; i++) {
      persistedObjects.addAll(persistDomainAndDescendants(String.format("domain%d.%s", i, tld)));
    }
    return persistedObjects.build();
  }

  private static void assertNotDeleted(Iterable<ImmutableObject> entities) {
    for (ImmutableObject entity : entities) {
      assertThat(ofy().load().entity(entity).now()).isNotNull();
    }
  }

  private static void assertDeleted(Iterable<ImmutableObject> entities) {
    for (ImmutableObject entity : entities) {
      assertThat(ofy().load().entity(entity).now()).isNull();
    }
  }
}
