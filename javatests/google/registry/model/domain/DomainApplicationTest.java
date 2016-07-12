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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.testing.DatastoreHelper.cloneAndSetAutoTimestamps;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import google.registry.model.EntityTestCase;
import google.registry.model.billing.BillingEvent;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.domain.launch.LaunchPhase;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.AuthInfo.PasswordAuth;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppcommon.Trid;
import google.registry.model.host.HostResource;
import google.registry.model.smd.EncodedSignedMark;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferData.TransferServerApproveEntity;
import google.registry.model.transfer.TransferStatus;
import google.registry.testing.ExceptionRule;
import org.joda.money.Money;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link DomainApplication}. */
public class DomainApplicationTest extends EntityTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  DomainApplication domainApplication;

  @Before
  public void setUp() throws Exception {
    createTld("com");
    // Set up a new persisted domain entity.
    domainApplication = cloneAndSetAutoTimestamps(
        new DomainApplication.Builder()
            .setFullyQualifiedDomainName("example.com")
            .setRepoId("1-COM")
            .setCreationClientId("a registrar")
            .setLastEppUpdateTime(clock.nowUtc())
            .setLastEppUpdateClientId("another registrar")
            .setLastTransferTime(clock.nowUtc())
            .setStatusValues(ImmutableSet.of(
                StatusValue.CLIENT_DELETE_PROHIBITED,
                StatusValue.SERVER_DELETE_PROHIBITED,
                StatusValue.SERVER_TRANSFER_PROHIBITED,
                StatusValue.SERVER_UPDATE_PROHIBITED,
                StatusValue.SERVER_RENEW_PROHIBITED,
                StatusValue.SERVER_HOLD))
            .setRegistrant(Ref.create(persistActiveContact("contact_id1")))
            .setContacts(ImmutableSet.of(DesignatedContact.create(
                DesignatedContact.Type.ADMIN,
                Ref.create(persistActiveContact("contact_id2")))))
            .setNameservers(
                ImmutableSet.of(Ref.create(persistActiveHost("ns1.example.com"))))
            .setCurrentSponsorClientId("a third registrar")
            .setAuthInfo(DomainAuthInfo.create(PasswordAuth.create("password")))
            .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 2, 3, new byte[] {0, 1, 2})))
            .setLaunchNotice(
                LaunchNotice.create("tcnid", "validatorId", START_OF_TIME, START_OF_TIME))
            .setTransferData(
                new TransferData.Builder()
                    .setExtendedRegistrationYears(0)
                    .setGainingClientId("gaining")
                    .setLosingClientId("losing")
                    .setPendingTransferExpirationTime(clock.nowUtc())
                    .setServerApproveEntities(
                        ImmutableSet.<Key<? extends TransferServerApproveEntity>>of(
                            Key.create(BillingEvent.OneTime.class, 1)))
                    .setTransferRequestTime(clock.nowUtc())
                    .setTransferStatus(TransferStatus.SERVER_APPROVED)
                    .setTransferRequestTrid(Trid.create("client trid"))
                    .build())
            .setCreationTrid(Trid.create("client creation trid"))
            .setPhase(LaunchPhase.LANDRUSH)
            .setEncodedSignedMarks(ImmutableList.of(EncodedSignedMark.create("base64", "abcdefg=")))
            .setApplicationStatus(ApplicationStatus.ALLOCATED)
            .setAuctionPrice(Money.of(USD, 11))
            .build());
    persistResource(domainApplication);
  }

  @Test
  public void testPersistence() throws Exception {
    assertThat(
        loadByUniqueId(
            DomainApplication.class, domainApplication.getForeignKey(), clock.nowUtc()))
            .isEqualTo(domainApplication);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(
        domainApplication,
        "allContacts.contactId.linked",
        "fullyQualifiedDomainName",
        "nameservers.linked",
        "deletionTime",
        "currentSponsorClientId",
        "tld");
  }

  private DomainApplication.Builder emptyBuilder() {
    return newDomainApplication("example.com").asBuilder();
  }

  @Test
  public void testEmptyStringsBecomeNull() {
    assertThat(emptyBuilder().setCurrentSponsorClientId(null).build()
        .getCurrentSponsorClientId()).isNull();
    assertThat(emptyBuilder().setCurrentSponsorClientId("").build()
        .getCurrentSponsorClientId()).isNull();
    assertThat(emptyBuilder().setCurrentSponsorClientId(" ").build()
        .getCurrentSponsorClientId()).isNotNull();
  }

  @Test
  public void testEmptySetsAndArraysBecomeNull() {
    assertThat(emptyBuilder().setNameservers(null).build().nameservers).isNull();
    assertThat(emptyBuilder()
        .setNameservers(ImmutableSet.<Ref<HostResource>>of())
        .build()
        .nameservers)
            .isNull();
    assertThat(emptyBuilder()
        .setNameservers(ImmutableSet.of(Ref.create(newHostResource("foo.example.tld"))))
        .build()
        .nameservers)
            .isNotNull();
    // This behavior should also hold true for ImmutableObjects nested in collections.
    assertThat(emptyBuilder()
        .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 1, 1, null)))
        .build()
        .getDsData().asList().get(0).getDigest())
            .isNull();
    assertThat(emptyBuilder()
        .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 1, 1, new byte[]{})))
        .build()
        .getDsData().asList().get(0).getDigest())
            .isNull();
    assertThat(emptyBuilder()
        .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 1, 1, new byte[]{1})))
        .build()
        .getDsData().asList().get(0).getDigest())
            .isNotNull();
  }

  @Test
  public void testEmptyTransferDataBecomesNull() throws Exception {
    DomainApplication withNull = emptyBuilder().setTransferData(null).build();
    DomainApplication withEmpty = withNull.asBuilder().setTransferData(TransferData.EMPTY).build();
    assertThat(withNull).isEqualTo(withEmpty);
    assertThat(withEmpty.hasTransferData()).isFalse();
  }
}
