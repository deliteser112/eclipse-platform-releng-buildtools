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

package com.google.domain.registry.flows.async;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.EppResourceUtils.loadByUniqueId;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.getOnlyPollMessageForHistoryEntry;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.poll.PollMessage.OneTime;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.request.HttpException.BadRequestException;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.InjectRule;
import com.google.domain.registry.testing.mapreduce.MapreduceTestCase;

import com.googlecode.objectify.Key;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link DeleteEppResourceAction}. */
public abstract class DeleteEppResourceActionTestCase<T extends DeleteEppResourceAction<?>>
    extends MapreduceTestCase<T> {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  DateTime now = DateTime.now(DateTimeZone.UTC);
  FakeClock clock = new FakeClock(now);
  final DateTime transferRequestTime = now.minusDays(3);
  final DateTime transferExpirationTime =
      transferRequestTime.plus(Registry.DEFAULT_TRANSFER_GRACE_PERIOD);

  ContactResource contactUsed;
  HostResource hostUsed;
  DomainResource domain;

  public void setupDeleteEppResourceAction(T deleteEppResourceAction) throws Exception {
    action = deleteEppResourceAction;
    action.mrRunner = new MapreduceRunner(Optional.<Integer>absent(), Optional.<Integer>absent());
    action.response = new FakeResponse();
    inject.setStaticField(Ofy.class, "clock", clock);
    inject.setStaticField(DeleteEppResourceAction.class, "clock", clock);

    createTld("tld");
    contactUsed = persistActiveContact("blah1234");
    hostUsed = persistActiveHost("ns1.example.tld");
    domain = persistResource(
        newDomainResource("example.tld", contactUsed).asBuilder()
            .setNameservers(ImmutableSet.of(ReferenceUnion.create(hostUsed)))
            .build());
  }

  void runMapreduce() throws Exception {
    clock.advanceOneMilli();
    action.run();
    executeTasksUntilEmpty("mapreduce");
    ofy().clearSessionCache();
    now = clock.nowUtc();
  }

  void runMapreduceWithParams(
      String resourceKeyString,
      String requestingClientId,
      boolean isSuperuser) throws Exception {
    action.resourceKeyString = resourceKeyString;
    action.requestingClientId = requestingClientId;
    action.isSuperuser = isSuperuser;
    runMapreduce();
  }

  void runMapreduceWithKeyParam(String resourceKeyString) throws Exception {
    runMapreduceWithParams(resourceKeyString, "TheRegistrar", false);
  }

  /**
   * Helper method to check that one poll message exists with a given history entry, resource,
   * client id, and message.
   */
  void assertPollMessageFor(
      HistoryEntry historyEntry,
      String clientId,
      String msg) {
    PollMessage.OneTime pollMessage = (OneTime) getOnlyPollMessageForHistoryEntry(historyEntry);
    assertThat(msg).isEqualTo(pollMessage.getMsg());
    assertThat(now).isEqualTo(pollMessage.getEventTime());
    assertThat(clientId).isEqualTo(pollMessage.getClientId());
    assertThat(pollMessage.getClientId()).isEqualTo(clientId);
  }

  @Test
  public void testFailure_domainKeyPassed() throws Exception {
    DomainResource domain = persistActiveDomain("fail.tld");
    thrown.expect(
        IllegalArgumentException.class, "Cannot delete a DomainResource via this action.");
    runMapreduceWithKeyParam(Key.create(domain).getString());
    assertThat(loadByUniqueId(DomainResource.class, "fail.tld", now)).isEqualTo(domain);
  }

  @Test
  public void testFailure_badKeyPassed() throws Exception {
    createTld("tld");
    thrown.expect(BadRequestException.class, "Could not parse key string: a bad key");
    runMapreduceWithKeyParam("a bad key");
  }
}
