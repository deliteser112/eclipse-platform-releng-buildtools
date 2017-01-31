// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

import static google.registry.testing.ContactResourceSubject.assertAboutContacts;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.deleteResource;
import static google.registry.testing.DatastoreHelper.persistResource;

import google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.exceptions.NoTransferHistoryToQueryException;
import google.registry.flows.exceptions.NotAuthorizedToViewTransferException;
import google.registry.model.contact.ContactAuthInfo;
import google.registry.model.contact.ContactResource;
import google.registry.model.eppcommon.AuthInfo.PasswordAuth;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferStatus;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ContactTransferQueryFlow}. */
public class ContactTransferQueryFlowTest
    extends ContactTransferFlowTestCase<ContactTransferQueryFlow, ContactResource> {

  @Before
  public void setUp() throws Exception {
    setEppInput("contact_transfer_query.xml");
    clock.setTo(DateTime.parse("2000-06-10T22:00:00.0Z"));
    setClientIdForFlow("NewRegistrar");
    setupContactWithPendingTransfer();
  }

  private void doSuccessfulTest(String commandFilename, String expectedXmlFilename)
      throws Exception {
    setEppInput(commandFilename);
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // Setup done; run the test.
    assertTransactionalFlow(false);
    runFlowAssertResponse(readFile(expectedXmlFilename));
    assertAboutContacts().that(reloadResourceByForeignKey(clock.nowUtc().minusDays(1)))
        .hasOneHistoryEntryEachOfTypes(HistoryEntry.Type.CONTACT_TRANSFER_REQUEST);
    assertNoBillingEvents();
  }

  private void doFailingTest(String commandFilename) throws Exception {
    setEppInput(commandFilename);
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // Setup done; run the test.
    assertTransactionalFlow(false);
    runFlow();
  }

  @Test
  public void testSuccess() throws Exception {
    doSuccessfulTest("contact_transfer_query.xml", "contact_transfer_query_response.xml");
  }

  @Test
  public void testSuccess_withContactRoid() throws Exception {
    doSuccessfulTest("contact_transfer_query_with_roid.xml", "contact_transfer_query_response.xml");
  }

  @Test
  public void testSuccess_sponsoringClient() throws Exception {
    setClientIdForFlow("TheRegistrar");
    doSuccessfulTest("contact_transfer_query.xml", "contact_transfer_query_response.xml");
  }

  @Test
  public void testSuccess_withAuthinfo() throws Exception {
    setClientIdForFlow("ClientZ");
    doSuccessfulTest("contact_transfer_query_with_authinfo.xml",
        "contact_transfer_query_response.xml");
  }

  @Test
  public void testSuccess_clientApproved() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_APPROVED);
    doSuccessfulTest("contact_transfer_query.xml",
        "contact_transfer_query_response_client_approved.xml");
  }

 @Test
  public void testSuccess_clientRejected() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_REJECTED);
    doSuccessfulTest("contact_transfer_query.xml",
        "contact_transfer_query_response_client_rejected.xml");
  }

 @Test
  public void testSuccess_clientCancelled() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_CANCELLED);
    doSuccessfulTest("contact_transfer_query.xml",
        "contact_transfer_query_response_client_cancelled.xml");
  }

  @Test
  public void testSuccess_serverApproved() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_APPROVED);
    doSuccessfulTest("contact_transfer_query.xml",
        "contact_transfer_query_response_server_approved.xml");
  }

  @Test
  public void testSuccess_serverCancelled() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    doSuccessfulTest("contact_transfer_query.xml",
        "contact_transfer_query_response_server_cancelled.xml");
  }

  @Test
  public void testFailure_pendingDeleteContact() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    contact = persistResource(
        contact.asBuilder().setDeletionTime(clock.nowUtc().plusDays(1)).build());
    doSuccessfulTest("contact_transfer_query.xml",
        "contact_transfer_query_response_server_cancelled.xml");
  }

  @Test
  public void testFailure_badContactPassword() throws Exception {
    // Change the contact's password so it does not match the password in the file.
    contact = persistResource(
        contact.asBuilder()
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("badpassword")))
            .build());
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("contact_transfer_query_with_authinfo.xml");
  }

  @Test
  public void testFailure_badContactRoid() throws Exception {
    // Set the contact to a different ROID, but don't persist it; this is just so the substitution
    // code above will write the wrong ROID into the file.
    contact = contact.asBuilder().setRepoId("DEADBEEF_TLD-ROID").build();
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("contact_transfer_query_with_roid.xml");
  }

  @Test
  public void testFailure_neverBeenTransferred() throws Exception {
    changeTransferStatus(null);
    thrown.expect(NoTransferHistoryToQueryException.class);
    doFailingTest("contact_transfer_query.xml");
  }

  @Test
  public void testFailure_unrelatedClient() throws Exception {
    setClientIdForFlow("ClientZ");
    thrown.expect(NotAuthorizedToViewTransferException.class);
    doFailingTest("contact_transfer_query.xml");
  }

  @Test
  public void testFailure_deletedContact() throws Exception {
    contact = persistResource(
        contact.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    doFailingTest("contact_transfer_query.xml");
  }

  @Test
  public void testFailure_nonexistentContact() throws Exception {
    deleteResource(contact);
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    doFailingTest("contact_transfer_query.xml");
  }
}
