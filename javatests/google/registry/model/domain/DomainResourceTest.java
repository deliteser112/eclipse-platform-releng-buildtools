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

package google.registry.model.domain;

import static com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig.getLocalMemcacheService;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.skip;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.testing.DatastoreHelper.cloneAndSetAutoTimestamps;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainResourceSubject.assertAboutDomains;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;

import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushRequest;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.googlecode.objectify.Key;
import google.registry.flows.EppXmlTransformer;
import google.registry.model.EntityTestCase;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.AuthInfo.PasswordAuth;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.eppoutput.Result;
import google.registry.model.eppoutput.Result.Code;
import google.registry.model.host.HostResource;
import google.registry.model.ofy.RequestCapturingAsyncDatastoreService;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferData.TransferServerApproveEntity;
import google.registry.model.transfer.TransferStatus;
import google.registry.testing.ExceptionRule;
import google.registry.xml.ValidationMode;
import java.util.List;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link DomainResource}. */
public class DomainResourceTest extends EntityTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  DomainResource domain;

  @Before
  public void setUp() throws Exception {
    createTld("com");
    HostResource hostResource = persistResource(
        new HostResource.Builder()
            .setFullyQualifiedHostName("ns1.example.com")
            .setRepoId("1-COM")
            .build());
    ContactResource contactResource1 = persistResource(
        new ContactResource.Builder()
            .setContactId("contact_id1")
            .setRepoId("2-COM")
            .build());
    ContactResource contactResource2 = persistResource(
        new ContactResource.Builder()
            .setContactId("contact_id2")
            .setRepoId("3-COM")
            .build());
    // Set up a new persisted domain entity.
    domain = cloneAndSetAutoTimestamps(
        new DomainResource.Builder()
            .setFullyQualifiedDomainName("example.com")
        .setRepoId("4-COM")
            .setCreationClientId("a registrar")
            .setLastEppUpdateTime(clock.nowUtc())
            .setLastEppUpdateClientId("AnotherRegistrar")
            .setLastTransferTime(clock.nowUtc())
            .setStatusValues(ImmutableSet.of(
                StatusValue.CLIENT_DELETE_PROHIBITED,
                StatusValue.SERVER_DELETE_PROHIBITED,
                StatusValue.SERVER_TRANSFER_PROHIBITED,
                StatusValue.SERVER_UPDATE_PROHIBITED,
                StatusValue.SERVER_RENEW_PROHIBITED,
                StatusValue.SERVER_HOLD))
            .setRegistrant(Key.create(contactResource1))
            .setContacts(ImmutableSet.of(DesignatedContact.create(
                DesignatedContact.Type.ADMIN,
                Key.create(contactResource2))))
            .setNameservers(ImmutableSet.of(Key.create(hostResource)))
            .setSubordinateHosts(ImmutableSet.of("ns1.example.com"))
            .setCurrentSponsorClientId("ThirdRegistrar")
            .setRegistrationExpirationTime(clock.nowUtc().plusYears(1))
            .setAuthInfo(DomainAuthInfo.create(PasswordAuth.create("password")))
            .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 2, 3, new byte[] {0, 1, 2})))
            .setLaunchNotice(
                LaunchNotice.create("tcnid", "validatorId", START_OF_TIME, START_OF_TIME))
            .setTransferData(
                new TransferData.Builder()
                    .setExtendedRegistrationYears(1)
                    .setGainingClientId("gaining")
                    .setLosingClientId("losing")
                    .setPendingTransferExpirationTime(clock.nowUtc())
                    .setServerApproveEntities(
                        ImmutableSet.<Key<? extends TransferServerApproveEntity>>of(
                            Key.create(BillingEvent.OneTime.class, 1),
                            Key.create(BillingEvent.Recurring.class, 2),
                            Key.create(PollMessage.Autorenew.class, 3)))
                    .setServerApproveBillingEvent(
                        Key.create(BillingEvent.OneTime.class, 1))
                    .setServerApproveAutorenewEvent(
                        Key.create(BillingEvent.Recurring.class, 2))
                    .setServerApproveAutorenewPollMessage(
                        Key.create(PollMessage.Autorenew.class, 3))
                    .setTransferRequestTime(clock.nowUtc().plusDays(1))
                    .setTransferStatus(TransferStatus.SERVER_APPROVED)
                    .setTransferRequestTrid(Trid.create("client trid"))
                    .build())
            .setDeletePollMessage(Key.create(PollMessage.OneTime.class, 1))
            .setAutorenewBillingEvent(Key.create(BillingEvent.Recurring.class, 1))
            .setAutorenewPollMessage(Key.create(PollMessage.Autorenew.class, 2))
            .setSmdId("smdid")
            .setApplicationTime(START_OF_TIME)
            .setApplication(Key.create(DomainApplication.class, 1))
            .addGracePeriod(GracePeriod.create(
                GracePeriodStatus.ADD, clock.nowUtc().plusDays(1), "registrar", null))
            .build());
    persistResource(domain);
  }

  @Test
  public void testPersistence() throws Exception {
    assertThat(loadByUniqueId(DomainResource.class, domain.getForeignKey(), clock.nowUtc()))
        .isEqualTo(domain);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(
        domain,
        "allContacts.contactId.linked",
        "fullyQualifiedDomainName",
        "nameservers.linked",
        "currentSponsorClientId",
        "deletionTime",
        "tld");
  }

  @Test
  public void testEmptyStringsBecomeNull() {
    assertThat(newDomainResource("example.com").asBuilder()
        .setCurrentSponsorClientId(null)
        .build()
            .getCurrentSponsorClientId())
                .isNull();
    assertThat(newDomainResource("example.com").asBuilder()
            .setCurrentSponsorClientId("")
            .build()
                .getCurrentSponsorClientId())
                    .isNull();
    assertThat(newDomainResource("example.com").asBuilder()
        .setCurrentSponsorClientId(" ")
        .build()
            .getCurrentSponsorClientId())
                .isNotNull();
  }

  @Test
  public void testEmptySetsAndArraysBecomeNull() {
    assertThat(newDomainResource("example.com").asBuilder()
        .setNameservers(null).build().nameservers).isNull();
    assertThat(newDomainResource("example.com").asBuilder()
        .setNameservers(ImmutableSet.<Key<HostResource>>of()).build().nameservers)
            .isNull();
    assertThat(newDomainResource("example.com").asBuilder()
        .setNameservers(ImmutableSet.of(Key.create(newHostResource("foo.example.tld"))))
            .build().nameservers)
                .isNotNull();
    // This behavior should also hold true for ImmutableObjects nested in collections.
    assertThat(newDomainResource("example.com").asBuilder()
        .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 1, 1, null)))
        .build().getDsData().asList().get(0).getDigest())
            .isNull();
    assertThat(newDomainResource("example.com").asBuilder()
        .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 1, 1, new byte[]{})))
        .build().getDsData().asList().get(0).getDigest())
            .isNull();
    assertThat(newDomainResource("example.com").asBuilder()
        .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 1, 1, new byte[]{1})))
        .build().getDsData().asList().get(0).getDigest())
            .isNotNull();
  }

  @Test
  public void testEmptyTransferDataBecomesNull() throws Exception {
    DomainResource withNull =
        newDomainResource("example.com").asBuilder().setTransferData(null).build();
    DomainResource withEmpty = withNull.asBuilder().setTransferData(TransferData.EMPTY).build();
    assertThat(withNull).isEqualTo(withEmpty);
    assertThat(withEmpty.hasTransferData()).isFalse();
  }

  @Test
  public void testImplicitStatusValues() {
    ImmutableSet<Key<HostResource>> nameservers =
        ImmutableSet.of(Key.create(newHostResource("foo.example.tld")));
    StatusValue[] statuses = {StatusValue.OK};
    // OK is implicit if there's no other statuses but there are nameservers.
    assertAboutDomains()
        .that(newDomainResource("example.com").asBuilder().setNameservers(nameservers).build())
        .hasExactlyStatusValues(statuses);
    StatusValue[] statuses1 = {StatusValue.CLIENT_HOLD};
    // If there are other status values, OK should be suppressed. (Domains can't be LINKED.)
    assertAboutDomains()
        .that(newDomainResource("example.com").asBuilder()
            .setNameservers(nameservers)
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_HOLD))
            .build())
        .hasExactlyStatusValues(statuses1);
    StatusValue[] statuses2 = {StatusValue.CLIENT_HOLD};
    // When OK is suppressed, it should be removed even if it was originally there.
    assertAboutDomains()
        .that(newDomainResource("example.com").asBuilder()
            .setNameservers(nameservers)
            .setStatusValues(ImmutableSet.of(StatusValue.OK, StatusValue.CLIENT_HOLD))
            .build())
        .hasExactlyStatusValues(statuses2);
    StatusValue[] statuses3 = {StatusValue.INACTIVE};
    // If there are no nameservers, INACTIVE should be added, which suppresses OK.
    assertAboutDomains()
        .that(newDomainResource("example.com").asBuilder().build())
        .hasExactlyStatusValues(statuses3);
    StatusValue[] statuses4 = {StatusValue.CLIENT_HOLD, StatusValue.INACTIVE};
    // If there are no nameservers but there are status values, INACTIVE should still be added.
    assertAboutDomains()
        .that(newDomainResource("example.com").asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_HOLD))
            .build())
        .hasExactlyStatusValues(statuses4);
    StatusValue[] statuses5 = {StatusValue.CLIENT_HOLD};
    // If there are nameservers, INACTIVE should be removed even if it was originally there.
    assertAboutDomains()
        .that(newDomainResource("example.com").asBuilder()
            .setNameservers(nameservers)
            .setStatusValues(ImmutableSet.of(StatusValue.INACTIVE, StatusValue.CLIENT_HOLD))
            .build())
        .hasExactlyStatusValues(statuses5);
  }

  private void assertTransferred(
      DomainResource domain,
      DateTime newExpirationTime,
      Key<BillingEvent.Recurring> newAutorenewEvent) {
    assertThat(domain.getTransferData().getTransferStatus())
        .isEqualTo(TransferStatus.SERVER_APPROVED);
    assertThat(domain.getCurrentSponsorClientId()).isEqualTo("winner");
    assertThat(domain.getLastTransferTime()).isEqualTo(clock.nowUtc().plusDays(1));
    assertThat(domain.getRegistrationExpirationTime()).isEqualTo(newExpirationTime);
    assertThat(domain.getAutorenewBillingEvent()).isEqualTo(newAutorenewEvent);
  }

  private void doExpiredTransferTest(DateTime oldExpirationTime) {
    HistoryEntry historyEntry = new HistoryEntry.Builder().setParent(domain).build();
    BillingEvent.OneTime transferBillingEvent = persistResource(
        new BillingEvent.OneTime.Builder()
            .setReason(Reason.TRANSFER)
            .setClientId("winner")
            .setTargetId("example.com")
            .setEventTime(clock.nowUtc())
            .setBillingTime(
                clock.nowUtc().plusDays(1).plus(Registry.get("com").getTransferGracePeriodLength()))
            .setCost(Money.of(USD, 11))
            .setPeriodYears(1)
            .setParent(historyEntry)
            .build());
    domain = domain.asBuilder()
       .setRegistrationExpirationTime(oldExpirationTime)
       .setTransferData(domain.getTransferData().asBuilder()
           .setTransferStatus(TransferStatus.PENDING)
           .setTransferRequestTime(clock.nowUtc().minusDays(4))
           .setPendingTransferExpirationTime(clock.nowUtc().plusDays(1))
           .setGainingClientId("winner")
           .setServerApproveBillingEvent(Key.create(transferBillingEvent))
           .setServerApproveEntities(ImmutableSet.<Key<? extends TransferServerApproveEntity>>of(
               Key.create(transferBillingEvent)))
           .setExtendedRegistrationYears(1)
           .build())
        .addGracePeriod(
            // Okay for billing event to be null since the point of this grace period is just
            // to check that the transfer will clear all existing grace periods.
            GracePeriod.create(GracePeriodStatus.ADD, clock.nowUtc().plusDays(100), "foo", null))
        .build();
    DomainResource afterTransfer = domain.cloneProjectedAtTime(clock.nowUtc().plusDays(1));
    DateTime newExpirationTime = oldExpirationTime.plusYears(1);
    Key<BillingEvent.Recurring> serverApproveAutorenewEvent =
        domain.getTransferData().getServerApproveAutorenewEvent();
    assertTransferred(afterTransfer, newExpirationTime, serverApproveAutorenewEvent);
    assertThat(afterTransfer.getGracePeriods())
        .containsExactly(GracePeriod.create(
            GracePeriodStatus.TRANSFER,
            clock.nowUtc().plusDays(1).plus(Registry.get("com").getTransferGracePeriodLength()),
            "winner",
            Key.create(transferBillingEvent)));
    // If we project after the grace period expires all should be the same except the grace period.
    DomainResource afterGracePeriod = domain.cloneProjectedAtTime(
        clock.nowUtc().plusDays(2).plus(Registry.get("com").getTransferGracePeriodLength()));
    assertTransferred(afterGracePeriod, newExpirationTime, serverApproveAutorenewEvent);
    assertThat(afterGracePeriod.getGracePeriods()).isEmpty();
  }

  @Test
  public void testExpiredTransfer() {
    doExpiredTransferTest(clock.nowUtc().plusMonths(1));
  }

  @Test
  public void testExpiredTransfer_autoRenewBeforeTransfer() {
    // Since transfer swallows a preceding autorenew, this should be identical to the regular
    // transfer case (and specifically, the new expiration and grace periods will be the same as if
    // there was no autorenew).
    doExpiredTransferTest(clock.nowUtc().minusDays(1));
  }

  @Test
  public void testStackedGracePeriods() {
    List<GracePeriod> gracePeriods = ImmutableList.of(
        GracePeriod.create(GracePeriodStatus.ADD, clock.nowUtc().plusDays(3), "foo", null),
        GracePeriod.create(GracePeriodStatus.ADD, clock.nowUtc().plusDays(2), "bar", null),
        GracePeriod.create(GracePeriodStatus.ADD, clock.nowUtc().plusDays(1), "baz", null));
    domain = domain.asBuilder().setGracePeriods(ImmutableSet.copyOf(gracePeriods)).build();
    for (int i = 1; i < 3; ++i) {
      assertThat(domain.cloneProjectedAtTime(clock.nowUtc().plusDays(i)).getGracePeriods())
          .containsExactlyElementsIn(Iterables.limit(gracePeriods, 3 - i));
    }
  }

  @Test
  public void testRenewalsHappenAtExpiration() {
    DomainResource renewed =
        domain.cloneProjectedAtTime(domain.getRegistrationExpirationTime());
    assertThat(renewed.getRegistrationExpirationTime())
        .isEqualTo(domain.getRegistrationExpirationTime().plusYears(1));
    assertThat(renewed.getLastEppUpdateTime()).isEqualTo(clock.nowUtc());
    assertThat(getOnlyElement(renewed.getGracePeriods()).getType())
        .isEqualTo(GracePeriodStatus.AUTO_RENEW);
  }

  @Test
  public void testTldGetsSet() {
    createTld("tld");
    domain = newDomainResource("foo.tld");
    assertThat(domain.getTld()).isEqualTo("tld");
  }

  @Test
  public void testRenewalsDontHappenOnFebruary29() {
    domain = domain.asBuilder()
        .setRegistrationExpirationTime(DateTime.parse("2004-02-29T22:00:00.0Z"))
        .build();
    DomainResource renewed =
        domain.cloneProjectedAtTime(domain.getRegistrationExpirationTime().plusYears(4));
    assertThat(renewed.getRegistrationExpirationTime().getDayOfMonth()).isEqualTo(28);
  }

  @Test
  public void testMultipleAutoRenews() {
    // Change the registry so that renewal costs change every year to make sure we are using the
    // autorenew time as the lookup time for the cost.
    DateTime oldExpirationTime = domain.getRegistrationExpirationTime();
    persistResource(
        Registry.get("com")
            .asBuilder()
            .setRenewBillingCostTransitions(
                new ImmutableSortedMap.Builder<DateTime, Money>(Ordering.natural())
                    .put(START_OF_TIME, Money.of(USD, 1))
                    .put(oldExpirationTime.plusMillis(1), Money.of(USD, 2))
                    .put(oldExpirationTime.plusYears(1).plusMillis(1), Money.of(USD, 3))
                    // Surround the third autorenew with price changes right before and after just
                    // to be 100% sure that we lookup the cost at the expiration time.
                    .put(oldExpirationTime.plusYears(2).minusMillis(1), Money.of(USD, 4))
                    .put(oldExpirationTime.plusYears(2).plusMillis(1), Money.of(USD, 5))
                    .build())
            .build());
    DomainResource renewedThreeTimes =
        domain.cloneProjectedAtTime(oldExpirationTime.plusYears(2));
    assertThat(renewedThreeTimes.getRegistrationExpirationTime())
        .isEqualTo(oldExpirationTime.plusYears(3));
    assertThat(renewedThreeTimes.getLastEppUpdateTime()).isEqualTo(clock.nowUtc());
    assertThat(renewedThreeTimes.getGracePeriods())
        .containsExactly(GracePeriod.createForRecurring(
            GracePeriodStatus.AUTO_RENEW,
            oldExpirationTime.plusYears(2).plus(
                Registry.get("com").getAutoRenewGracePeriodLength()),
            renewedThreeTimes.getCurrentSponsorClientId(),
            renewedThreeTimes.autorenewBillingEvent));
  }

  @Test
  public void testMarshalingLoadsResourcesEfficiently() throws Exception {
    // All of the resources are in memcache because they were put there when initially persisted.
    // Clear out memcache so that we count actual datastore calls.
    getLocalMemcacheService().flushAll(
        new LocalRpcService.Status(), MemcacheFlushRequest.newBuilder().build());
    int numPreviousReads = RequestCapturingAsyncDatastoreService.getReads().size();
    EppXmlTransformer.marshal(
        EppOutput.create(new EppResponse.Builder()
            .setResult(Result.create(Code.Success))
            .setResData(ImmutableList.of(domain))
            .setTrid(Trid.create(null, "abc"))
            .build()),
        ValidationMode.STRICT);
    // Assert that there was only one call to datastore (that may have loaded many keys).
    assertThat(skip(RequestCapturingAsyncDatastoreService.getReads(), numPreviousReads)).hasSize(1);
  }
}
