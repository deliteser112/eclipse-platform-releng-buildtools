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

package com.google.domain.registry.flows.contact;

import static com.google.domain.registry.flows.async.AsyncFlowUtils.ASYNC_FLOW_QUEUE_NAME;
import static com.google.domain.registry.request.Actions.getPathForAction;
import static com.google.domain.registry.testing.ContactResourceSubject.assertAboutContacts;
import static com.google.domain.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.newContactResource;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistDeletedContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.TaskQueueHelper.assertTasksEnqueued;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.flows.FlowRunner.CommitMode;
import com.google.domain.registry.flows.FlowRunner.UserPrivileges;
import com.google.domain.registry.flows.ResourceAsyncDeleteFlow.ResourceToDeleteIsReferencedException;
import com.google.domain.registry.flows.ResourceFlowTestCase;
import com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException;
import com.google.domain.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException;
import com.google.domain.registry.flows.async.DeleteContactResourceAction;
import com.google.domain.registry.flows.async.DeleteEppResourceAction;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.testing.TaskQueueHelper.TaskMatcher;

import com.googlecode.objectify.Key;

import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ContactDeleteFlow}. */
public class ContactDeleteFlowTest
    extends ResourceFlowTestCase<ContactDeleteFlow, ContactResource> {

  @Before
  public void initFlowTest() {
    setEppInput("contact_delete.xml");
  }

  private void doFailingStatusTest(StatusValue statusValue, Class<? extends Exception> exception)
      throws Exception {
    thrown.expect(exception);
    persistResource(
        newContactResource(getUniqueIdFromCommand()).asBuilder()
            .setStatusValues(ImmutableSet.of(statusValue))
            .build());
    runFlow();
  }

  @Test
  public void testDryRun() throws Exception {
    persistActiveContact(getUniqueIdFromCommand());
    dryRunFlowAssertResponse(readFile("contact_delete_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    persistActiveContact(getUniqueIdFromCommand());
    clock.advanceOneMilli();
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile("contact_delete_response.xml"));
    ContactResource deletedContact = reloadResourceByUniqueId();
    assertAboutContacts().that(deletedContact).hasStatusValue(StatusValue.PENDING_DELETE);
    assertTasksEnqueued(ASYNC_FLOW_QUEUE_NAME, new TaskMatcher()
        .url(getPathForAction(DeleteContactResourceAction.class))
        .etaDelta(Duration.standardSeconds(75), Duration.standardSeconds(105)) // expected: 90
        .param(
            DeleteEppResourceAction.PARAM_REQUESTING_CLIENT_ID,
            "TheRegistrar")
        .param(
            DeleteEppResourceAction.PARAM_IS_SUPERUSER,
            Boolean.toString(false))
        .param(
            DeleteEppResourceAction.PARAM_RESOURCE_KEY,
            Key.create(deletedContact).getString()));
    assertAboutContacts().that(deletedContact)
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.CONTACT_PENDING_DELETE);
    assertNoBillingEvents();
  }

  @Test
  public void testFailure_neverExisted() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_existedButWasDeleted() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    persistDeletedContact(getUniqueIdFromCommand(), clock.nowUtc());
    runFlow();
  }

  @Test
  public void testFailure_existedButWasClientDeleteProhibited() throws Exception {
    doFailingStatusTest(
        StatusValue.CLIENT_DELETE_PROHIBITED, ResourceStatusProhibitsOperationException.class);
  }

  @Test
  public void testFailure_existedButWasServerDeleteProhibited() throws Exception {
    doFailingStatusTest(
        StatusValue.SERVER_DELETE_PROHIBITED, ResourceStatusProhibitsOperationException.class);
  }

  @Test
  public void testFailure_existedButWasPendingDelete() throws Exception {
    doFailingStatusTest(
        StatusValue.PENDING_DELETE, ResourceStatusProhibitsOperationException.class);
  }

  @Test
  public void testFailure_unauthorizedClient() throws Exception {
    thrown.expect(ResourceNotOwnedException.class);
    sessionMetadata.setClientId("NewRegistrar");
    persistActiveContact(getUniqueIdFromCommand());
    runFlow();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    sessionMetadata.setSuperuser(true);
    sessionMetadata.setClientId("NewRegistrar");
    persistActiveContact(getUniqueIdFromCommand());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("contact_delete_response.xml"));
    ContactResource deletedContact = reloadResourceByUniqueId();
    assertAboutContacts().that(deletedContact).hasStatusValue(StatusValue.PENDING_DELETE);
    assertTasksEnqueued(ASYNC_FLOW_QUEUE_NAME, new TaskMatcher()
        .url(getPathForAction(DeleteContactResourceAction.class))
        .etaDelta(Duration.standardSeconds(75), Duration.standardSeconds(105)) // expected: 90
        .param(
            DeleteEppResourceAction.PARAM_REQUESTING_CLIENT_ID,
            "NewRegistrar")
        .param(
            DeleteEppResourceAction.PARAM_IS_SUPERUSER,
            Boolean.toString(true))
        .param(
            DeleteEppResourceAction.PARAM_RESOURCE_KEY,
            Key.create(deletedContact).getString()));
    assertAboutContacts().that(deletedContact)
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.CONTACT_PENDING_DELETE);
    assertNoBillingEvents();
  }

  @Test
  public void testFailure_failfastWhenLinkedToDomain() throws Exception {
    createTld("tld");
    persistResource(
        newDomainResource("example.tld", persistActiveContact(getUniqueIdFromCommand())));
    thrown.expect(ResourceToDeleteIsReferencedException.class);
    runFlow();
  }

  @Test
  public void testFailure_failfastWhenLinkedToApplication() throws Exception {
    createTld("tld");
    persistResource(
        newDomainResource("example.tld", persistActiveContact(getUniqueIdFromCommand())));
    thrown.expect(ResourceToDeleteIsReferencedException.class);
    runFlow();
  }
}
