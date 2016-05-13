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

package com.google.domain.registry.flows.domain;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.assertBillingEvents;
import static com.google.domain.registry.testing.DatastoreHelper.deleteResource;
import static com.google.domain.registry.testing.DatastoreHelper.getOnlyPollMessage;
import static com.google.domain.registry.testing.DatastoreHelper.getPollMessages;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.DomainResourceSubject.assertAboutDomains;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.domain.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException;
import com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException;
import com.google.domain.registry.flows.ResourceMutatePendingTransferFlow.NotPendingTransferException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import com.google.domain.registry.model.contact.ContactAuthInfo;
import com.google.domain.registry.model.domain.DomainAuthInfo;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.GracePeriod;
import com.google.domain.registry.model.eppcommon.AuthInfo.PasswordAuth;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.poll.PendingActionNotificationResponse;
import com.google.domain.registry.model.poll.PollMessage;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.model.transfer.TransferResponse;
import com.google.domain.registry.model.transfer.TransferStatus;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainTransferRejectFlow}. */
public class DomainTransferRejectFlowTest
    extends DomainTransferFlowTestCase<DomainTransferRejectFlow, DomainResource> {

  @Before
  public void setUp() throws Exception {
    setEppInput("domain_transfer_reject.xml");
    setClientIdForFlow("TheRegistrar");
    setupDomainWithPendingTransfer();
    clock.advanceOneMilli();
  }

  private void doSuccessfulTest(String commandFilename, String expectedXmlFilename)
      throws Exception {
    setEppInput(commandFilename);
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // Make sure the implicit billing event is there; it will be deleted by the flow.
    // We also expect to see autorenew events for the gaining and losing registrars.
    assertBillingEvents(
        getBillingEventForImplicitTransfer(),
        getGainingClientAutorenewEvent(),
        getLosingClientAutorenewEvent());
    // Look in the future and make sure the poll messages for implicit ack are there.
    assertThat(getPollMessages("NewRegistrar", clock.nowUtc().plusMonths(1)))
        .hasSize(1);
    assertThat(getPollMessages("TheRegistrar", clock.nowUtc().plusMonths(1)))
        .hasSize(1);
    // Setup done; run the test.
    assertTransactionalFlow(true);
    DateTime originalExpirationTime = domain.getRegistrationExpirationTime();
    ImmutableSet<GracePeriod> originalGracePeriods = domain.getGracePeriods();
    runFlowAssertResponse(readFile(expectedXmlFilename));
    // Transfer should have been rejected. Verify correct fields were set.
    domain = reloadResourceByUniqueId();
    assertTransferFailed(domain, TransferStatus.CLIENT_REJECTED);
    assertTransferFailed(
        reloadResourceAndCloneAtTime(subordinateHost, clock.nowUtc()),
        TransferStatus.CLIENT_REJECTED);
    assertAboutDomains().that(domain)
        .hasRegistrationExpirationTime(originalExpirationTime).and()
        .hasLastTransferTimeNotEqualTo(clock.nowUtc()).and()
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.DOMAIN_CREATE,
            HistoryEntry.Type.DOMAIN_TRANSFER_REQUEST,
            HistoryEntry.Type.DOMAIN_TRANSFER_REJECT);
    // The only billing event left should be the original autorenew event, now reopened.
    assertBillingEvents(
        getLosingClientAutorenewEvent().asBuilder().setRecurrenceEndTime(END_OF_TIME).build());
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
    // The original grace periods should remain untouched.
    assertThat(domain.getGracePeriods()).isEqualTo(originalGracePeriods);
  }

  private void doFailingTest(String commandFilename) throws Exception {
    setEppInput(commandFilename);
    // Replace the ROID in the xml file with the one generated in our test.
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlow();
  }

  @Test
  public void testSuccess() throws Exception {
    doSuccessfulTest("domain_transfer_reject.xml", "domain_transfer_reject_response.xml");
  }

  @Test
  public void testDryRun() throws Exception {
    setEppInput("domain_transfer_reject.xml");
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    dryRunFlowAssertResponse(readFile("domain_transfer_reject_response.xml"));
  }

  @Test
  public void testSuccess_domainAuthInfo() throws Exception {
    doSuccessfulTest("domain_transfer_reject_domain_authinfo.xml",
        "domain_transfer_reject_response.xml");
  }

  @Test
  public void testSuccess_contactAuthInfo() throws Exception {
    doSuccessfulTest("domain_transfer_reject_contact_authinfo.xml",
        "domain_transfer_reject_response.xml");
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    thrown.expect(NotAuthorizedForTldException.class);
    persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    doSuccessfulTest("domain_transfer_reject.xml", "domain_transfer_reject_response.xml");
  }

  @Test
  public void testFailure_badContactPassword() throws Exception {
    thrown.expect(BadAuthInfoForResourceException.class);
    // Change the contact's password so it does not match the password in the file.
    contact = persistResource(
        contact.asBuilder()
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("badpassword")))
            .build());
    doFailingTest("domain_transfer_reject_contact_authinfo.xml");
  }

  @Test
  public void testFailure_badDomainPassword() throws Exception {
    thrown.expect(BadAuthInfoForResourceException.class);
    // Change the domain's password so it does not match the password in the file.
    domain = persistResource(
        domain.asBuilder()
            .setAuthInfo(DomainAuthInfo.create(PasswordAuth.create("badpassword")))
            .build());
    doFailingTest("domain_transfer_reject_domain_authinfo.xml");
  }

  @Test
  public void testFailure_neverBeenTransferred() throws Exception {
    thrown.expect(NotPendingTransferException.class);
    changeTransferStatus(null);
    doFailingTest("domain_transfer_reject.xml");
  }

  @Test
  public void testFailure_clientApproved() throws Exception {
    thrown.expect(NotPendingTransferException.class);
    changeTransferStatus(TransferStatus.CLIENT_APPROVED);
    doFailingTest("domain_transfer_reject.xml");
  }

 @Test
  public void testFailure_clientRejected() throws Exception {
    thrown.expect(NotPendingTransferException.class);
    changeTransferStatus(TransferStatus.CLIENT_REJECTED);
    doFailingTest("domain_transfer_reject.xml");
  }

 @Test
  public void testFailure_clientCancelled() throws Exception {
    thrown.expect(NotPendingTransferException.class);
    changeTransferStatus(TransferStatus.CLIENT_CANCELLED);
    doFailingTest("domain_transfer_reject.xml");
  }

  @Test
  public void testFailure_serverApproved() throws Exception {
    thrown.expect(NotPendingTransferException.class);
    changeTransferStatus(TransferStatus.SERVER_APPROVED);
    doFailingTest("domain_transfer_reject.xml");
  }

  @Test
  public void testFailure_serverCancelled() throws Exception {
    thrown.expect(NotPendingTransferException.class);
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    doFailingTest("domain_transfer_reject.xml");
  }

  @Test
  public void testFailure_gainingClient() throws Exception {
    thrown.expect(ResourceNotOwnedException.class);
    setClientIdForFlow("NewRegistrar");
    doFailingTest("domain_transfer_reject.xml");
  }

  @Test
  public void testFailure_unrelatedClient() throws Exception {
    thrown.expect(ResourceNotOwnedException.class);
    setClientIdForFlow("ClientZ");
    doFailingTest("domain_transfer_reject.xml");
  }

  @Test
  public void testFailure_deletedDomain() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    domain = persistResource(
        domain.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    doFailingTest("domain_transfer_reject.xml");
  }

  @Test
  public void testFailure_nonexistentDomain() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    deleteResource(domain);
    doFailingTest("domain_transfer_reject.xml");
  }

  // NB: No need to test pending delete status since pending transfers will get cancelled upon
  // entering pending delete phase. So it's already handled in that test case.
}
