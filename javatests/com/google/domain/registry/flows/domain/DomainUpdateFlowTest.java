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

import static com.google.common.collect.Sets.union;
import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.EppResourceUtils.loadByUniqueId;
import static com.google.domain.registry.testing.DatastoreHelper.assertBillingEvents;
import static com.google.domain.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.getOnlyHistoryEntryOfType;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveSubordinateHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistDeletedDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.DomainResourceSubject.assertAboutDomains;
import static com.google.domain.registry.testing.HistoryEntrySubject.assertAboutHistoryEntries;
import static com.google.domain.registry.testing.TaskQueueHelper.assertDnsTasksEnqueued;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.flows.EppException.UnimplementedExtensionException;
import com.google.domain.registry.flows.FlowRunner.CommitMode;
import com.google.domain.registry.flows.FlowRunner.UserPrivileges;
import com.google.domain.registry.flows.ResourceCreateOrMutateFlow.OnlyToolCanPassMetadataException;
import com.google.domain.registry.flows.ResourceFlowTestCase;
import com.google.domain.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import com.google.domain.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException;
import com.google.domain.registry.flows.ResourceUpdateFlow.AddRemoveSameValueEppException;
import com.google.domain.registry.flows.ResourceUpdateFlow.ResourceHasClientUpdateProhibitedException;
import com.google.domain.registry.flows.ResourceUpdateFlow.StatusNotClientSettableException;
import com.google.domain.registry.flows.SessionMetadata.SessionSource;
import com.google.domain.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException;
import com.google.domain.registry.flows.domain.BaseDomainUpdateFlow.EmptySecDnsUpdateException;
import com.google.domain.registry.flows.domain.BaseDomainUpdateFlow.MaxSigLifeChangeNotSupportedException;
import com.google.domain.registry.flows.domain.BaseDomainUpdateFlow.SecDnsAllUsageException;
import com.google.domain.registry.flows.domain.BaseDomainUpdateFlow.UrgentAttributeNotSupportedException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.DuplicateContactForRoleException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.LinkedResourceDoesNotExistException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.LinkedResourceInPendingDeleteProhibitsOperationException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.MissingAdminContactException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.MissingContactTypeException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.MissingTechnicalContactException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.TooManyDsRecordsException;
import com.google.domain.registry.flows.domain.DomainFlowUtils.TooManyNameserversException;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.billing.BillingEvent.Reason;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DesignatedContact;
import com.google.domain.registry.model.domain.DesignatedContact.Type;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.GracePeriod;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.domain.rgp.GracePeriodStatus;
import com.google.domain.registry.model.domain.secdns.DelegationSignerData;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.reporting.HistoryEntry;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;

import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainUpdateFlow}. */
public class DomainUpdateFlowTest extends ResourceFlowTestCase<DomainUpdateFlow, DomainResource> {

  private static final DelegationSignerData SOME_DSDATA =
      DelegationSignerData.create(1, 2, 3, new byte[]{0, 1, 2});

  ContactResource sh8013Contact;
  ContactResource mak21Contact;
  ContactResource unusedContact;
  HistoryEntry historyEntryDomainCreate;

  public DomainUpdateFlowTest() {
    // Note that "domain_update.xml" tests adding and removing the same contact type.
    setEppInput("domain_update.xml");
  }

  @Before
  public void initDomainTest() {
    createTld("tld");
  }

  private void persistReferencedEntities() {
    for (int i = 1; i <= 14; ++i) {
      persistActiveHost(String.format("ns%d.example.foo", i));
    }
    sh8013Contact = persistActiveContact("sh8013");
    mak21Contact = persistActiveContact("mak21");
    unusedContact = persistActiveContact("unused");
  }

