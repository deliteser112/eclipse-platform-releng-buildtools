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

package google.registry.flows.async;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.getOnlyPollMessageForHistoryEntry;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.model.ofy.Ofy;
import google.registry.model.poll.PollMessage;
import google.registry.model.poll.PollMessage.OneTime;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.request.HttpException.BadRequestException;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.testing.mapreduce.MapreduceTestCase;
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
    action.mrRunner = new MapreduceRunner(Optional.<Integer>of(5), Optional.<Integer>absent());
    action.response = new FakeResponse();
    action.clock = clock;
    inject.setStaticField(Ofy.class, "clock", clock);

    createTld("tld");
    contactUsed = persistActiveContact("blah1234");
    hostUsed = persistActiveHost("ns1.example.tld");
    domain = persistResource(
        newDomainResource("example.tld", contactUsed).asBuilder()
            .setNameservers(ImmutableSet.of(Ref.create(hostUsed)))
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
