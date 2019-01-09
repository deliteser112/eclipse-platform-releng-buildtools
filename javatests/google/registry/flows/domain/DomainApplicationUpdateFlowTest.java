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

import static com.google.common.collect.Sets.union;
import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.generateNewDomainRoid;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainApplicationSubject.assertAboutApplications;
import static google.registry.testing.EppExceptionSubject.assertAboutEppExceptions;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.UnimplementedExtensionException;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.ResourceFlowUtils.AddRemoveSameValueException;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import google.registry.flows.ResourceFlowUtils.StatusNotClientSettableException;
import google.registry.flows.domain.DomainApplicationUpdateFlow.ApplicationStatusProhibitsUpdateException;
import google.registry.flows.domain.DomainFlowUtils.ApplicationDomainNameMismatchException;
import google.registry.flows.domain.DomainFlowUtils.DuplicateContactForRoleException;
import google.registry.flows.domain.DomainFlowUtils.EmptySecDnsUpdateException;
import google.registry.flows.domain.DomainFlowUtils.FeesMismatchException;
import google.registry.flows.domain.DomainFlowUtils.LinkedResourcesDoNotExistException;
import google.registry.flows.domain.DomainFlowUtils.MaxSigLifeChangeNotSupportedException;
import google.registry.flows.domain.DomainFlowUtils.MissingAdminContactException;
import google.registry.flows.domain.DomainFlowUtils.MissingContactTypeException;
import google.registry.flows.domain.DomainFlowUtils.MissingTechnicalContactException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotAllowedForDomainException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotAllowedForTldException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotSpecifiedForNameserverRestrictedDomainException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotSpecifiedForTldWithNameserverWhitelistException;
import google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import google.registry.flows.domain.DomainFlowUtils.RegistrantNotAllowedException;
import google.registry.flows.domain.DomainFlowUtils.SecDnsAllUsageException;
import google.registry.flows.domain.DomainFlowUtils.TooManyDsRecordsException;
import google.registry.flows.domain.DomainFlowUtils.TooManyNameserversException;
import google.registry.flows.domain.DomainFlowUtils.UrgentAttributeNotSupportedException;
import google.registry.flows.exceptions.ResourceHasClientUpdateProhibitedException;
import google.registry.flows.exceptions.ResourceStatusProhibitsOperationException;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DesignatedContact.Type;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainApplication.Builder;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.reporting.HistoryEntry;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainApplicationUpdateFlow}. */
public class DomainApplicationUpdateFlowTest
    extends ResourceFlowTestCase<DomainApplicationUpdateFlow, DomainApplication> {

  private static final DelegationSignerData SOME_DSDATA =
      DelegationSignerData.create(1, 2, 3, new byte[] {0, 1, 2});

  ContactResource sh8013Contact;
  ContactResource mak21Contact;
  ContactResource unusedContact;

  public DomainApplicationUpdateFlowTest() {
    // Note that "domain_update_sunrise.xml" tests adding and removing the same contact type.
    setEppInput("domain_update_sunrise.xml");
  }

  @Before
  public void setUp() {
    createTld("tld", TldState.SUNRUSH);
  }

  private void persistReferencedEntities() {
    // Grab the 1 id for use with the DomainApplication.
    generateNewDomainRoid("tld");
    for (int i = 1; i <= 14; ++i) {
      persistActiveHost(String.format("ns%d.example.tld", i));
    }
    sh8013Contact = persistActiveContact("sh8013");
    mak21Contact = persistActiveContact("mak21");
    unusedContact = persistActiveContact("unused");
  }

  private DomainApplication persistApplication() {
    HostResource host =
        loadByForeignKey(HostResource.class, "ns1.example.tld", clock.nowUtc()).get();
    return persistResource(
        newApplicationBuilder()
            .setContacts(
                ImmutableSet.of(
                    DesignatedContact.create(Type.TECH, Key.create(sh8013Contact)),
                    DesignatedContact.create(Type.ADMIN, Key.create(unusedContact))))
            .setNameservers(ImmutableSet.of(Key.create(host)))
            .build());
  }

  private Builder newApplicationBuilder() {
    return newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD");
  }

  private DomainApplication persistNewApplication() {
    return persistResource(newApplicationBuilder().build());
  }

  private void doSuccessfulTest() throws Exception {
    assertTransactionalFlow(true);
    clock.advanceOneMilli();
    runFlowAssertResponse(loadFile("generic_success_response.xml"));
    // Check that the application was updated. These values came from the xml.
    DomainApplication application = reloadDomainApplication();
    assertAboutApplications()
        .that(application)
        .hasStatusValue(StatusValue.CLIENT_HOLD)
        .and()
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE);
    assertThat(application.getAuthInfo().getPw().getValue()).isEqualTo("2BARfoo");
    // Check that the hosts and contacts have correct linked status
    assertNoBillingEvents();
  }

  @Test
  public void testDryRun() throws Exception {
    persistReferencedEntities();
    persistApplication();
    dryRunFlowAssertResponse(loadFile("generic_success_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    persistReferencedEntities();
    persistApplication();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_maxNumberOfNameservers() throws Exception {
    persistReferencedEntities();
    persistApplication();
    modifyApplicationToHave13Nameservers();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_removeContact() throws Exception {
    setEppInput("domain_update_sunrise_remove_contact.xml");
    persistReferencedEntities();
    persistApplication();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_registrantMovedToTechContact() throws Exception {
    setEppInput("domain_update_sunrise_registrant_to_tech.xml");
    persistReferencedEntities();
    ContactResource sh8013 =
        loadByForeignKey(ContactResource.class, "sh8013", clock.nowUtc()).get();
    persistResource(newApplicationBuilder().setRegistrant(Key.create(sh8013)).build());
    clock.advanceOneMilli();
    runFlowAssertResponse(loadFile("generic_success_response.xml"));
  }

  @Test
  public void testSuccess_multipleReferencesToSameContactRemoved() throws Exception {
    setEppInput("domain_update_sunrise_remove_multiple_contacts.xml");
    persistReferencedEntities();
    ContactResource sh8013 =
        loadByForeignKey(ContactResource.class, "sh8013", clock.nowUtc()).get();
    Key<ContactResource> sh8013Key = Key.create(sh8013);
    persistResource(
        newApplicationBuilder()
            .setRegistrant(sh8013Key)
            .setContacts(
                ImmutableSet.of(
                    DesignatedContact.create(Type.ADMIN, sh8013Key),
                    DesignatedContact.create(Type.BILLING, sh8013Key),
                    DesignatedContact.create(Type.TECH, sh8013Key)))
            .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(loadFile("generic_success_response.xml"));
  }

  @Test
  public void testSuccess_removeClientUpdateProhibited() throws Exception {
    persistReferencedEntities();
    persistResource(
        persistApplication()
            .asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED))
            .build());
    clock.advanceOneMilli();
    runFlow();
    assertAboutApplications()
        .that(reloadDomainApplication())
        .doesNotHaveStatusValue(StatusValue.CLIENT_UPDATE_PROHIBITED);
  }

  private void doSecDnsSuccessfulTest(
      String xmlFilename,
      ImmutableSet<DelegationSignerData> originalDsData,
      ImmutableSet<DelegationSignerData> expectedDsData)
      throws Exception {
    setEppInput(xmlFilename);
    persistResource(newApplicationBuilder().setDsData(originalDsData).build());
    assertTransactionalFlow(true);
    clock.advanceOneMilli();
    runFlowAssertResponse(loadFile("generic_success_response.xml"));
    assertAboutApplications()
        .that(reloadDomainApplication())
        .hasExactlyDsData(expectedDsData)
        .and()
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE);
  }

  @Test
  public void testSuccess_secDnsAdd() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add.xml",
        null,
        ImmutableSet.of(
            DelegationSignerData.create(12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))));
  }

  @Test
  public void testSuccess_secDnsAddPreservesExisting() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add.xml",
        ImmutableSet.of(SOME_DSDATA),
        ImmutableSet.of(
            SOME_DSDATA,
            DelegationSignerData.create(12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))));
  }

  @Test
  public void testSuccess_secDnsAddToMaxRecords() throws Exception {
    ImmutableSet.Builder<DelegationSignerData> builder = new ImmutableSet.Builder<>();
    for (int i = 0; i < 7; ++i) {
      builder.add(DelegationSignerData.create(i, 2, 3, new byte[] {0, 1, 2}));
    }
    ImmutableSet<DelegationSignerData> commonDsData = builder.build();

    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add.xml",
        commonDsData,
        ImmutableSet.copyOf(
            union(
                commonDsData,
                ImmutableSet.of(
                    DelegationSignerData.create(
                        12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))))));
  }

  @Test
  public void testSuccess_secDnsRemove() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_rem.xml",
        ImmutableSet.of(
            SOME_DSDATA,
            DelegationSignerData.create(12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))),
        ImmutableSet.of(SOME_DSDATA));
  }

  @Test
  public void testSuccess_secDnsRemoveAll() throws Exception {
    // As an aside, this test also validates that it's ok to set the 'urgent' attribute to false.
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_rem_all.xml",
        ImmutableSet.of(
            SOME_DSDATA,
            DelegationSignerData.create(12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))),
        ImmutableSet.of());
  }

  @Test
  public void testSuccess_secDnsAddRemove() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add_rem.xml",
        ImmutableSet.of(
            SOME_DSDATA,
            DelegationSignerData.create(12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))),
        ImmutableSet.of(
            SOME_DSDATA,
            DelegationSignerData.create(12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))));
  }

  @Test
  public void testSuccess_secDnsAddRemoveToMaxRecords() throws Exception {
    ImmutableSet.Builder<DelegationSignerData> builder = new ImmutableSet.Builder<>();
    for (int i = 0; i < 7; ++i) {
      builder.add(DelegationSignerData.create(i, 2, 3, new byte[] {0, 1, 2}));
    }
    ImmutableSet<DelegationSignerData> commonDsData = builder.build();

    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add_rem.xml",
        ImmutableSet.copyOf(
            union(
                commonDsData,
                ImmutableSet.of(
                    DelegationSignerData.create(
                        12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))))),
        ImmutableSet.copyOf(
            union(
                commonDsData,
                ImmutableSet.of(
                    DelegationSignerData.create(
                        12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))))));
  }

  @Test
  public void testSuccess_secDnsAddRemoveSame() throws Exception {
    // Adding and removing the same dsData is a no-op because removes are processed first.
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add_rem_same.xml",
        ImmutableSet.of(
            SOME_DSDATA,
            DelegationSignerData.create(12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))),
        ImmutableSet.of(
            SOME_DSDATA,
            DelegationSignerData.create(12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))));
  }

  @Test
  public void testSuccess_secDnsRemoveAlreadyNotThere() throws Exception {
    // Removing a dsData that isn't there is a no-op.
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_rem.xml",
        ImmutableSet.of(SOME_DSDATA),
        ImmutableSet.of(SOME_DSDATA));
  }

  private void doSecDnsFailingTest(
      Class<? extends EppException> expectedException, String xmlFilename) {
    setEppInput(xmlFilename);
    persistReferencedEntities();
    persistNewApplication();
    EppException thrown = assertThrows(expectedException, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_secDnsAllCannotBeFalse() {
    doSecDnsFailingTest(
        SecDnsAllUsageException.class, "domain_update_sunrise_dsdata_rem_all_false.xml");
  }

  @Test
  public void testFailure_secDnsEmptyNotAllowed() {
    doSecDnsFailingTest(EmptySecDnsUpdateException.class, "domain_update_sunrise_dsdata_empty.xml");
  }

  @Test
  public void testFailure_secDnsUrgentNotSupported() {
    doSecDnsFailingTest(
        UrgentAttributeNotSupportedException.class, "domain_update_sunrise_dsdata_urgent.xml");
  }

  @Test
  public void testFailure_secDnsChangeNotSupported() {
    doSecDnsFailingTest(
        MaxSigLifeChangeNotSupportedException.class, "domain_update_sunrise_maxsiglife.xml");
  }

  @Test
  public void testFailure_secDnsTooManyDsRecords() {
    ImmutableSet.Builder<DelegationSignerData> builder = new ImmutableSet.Builder<>();
    for (int i = 0; i < 8; ++i) {
      builder.add(DelegationSignerData.create(i, 2, 3, new byte[] {0, 1, 2}));
    }

    setEppInput("domain_update_sunrise_dsdata_add.xml");
    persistResource(newApplicationBuilder().setDsData(builder.build()).build());
    EppException thrown = assertThrows(TooManyDsRecordsException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  private void modifyApplicationToHave13Nameservers() throws Exception {
    ImmutableSet.Builder<Key<HostResource>> nameservers = new ImmutableSet.Builder<>();
    for (int i = 1; i < 15; i++) {
      if (i != 2) { // Skip 2 since that's the one that the tests will add.
        nameservers.add(
            Key.create(
                loadByForeignKey(
                        HostResource.class, String.format("ns%d.example.tld", i), clock.nowUtc())
                    .get()));
      }
    }
    persistResource(
        reloadDomainApplication().asBuilder().setNameservers(nameservers.build()).build());
  }

  @Test
  public void testFailure_tooManyNameservers() throws Exception {
    persistReferencedEntities();
    persistApplication();
    // Modify application to have 13 nameservers. We will then remove one and add one in the test.
    modifyApplicationToHave13Nameservers();
    setEppInput("domain_update_sunrise_add_nameserver.xml");
    EppException thrown = assertThrows(TooManyNameserversException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_wrongExtension() {
    setEppInput("domain_update_sunrise_wrong_extension.xml");
    EppException thrown = assertThrows(UnimplementedExtensionException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_applicationDomainNameMismatch() {
    persistReferencedEntities();
    persistResource(newApplicationBuilder().setFullyQualifiedDomainName("something.tld").build());
    EppException thrown = assertThrows(ApplicationDomainNameMismatchException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_neverExisted() throws Exception {
    persistReferencedEntities();
    ResourceDoesNotExistException thrown =
        assertThrows(ResourceDoesNotExistException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains(String.format("(%s)", getUniqueIdFromCommand()));
  }

  @Test
  public void testFailure_existedButWasDeleted() throws Exception {
    persistReferencedEntities();
    persistResource(newApplicationBuilder().setDeletionTime(START_OF_TIME).build());
    ResourceDoesNotExistException thrown =
        assertThrows(ResourceDoesNotExistException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains(String.format("(%s)", getUniqueIdFromCommand()));
  }

  @Test
  public void testFailure_clientUpdateProhibited() {
    setEppInput("domain_update_sunrise_authinfo.xml");
    persistReferencedEntities();
    persistResource(
        newApplicationBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED))
            .build());
    EppException thrown =
        assertThrows(ResourceHasClientUpdateProhibitedException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_serverUpdateProhibited() {
    persistReferencedEntities();
    persistResource(
        newApplicationBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.SERVER_UPDATE_PROHIBITED))
            .build());
    ResourceStatusProhibitsOperationException thrown =
        assertThrows(ResourceStatusProhibitsOperationException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains("serverUpdateProhibited");
  }

  private void doIllegalApplicationStatusTest(ApplicationStatus status) {
    persistReferencedEntities();
    persistResource(newApplicationBuilder().setApplicationStatus(status).build());
    EppException thrown =
        assertThrows(ApplicationStatusProhibitsUpdateException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_allocatedApplicationStatus() {
    doIllegalApplicationStatusTest(ApplicationStatus.ALLOCATED);
  }

  @Test
  public void testFailure_invalidApplicationStatus() {
    doIllegalApplicationStatusTest(ApplicationStatus.INVALID);
  }

  @Test
  public void testFailure_rejectedApplicationStatus() {
    doIllegalApplicationStatusTest(ApplicationStatus.REJECTED);
  }

  @Test
  public void testFailure_missingHost() {
    persistActiveHost("ns1.example.tld");
    persistActiveContact("sh8013");
    persistActiveContact("mak21");
    persistNewApplication();
    LinkedResourcesDoNotExistException thrown =
        assertThrows(LinkedResourcesDoNotExistException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains("(ns2.example.tld)");
  }

  @Test
  public void testFailure_missingContact() {
    persistActiveHost("ns1.example.tld");
    persistActiveHost("ns2.example.tld");
    persistActiveContact("mak21");
    persistNewApplication();
    LinkedResourcesDoNotExistException thrown =
        assertThrows(LinkedResourcesDoNotExistException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains("(sh8013)");
  }

  @Test
  public void testFailure_addingDuplicateContact() throws Exception {
    persistReferencedEntities();
    persistActiveContact("foo");
    persistNewApplication();
    // Add a tech contact to the persisted entity, which should cause the flow to fail when it tries
    // to add "mak21" as a second tech contact.
    persistResource(
        reloadDomainApplication()
            .asBuilder()
            .setContacts(
                ImmutableSet.of(
                    DesignatedContact.create(
                        Type.TECH,
                        Key.create(
                            loadByForeignKey(ContactResource.class, "foo", clock.nowUtc()).get()))))
            .build());
    EppException thrown = assertThrows(DuplicateContactForRoleException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_clientProhibitedStatusValue() {
    setEppInput("domain_update_sunrise_prohibited_status.xml");
    persistReferencedEntities();
    persistNewApplication();
    EppException thrown = assertThrows(StatusNotClientSettableException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_superuserProhibitedStatusValue() throws Exception {
    setEppInput("domain_update_sunrise_prohibited_status.xml");
    persistReferencedEntities();
    persistNewApplication();
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testFailure_duplicateContactInCommand() {
    setEppInput("domain_update_sunrise_duplicate_contact.xml");
    persistReferencedEntities();
    persistNewApplication();
    EppException thrown = assertThrows(DuplicateContactForRoleException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_missingContactType() {
    setEppInput("domain_update_sunrise_missing_contact_type.xml");
    persistReferencedEntities();
    persistNewApplication();
    // We need to test for missing type, but not for invalid - the schema enforces that for us.
    EppException thrown = assertThrows(MissingContactTypeException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_unauthorizedClient() {
    sessionMetadata.setClientId("NewRegistrar");
    persistReferencedEntities();
    persistApplication();
    EppException thrown = assertThrows(ResourceNotOwnedException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistReferencedEntities();
    persistApplication();
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testFailure_notAuthorizedForTld() {
    persistResource(
        loadRegistrar("TheRegistrar").asBuilder().setAllowedTlds(ImmutableSet.of()).build());
    persistReferencedEntities();
    persistApplication();
    EppException thrown = assertThrows(NotAuthorizedForTldException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_superuserNotAuthorizedForTld() throws Exception {
    persistResource(
        loadRegistrar("TheRegistrar").asBuilder().setAllowedTlds(ImmutableSet.of()).build());
    persistReferencedEntities();
    persistApplication();
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, loadFile("generic_success_response.xml"));
  }

  @Test
  public void testFailure_sameNameserverAddedAndRemoved() {
    setEppInput("domain_update_sunrise_add_remove_same_host.xml");
    persistReferencedEntities();
    persistResource(
        newApplicationBuilder()
            .setNameservers(
                ImmutableSet.of(
                    Key.create(
                        loadByForeignKey(HostResource.class, "ns1.example.tld", clock.nowUtc())
                            .get())))
            .build());
    EppException thrown = assertThrows(AddRemoveSameValueException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_sameContactAddedAndRemoved() {
    setEppInput("domain_update_sunrise_add_remove_same_contact.xml");
    persistReferencedEntities();
    persistResource(
        newApplicationBuilder()
            .setContacts(
                ImmutableSet.of(
                    DesignatedContact.create(
                        Type.TECH,
                        Key.create(
                            loadByForeignKey(ContactResource.class, "sh8013", clock.nowUtc())
                                .get()))))
            .build());
    EppException thrown = assertThrows(AddRemoveSameValueException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_removeAdmin() {
    setEppInput("domain_update_sunrise_remove_admin.xml");
    persistReferencedEntities();
    persistResource(
        newApplicationBuilder()
            .setContacts(
                ImmutableSet.of(
                    DesignatedContact.create(Type.ADMIN, Key.create(sh8013Contact)),
                    DesignatedContact.create(Type.TECH, Key.create(sh8013Contact))))
            .build());
    EppException thrown = assertThrows(MissingAdminContactException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_removeTech() {
    setEppInput("domain_update_sunrise_remove_tech.xml");
    persistReferencedEntities();
    persistResource(
        newApplicationBuilder()
            .setContacts(
                ImmutableSet.of(
                    DesignatedContact.create(Type.ADMIN, Key.create(sh8013Contact)),
                    DesignatedContact.create(Type.TECH, Key.create(sh8013Contact))))
            .build());
    EppException thrown = assertThrows(MissingTechnicalContactException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_newRegistrantNotWhitelisted() {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedRegistrantContactIds(ImmutableSet.of("contact1234"))
            .build());
    clock.advanceOneMilli();
    EppException thrown = assertThrows(RegistrantNotAllowedException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testFailure_newNameserverNotWhitelisted() {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(ImmutableSet.of("ns1.example.foo"))
            .build());
    clock.advanceOneMilli();
    EppException thrown = assertThrows(NameserversNotAllowedForTldException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_nameserverAndRegistrantWhitelisted() throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedRegistrantContactIds(ImmutableSet.of("sh8013"))
            .setAllowedFullyQualifiedHostNames(ImmutableSet.of("ns2.example.tld"))
            .build());
    persistReferencedEntities();
    persistApplication();
    doSuccessfulTest();
  }

  @Test
  public void testFailure_tldWithNameserverWhitelist_removeLastNameserver() {
    setEppInput("domain_update_sunrise_remove_nameserver.xml");
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    EppException thrown =
        assertThrows(
            NameserversNotSpecifiedForTldWithNameserverWhitelistException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_tldWithNameserverWhitelist_removeNameserver() throws Exception {
    setEppInput("domain_update_sunrise_remove_nameserver.xml");
    persistReferencedEntities();
    persistApplication();
    persistResource(
        reloadDomainApplication()
            .asBuilder()
            .addNameserver(
                Key.create(
                    loadByForeignKey(HostResource.class, "ns2.example.tld", clock.nowUtc()).get()))
            .build());
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_domainNameserverRestricted_addedNameserverAllowed() throws Exception {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testFailure_domainNameserverRestricted_addedNameserverDisallowed() {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns3.example.tld"))
            .build());
    clock.advanceOneMilli();
    NameserversNotAllowedForDomainException thrown =
        assertThrows(NameserversNotAllowedForDomainException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains("ns2.example.tld");
  }

  @Test
  public void testFailure_domainNameserverRestricted_removeLastNameserver() {
    setEppInput("domain_update_sunrise_remove_nameserver.xml");
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    EppException thrown =
        assertThrows(
            NameserversNotSpecifiedForNameserverRestrictedDomainException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testSuccess_domainNameserverRestricted_removeNameservers() throws Exception {
    setEppInput("domain_update_sunrise_remove_nameserver.xml");
    persistReferencedEntities();
    persistApplication();
    persistResource(
        reloadDomainApplication()
            .asBuilder()
            .addNameserver(
                Key.create(
                    loadByForeignKey(HostResource.class, "ns2.example.tld", clock.nowUtc()).get()))
            .build());
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_addedNameserversAllowedInTldAndDomainNameserversWhitelists()
      throws Exception {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns2.example.tld"))
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testFailure_addedNameserversAllowedInTld_disallowedInDomainNameserversWhitelists() {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns2.example.tld"))
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns3.example.tld"))
            .build());
    clock.advanceOneMilli();
    NameserversNotAllowedForDomainException thrown =
        assertThrows(NameserversNotAllowedForDomainException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains("ns2.example.tld");
  }

  @Test
  public void testFailure_addedNameserversDisallowedInTld_AllowedInDomainNameserversWhitelists() {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns3.example.tld"))
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    NameserversNotAllowedForTldException thrown =
        assertThrows(NameserversNotAllowedForTldException.class, this::runFlow);
    assertThat(thrown).hasMessageThat().contains("ns2.example.tld");
  }

  @Test
  public void testFailure_customPricingLogic_feeMismatch() {
    persistReferencedEntities();
    persistResource(
        newDomainApplication("non-free-update.tld").asBuilder().setRepoId("1-ROID").build());
    setEppInput(
        "domain_update_sunrise_fee.xml",
        ImmutableMap.of("DOMAIN", "non-free-update.tld", "AMOUNT", "12.00"));
    clock.advanceOneMilli();
    EppException thrown = assertThrows(FeesMismatchException.class, this::runFlow);
    assertAboutEppExceptions().that(thrown).marshalsToXml();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    persistReferencedEntities();
    persistApplication();
    clock.advanceOneMilli();
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-dom-update");
    assertTldsFieldLogged("tld");
  }
}
