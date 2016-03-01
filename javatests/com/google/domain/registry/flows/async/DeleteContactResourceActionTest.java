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
import static com.google.domain.registry.testing.ContactResourceSubject.assertAboutContacts;
import static com.google.domain.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.getOnlyHistoryEntryOfType;
import static com.google.domain.registry.testing.DatastoreHelper.getPollMessages;
import static com.google.domain.registry.testing.DatastoreHelper.newContactResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistContactWithPendingTransfer;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.domain.registry.model.contact.ContactAddress;
import com.google.domain.registry.model.contact.ContactPhoneNumber;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.contact.PostalInfo;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.poll.PendingActionNotificationResponse;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.model.transfer.TransferResponse;
import com.google.domain.registry.model.transfer.TransferStatus;
import com.google.domain.registry.request.HttpException.BadRequestException;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeleteContactResourceAction}. */
@RunWith(JUnit4.class)
public class DeleteContactResourceActionTest
    extends DeleteEppResourceActionTestCase<DeleteContactResourceAction> {

  ContactResource contactUnused;

  @Before
  public void setup() throws Exception {
    setupDeleteEppResourceAction(new DeleteContactResourceAction());
    contactUnused = persistActiveContact("blah1235");
  }

  @Test
  public void testSuccess_contact_referencedByActiveDomain_doesNotGetDeleted() throws Exception {
    contactUsed = persistResource(
        contactUsed.asBuilder()
            .addStatusValue(StatusValue.PENDING_DELETE)
            .build());
    runMapreduceWithKeyParam(Key.create(contactUsed).getString());
    contactUsed = loadByUniqueId(ContactResource.class, "blah1234", now);
    assertAboutContacts().that(contactUsed).doesNotHaveStatusValue(StatusValue.PENDING_DELETE)
        .and().hasDeletionTime(END_OF_TIME);
    domain = loadByUniqueId(DomainResource.class, "example.tld", now);
    assertThat(domain.getReferencedContacts())
        .contains(ReferenceUnion.<ContactResource>create(Ref.create(contactUsed)));
    HistoryEntry historyEntry =
        getOnlyHistoryEntryOfType(contactUsed, HistoryEntry.Type.CONTACT_DELETE_FAILURE);
    assertPollMessageFor(
        historyEntry,
        "TheRegistrar",
        "Can't delete contact blah1234 because it is referenced by a domain.");
  }

  @Test
  public void testSuccess_contact_notReferenced_getsDeleted() throws Exception {
    contactUnused = persistResource(
        contactUnused.asBuilder()
            .setLocalizedPostalInfo(
                new PostalInfo.Builder()
                    .setType(PostalInfo.Type.LOCALIZED)
                    .setAddress(new ContactAddress.Builder()
                        .setStreet(ImmutableList.of("123 Grand Ave"))
                        .build())
                    .build())
            .setInternationalizedPostalInfo(
                new PostalInfo.Builder()
                    .setType(PostalInfo.Type.INTERNATIONALIZED)
                    .setAddress(new ContactAddress.Builder()
                        .setStreet(ImmutableList.of("123 Avenida Grande"))
                        .build())
                    .build())
            .setEmailAddress("bob@bob.com")
            .setVoiceNumber(new ContactPhoneNumber.Builder().setPhoneNumber("555-1212").build())
            .setFaxNumber(new ContactPhoneNumber.Builder().setPhoneNumber("555-1212").build())
            .addStatusValue(StatusValue.PENDING_DELETE)
            .build());
    assertAboutContacts().that(contactUnused).hasNonNullLocalizedPostalInfo()
        .and().hasNonNullInternationalizedPostalInfo()
        .and().hasNonNullEmailAddress()
        .and().hasNonNullVoiceNumber()
        .and().hasNonNullFaxNumber();
    Key<ContactResource> key = Key.create(contactUnused);
    runMapreduceWithKeyParam(key.getString());
    assertThat(loadByUniqueId(ContactResource.class, "blah1235", now)).isNull();
    ContactResource contactAfterDeletion = ofy().load().key(key).now();
    assertAboutContacts().that(contactAfterDeletion).hasDeletionTime(now)
        // Note that there will be another history entry of CONTACT_PENDING_DELETE, but this is
        // added by the flow and not the mapreduce itself.
        .and().hasOnlyOneHistoryEntryWhich().hasType(HistoryEntry.Type.CONTACT_DELETE);
    assertAboutContacts().that(contactAfterDeletion).hasNullLocalizedPostalInfo()
        .and().hasNullInternationalizedPostalInfo()
        .and().hasNullEmailAddress()
        .and().hasNullVoiceNumber()
        .and().hasNullFaxNumber();
    HistoryEntry historyEntry =
        getOnlyHistoryEntryOfType(contactAfterDeletion, HistoryEntry.Type.CONTACT_DELETE);
    assertPollMessageFor(historyEntry, "TheRegistrar", "Deleted contact blah1235.");
  }

  @Test
  public void testSuccess_contactWithPendingTransfer_getsDeleted() throws Exception {
    ContactResource contact = persistContactWithPendingTransfer(
        persistActiveContact("sh8013").asBuilder()
            .addStatusValue(StatusValue.PENDING_DELETE)
            .build(),
        transferRequestTime,
        transferExpirationTime,
        clock.nowUtc());
    runMapreduceWithKeyParam(Key.create(contact).getString());
    // Check that the contact is deleted as of now.
    assertThat(loadByUniqueId(ContactResource.class, "sh8013", now)).isNull();
    // Check that it's still there (it wasn't deleted yesterday) and that it has history.
    assertAboutContacts()
        .that(loadByUniqueId(ContactResource.class, "sh8013", now.minusDays(1)))
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.CONTACT_TRANSFER_REQUEST,
            HistoryEntry.Type.CONTACT_DELETE);
    assertNoBillingEvents();
    PollMessage deletePollMessage = Iterables.getOnlyElement(
        getPollMessages("TheRegistrar", clock.nowUtc().plusMonths(1)));
    assertThat(deletePollMessage.getMsg()).isEqualTo("Deleted contact sh8013.");
    // The poll message in the future to the gaining registrar should be gone too, but there
    // should be one at the current time to the gaining registrar.
    PollMessage gainingPollMessage = Iterables.getOnlyElement(
        getPollMessages("NewRegistrar", clock.nowUtc()));
    System.out.println(gainingPollMessage);
    assertThat(gainingPollMessage.getEventTime()).isEqualTo(clock.nowUtc());
    assertThat(
        Iterables.getOnlyElement(FluentIterable.from(gainingPollMessage.getResponseData())
            .filter(TransferResponse.class))
                .getTransferStatus())
                .isEqualTo(TransferStatus.SERVER_CANCELLED);
    PendingActionNotificationResponse panData = Iterables.getOnlyElement(FluentIterable
        .from(gainingPollMessage.getResponseData())
        .filter(PendingActionNotificationResponse.class));
    assertThat(panData.getTrid())
        .isEqualTo(Trid.create("transferClient-trid", "transferServer-trid"));
    assertThat(panData.getActionResult()).isFalse();
  }

  @Test
  public void testSuccess_contact_referencedByDeleteDomain_getsDeleted() throws Exception {
    contactUsed = persistResource(
        contactUsed.asBuilder()
            .addStatusValue(StatusValue.PENDING_DELETE)
            .build());
    domain = persistResource(
        domain.asBuilder()
            .setDeletionTime(now.minusDays(3))
            .build());
    runMapreduceWithKeyParam(Key.create(contactUsed).getString());
    assertThat(loadByUniqueId(ContactResource.class, "blah1234", now)).isNull();
    ContactResource contactBeforeDeletion =
        loadByUniqueId(ContactResource.class, "blah1234", now.minusDays(1));
    assertAboutContacts().that(contactBeforeDeletion).hasDeletionTime(now)
        .and().hasExactlyStatusValues(StatusValue.OK)
        // Note that there will be another history entry of CONTACT_PENDING_DELETE, but this is
        // added by the flow and not the mapreduce itself.
        .and().hasOnlyOneHistoryEntryWhich().hasType(HistoryEntry.Type.CONTACT_DELETE);
    HistoryEntry historyEntry =
        getOnlyHistoryEntryOfType(contactBeforeDeletion, HistoryEntry.Type.CONTACT_DELETE);
    assertPollMessageFor(historyEntry, "TheRegistrar", "Deleted contact blah1234.");
  }

  @Test
  public void testFailure_notPendingDelete() throws Exception {
    thrown.expect(IllegalStateException.class, "Resource blah1235 is not set as PENDING_DELETE");
    runMapreduceWithKeyParam(Key.create(contactUnused).getString());
    assertThat(
        loadByUniqueId(ContactResource.class, "blah1235", now)).isEqualTo(contactUnused);
  }

  @Test
  public void testSuccess_notRequestedByOwner_doesNotGetDeleted() throws Exception {
    contactUnused = persistResource(
        contactUnused.asBuilder()
            .addStatusValue(StatusValue.PENDING_DELETE)
            .build());
    Key<ContactResource> key = Key.create(contactUnused);
    runMapreduceWithParams(key.getString(), "OtherRegistrar", false);
    contactUnused = loadByUniqueId(ContactResource.class, "blah1235", now);
    assertAboutContacts().that(contactUnused).doesNotHaveStatusValue(StatusValue.PENDING_DELETE)
        .and().hasDeletionTime(END_OF_TIME);
    domain = loadByUniqueId(DomainResource.class, "example.tld", now);
    HistoryEntry historyEntry =
        getOnlyHistoryEntryOfType(contactUnused, HistoryEntry.Type.CONTACT_DELETE_FAILURE);
    assertPollMessageFor(
        historyEntry,
        "OtherRegistrar",
        "Can't delete contact blah1235 because it was transferred prior to deletion.");
  }

  @Test
  public void testSuccess_notRequestedByOwner_isSuperuser_getsDeleted() throws Exception {
    contactUnused = persistResource(
        contactUnused.asBuilder()
            .setLocalizedPostalInfo(
                new PostalInfo.Builder()
                    .setType(PostalInfo.Type.LOCALIZED)
                    .setAddress(new ContactAddress.Builder()
                        .setStreet(ImmutableList.of("123 Grand Ave"))
                        .build())
                    .build())
            .setInternationalizedPostalInfo(
                new PostalInfo.Builder()
                    .setType(PostalInfo.Type.INTERNATIONALIZED)
                    .setAddress(new ContactAddress.Builder()
                        .setStreet(ImmutableList.of("123 Avenida Grande"))
                        .build())
                    .build())
            .setEmailAddress("bob@bob.com")
            .setVoiceNumber(new ContactPhoneNumber.Builder().setPhoneNumber("555-1212").build())
            .setFaxNumber(new ContactPhoneNumber.Builder().setPhoneNumber("555-1212").build())
            .addStatusValue(StatusValue.PENDING_DELETE)
            .build());
    Key<ContactResource> key = Key.create(contactUnused);
    runMapreduceWithParams(key.getString(), "OtherRegistrar", true);
    assertThat(loadByUniqueId(ContactResource.class, "blah1235", now)).isNull();
    ContactResource contactAfterDeletion = ofy().load().key(key).now();
    assertAboutContacts().that(contactAfterDeletion).hasDeletionTime(now)
        // Note that there will be another history entry of CONTACT_PENDING_DELETE, but this is
        // added by the flow and not the mapreduce itself.
        .and().hasOnlyOneHistoryEntryWhich().hasType(HistoryEntry.Type.CONTACT_DELETE);
    assertAboutContacts().that(contactAfterDeletion).hasNullLocalizedPostalInfo()
        .and().hasNullInternationalizedPostalInfo()
        .and().hasNullEmailAddress()
        .and().hasNullVoiceNumber()
        .and().hasNullFaxNumber();
    HistoryEntry historyEntry =
        getOnlyHistoryEntryOfType(contactAfterDeletion, HistoryEntry.Type.CONTACT_DELETE);
    assertPollMessageFor(historyEntry, "OtherRegistrar", "Deleted contact blah1235.");
  }

  @Test
  public void testFailure_targetResourceDoesntExist() throws Exception {
    createTld("tld");
    ContactResource notPersisted = newContactResource("somecontact");
    thrown.expect(
        BadRequestException.class,
        "Could not load resource for key: Key<?>(ContactResource(\"7-ROID\"))");
    runMapreduceWithKeyParam(Key.create(notPersisted).getString());
  }

  @Test
  public void testFailure_contactAlreadyDeleted() throws Exception {
    ContactResource contactDeleted = persistResource(
        newContactResource("blah1236").asBuilder()
            .setCreationTimeForTest(clock.nowUtc().minusDays(2))
            .setDeletionTime(clock.nowUtc().minusDays(1))
            .build());
    thrown.expect(
        IllegalStateException.class,
        "Resource blah1236 is already deleted.");
    runMapreduceWithKeyParam(Key.create(contactDeleted).getString());
  }
}
