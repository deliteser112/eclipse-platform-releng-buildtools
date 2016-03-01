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

package com.google.domain.registry.model.domain;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.EppResourceUtils.loadByUniqueId;
import static com.google.domain.registry.testing.DatastoreHelper.cloneAndSetAutoTimestamps;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainApplication;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.EntityTestCase;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.EppResource.SharedFields;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.domain.launch.ApplicationStatus;
import com.google.domain.registry.model.domain.launch.LaunchNotice;
import com.google.domain.registry.model.domain.launch.LaunchPhase;
import com.google.domain.registry.model.domain.secdns.DelegationSignerData;
import com.google.domain.registry.model.eppcommon.AuthInfo.PasswordAuth;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.smd.EncodedSignedMark;
import com.google.domain.registry.model.transfer.TransferData;
import com.google.domain.registry.model.transfer.TransferData.TransferServerApproveEntity;
import com.google.domain.registry.model.transfer.TransferStatus;
import com.google.domain.registry.testing.ExceptionRule;

import com.googlecode.objectify.Key;

import org.joda.money.Money;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;

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
            .setRegistrant(ReferenceUnion.create(persistActiveContact("contact_id1")))
            .setContacts(ImmutableSet.of(DesignatedContact.create(
                DesignatedContact.Type.ADMIN,
                ReferenceUnion.create(persistActiveContact("contact_id2")))))
            .setNameservers(
                ImmutableSet.of(ReferenceUnion.create(persistActiveHost("ns1.example.com"))))
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
        "sharedFields.deletionTime",
        "sharedFields.currentSponsorClientId",
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
        .setNameservers(ImmutableSet.<ReferenceUnion<HostResource>>of())
        .build()
        .nameservers)
            .isNull();
    assertThat(emptyBuilder()
        .setNameservers(ImmutableSet.of(ReferenceUnion.<HostResource>create("foo")))
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
    // We don't have package access to SharedFields so we need to use reflection to check for null.
    Field sharedFieldsField = EppResource.class.getDeclaredField("sharedFields");
    sharedFieldsField.setAccessible(true);
    Field transferDataField = SharedFields.class.getDeclaredField("transferData");
    transferDataField.setAccessible(true);
    assertThat(transferDataField.get(sharedFieldsField.get(withEmpty))).isNull();
  }
}
