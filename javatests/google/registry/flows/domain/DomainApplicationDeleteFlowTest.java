// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.flows.domain;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.isLinked;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.index.ForeignKeyIndex.loadAndGetKey;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException.UnimplementedExtensionException;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import google.registry.flows.domain.DomainApplicationDeleteFlow.SunriseApplicationCannotBeDeletedInLandrushException;
import google.registry.flows.domain.DomainFlowUtils.ApplicationDomainNameMismatchException;
import google.registry.flows.domain.DomainFlowUtils.BadCommandForRegistryPhaseException;
import google.registry.flows.domain.DomainFlowUtils.LaunchPhaseMismatchException;
import google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.launch.LaunchPhase;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry.TldState;
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
    createTld("extra", TldState.LANDRUSH);
  }

  public void doSuccessfulTest() throws Exception {
    assertTransactionalFlow(true);
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("domain_delete_response.xml"));
    // Check that the domain is fully deleted.
    assertThat(reloadDomainApplication()).isNull();
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
            Key.create(
                loadByForeignKey(ContactResource.class, "sh8013", clock.nowUtc())))
        .setNameservers(ImmutableSet.of(
            Key.create(
                loadByForeignKey(HostResource.class, "ns1.example.net", clock.nowUtc()))))
        .build());
    doSuccessfulTest();
    for (Key<? extends EppResource> key : ImmutableList.of(
        loadAndGetKey(ContactResource.class, "sh8013", clock.nowUtc()),
        loadAndGetKey(HostResource.class, "ns1.example.net", clock.nowUtc()))) {
      assertThat(isLinked(key, clock.nowUtc())).isFalse();
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
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_existedButWasDeleted() throws Exception {
    persistResource(newDomainApplication("example.tld").asBuilder()
        .setRepoId("1-TLD")
        .setDeletionTime(clock.nowUtc().minusSeconds(1))
        .build());
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_unauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    thrown.expect(ResourceNotOwnedException.class);
    runFlow();
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    thrown.expect(NotAuthorizedForTldException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
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
    setEppInput("domain_delete_application_landrush.xml", ImmutableMap.of("DOMAIN", "example.tld"));
    persistResource(newDomainApplication("example.tld")
        .asBuilder()
        .setRepoId("1-TLD")
        .setPhase(LaunchPhase.SUNRISE)
        .build());
    thrown.expect(SunriseApplicationCannotBeDeletedInLandrushException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserSunriseDuringLandrush() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_delete_application_landrush.xml", ImmutableMap.of("DOMAIN", "example.tld"));
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
    setEppInput("domain_delete_application_landrush.xml", ImmutableMap.of("DOMAIN", "example.tld"));
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
    setEppInput("domain_delete_application_landrush.xml", ImmutableMap.of("DOMAIN", "example.tld"));
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    thrown.expect(LaunchPhaseMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongExtension() throws Exception {
    setEppInput("domain_delete_application_wrong_extension.xml");
    persistActiveDomainApplication("example.tld");
    thrown.expect(UnimplementedExtensionException.class);
    runFlow();
  }

  @Test
  public void testFailure_predelegation() throws Exception {
    createTld("tld", TldState.PREDELEGATION);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    thrown.expect(BadCommandForRegistryPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_quietPeriod() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    thrown.expect(BadCommandForRegistryPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_generalAvailability() throws Exception {
    createTld("tld", TldState.GENERAL_AVAILABILITY);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    thrown.expect(BadCommandForRegistryPhaseException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserQuietPeriod() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_delete_response.xml"));
  }

  @Test
  public void testSuccess_superuserPredelegation() throws Exception {
    createTld("tld", TldState.PREDELEGATION);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_delete_response.xml"));
  }

  @Test
  public void testSuccess_superuserGeneralAvailability() throws Exception {
    createTld("tld", TldState.GENERAL_AVAILABILITY);
    persistResource(
        newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_delete_response.xml"));
  }

  @Test
  public void testFailure_applicationIdForDifferentDomain() throws Exception {
    persistResource(
        newDomainApplication("invalid.tld").asBuilder().setRepoId("1-TLD").build());
    thrown.expect(ApplicationDomainNameMismatchException.class);
    runFlow();
  }
}
