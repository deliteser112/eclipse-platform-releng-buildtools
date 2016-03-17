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

package com.google.domain.registry.model.contact;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.EppResourceUtils.loadByUniqueId;
import static com.google.domain.registry.testing.ContactResourceSubject.assertAboutContacts;
import static com.google.domain.registry.testing.DatastoreHelper.cloneAndSetAutoTimestamps;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.EntityTestCase;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.contact.Disclose.PostalInfoChoice;
import com.google.domain.registry.model.contact.PostalInfo.Type;
import com.google.domain.registry.model.eppcommon.AuthInfo.PasswordAuth;
import com.google.domain.registry.model.eppcommon.PresenceMarker;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.transfer.TransferData;
import com.google.domain.registry.model.transfer.TransferData.TransferServerApproveEntity;
import com.google.domain.registry.model.transfer.TransferStatus;
import com.google.domain.registry.testing.ExceptionRule;

import com.googlecode.objectify.Key;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link ContactResource}. */
public class ContactResourceTest extends EntityTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  ContactResource contactResource;

  @Before
  public void setUp() throws Exception {
    createTld("foobar");
    // Set up a new persisted ContactResource entity.
    contactResource = cloneAndSetAutoTimestamps(
        new ContactResource.Builder()
            .setContactId("contact_id")
            .setRepoId("1-FOOBAR")
            .setCreationClientId("a registrar")
            .setLastEppUpdateTime(clock.nowUtc())
            .setLastEppUpdateClientId("another registrar")
            .setLastTransferTime(clock.nowUtc())
            .setCurrentSponsorClientId("a third registrar")
            .setLocalizedPostalInfo(new PostalInfo.Builder()
                .setType(Type.LOCALIZED)
                .setAddress(new ContactAddress.Builder()
                    .setStreet(ImmutableList.of("111 8th Ave", "4th Floor"))
                    .setCity("New York")
                    .setState("NY")
                    .setZip("10011")
                    .setCountryCode("US")
                    .build())
                .build())
            .setInternationalizedPostalInfo(new PostalInfo.Builder()
                .setType(Type.INTERNATIONALIZED)
                .setAddress(new ContactAddress.Builder()
                    .setStreet(ImmutableList.of("111 8th Ave", "4th Floor"))
                    .setCity("New York")
                    .setState("NY")
                    .setZip("10011")
                    .setCountryCode("US")
                    .build())
                .build())
            .setVoiceNumber(new ContactPhoneNumber.Builder()
                .setPhoneNumber("867-5309")
                .build())
            .setFaxNumber(new ContactPhoneNumber.Builder()
                .setPhoneNumber("867-5309")
                .setExtension("1000")
                .build())
            .setEmailAddress("jenny@example.com")
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("passw0rd")))
            .setDisclose(new Disclose.Builder()
                .setVoice(new PresenceMarker())
                .setEmail(new PresenceMarker())
                .setFax(new PresenceMarker())
                .setFlag(true)
                .setAddrs(ImmutableList.of(PostalInfoChoice.create(Type.INTERNATIONALIZED)))
                .setNames(ImmutableList.of(PostalInfoChoice.create(Type.INTERNATIONALIZED)))
                .setOrgs(ImmutableList.of(PostalInfoChoice.create(Type.INTERNATIONALIZED)))
                .build())
            .setStatusValues(ImmutableSet.of(StatusValue.OK))
            .setTransferData(new TransferData.Builder()
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
            .build());
    persistResource(contactResource);
  }

  @Test
  public void testPersistence() throws Exception {
    assertThat(
        loadByUniqueId(ContactResource.class, contactResource.getForeignKey(), clock.nowUtc()))
        .isEqualTo(contactResource);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(
        contactResource,
        "deletionTime",
        "currentSponsorClientId");
  }

  @Test
  public void testEmptyStringsBecomeNull() {
    assertThat(new ContactResource.Builder().setContactId(null).build().getContactId()).isNull();
    assertThat(new ContactResource.Builder().setContactId("").build().getContactId()).isNull();
    assertThat(new ContactResource.Builder().setContactId(" ").build().getContactId()).isNotNull();
    // Nested ImmutableObjects should also be fixed
    assertThat(new ContactResource.Builder()
        .setInternationalizedPostalInfo(new PostalInfo.Builder()
            .setType(Type.INTERNATIONALIZED)
                .setName(null)
                .build())
        .build()
        .getInternationalizedPostalInfo().getName()).isNull();
    assertThat(new ContactResource.Builder()
        .setInternationalizedPostalInfo(new PostalInfo.Builder()
            .setType(Type.INTERNATIONALIZED)
                .setName("")
                .build())
        .build()
        .getInternationalizedPostalInfo().getName()).isNull();
    assertThat(new ContactResource.Builder()
        .setInternationalizedPostalInfo(new PostalInfo.Builder()
            .setType(Type.INTERNATIONALIZED)
                .setName(" ")
                .build())
        .build()
        .getInternationalizedPostalInfo().getName()).isNotNull();
  }

  @Test
  public void testEmptyTransferDataBecomesNull() throws Exception {
    ContactResource withNull = new ContactResource.Builder().setTransferData(null).build();
    ContactResource withEmpty = withNull.asBuilder().setTransferData(TransferData.EMPTY).build();
    assertThat(withNull).isEqualTo(withEmpty);
    assertThat(withEmpty.hasTransferData()).isFalse();
  }

  @Test
  public void testImplicitStatusValues() {
    // OK is implicit if there's no other statuses.
    StatusValue[] statuses = {StatusValue.OK};
    assertAboutContacts()
        .that(new ContactResource.Builder().build())
        .hasExactlyStatusValues(statuses);
    StatusValue[] statuses1 = {StatusValue.OK, StatusValue.LINKED};
    // OK is also implicit if the only other status is LINKED.
    assertAboutContacts()
        .that(new ContactResource.Builder()
            .setStatusValues(ImmutableSet.of(StatusValue.LINKED))
            .build())
        .hasExactlyStatusValues(statuses1);
    StatusValue[] statuses2 = {StatusValue.CLIENT_HOLD};
    // If there are other status values, OK should be suppressed.
    assertAboutContacts()
        .that(new ContactResource.Builder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_HOLD))
            .build())
        .hasExactlyStatusValues(statuses2);
    StatusValue[] statuses3 = {StatusValue.LINKED, StatusValue.CLIENT_HOLD};
    assertAboutContacts()
        .that(new ContactResource.Builder()
            .setStatusValues(ImmutableSet.of(StatusValue.LINKED, StatusValue.CLIENT_HOLD))
            .build())
        .hasExactlyStatusValues(statuses3);
    StatusValue[] statuses4 = {StatusValue.CLIENT_HOLD};
    // When OK is suppressed, it should be removed even if it was originally there.
    assertAboutContacts()
        .that(new ContactResource.Builder()
            .setStatusValues(ImmutableSet.of(StatusValue.OK, StatusValue.CLIENT_HOLD))
            .build())
        .hasExactlyStatusValues(statuses4);
  }

  @Test
  public void testExpiredTransfer() {
    ContactResource afterTransfer = contactResource.asBuilder()
       .setTransferData(contactResource.getTransferData().asBuilder()
           .setTransferStatus(TransferStatus.PENDING)
           .setPendingTransferExpirationTime(clock.nowUtc().plusDays(1))
           .setGainingClientId("winner")
           .build())
        .build()
        .cloneProjectedAtTime(clock.nowUtc().plusDays(1));
    assertThat(afterTransfer.getTransferData().getTransferStatus()).isEqualTo(
        TransferStatus.SERVER_APPROVED);
    assertThat(afterTransfer.getCurrentSponsorClientId()).isEqualTo("winner");
    assertThat(afterTransfer.getLastTransferTime()).isEqualTo(clock.nowUtc().plusDays(1));
  }
}
