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
import static com.google.domain.registry.model.EppResourceUtils.loadByUniqueId;
import static com.google.domain.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainApplication;
import static com.google.domain.registry.testing.DatastoreHelper.newHostResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.GenericEppResourceSubject.assertAboutEppResources;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.flows.EppException.UnimplementedExtensionException;
import com.google.domain.registry.flows.FlowRunner.CommitMode;
import com.google.domain.registry.flows.FlowRunner.UserPrivileges;
import com.google.domain.registry.flows.ResourceFlow.BadCommandForRegistryPhaseException;
import com.google.domain.registry.flows.ResourceFlowTestCase;
import com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException;
import com.google.domain.registry.flows.domain.DomainApplicationDeleteFlow.SunriseApplicationCannotBeDeletedInLandrushException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.ApplicationDomainNameMismatchException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.LaunchPhaseMismatchException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.domain.launch.LaunchPhase;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registry.Registry.TldState;

import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainApplicationDeleteFlow}. */
public class DomainApplicationDeleteFlowTest
    extends ResourceFlowTestCase<DomainApplicationDeleteFlow, DomainApplication> {

  public DomainApplicationDeleteFlowTest() {
    setEppInput("domain_delete_application.xml");
  }

  @Before
  public void setUp() {
    createTld("tld", TldState.SUNRUSH);
  }

  public void doSuccessfulTest() throws Exception {
    assertTransactionalFlow(true);
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("domain_delete_response.xml"));
    // Check that the domain is fully deleted.
    assertThat(reloadResourceByUniqueId()).isNull();
    assertNoBillingEvents();
  }

  @Test
  public void testDryRun() throws Exception {
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    dryRunFlowAssertResponse(readFile("domain_delete_response.xml"));
  }

  @Test
  public void testSuccess_contactsAndHostUnlinked() throws Exception {
    // Persist a linked contact and host
    persistActiveContact("sh8013");
    persistResource(newHostResource("ns1.example.net"));
    // Create the DomainApplication to test.
    persistResource(newDomainApplication("example.tld").asBuilder()
        .setRepoId("1-TLD")
        .setRegistrant(
            ReferenceUnion.create(
                loadByUniqueId(ContactResource.class, "sh8013", clock.nowUtc())))
        .setNameservers(ImmutableSet.of(
            ReferenceUnion.create(
                loadByUniqueId(HostResource.class, "ns1.example.net", clock.nowUtc()))))
        .build());
    doSuccessfulTest();
    for (EppResource resource : new EppResource[]{
        loadByUniqueId(ContactResource.class, "sh8013", clock.nowUtc()),
        loadByUniqueId(HostResource.class, "ns1.example.net", clock.nowUtc()) }) {
      assertAboutEppResources().that(resource).doesNotHaveStatusValue(StatusValue.LINKED);
    }
  }

  @Test
  public void testSuccess_clientDeleteProhibited() throws Exception {
    persistResource(newDomainApplication("example.tld").asBuilder()
        .setRepoId("1-TLD")
        .addStatusValue(StatusValue.CLIENT_DELETE_PROHIBITED)
        .build());
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_serverDeleteProhibited() throws Exception {
    persistResource(newDomainApplication("example.tld").asBuilder()
        .setRepoId("1-TLD")
        .addStatusValue(StatusValue.SERVER_DELETE_PROHIBITED)
        .build());
    doSuccessfulTest();
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
    persistResource(newDomainApplication("example.tld").asBuilder()
        .setRepoId("1-TLD")
        .setDeletionTime(clock.nowUtc().minusSeconds(1))
        .build());
    runFlow();
  }

  @Test
  public void testFailure_unauthorizedClient() throws Exception {
    thrown.expect(ResourceNotOwnedException.class);
    sessionMetadata.setClientId("NewRegistrar");
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    runFlow();
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    thrown.expect(NotAuthorizedForTldException.class);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    runFlow();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    sessionMetadata.setSuperuser(true);
    sessionMetadata.setClientId("NewRegistrar");
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_delete_response.xml"));
  }

  @Test
  public void testFailure_sunriseDuringLandrush() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_delete_application_landrush.xml");
    thrown.expect(SunriseApplicationCannotBeDeletedInLandrushException.class);
    persistResource(newDomainApplication("example.tld")
        .asBuilder()
        .setRepoId("1-TLD")
        .setPhase(LaunchPhase.SUNRISE)
        .build());
    runFlow();
  }

  @Test
  public void testSuccess_superuserSunriseDuringLandrush() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_delete_application_landrush.xml");
    persistResource(newDomainApplication("example.tld")
        .asBuilder()
        .setRepoId("1-TLD")
        .setPhase(LaunchPhase.SUNRISE)
        .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_delete_response.xml"));
  }

  @Test
  public void testSuccess_sunrushDuringLandrush() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_delete_application_landrush.xml");
    persistResource(newDomainApplication("example.tld")
        .asBuilder()
        .setRepoId("1-TLD")
        .setPhase(LaunchPhase.SUNRUSH)
        .build());
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_sunriseDuringSunrush() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_delete_application_sunrush.xml");
    persistResource(newDomainApplication("example.tld")
        .asBuilder()
        .setRepoId("1-TLD")
        .setPhase(LaunchPhase.SUNRISE)
        .build());
    doSuccessfulTest();
  }

  @Test
  public void testFailure_mismatchedPhase() throws Exception {
    thrown.expect(LaunchPhaseMismatchException.class);
    setEppInput("domain_delete_application_landrush.xml");
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    runFlow();
  }

  @Test
  public void testFailure_wrongExtension() throws Exception {
    thrown.expect(UnimplementedExtensionException.class);
    setEppInput("domain_delete_application_wrong_extension.xml");
    persistActiveDomainApplication("example.tld");
    runFlow();
  }

  @Test
  public void testFailure_predelegation() throws Exception {
    thrown.expect(BadCommandForRegistryPhaseException.class);
    createTld("tld", TldState.PREDELEGATION);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    runFlow();
  }

  @Test
  public void testFailure_quietPeriod() throws Exception {
    thrown.expect(BadCommandForRegistryPhaseException.class);
    createTld("tld", TldState.QUIET_PERIOD);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    runFlow();
  }

  @Test
  public void testFailure_generalAvailability() throws Exception {
    thrown.expect(BadCommandForRegistryPhaseException.class);
    createTld("tld", TldState.GENERAL_AVAILABILITY);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    runFlow();
  }

  @Test
  public void testFailure_applicationIdForDifferentDomain() throws Exception {
    thrown.expect(ApplicationDomainNameMismatchException.class);
    persistResource(
        newDomainApplication("invalid.tld").asBuilder().setRepoId("1-TLD").build());
    runFlow();
  }
}