  private DomainResource persistDomain() throws Exception {
    HostResource host = loadByUniqueId(HostResource.class, "ns1.example.foo", clock.nowUtc());
    DomainResource domain = persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setContacts(ImmutableSet.of(
                DesignatedContact.create(Type.TECH, ReferenceUnion.create(sh8013Contact)),
                DesignatedContact.create(Type.ADMIN, ReferenceUnion.create(unusedContact))))
            .setNameservers(ImmutableSet.of(ReferenceUnion.create(host)))
            .build());
    historyEntryDomainCreate = persistResource(
        new HistoryEntry.Builder()
            .setType(HistoryEntry.Type.DOMAIN_CREATE)
            .setParent(domain)
            .build());
    clock.advanceOneMilli();
    return domain;
  }

  private void doSuccessfulTest() throws Exception {
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile("domain_update_response.xml"));
    // Check that the domain was updated. These values came from the xml.
    assertAboutDomains().that(reloadResourceByUniqueId())
        .hasStatusValue(StatusValue.CLIENT_HOLD).and()
        .hasAuthInfoPwd("2BARfoo").and()
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.DOMAIN_CREATE,
            HistoryEntry.Type.DOMAIN_UPDATE);
    assertNoBillingEvents();
    assertDnsTasksEnqueued("example.tld");
  }

  @Test
  public void testDryRun() throws Exception {
    persistReferencedEntities();
    persistDomain();
    dryRunFlowAssertResponse(readFile("domain_update_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    persistReferencedEntities();
    persistDomain();
    doSuccessfulTest();
  }

  private void doSunrushAddTest(
      BillingEvent.OneTime sunrushAddBillingEvent,
      UserPrivileges userPrivileges,
      DateTime addExpirationTime) throws Exception {
    // The billing event for the original sunrush add should already exist; check for it now to
    // avoid a confusing assertBillingEvents() later if it doesn't exist.
    assertBillingEvents(sunrushAddBillingEvent);

    runFlowAssertResponse(
        CommitMode.LIVE, userPrivileges, readFile("domain_update_response.xml"));

    // Verify that the domain now has the new nameserver and is in the add grace period.
    DomainResource resource = reloadResourceByUniqueId();
    HistoryEntry historyEntryDomainUpdate =
        getOnlyHistoryEntryOfType(resource, HistoryEntry.Type.DOMAIN_UPDATE);
    assertThat(resource.getNameservers()).containsExactly(
        ReferenceUnion.create(
            loadByUniqueId(HostResource.class, "ns2.example.foo", clock.nowUtc())));
    BillingEvent.OneTime regularAddBillingEvent = new BillingEvent.OneTime.Builder()
        .setReason(Reason.CREATE)
        .setTargetId("example.tld")
        .setClientId("TheRegistrar")
        .setCost(Money.of(USD, 50))
        .setPeriodYears(4)
        .setEventTime(clock.nowUtc())
        .setBillingTime(addExpirationTime)
        .setParent(historyEntryDomainUpdate)
        .build();
    assertBillingEvents(
        sunrushAddBillingEvent,
        // There should be a cancellation for the original sunrush add billing event.
        new BillingEvent.Cancellation.Builder()
            .setReason(Reason.CREATE)
            .setTargetId("example.tld")
            .setClientId("TheRegistrar")
            .setEventTime(clock.nowUtc())
            .setBillingTime(sunrushAddBillingEvent.getBillingTime())
            .setOneTimeEventRef(Ref.create(sunrushAddBillingEvent))
            .setParent(historyEntryDomainUpdate)
            .build(),
        regularAddBillingEvent);
    assertGracePeriods(
        resource.getGracePeriods(),
        ImmutableMap.of(
            GracePeriod.create(GracePeriodStatus.ADD, addExpirationTime, "TheRegistrar", null),
            regularAddBillingEvent));
  }

  private BillingEvent.OneTime getSunrushAddBillingEvent(DateTime billingTime) {
    return new BillingEvent.OneTime.Builder()
        .setReason(Reason.CREATE)
        .setTargetId("example.tld")
        .setClientId("TheRegistrar")
        .setCost(Money.of(USD, 50))
        .setPeriodYears(4)
        .setEventTime(billingTime.minusDays(30))
        .setBillingTime(billingTime)
        .setParent(historyEntryDomainCreate)
        .build();
  }

  @Test
  public void testSuccess_sunrushAddGracePeriod() throws Exception {
    setEppInput("domain_update_add_nameserver.xml");
    persistReferencedEntities();
    persistDomain();
    BillingEvent.OneTime sunrushAddBillingEvent = persistResource(
            getSunrushAddBillingEvent(clock.nowUtc().plusDays(20)));
    // Modify domain so it has no nameservers and is in the sunrush add grace period.
    persistResource(
        reloadResourceByUniqueId().asBuilder()
            .setNameservers(null)
            .addGracePeriod(GracePeriod.forBillingEvent(
                GracePeriodStatus.SUNRUSH_ADD, sunrushAddBillingEvent))
            .build());
    clock.advanceOneMilli();
    doSunrushAddTest(
        sunrushAddBillingEvent,
        UserPrivileges.NORMAL,
        clock.nowUtc().plus(Registry.get("tld").getAddGracePeriodLength()));
  }

  @Test
  public void testSuccess_truncatedSunrushAddGracePeriod() throws Exception {
    setEppInput("domain_update_add_nameserver.xml");
    persistReferencedEntities();
    persistDomain();
    DateTime billingTime = clock.nowUtc().plusDays(2);
    BillingEvent.OneTime sunrushAddBillingEvent =
        persistResource(getSunrushAddBillingEvent(billingTime));
    // Modify domain so it has no nameservers and is in the sunrush add grace period, but make sure
    // that grace period only has a couple days left so that the resultant add grace period will get
    // truncated.
    persistResource(
        reloadResourceByUniqueId().asBuilder()
            .setNameservers(null)
            .addGracePeriod(GracePeriod.forBillingEvent(
                GracePeriodStatus.SUNRUSH_ADD, sunrushAddBillingEvent))
            .build());
    clock.advanceOneMilli();
    doSunrushAddTest(sunrushAddBillingEvent, UserPrivileges.NORMAL, billingTime);
  }

  @Test
  public void testSuccess_sunrushAddGracePeriodRemoveServerHold() throws Exception {
    setEppInput("domain_update_remove_server_hold.xml");
    persistReferencedEntities();
    persistDomain();
    BillingEvent.OneTime sunrushAddBillingEvent =
        persistResource(getSunrushAddBillingEvent(clock.nowUtc().plusDays(20)));
    // Modify domain so that it is in the sunrush add grace period, has nameservers, but has a
    // serverHold on it.
    persistResource(
        reloadResourceByUniqueId().asBuilder()
            .setNameservers(ImmutableSet.of(ReferenceUnion.create(
                loadByUniqueId(HostResource.class, "ns2.example.foo", clock.nowUtc()))))
            .addGracePeriod(GracePeriod.forBillingEvent(
                GracePeriodStatus.SUNRUSH_ADD, sunrushAddBillingEvent))
            .addStatusValue(StatusValue.SERVER_HOLD)
            .build());
    clock.advanceOneMilli();
    doSunrushAddTest(
        sunrushAddBillingEvent,
        UserPrivileges.SUPERUSER,
        clock.nowUtc().plus(Registry.get("tld").getAddGracePeriodLength()));
  }

  @Test
  public void testSuccess_sunrushAddGracePeriodRemoveClientHold() throws Exception {
    setEppInput("domain_update_remove_client_hold.xml");
    persistReferencedEntities();
    persistDomain();
    BillingEvent.OneTime sunrushAddBillingEvent =
        persistResource(getSunrushAddBillingEvent(clock.nowUtc().plusDays(20)));
    // Modify domain so that it is in the sunrush add grace period, has nameservers, but has a
    // serverHold on it.
    persistResource(
        reloadResourceByUniqueId().asBuilder()
            .setNameservers(ImmutableSet.of(ReferenceUnion.create(
                loadByUniqueId(HostResource.class, "ns2.example.foo", clock.nowUtc()))))
            .addGracePeriod(GracePeriod.forBillingEvent(
                GracePeriodStatus.SUNRUSH_ADD, sunrushAddBillingEvent))
            .addStatusValue(StatusValue.CLIENT_HOLD)
            .build());
    clock.advanceOneMilli();
    doSunrushAddTest(
        sunrushAddBillingEvent,
        UserPrivileges.NORMAL,
        clock.nowUtc().plus(Registry.get("tld").getAddGracePeriodLength()));
  }

  @Test
  public void testSuccess_sunrushAddGracePeriodRemainsBecauseOfServerHold() throws Exception {
    setEppInput("domain_update_add_nameserver.xml");
    persistReferencedEntities();
    persistDomain();
    DateTime endOfGracePeriod = clock.nowUtc().plusDays(20);
    BillingEvent.OneTime sunrushAddBillingEvent =
        persistResource(getSunrushAddBillingEvent(endOfGracePeriod));
    // Modify domain so it has no nameservers and is in the sunrush add grace period, but also has a
    // server hold on it that will prevent the sunrush add grace period from being removed.
    persistResource(
        reloadResourceByUniqueId().asBuilder()
            .setNameservers(null)
            .addGracePeriod(GracePeriod.forBillingEvent(
                GracePeriodStatus.SUNRUSH_ADD, sunrushAddBillingEvent))
            .addStatusValue(StatusValue.SERVER_HOLD)
            .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.NORMAL, readFile("domain_update_response.xml"));
    // Verify that the domain is still in the sunrush add grace period.
    assertGracePeriods(
        reloadResourceByUniqueId().getGracePeriods(),
        ImmutableMap.of(
            GracePeriod.create(
                GracePeriodStatus.SUNRUSH_ADD, endOfGracePeriod, "TheRegistrar", null),
            sunrushAddBillingEvent));
  }

  private void modifyDomainToHave13Nameservers() throws Exception {
    ImmutableSet.Builder<ReferenceUnion<HostResource>> nameservers = new ImmutableSet.Builder<>();
    for (int i = 1; i < 15; i++) {
      if (i != 2) { // Skip 2 since that's the one that the tests will add.
        nameservers.add(ReferenceUnion.create(loadByUniqueId(
            HostResource.class, String.format("ns%d.example.foo", i), clock.nowUtc())));
      }
    }
    persistResource(
        reloadResourceByUniqueId().asBuilder()
            .setNameservers(nameservers.build())
            .build());
    clock.advanceOneMilli();
  }

  @Test
  public void testSuccess_maxNumberOfNameservers() throws Exception {
    persistReferencedEntities();
    persistDomain();
    // Modify domain to have 13 nameservers. We will then remove one and add one in the test.
    modifyDomainToHave13Nameservers();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_addAndRemoveLargeNumberOfNameserversAndContacts() throws Exception {
    persistReferencedEntities();
    persistDomain();
    setEppInput("domain_update_max_everything.xml");
    // Create 26 hosts and 8 contacts. Start the domain with half of them.
    ImmutableSet.Builder<ReferenceUnion<HostResource>> nameservers = new ImmutableSet.Builder<>();
    for (int i = 0; i < 26; i++) {
      HostResource host = persistActiveHost(String.format("max_test_%d.example.tld", i));
      if (i < 13) {
        nameservers.add(ReferenceUnion.create(host));
      }
    }
    ImmutableSet.Builder<DesignatedContact> contacts = new ImmutableSet.Builder<>();
    for (int i = 0; i < 8; i++) {
      ContactResource contact = persistActiveContact(String.format("max_test_%d", i));
      if (i < 4) {
        contacts.add(
            DesignatedContact.create(
                DesignatedContact.Type.values()[i],
                ReferenceUnion.create(contact)));
      }
    }
    persistResource(
        reloadResourceByUniqueId().asBuilder()
            .setNameservers(nameservers.build())
            .setContacts(contacts.build())
            .build());
    clock.advanceOneMilli();
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile("domain_update_response.xml"));
    DomainResource domain = reloadResourceByUniqueId();
    assertAboutDomains().that(domain)
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.DOMAIN_CREATE,
            HistoryEntry.Type.DOMAIN_UPDATE);
    assertThat(domain.getNameservers()).hasSize(13);
    // getContacts does not return contacts of type REGISTRANT, so check these separately.
    assertThat(domain.getContacts()).hasSize(3);
    assertThat(domain.getRegistrant().getLinked().get().getContactId()).isEqualTo("max_test_7");
    assertNoBillingEvents();
    assertDnsTasksEnqueued("example.tld");
  }

  @Test
  public void testSuccess_metadata() throws Exception {
    sessionMetadata.setSessionSource(SessionSource.TOOL);
    setEppInput("domain_update_metadata.xml");
    persistReferencedEntities();
    persistDomain();
    runFlow();
    DomainResource domain = reloadResourceByUniqueId();
    assertAboutDomains().that(domain)
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.DOMAIN_CREATE,
            HistoryEntry.Type.DOMAIN_UPDATE);
    assertAboutHistoryEntries()
        .that(getOnlyHistoryEntryOfType(domain, HistoryEntry.Type.DOMAIN_UPDATE))
        .hasMetadataReason("domain-update-test").and()
        .hasMetadataRequestedByRegistrar(true);
  }

  @Test
  public void testSuccess_metadataNotFromTool() throws Exception {
    thrown.expect(OnlyToolCanPassMetadataException.class);
    setEppInput("domain_update_metadata.xml");
    persistReferencedEntities();
    persistDomain();
    runFlow();
  }

  @Test
  public void testSuccess_removeContact() throws Exception {
    setEppInput("domain_update_remove_contact.xml");
    persistReferencedEntities();
    persistDomain();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_addAndRemoveSubordinateHostNameservers() throws Exception {
    // Test that operations involving subordinate hosts as nameservers do not change the subordinate
    // host relationship itself.
    setEppInput("domain_update_subordinate_hosts.xml");
    persistReferencedEntities();
    DomainResource domain = persistDomain();
    HostResource existingHost = persistActiveSubordinateHost("ns1.example.tld", domain);
    HostResource addedHost = persistActiveSubordinateHost("ns2.example.tld", domain);
    domain = persistResource(domain.asBuilder()
        .addSubordinateHost("ns1.example.tld")
        .addSubordinateHost("ns2.example.tld")
        .setNameservers(ImmutableSet.of(ReferenceUnion.create(
            loadByUniqueId(HostResource.class, "ns1.example.tld", clock.nowUtc()))))
        .build());
    clock.advanceOneMilli();
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile("domain_update_response.xml"));
    domain = reloadResourceByUniqueId();
    assertThat(domain.getNameservers()).containsExactly(ReferenceUnion.create(addedHost));
    assertThat(domain.getSubordinateHosts()).containsExactly("ns1.example.tld", "ns2.example.tld");
    existingHost = loadByUniqueId(HostResource.class, "ns1.example.tld", clock.nowUtc());
    addedHost = loadByUniqueId(HostResource.class, "ns2.example.tld", clock.nowUtc());
    assertThat(existingHost.getSuperordinateDomain()).isEqualTo(Ref.create(Key.create(domain)));
    assertThat(addedHost.getSuperordinateDomain()).isEqualTo(Ref.create(Key.create(domain)));
  }

  @Test
  public void testSuccess_registrantMovedToTechContact() throws Exception {
    setEppInput("domain_update_registrant_to_tech.xml");
    persistReferencedEntities();
    ContactResource sh8013 = loadByUniqueId(ContactResource.class, "sh8013", clock.nowUtc());
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setRegistrant(ReferenceUnion.create(sh8013))
            .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("domain_update_response.xml"));
  }

  @Test
  public void testSuccess_multipleReferencesToSameContactRemoved() throws Exception {
    setEppInput("domain_update_remove_multiple_contacts.xml");
    persistReferencedEntities();
    ContactResource sh8013 = loadByUniqueId(ContactResource.class, "sh8013", clock.nowUtc());
    ReferenceUnion<ContactResource> sh8013ReferenceUnion = ReferenceUnion.create(sh8013);
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setRegistrant(sh8013ReferenceUnion)
            .setContacts(ImmutableSet.of(
                DesignatedContact.create(Type.ADMIN, sh8013ReferenceUnion),
                DesignatedContact.create(Type.BILLING, sh8013ReferenceUnion),
                DesignatedContact.create(Type.TECH, sh8013ReferenceUnion)))
            .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("domain_update_response.xml"));
  }

  @Test
  public void testSuccess_removeClientUpdateProhibited() throws Exception {
    persistReferencedEntities();
    persistResource(
        persistDomain().asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED))
            .build());
    clock.advanceOneMilli();
    runFlow();
    assertAboutDomains().that(reloadResourceByUniqueId())
        .doesNotHaveStatusValue(StatusValue.CLIENT_UPDATE_PROHIBITED);
  }

  private void doSecDnsSuccessfulTest(
      String xmlFilename,
      ImmutableSet<DelegationSignerData> originalDsData,
      ImmutableSet<DelegationSignerData> expectedDsData)
      throws Exception {
    setEppInput(xmlFilename);
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setDsData(originalDsData)
            .build());
    assertTransactionalFlow(true);
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("domain_update_response.xml"));
    DomainResource resource = reloadResourceByUniqueId();
    assertAboutDomains().that(resource)
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.DOMAIN_UPDATE);
    assertThat(resource.getDsData()).isEqualTo(expectedDsData);
    assertDnsTasksEnqueued("example.tld");
  }

  @Test
  public void testSuccess_secDnsAdd() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_dsdata_add.xml",
        null,
        ImmutableSet.of(DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))));
  }

  @Test
  public void testSuccess_secDnsAddPreservesExisting() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_dsdata_add.xml",
        ImmutableSet.of(SOME_DSDATA),
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))));
  }

  @Test
  public void testSuccess_secDnsAddToMaxRecords() throws Exception {
    ImmutableSet.Builder<DelegationSignerData> builder = new ImmutableSet.Builder<>();
    for (int i = 0; i < 7; ++i) {
      builder.add(DelegationSignerData.create(i, 2, 3, new byte[]{0, 1, 2}));
    }
    ImmutableSet<DelegationSignerData> commonDsData = builder.build();

    doSecDnsSuccessfulTest(
        "domain_update_dsdata_add.xml",
        commonDsData,
        ImmutableSet.copyOf(
            union(commonDsData, ImmutableSet.of(DelegationSignerData.create(
                12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))))));
  }

  @Test
  public void testSuccess_secDnsRemove() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_dsdata_rem.xml",
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))),
        ImmutableSet.of(SOME_DSDATA));
  }

  @Test
  public void testSuccess_secDnsRemoveAll() throws Exception {
    // As an aside, this test also validates that it's ok to set the 'urgent' attribute to false.
    doSecDnsSuccessfulTest(
        "domain_update_dsdata_rem_all.xml",
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))),
        ImmutableSet.<DelegationSignerData>of());
  }

  @Test
  public void testSuccess_secDnsAddRemove() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_dsdata_add_rem.xml",
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))),
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))));
  }

  @Test
  public void testSuccess_secDnsAddRemoveToMaxRecords() throws Exception {
    ImmutableSet.Builder<DelegationSignerData> builder = new ImmutableSet.Builder<>();
    for (int i = 0; i < 7; ++i) {
      builder.add(DelegationSignerData.create(i, 2, 3, new byte[]{0, 1, 2}));
    }
    ImmutableSet<DelegationSignerData> commonDsData = builder.build();

    doSecDnsSuccessfulTest(
        "domain_update_dsdata_add_rem.xml",
        ImmutableSet.copyOf(
            union(commonDsData, ImmutableSet.of(DelegationSignerData.create(
                12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))))),
        ImmutableSet.copyOf(
            union(commonDsData, ImmutableSet.of(DelegationSignerData.create(
                12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))))));
  }

  @Test
  public void testSuccess_secDnsAddRemoveSame() throws Exception {
    // Adding and removing the same dsData is a no-op because removes are processed first.
    doSecDnsSuccessfulTest(
        "domain_update_dsdata_add_rem_same.xml",
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))),
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))));
  }

  @Test
  public void testSuccess_secDnsRemoveAlreadyNotThere() throws Exception {
    // Removing a dsData that isn't there is a no-op.
    doSecDnsSuccessfulTest(
        "domain_update_dsdata_rem.xml",
        ImmutableSet.of(SOME_DSDATA),
        ImmutableSet.of(SOME_DSDATA));
  }

  public void doServerStatusBillingTest(String xmlFilename, boolean isBillable) throws Exception {
    setEppInput(xmlFilename);
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_update_response.xml"));

     if (isBillable) {
       assertBillingEvents(
           new BillingEvent.OneTime.Builder()
               .setReason(Reason.SERVER_STATUS)
               .setTargetId("example.tld")
               .setClientId("TheRegistrar")
               .setCost(Money.of(USD, 19))
               .setEventTime(clock.nowUtc())
               .setBillingTime(clock.nowUtc())
               .setParent(getOnlyHistoryEntryOfType(
                   reloadResourceByUniqueId(), HistoryEntry.Type.DOMAIN_UPDATE))
               .build());
     } else {
       assertNoBillingEvents();
     }
  }

  @Test
  public void testSuccess_addServerStatusBillingEvent() throws Exception {
    sessionMetadata.setSessionSource(SessionSource.TOOL);
    persistReferencedEntities();
    persistDomain();
    doServerStatusBillingTest("domain_update_add_server_status.xml", true);
  }

  @Test
  public void testSuccess_noBillingOnPreExistingServerStatus() throws Exception {
    sessionMetadata.setSessionSource(SessionSource.TOOL);
    DomainResource addStatusDomain = persistActiveDomain(getUniqueIdFromCommand());
    persistResource(
        addStatusDomain.asBuilder()
            .addStatusValue(StatusValue.SERVER_RENEW_PROHIBITED)
            .build());
    doServerStatusBillingTest("domain_update_add_server_status.xml", false);
  }

  @Test
  public void testSuccess_removeServerStatusBillingEvent() throws Exception {
    sessionMetadata.setSessionSource(SessionSource.TOOL);
    persistReferencedEntities();
    DomainResource removeStatusDomain = persistDomain();
    persistResource(
        removeStatusDomain.asBuilder()
            .addStatusValue(StatusValue.SERVER_RENEW_PROHIBITED)
            .build());
    doServerStatusBillingTest("domain_update_remove_server_status.xml", true);
  }

  @Test
  public void testSuccess_changeServerStatusBillingEvent() throws Exception {
    sessionMetadata.setSessionSource(SessionSource.TOOL);
    persistReferencedEntities();
    DomainResource changeStatusDomain = persistDomain();
    persistResource(
        changeStatusDomain.asBuilder()
            .addStatusValue(StatusValue.SERVER_RENEW_PROHIBITED)
            .build());
    doServerStatusBillingTest("domain_update_change_server_status.xml", true);
  }

  @Test
  public void testSuccess_noBillingEventOnNonServerStatusChange() throws Exception {
    persistActiveDomain(getUniqueIdFromCommand());
    doServerStatusBillingTest("domain_update_add_non_server_status.xml", false);
  }

  @Test
  public void testSuccess_noBillingEventOnServerHoldStatusChange() throws Exception {
    persistActiveDomain(getUniqueIdFromCommand());
    doServerStatusBillingTest("domain_update_add_server_hold_status.xml", false);
  }

  @Test
  public void testSuccess_noBillingEventOnServerStatusChangeNotFromRegistrar() throws Exception {
    sessionMetadata.setSessionSource(SessionSource.TOOL);
    persistActiveDomain(getUniqueIdFromCommand());
    doServerStatusBillingTest("domain_update_add_server_status_non_registrar.xml", false);
  }

  @Test
  public void testSuccess_superuserClientUpdateProhibited() throws Exception {
    setEppInput("domain_update_add_server_hold_status.xml");
    persistReferencedEntities();
    persistResource(
        persistActiveDomain(getUniqueIdFromCommand()).asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED))
            .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_update_response.xml"));
    assertAboutDomains().that(reloadResourceByUniqueId())
        .hasStatusValue(StatusValue.CLIENT_UPDATE_PROHIBITED).and()
        .hasStatusValue(StatusValue.SERVER_HOLD);
  }

  private void doSecDnsFailingTest(Class<? extends Exception> expectedException, String xmlFilename)
      throws Exception {
    thrown.expect(expectedException);
    setEppInput(xmlFilename);
    persistReferencedEntities();
    persistActiveDomain(getUniqueIdFromCommand());
    runFlow();
  }

  @Test
  public void testFailure_secDnsAllCannotBeFalse() throws Exception {
    doSecDnsFailingTest(
        SecDnsAllUsageException.class, "domain_update_dsdata_rem_all_false.xml");
  }

  @Test
  public void testFailure_secDnsEmptyNotAllowed() throws Exception {
    doSecDnsFailingTest(EmptySecDnsUpdateException.class, "domain_update_dsdata_empty.xml");
  }

  @Test
  public void testFailure_secDnsUrgentNotSupported() throws Exception {
    doSecDnsFailingTest(
        UrgentAttributeNotSupportedException.class,
        "domain_update_dsdata_urgent.xml");
  }

  @Test
  public void testFailure_secDnsChangeNotSupported() throws Exception {
    doSecDnsFailingTest(
        MaxSigLifeChangeNotSupportedException.class,
        "domain_update_maxsiglife.xml");
  }

  @Test
  public void testFailure_secDnsTooManyDsRecords() throws Exception {
    thrown.expect(TooManyDsRecordsException.class);
    ImmutableSet.Builder<DelegationSignerData> builder = new ImmutableSet.Builder<>();
    for (int i = 0; i < 8; ++i) {
      builder.add(DelegationSignerData.create(i, 2, 3, new byte[]{0, 1, 2}));
    }

    setEppInput("domain_update_dsdata_add.xml");
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setDsData(builder.build())
            .build());
    runFlow();
  }

  @Test
  public void testFailure_tooManyNameservers() throws Exception {
    thrown.expect(TooManyNameserversException.class);
    setEppInput("domain_update_add_nameserver.xml");
    persistReferencedEntities();
    persistDomain();
    // Modify domain so it has 13 nameservers. We will then try to add one in the test.
    modifyDomainToHave13Nameservers();
    runFlow();
  }

  @Test
  public void testFailure_wrongExtension() throws Exception {
    thrown.expect(UnimplementedExtensionException.class);
    setEppInput("domain_update_wrong_extension.xml");
    runFlow();
  }

  @Test
  public void testFailure_neverExisted() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    persistReferencedEntities();
    runFlow();
  }

  @Test
  public void testFailure_existedButWasDeleted() throws Exception {
    thrown.expect(
        ResourceToMutateDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    persistReferencedEntities();
    persistDeletedDomain(getUniqueIdFromCommand(), clock.nowUtc());
    runFlow();
  }

  @Test
  public void testFailure_clientUpdateProhibited() throws Exception {
    createTld("com");
    thrown.expect(ResourceHasClientUpdateProhibitedException.class);
    setEppInput("domain_update_authinfo.xml");
    persistReferencedEntities();
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED))
            .build());
    runFlow();
  }

  @Test
  public void testFailure_serverUpdateProhibited() throws Exception {
    thrown.expect(ResourceStatusProhibitsOperationException.class);
    persistReferencedEntities();
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.SERVER_UPDATE_PROHIBITED))
            .build());
    runFlow();
  }

  @Test
  public void testFailure_missingHost() throws Exception {
    thrown.expect(
        LinkedResourceDoesNotExistException.class,
        "(ns2.example.foo)");
    persistActiveHost("ns1.example.foo");
    persistActiveContact("sh8013");
    persistActiveContact("mak21");
    persistActiveDomain(getUniqueIdFromCommand());
    runFlow();
  }

  @Test
  public void testFailure_missingContact() throws Exception {
    thrown.expect(
        LinkedResourceDoesNotExistException.class,
        "(sh8013)");
    persistActiveHost("ns1.example.foo");
    persistActiveHost("ns2.example.foo");
    persistActiveContact("mak21");
    persistActiveDomain(getUniqueIdFromCommand());
    runFlow();
  }

  @Test
  public void testFailure_addingDuplicateContact() throws Exception {
    thrown.expect(DuplicateContactForRoleException.class);
    persistReferencedEntities();
    persistActiveContact("foo");
    persistDomain();
    // Add a tech contact to the persisted entity, which should cause the flow to fail when it tries
    // to add "mak21" as a second tech contact.
    persistResource(
        reloadResourceByUniqueId().asBuilder()
            .setContacts(ImmutableSet.of(
                DesignatedContact.create(Type.TECH, ReferenceUnion.create(
                    loadByUniqueId(ContactResource.class, "foo", clock.nowUtc())))))
            .build());
    runFlow();
  }

  @Test
  public void testFailure_clientProhibitedStatusValue() throws Exception {
    thrown.expect(StatusNotClientSettableException.class);
    setEppInput("domain_update_prohibited_status.xml");
    persistReferencedEntities();
    persistDomain();
    runFlow();
  }

  @Test
  public void testSuccess_superuserClientProhibitedStatusValue() throws Exception {
    setEppInput("domain_update_prohibited_status.xml");
    persistReferencedEntities();
    persistDomain();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_update_response.xml"));
  }

  @Test
  public void testFailure_pendingDelete() throws Exception {
    thrown.expect(ResourceStatusProhibitsOperationException.class);
    persistReferencedEntities();
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setDeletionTime(clock.nowUtc().plusDays(1))
            .addStatusValue(StatusValue.PENDING_DELETE)
            .build());
    runFlow();
  }

  @Test
  public void testFailure_duplicateContactInCommand() throws Exception {
    thrown.expect(DuplicateContactForRoleException.class);
    setEppInput("domain_update_duplicate_contact.xml");
    persistReferencedEntities();
    persistDomain();
    runFlow();
  }

  @Test
  public void testFailure_missingContactType() throws Exception {
    // We need to test for missing type, but not for invalid - the schema enforces that for us.
    thrown.expect(MissingContactTypeException.class);
    setEppInput("domain_update_missing_contact_type.xml");
    persistReferencedEntities();
    persistDomain();
    runFlow();
  }

  @Test
  public void testFailure_unauthorizedClient() throws Exception {
    thrown.expect(ResourceNotOwnedException.class);
    sessionMetadata.setClientId("NewRegistrar");
    persistReferencedEntities();
    persistDomain();
    runFlow();
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    thrown.expect(NotAuthorizedForTldException.class);
    persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    persistReferencedEntities();
    persistDomain();
    runFlow();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    sessionMetadata.setSuperuser(true);
    sessionMetadata.setClientId("NewRegistrar");
    persistReferencedEntities();
    persistDomain();
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_update_response.xml"));
  }

  @Test
  public void testFailure_sameNameserverAddedAndRemoved() throws Exception {
    thrown.expect(AddRemoveSameValueEppException.class);
    setEppInput("domain_update_add_remove_same_host.xml");
    persistReferencedEntities();
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setNameservers(ImmutableSet.of(ReferenceUnion.create(
                loadByUniqueId(HostResource.class, "ns1.example.foo", clock.nowUtc()))))
            .build());
    runFlow();
  }

  @Test
  public void testFailure_sameContactAddedAndRemoved() throws Exception {
    thrown.expect(AddRemoveSameValueEppException.class);
    setEppInput("domain_update_add_remove_same_contact.xml");
    persistReferencedEntities();
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setContacts(ImmutableSet.of(DesignatedContact.create(
                Type.TECH,
                ReferenceUnion.create(
                    loadByUniqueId(ContactResource.class, "sh8013", clock.nowUtc())))))
            .build());
    runFlow();
  }

  @Test
  public void testFailure_removeAdmin() throws Exception {
    thrown.expect(MissingAdminContactException.class);
    setEppInput("domain_update_remove_admin.xml");
    persistReferencedEntities();
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setContacts(ImmutableSet.of(
                DesignatedContact.create(Type.ADMIN, ReferenceUnion.create(sh8013Contact)),
                DesignatedContact.create(Type.TECH, ReferenceUnion.create(sh8013Contact))))
            .build());
    runFlow();
  }

  @Test
  public void testFailure_removeTech() throws Exception {
    thrown.expect(MissingTechnicalContactException.class);
    setEppInput("domain_update_remove_tech.xml");
    persistReferencedEntities();
    persistResource(
        newDomainResource(getUniqueIdFromCommand()).asBuilder()
            .setContacts(ImmutableSet.of(
                DesignatedContact.create(Type.ADMIN, ReferenceUnion.create(sh8013Contact)),
                DesignatedContact.create(Type.TECH, ReferenceUnion.create(sh8013Contact))))
            .build());
    runFlow();
  }

  @Test
  public void testFailure_addPendingDeleteContact() throws Exception {
    thrown.expect(
        LinkedResourceInPendingDeleteProhibitsOperationException.class,
        "mak21");
    persistReferencedEntities();
    persistDomain();
    persistActiveHost("ns1.example.foo");
    persistActiveHost("ns2.example.foo");
    persistActiveContact("sh8013");
    persistResource(loadByUniqueId(ContactResource.class, "mak21", clock.nowUtc()).asBuilder()
        .addStatusValue(StatusValue.PENDING_DELETE)
        .build());
    clock.advanceOneMilli();
    runFlow();
  }

  @Test
  public void testFailure_addPendingDeleteHost() throws Exception {
    thrown.expect(
        LinkedResourceInPendingDeleteProhibitsOperationException.class,
        "ns2.example.foo");
    persistReferencedEntities();
    persistDomain();
    persistActiveHost("ns1.example.foo");
    persistActiveContact("mak21");
    persistActiveContact("sh8013");
    persistResource(
        loadByUniqueId(HostResource.class, "ns2.example.foo", clock.nowUtc()).asBuilder()
          .addStatusValue(StatusValue.PENDING_DELETE)
          .build());
    clock.advanceOneMilli();
    runFlow();
  }
}
