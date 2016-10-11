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
import static google.registry.testing.DatastoreHelper.getOnlyPollMessage;
import static google.registry.testing.DatastoreHelper.getPollMessages;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import google.registry.flows.exceptions.NotPendingTransferException;
import google.registry.model.contact.ContactAuthInfo;
import google.registry.model.contact.ContactResource;
import google.registry.model.eppcommon.AuthInfo.PasswordAuth;
import google.registry.model.eppcommon.Trid;
import google.registry.model.poll.PendingActionNotificationResponse;
import google.registry.model.poll.PollMessage;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferResponse;
import google.registry.model.transfer.TransferStatus;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ContactTransferRejectFlow}. */
public class ContactTransferRejectFlowTest
    extends ContactTransferFlowTestCase<ContactTransferRejectFlow, ContactResource> {

  @Before
  public void setUp() throws Exception {
    setEppInput("contact_transfer_reject.xml");
    setClientIdForFlow("TheRegistrar");
    setupContactWithPendingTransfer();
    clock.advanceOneMilli();
  }

  private void doSuccessfulTest(String commandFilename, String expectedXmlFilename)
      throws Exception {
    setEppInput(commandFilename);
    // Look in the future and make sure the poll messages for implicit ack are there.
    assertThat(getPollMessages("NewRegistrar", clock.nowUtc().plusMonths(1)))
        .hasSize(1);
    assertThat(getPollMessages("TheRegistrar", clock.nowUtc().plusMonths(1)))
        .hasSize(1);

    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile(expectedXmlFilename));

    // Transfer should have failed. Verify correct fields were set.
    contact = reloadResourceByForeignKey();
    assertAboutContacts().that(contact)
        .hasCurrentSponsorClientId("TheRegistrar").and()
        .hasLastTransferTimeNotEqualTo(clock.nowUtc()).and()
        .hasTransferStatus(TransferStatus.CLIENT_REJECTED).and()
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.CONTACT_TRANSFER_REQUEST,
            HistoryEntry.Type.CONTACT_TRANSFER_REJECT);
    // The poll message (in the future) to the losing registrar for implicit ack should be gone.
    assertThat(getPollMessages("TheRegistrar", clock.nowUtc().plusMonths(1)))
        .isEmpty();
    // The poll message in the future to the gaining registrar should be gone too, but there
    // should be one at the current time to the gaining registrar.
    PollMessage gainingPollMessage = getOnlyPollMessage("NewRegistrar");
    assertThat(gainingPollMessage.getEventTime()).isEqualTo(clock.nowUtc());
    assertThat(
        Iterables.getOnlyElement(FluentIterable
            .from(gainingPollMessage.getResponseData())
            .filter(TransferResponse.class))
                .getTransferStatus())
                .isEqualTo(TransferStatus.CLIENT_REJECTED);
    PendingActionNotificationResponse panData = Iterables.getOnlyElement(FluentIterable
        .from(gainingPollMessage.getResponseData())
        .filter(PendingActionNotificationResponse.class));
    assertThat(panData.getTrid())
        .isEqualTo(Trid.create("transferClient-trid", "transferServer-trid"));
    assertThat(panData.getActionResult()).isFalse();
    assertNoBillingEvents();
  }

  private void doFailingTest(String commandFilename) throws Exception {
    setEppInput(commandFilename);
    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlow();
  }

  @Test
  public void testDryRun() throws Exception {
    setEppInput("contact_transfer_reject.xml");
    dryRunFlowAssertResponse(readFile("contact_transfer_reject_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    doSuccessfulTest("contact_transfer_reject.xml", "contact_transfer_reject_response.xml");
  }

  @Test
  public void testSuccess_domainAuthInfo() throws Exception {
    doSuccessfulTest("contact_transfer_reject_with_authinfo.xml",
        "contact_transfer_reject_response.xml");
  }

  @Test
  public void testFailure_badPassword() throws Exception {
    // Change the contact's password so it does not match the password in the file.
    contact = persistResource(
        contact.asBuilder()
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("badpassword")))
            .build());
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("contact_transfer_reject_with_authinfo.xml");
  }

  @Test
  public void testFailure_neverBeenTransferred() throws Exception {
    changeTransferStatus(null);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("contact_transfer_reject.xml");
  }

  @Test
  public void testFailure_clientApproved() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_APPROVED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("contact_transfer_reject.xml");
  }

 @Test
  public void testFailure_clientRejected() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_REJECTED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("contact_transfer_reject.xml");
  }

 @Test
  public void testFailure_clientCancelled() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_CANCELLED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("contact_transfer_reject.xml");
  }

  @Test
  public void testFailure_serverApproved() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_APPROVED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("contact_transfer_reject.xml");
  }

  @Test
  public void testFailure_serverCancelled() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("contact_transfer_reject.xml");
  }

  @Test
  public void testFailure_gainingClient() throws Exception {
    setClientIdForFlow("NewRegistrar");
    thrown.expect(ResourceNotOwnedException.class);
    doFailingTest("contact_transfer_reject.xml");
  }

  @Test
  public void testFailure_unrelatedClient() throws Exception {
    setClientIdForFlow("ClientZ");
    thrown.expect(ResourceNotOwnedException.class);
    doFailingTest("contact_transfer_reject.xml");
  }

  @Test
  public void testFailure_deletedContact() throws Exception {
    contact = persistResource(
        contact.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    doFailingTest("contact_transfer_reject.xml");
  }

  @Test
  public void testFailure_nonexistentContact() throws Exception {
    deleteResource(contact);
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    doFailingTest("contact_transfer_reject.xml");
  }
}
