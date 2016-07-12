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

package google.registry.flows.contact;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.ContactResourceSubject.assertAboutContacts;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.deleteResource;
import static google.registry.testing.DatastoreHelper.getPollMessages;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistResource;

import google.registry.config.RegistryEnvironment;
import google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException;
import google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException;
import google.registry.flows.ResourceTransferRequestFlow.AlreadyPendingTransferException;
import google.registry.flows.ResourceTransferRequestFlow.MissingTransferRequestAuthInfoException;
import google.registry.flows.ResourceTransferRequestFlow.ObjectAlreadySponsoredException;
import google.registry.model.contact.ContactAuthInfo;
import google.registry.model.contact.ContactResource;
import google.registry.model.eppcommon.AuthInfo.PasswordAuth;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferStatus;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ContactTransferRequestFlow}. */
public class ContactTransferRequestFlowTest
    extends ContactTransferFlowTestCase<ContactTransferRequestFlow, ContactResource> {

  public ContactTransferRequestFlowTest() {
    // We need the transfer to happen at exactly this time in order for the response to match up.
    clock.setTo(DateTime.parse("2000-06-08T22:00:00.0Z"));
  }

  @Before
  public void setUp() throws Exception {
    setEppInput("contact_transfer_request.xml");
    setClientIdForFlow("NewRegistrar");
    contact = persistActiveContact("sh8013");
    clock.advanceOneMilli();
  }

  private void doSuccessfulTest(String commandFilename, String expectedXmlFilename)
      throws Exception {
    setEppInput(commandFilename);
    DateTime afterTransfer =
        clock.nowUtc().plus(RegistryEnvironment.get().config().getContactAutomaticTransferLength());

    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile(expectedXmlFilename));

    // Transfer should have been requested. Verify correct fields were set.
    contact = reloadResourceByUniqueId();
    assertAboutContacts().that(contact)
        .hasTransferStatus(TransferStatus.PENDING).and()
        .hasTransferGainingClientId("NewRegistrar").and()
        .hasTransferLosingClientId("TheRegistrar").and()
        .hasTransferRequestClientTrid(getClientTrid()).and()
        .hasCurrentSponsorClientId("TheRegistrar").and()
        .hasPendingTransferExpirationTime(afterTransfer).and()
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.CONTACT_TRANSFER_REQUEST);
    assertNoBillingEvents();
    assertThat(getPollMessages("TheRegistrar", clock.nowUtc())).hasSize(1);

    // If we fast forward AUTOMATIC_TRANSFER_DAYS the transfer should have happened.
    assertAboutContacts().that(contact.cloneProjectedAtTime(afterTransfer))
        .hasCurrentSponsorClientId("NewRegistrar");
    assertThat(getPollMessages("NewRegistrar", afterTransfer)).hasSize(1);
    assertThat(getPollMessages("TheRegistrar", afterTransfer)).hasSize(2);
  }

  private void doFailingTest(String commandFilename) throws Exception {
    setEppInput(commandFilename);
    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlow();
  }

  @Test
  public void testDryRun() throws Exception {
    setEppInput("contact_transfer_request.xml");
    dryRunFlowAssertResponse(readFile("contact_transfer_request_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    doSuccessfulTest("contact_transfer_request.xml", "contact_transfer_request_response.xml");
  }

  @Test
  public void testFailure_noAuthInfo() throws Exception {
    thrown.expect(MissingTransferRequestAuthInfoException.class);
    doFailingTest("contact_transfer_request_no_authinfo.xml");
  }

  @Test
  public void testFailure_badPassword() throws Exception {
    thrown.expect(BadAuthInfoForResourceException.class);
    // Change the contact's password so it does not match the password in the file.
    contact = persistResource(
        contact.asBuilder()
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("badpassword")))
            .build());
    doFailingTest("contact_transfer_request.xml");
  }

  @Test
  public void testSuccess_clientApproved() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_APPROVED);
    doSuccessfulTest("contact_transfer_request.xml", "contact_transfer_request_response.xml");
  }

 @Test
  public void testSuccess_clientRejected() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_REJECTED);
    doSuccessfulTest("contact_transfer_request.xml", "contact_transfer_request_response.xml");
  }

 @Test
  public void testSuccess_clientCancelled() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_CANCELLED);
    doSuccessfulTest("contact_transfer_request.xml", "contact_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_serverApproved() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_APPROVED);
    doSuccessfulTest("contact_transfer_request.xml", "contact_transfer_request_response.xml");
  }

  @Test
  public void testSuccess_serverCancelled() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    doSuccessfulTest("contact_transfer_request.xml", "contact_transfer_request_response.xml");
  }

  @Test
  public void testFailure_pending() throws Exception {
    thrown.expect(AlreadyPendingTransferException.class);
    contact = persistResource(
        contact.asBuilder()
            .setTransferData(contact.getTransferData().asBuilder()
                .setTransferStatus(TransferStatus.PENDING)
                .setPendingTransferExpirationTime(clock.nowUtc().plusDays(1))
                .build())
            .build());
    doFailingTest("contact_transfer_request.xml");
  }

  @Test
  public void testFailure_sponsoringClient() throws Exception {
    thrown.expect(ObjectAlreadySponsoredException.class);
    setClientIdForFlow("TheRegistrar");
    doFailingTest("contact_transfer_request.xml");
  }

  @Test
  public void testFailure_deletedContact() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    contact = persistResource(
        contact.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    doFailingTest("contact_transfer_request.xml");
  }

  @Test
  public void testFailure_nonexistentContact() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    deleteResource(contact);
    doFailingTest("contact_transfer_request.xml");
  }
}
