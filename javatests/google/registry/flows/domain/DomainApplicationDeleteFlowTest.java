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

package google.registry.flows.domain;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static google.registry.model.EppResourceUtils.isLinked;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.EppResourceUtils.loadDomainApplication;
import static google.registry.model.index.ForeignKeyIndex.loadAndGetKey;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.EppExceptionSubject.assertAboutEppExceptions;
import static google.registry.testing.JUnitBackports.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
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
    runFlowAssertResponse(loadFile("generic_success_response.xml"));
    // Check that the domain is fully deleted.
    assertThat(loadDomainApplication(getUniqueIdFromCommand(), clock.nowUtc())).isEmpty();
    assertNoBillingEvents();
  }

  @Test
  public void testDryRun() throws Exception {
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    dryRunFlowAssertResponse(loadFile("generic_success_response.xml"));
  }

  @Test
  public void testSuccess_contactsAndHostUnlinked() throws Exception {
    // Persist a linked contact and host
    persistActiveContact("sh8013");
    persistResource(newHostResource("ns1.example.net"));
    // Create the DomainApplication to test.
    persistResource(
        newDomainApplication("example.tld")
            .asBuilder()
            .setRepoId("1-TLD")
            .setRegistrant(
                Key.create(loadByForeignKey(ContactResource.class, "sh8013", clock.nowUtc()).get()))
            .setNameservers(
                Key.create(
                    loadByForeignKey(HostResource.class, "ns1.example.net", clock.nowUtc()).get()))
            .build());
    doSuccessfulTest();
    for (Key<? extends EppResource> key :
        ImmutableList.of(
            loadAndGetKey(ContactResource.class, "sh8013", clock.nowUtc()),
            loadAndGetKey(HostResource.class, "ns1.example.net", clock.nowUtc()))) {
      assertThat(isLinked(key, clock.nowUtc())).isFalse();
    }
  }

  @Test
  public void testSuccess_clientDeleteProhibited() throws Exception {
    persistResource(
        newDomainApplication("example.tld")
            .asBuilder()
            .setRepoId("1-TLD")
            .addStatusValue(StatusValue.CLIENT_DELETE_PROHIBITED)
            .build());
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_serverDeleteProhibited() throws Exception {
    persistResource(
        newDomainApplication("example.tld")
            .asBuilder()
            .setRepoId("1-TLD")
            .addStatusValue(StatusValue.SERVER_DELETE_PROHIBITED)
            .build());
    doSuccessfulTest();
  }

  @Test
  public void testFailure_neverExisted() throws Exception {
    ResourceDoesNotExistException thrown =
        assertThrows(ResourceDoesNotExistException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains(String.format("(%s)", getUniqueIdFromCommand()));
  }

  @Test
  public void testFailure_existedButWasDeleted() throws Exception {
    persistResource(
        newDomainApplication("example.tld")
            .asBuilder()
            .setRepoId("1-TLD")
            .setDeletionTime(clock.nowUtc().minusSeconds(1))
            .build());
    ResourceDoesNotExistException thrown =
        assertThrows(ResourceDoesNotExistException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains(String.format("(%s)", getUniqueIdFromCommand()));
  }

  @Test
  public void testFailure_unauthorizedClient() {
    sessionMetadata.setClientId("NewRegistrar");
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    EppException thrown = assertThrows(ResourceNotOwnedException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testFailure_notAuthorizedForTld() {
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    persistResource(
        loadRegistrar("TheRegistrar").asBuilder().setAllowedTlds(ImmutableSet.of()).build());
    EppException thrown = assertThrows(NotAuthorizedForTldException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_superuserNotAuthorizedForTld() throws Exception {
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    persistResource(
        loadRegistrar("TheRegistrar").asBuilder().setAllowedTlds(ImmutableSet.of()).build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testFailure_sunriseDuringLandrush() {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_delete_application_landrush.xml", ImmutableMap.of("DOMAIN", "example.tld"));
    persistResource(
        newDomainApplication("example.tld")
            .asBuilder()
            .setRepoId("1-TLD")
            .setPhase(LaunchPhase.SUNRISE)
            .build());
    EppException thrown =
        assertThrows(SunriseApplicationCannotBeDeletedInLandrushException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_superuserSunriseDuringLandrush() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_delete_application_landrush.xml", ImmutableMap.of("DOMAIN", "example.tld"));
    persistResource(
        newDomainApplication("example.tld")
            .asBuilder()
            .setRepoId("1-TLD")
            .setPhase(LaunchPhase.SUNRISE)
            .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testSuccess_sunrushDuringLandrush() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_delete_application_landrush.xml", ImmutableMap.of("DOMAIN", "example.tld"));
    persistResource(
        newDomainApplication("example.tld")
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
    persistResource(
        newDomainApplication("example.tld")
            .asBuilder()
            .setRepoId("1-TLD")
            .setPhase(LaunchPhase.SUNRISE)
            .build());
    doSuccessfulTest();
  }

  @Test
  public void testFailure_mismatchedPhase() {
    setEppInput("domain_delete_application_landrush.xml", ImmutableMap.of("DOMAIN", "example.tld"));
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    EppException thrown = assertThrows(LaunchPhaseMismatchException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_wrongExtension() {
    setEppInput("domain_delete_application_wrong_extension.xml");
    persistActiveDomainApplication("example.tld");
    EppException thrown = assertThrows(UnimplementedExtensionException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_predelegation() {
    createTld("tld", TldState.PREDELEGATION);
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    EppException thrown = assertThrows(BadCommandForRegistryPhaseException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_quietPeriod() {
    createTld("tld", TldState.QUIET_PERIOD);
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    EppException thrown = assertThrows(BadCommandForRegistryPhaseException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_generalAvailability() {
    createTld("tld", TldState.GENERAL_AVAILABILITY);
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    EppException thrown = assertThrows(BadCommandForRegistryPhaseException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_startDateSunrise() {
    createTld("tld", TldState.START_DATE_SUNRISE);
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    EppException thrown = assertThrows(BadCommandForRegistryPhaseException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_superuserQuietPeriod() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testSuccess_superuserPredelegation() throws Exception {
    createTld("tld", TldState.PREDELEGATION);
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testSuccess_superuserGeneralAvailability() throws Exception {
    createTld("tld", TldState.GENERAL_AVAILABILITY);
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testSuccess_superuserStartDateSunrise() throws Exception {
    createTld("tld", TldState.START_DATE_SUNRISE);
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testFailure_applicationIdForDifferentDomain() {
    persistResource(newDomainApplication("invalid.tld").asBuilder().setRepoId("1-TLD").build());
    EppException thrown = assertThrows(ApplicationDomainNameMismatchException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    persistResource(newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD").build());
    clock.advanceOneMilli();
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-dom-delete");
    assertTldsFieldLogged("tld");
  }
}
