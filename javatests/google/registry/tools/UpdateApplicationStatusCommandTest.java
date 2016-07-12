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

package google.registry.tools;

import static com.google.common.collect.Iterables.getLast;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static google.registry.model.domain.launch.ApplicationStatus.ALLOCATED;
import static google.registry.model.domain.launch.ApplicationStatus.PENDING_ALLOCATION;
import static google.registry.model.domain.launch.ApplicationStatus.REJECTED;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newContactResourceWithRoid;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainApplicationSubject.assertAboutApplications;
import static google.registry.testing.HistoryEntrySubject.assertAboutHistoryEntries;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.FluentIterable;
import google.registry.model.domain.DomainApplication;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppcommon.Trid;
import google.registry.model.poll.PendingActionNotificationResponse.DomainPendingActionNotificationResponse;
import google.registry.model.poll.PollMessage;
import google.registry.model.registrar.Registrar;
import google.registry.model.reporting.HistoryEntry;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link UpdateApplicationStatusCommand}. */
public class UpdateApplicationStatusCommandTest
    extends CommandTestCase<UpdateApplicationStatusCommand> {

  private Trid trid = Trid.create("ABC123");
  private DomainApplication domainApplication;
  private DateTime creationTime;

  @Before
  public void init() {
    // Since the command's history client ID defaults to CharlestonRoad, resave TheRegistrar as
    // CharlestonRoad so we don't have to pass in --history_client_id everywhere below.
    persistResource(Registrar.loadByClientId("TheRegistrar").asBuilder()
        .setClientIdentifier("CharlestonRoad")
        .build());

    createTld("xn--q9jyb4c");
    domainApplication = persistResource(newDomainApplication(
        "label.xn--q9jyb4c", persistResource(newContactResourceWithRoid("contact1", "C1-ROID")))
            .asBuilder()
            .setCurrentSponsorClientId("TheRegistrar")
            .build());

    this.creationTime = domainApplication.getCreationTime();

    // Add a history entry under this application that corresponds to its creation.
    persistResource(
        new HistoryEntry.Builder()
            .setParent(domainApplication)
            .setModificationTime(creationTime)
            .setTrid(trid)
            .setType(HistoryEntry.Type.DOMAIN_APPLICATION_CREATE)
            .build());

    // Add a second history entry with a different Trid just to make sure we are always retrieving
    // the right one.
    persistResource(
        new HistoryEntry.Builder()
            .setParent(domainApplication)
            .setModificationTime(creationTime)
            .setTrid(Trid.create("ABC124"))
            .setType(HistoryEntry.Type.DOMAIN_APPLICATION_CREATE)
            .build());
  }

  private HistoryEntry loadLastHistoryEntry() {
    // Loading everything and using getLast() is inefficient, but to do it right would require a new
    // descending index on modification time, and this is fine for testing.
    return getLast(ofy().load()
        .type(HistoryEntry.class)
        .ancestor(domainApplication)
        .order("modificationTime")
        .list());
  }

  @Test
  public void testSuccess_rejected() throws Exception {
    DateTime before = new DateTime(UTC);

    assertAboutApplications()
        .that(domainApplication)
        .hasStatusValue(StatusValue.PENDING_CREATE).and()
        .doesNotHaveApplicationStatus(REJECTED);
    assertThat(getPollMessageCount()).isEqualTo(0);

    Trid creationTrid = Trid.create("DEF456");
    persistResource(reloadResource(domainApplication).asBuilder()
        .setCreationTrid(creationTrid)
        .build());
    runCommandForced("--ids=2-Q9JYB4C", "--msg=\"Application rejected\"", "--status=REJECTED");

    domainApplication = ofy().load().entity(domainApplication).now();
    assertAboutApplications().that(domainApplication)
        .doesNotHaveStatusValue(StatusValue.PENDING_CREATE).and()
        .hasApplicationStatus(REJECTED).and()
        .hasLastEppUpdateTimeAtLeast(before).and()
        .hasLastEppUpdateClientId("TheRegistrar");
    assertAboutHistoryEntries().that(loadLastHistoryEntry())
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_STATUS_UPDATE).and()
        .hasClientId("CharlestonRoad");
    assertThat(getPollMessageCount()).isEqualTo(1);

    PollMessage pollMessage = getFirstPollMessage();
    assertThat(pollMessage.getMsg()).isEqualTo("Application rejected");
    DomainPendingActionNotificationResponse response = (DomainPendingActionNotificationResponse)
        FluentIterable.from(pollMessage.getResponseData()).first().get();
    assertThat(response.getTrid()).isEqualTo(creationTrid);
    assertThat(response.getActionResult()).isFalse();
  }

  private PollMessage getFirstPollMessage() {
    return ofy().load().type(PollMessage.class).first().safe();
  }

  @Test
  public void testSuccess_allocated() throws Exception {
    DateTime before = new DateTime(UTC);

    assertAboutApplications().that(domainApplication)
        .hasStatusValue(StatusValue.PENDING_CREATE).and()
        .doesNotHaveApplicationStatus(ALLOCATED);
    assertThat(getPollMessageCount()).isEqualTo(0);

    Trid creationTrid = Trid.create("DEF456");
    persistResource(reloadResource(domainApplication).asBuilder()
        .setCreationTrid(creationTrid)
        .build());
    runCommandForced("--ids=2-Q9JYB4C", "--msg=\"Application allocated\"", "--status=ALLOCATED");

    domainApplication = ofy().load().entity(domainApplication).now();
    assertAboutApplications().that(domainApplication)
        .doesNotHaveStatusValue(StatusValue.PENDING_CREATE).and()
        .hasApplicationStatus(ALLOCATED).and()
        .hasLastEppUpdateTimeAtLeast(before).and()
        .hasLastEppUpdateClientId("TheRegistrar");
    assertAboutHistoryEntries().that(loadLastHistoryEntry())
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_STATUS_UPDATE).and()
        .hasClientId("CharlestonRoad");
    assertThat(getPollMessageCount()).isEqualTo(1);

    PollMessage pollMessage = getFirstPollMessage();
    assertThat(pollMessage.getMsg()).isEqualTo("Application allocated");
    DomainPendingActionNotificationResponse response = (DomainPendingActionNotificationResponse)
        FluentIterable.from(pollMessage.getResponseData()).first().get();
    assertThat(response.getTrid()).isEqualTo(creationTrid);
    assertThat(response.getActionResult()).isTrue();
  }

  @Test
  public void testSuccess_pendingAllocation() throws Exception {
    DateTime before = new DateTime(UTC);

    assertAboutApplications().that(domainApplication)
        .doesNotHaveApplicationStatus(PENDING_ALLOCATION).and()
        .hasStatusValue(StatusValue.PENDING_CREATE);
    assertThat(getPollMessageCount()).isEqualTo(0);

    Trid creationTrid = Trid.create("DEF456");
    persistResource(reloadResource(domainApplication).asBuilder()
        .setCreationTrid(creationTrid)
        .build());
    runCommandForced(
        "--ids=2-Q9JYB4C",
        "--msg=\"Application pending allocation\"",
        "--status=PENDING_ALLOCATION");

    domainApplication = ofy().load().entity(domainApplication).now();
    assertAboutApplications().that(domainApplication)
        .hasStatusValue(StatusValue.PENDING_CREATE).and()
        .hasApplicationStatus(PENDING_ALLOCATION).and()
        .hasLastEppUpdateTimeAtLeast(before).and()
        .hasLastEppUpdateClientId("TheRegistrar");
    assertAboutHistoryEntries().that(loadLastHistoryEntry())
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_STATUS_UPDATE).and()
        .hasClientId("CharlestonRoad");
    assertThat(getPollMessageCount()).isEqualTo(1);

    PollMessage pollMessage = getFirstPollMessage();
    assertThat(pollMessage.getMsg()).isEqualTo("Application pending allocation");
    assertThat(pollMessage.getResponseData()).isEmpty();
    assertThat(pollMessage.getResponseExtensions()).isNotEmpty();
  }

  @Test
  public void testSuccess_rejectedTridFromHistoryEntry() throws Exception {
    DateTime before = new DateTime(UTC);

    assertAboutApplications().that(domainApplication)
        .hasStatusValue(StatusValue.PENDING_CREATE).and()
        .doesNotHaveApplicationStatus(REJECTED);
    assertThat(getPollMessageCount()).isEqualTo(0);

    runCommandForced("--ids=2-Q9JYB4C", "--msg=\"Application rejected\"", "--status=REJECTED");

    domainApplication = ofy().load().entity(domainApplication).now();
    assertAboutApplications().that(domainApplication)
        .doesNotHaveStatusValue(StatusValue.PENDING_CREATE).and()
        .hasApplicationStatus(REJECTED).and()
        .hasLastEppUpdateTimeAtLeast(before).and()
        .hasLastEppUpdateClientId("TheRegistrar");
    assertAboutHistoryEntries().that(loadLastHistoryEntry())
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_STATUS_UPDATE).and()
        .hasClientId("CharlestonRoad");
    assertThat(getPollMessageCount()).isEqualTo(1);

    PollMessage pollMessage = getFirstPollMessage();
    DomainPendingActionNotificationResponse response = (DomainPendingActionNotificationResponse)
        FluentIterable.from(pollMessage.getResponseData()).first().get();
    assertThat(response.getTrid()).isEqualTo(trid);
  }

  @Test
  public void testFailure_applicationAlreadyRejected() throws Exception {
    assertThat(getPollMessageCount()).isEqualTo(0);
    persistResource(reloadResource(domainApplication).asBuilder()
        .setApplicationStatus(REJECTED)
        .build());

    runCommandForced("--ids=2-Q9JYB4C", "--msg=\"Application rejected\"", "--status=REJECTED");

    assertAboutApplications().that(ofy().load().entity(domainApplication).now())
        .hasApplicationStatus(REJECTED);
    assertThat(getPollMessageCount()).isEqualTo(0);
    assertAboutHistoryEntries().that(loadLastHistoryEntry())
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_CREATE);
  }

  @Test
  public void testFailure_applicationAlreadyAllocated() throws Exception {
    persistResource(reloadResource(domainApplication).asBuilder()
        .setApplicationStatus(ALLOCATED)
        .build());

    try {
      runCommandForced("--ids=2-Q9JYB4C", "--msg=\"Application rejected\"", "--status=REJECTED");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("Domain application has final status ALLOCATED");
      assertAboutApplications().that(ofy().load().entity(domainApplication).now())
          .hasApplicationStatus(ALLOCATED);
      assertThat(getPollMessageCount()).isEqualTo(0);
      assertAboutHistoryEntries().that(loadLastHistoryEntry())
          .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_CREATE);
      return;
    }
    assert_().fail(
        "Expected IllegalStateException \"Domain application has final status ALLOCATED\"");
  }

  @Test
  public void testFailure_applicationDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--ids=555-Q9JYB4C", "--msg=\"Application rejected\"", "--status=REJECTED");
  }

  @Test
  public void testFailure_historyClientIdDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class, "fakeclient");
    runCommandForced(
        "--history_client_id=fakeclient", "--ids=2-Q9JYB4C", "--msg=Ignored", "--status=REJECTED");
  }
}

