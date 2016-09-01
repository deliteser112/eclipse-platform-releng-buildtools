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

package google.registry.export.sheet;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.deleteResource;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Duration.standardHours;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.config.RegistryEnvironment;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarAddress;
import google.registry.model.registrar.RegistrarContact;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link SyncRegistrarsSheet}. */
@RunWith(MockitoJUnitRunner.class)
public class SyncRegistrarsSheetTest {

  private static final RegistryEnvironment ENVIRONMENT = RegistryEnvironment.get();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Captor
  private ArgumentCaptor<ImmutableList<ImmutableMap<String, String>>> rowsCaptor;

  @Mock
  private SheetSynchronizer sheetSynchronizer;

  private final FakeClock clock = new FakeClock(DateTime.now(UTC));

  private SyncRegistrarsSheet newSyncRegistrarsSheet() {
    SyncRegistrarsSheet result = new SyncRegistrarsSheet();
    result.clock = clock;
    result.sheetSynchronizer = sheetSynchronizer;
    return result;
  }

  @Before
  public void before() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    createTld("example");
    // Remove Registrar entities created by AppEngineRule.
    for (Registrar registrar : Registrar.loadAll()) {
      deleteResource(registrar);
    }
  }

  @Test
  public void testWasRegistrarsModifiedInLast_noRegistrars_returnsFalse() throws Exception {
    SyncRegistrarsSheet sync = newSyncRegistrarsSheet();
    assertThat(sync.wasRegistrarsModifiedInLast(Duration.standardHours(1))).isFalse();
  }

  @Test
  public void testWasRegistrarsModifiedInLastInterval() throws Exception {
    Duration interval = standardHours(1);
    persistResource(new Registrar.Builder()
        .setClientIdentifier("SomeRegistrar")
        .setRegistrarName("Some Registrar Inc.")
        .setType(Registrar.Type.REAL)
        .setIanaIdentifier(8L)
        .setState(Registrar.State.ACTIVE)
        .build());
    clock.advanceBy(interval);
    assertThat(newSyncRegistrarsSheet().wasRegistrarsModifiedInLast(interval)).isTrue();
    clock.advanceOneMilli();
    assertThat(newSyncRegistrarsSheet().wasRegistrarsModifiedInLast(interval)).isFalse();
  }

  @Test
  public void testRun() throws Exception {
    persistResource(new Registrar.Builder()
        .setClientIdentifier("anotherregistrar")
        .setRegistrarName("Another Registrar LLC")
        .setType(Registrar.Type.REAL)
        .setIanaIdentifier(1L)
        .setState(Registrar.State.ACTIVE)
        .setInternationalizedAddress(new RegistrarAddress.Builder()
            .setStreet(ImmutableList.of("I will get ignored :'("))
            .setCity("Williamsburg")
            .setState("NY")
            .setZip("11211")
            .setCountryCode("US")
            .build())
        .setLocalizedAddress(new RegistrarAddress.Builder()
            .setStreet(ImmutableList.of(
                "123 Main St",
                "Suite 100"))
            .setCity("Smalltown")
            .setState("NY")
            .setZip("11211")
            .setCountryCode("US")
            .build())
        .setPhoneNumber("+1.2125551212")
        .setFaxNumber("+1.2125551213")
        .setEmailAddress("contact-us@example.com")
        .setWhoisServer("whois.example.com")
        .setUrl("http://www.example.org/another_registrar")
        .setIcannReferralEmail("jim@example.net")
        .build());

    Registrar registrar = new Registrar.Builder()
        .setClientIdentifier("aaaregistrar")
        .setRegistrarName("AAA Registrar Inc.")
        .setType(Registrar.Type.REAL)
        .setIanaIdentifier(8L)
        .setState(Registrar.State.SUSPENDED)
        .setPassword("pa$$word")
        .setEmailAddress("nowhere@example.org")
        .setInternationalizedAddress(new RegistrarAddress.Builder()
            .setStreet(ImmutableList.of("I get fallen back upon since there's no l10n addr"))
            .setCity("Williamsburg")
            .setState("NY")
            .setZip("11211")
            .setCountryCode("US")
            .build())
        .setAllowedTlds(ImmutableSet.of("example"))
        .setPhoneNumber("+1.2223334444")
        .setUrl("http://www.example.org/aaa_registrar")
        .build();
    ImmutableList<RegistrarContact> contacts = ImmutableList.of(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Jane Doe")
            .setEmailAddress("contact@example.com")
            .setPhoneNumber("+1.1234567890")
            .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN, RegistrarContact.Type.BILLING))
            .build(),
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("John Doe")
            .setEmailAddress("john.doe@example.tld")
            .setPhoneNumber("+1.1234567890")
            .setFaxNumber("+1.1234567891")
            .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
            // Purposely flip the internal/external admin/tech
            // distinction to make sure we're not relying on it.  Sigh.
            .setVisibleInWhoisAsAdmin(false)
            .setVisibleInWhoisAsTech(true)
            .setGaeUserId("light")
            .build(),
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Jane Smith")
            .setEmailAddress("pride@example.net")
            .setTypes(ImmutableSet.of(RegistrarContact.Type.TECH))
        .build());
    // Use registrar key for contacts' parent.
    persistSimpleResources(contacts);
    persistResource(registrar);

    newSyncRegistrarsSheet().run("foobar");

    verify(sheetSynchronizer).synchronize(eq("foobar"), rowsCaptor.capture());
    ImmutableList<ImmutableMap<String, String>> rows = getOnlyElement(rowsCaptor.getAllValues());
    assertThat(rows).hasSize(2);

    ImmutableMap<String, String> row = rows.get(0);
    assertThat(row).containsEntry("clientIdentifier", "aaaregistrar");
    assertThat(row).containsEntry("registrarName", "AAA Registrar Inc.");
    assertThat(row).containsEntry("state", "SUSPENDED");
    assertThat(row).containsEntry("ianaIdentifier", "8");
    assertThat(row).containsEntry("billingIdentifier", "");
    assertThat(row).containsEntry("primaryContacts", ""
        + "Jane Doe\n"
        + "contact@example.com\n"
        + "Tel: +1.1234567890\n"
        + "Types: [ADMIN, BILLING]\n"
        + "Visible in WHOIS as Admin contact: No\n"
        + "Visible in WHOIS as Technical contact: No\n"
        + "\n"
        + "John Doe\n"
        + "john.doe@example.tld\n"
        + "Tel: +1.1234567890\n"
        + "Fax: +1.1234567891\n"
        + "Types: [ADMIN]\n"
        + "Visible in WHOIS as Admin contact: No\n"
        + "Visible in WHOIS as Technical contact: Yes\n"
        + "GAE-UserID: light\n");
    assertThat(row).containsEntry("techContacts", ""
        + "Jane Smith\n"
        + "pride@example.net\n"
        + "Types: [TECH]\n"
        + "Visible in WHOIS as Admin contact: No\n"
        + "Visible in WHOIS as Technical contact: No\n");
    assertThat(row).containsEntry("marketingContacts", "");
    assertThat(row).containsEntry("abuseContacts", "");
    assertThat(row).containsEntry("whoisInquiryContacts", "");
    assertThat(row).containsEntry("legalContacts", "");
    assertThat(row).containsEntry("billingContacts", ""
        + "Jane Doe\n"
        + "contact@example.com\n"
        + "Tel: +1.1234567890\n"
        + "Types: [ADMIN, BILLING]\n"
        + "Visible in WHOIS as Admin contact: No\n"
        + "Visible in WHOIS as Technical contact: No\n");
    assertThat(row).containsEntry("contactsMarkedAsWhoisAdmin", "");
    assertThat(row).containsEntry("contactsMarkedAsWhoisTech", ""
        + "John Doe\n"
        + "john.doe@example.tld\n"
        + "Tel: +1.1234567890\n"
        + "Fax: +1.1234567891\n"
        + "Types: [ADMIN]\n"
        + "Visible in WHOIS as Admin contact: No\n"
        + "Visible in WHOIS as Technical contact: Yes\n"
        + "GAE-UserID: light\n");
    assertThat(row).containsEntry("emailAddress", "nowhere@example.org");
    assertThat(row).containsEntry(
        "address.street", "I get fallen back upon since there's no l10n addr");
    assertThat(row).containsEntry("address.city", "Williamsburg");
    assertThat(row).containsEntry("address.state", "NY");
    assertThat(row).containsEntry("address.zip", "11211");
    assertThat(row).containsEntry("address.countryCode", "US");
    assertThat(row).containsEntry("phoneNumber", "+1.2223334444");
    assertThat(row).containsEntry("faxNumber", "");
    assertThat(row.get("creationTime")).isEqualTo(clock.nowUtc().toString());
    assertThat(row.get("lastUpdateTime")).isEqualTo(clock.nowUtc().toString());
    assertThat(row).containsEntry("allowedTlds", "example");
    assertThat(row).containsEntry("blockPremiumNames", "false");
    assertThat(row).containsEntry("ipAddressWhitelist", "");
    assertThat(row).containsEntry("url", "http://www.example.org/aaa_registrar");
    assertThat(row).containsEntry("icannReferralEmail", "");
    assertThat(row).containsEntry("whoisServer",
        ENVIRONMENT.config().getRegistrarDefaultWhoisServer());
    assertThat(row).containsEntry("referralUrl",
        ENVIRONMENT.config().getRegistrarDefaultReferralUrl().toString());

    row = rows.get(1);
    assertThat(row).containsEntry("clientIdentifier", "anotherregistrar");
    assertThat(row).containsEntry("registrarName", "Another Registrar LLC");
    assertThat(row).containsEntry("state", "ACTIVE");
    assertThat(row).containsEntry("ianaIdentifier", "1");
    assertThat(row).containsEntry("billingIdentifier", "");
    assertThat(row).containsEntry("primaryContacts", "");
    assertThat(row).containsEntry("techContacts", "");
    assertThat(row).containsEntry("marketingContacts", "");
    assertThat(row).containsEntry("abuseContacts", "");
    assertThat(row).containsEntry("whoisInquiryContacts", "");
    assertThat(row).containsEntry("legalContacts", "");
    assertThat(row).containsEntry("billingContacts", "");
    assertThat(row).containsEntry("contactsMarkedAsWhoisAdmin", "");
    assertThat(row).containsEntry("contactsMarkedAsWhoisTech", "");
    assertThat(row).containsEntry("emailAddress", "contact-us@example.com");
    assertThat(row).containsEntry("address.street", "123 Main St\nSuite 100");
    assertThat(row).containsEntry("address.city", "Smalltown");
    assertThat(row).containsEntry("address.state", "NY");
    assertThat(row).containsEntry("address.zip", "11211");
    assertThat(row).containsEntry("address.countryCode", "US");
    assertThat(row).containsEntry("phoneNumber", "+1.2125551212");
    assertThat(row).containsEntry("faxNumber", "+1.2125551213");
    assertThat(row.get("creationTime")).isEqualTo(clock.nowUtc().toString());
    assertThat(row.get("lastUpdateTime")).isEqualTo(clock.nowUtc().toString());
    assertThat(row).containsEntry("allowedTlds", "");
    assertThat(row).containsEntry("whoisServer", "whois.example.com");
    assertThat(row).containsEntry("blockPremiumNames", "false");
    assertThat(row).containsEntry("ipAddressWhitelist", "");
    assertThat(row).containsEntry("url", "http://www.example.org/another_registrar");
    assertThat(row).containsEntry("referralUrl",
        ENVIRONMENT.config().getRegistrarDefaultReferralUrl().toString());
    assertThat(row).containsEntry("icannReferralEmail", "jim@example.net");
  }

  @Test
  public void testRun_missingValues_stillWorks() throws Exception {
    persistResource(new Registrar.Builder()
        .setClientIdentifier("SomeRegistrar")
        .setType(Registrar.Type.REAL)
        .setIanaIdentifier(8L)
        .build());

    newSyncRegistrarsSheet().run("foobar");

    verify(sheetSynchronizer).synchronize(eq("foobar"), rowsCaptor.capture());
    ImmutableMap<String, String> row = getOnlyElement(getOnlyElement(rowsCaptor.getAllValues()));
    assertThat(row).containsEntry("clientIdentifier", "SomeRegistrar");
    assertThat(row).containsEntry("registrarName", "");
    assertThat(row).containsEntry("state", "");
    assertThat(row).containsEntry("ianaIdentifier", "8");
    assertThat(row).containsEntry("billingIdentifier", "");
    assertThat(row).containsEntry("primaryContacts", "");
    assertThat(row).containsEntry("techContacts", "");
    assertThat(row).containsEntry("marketingContacts", "");
    assertThat(row).containsEntry("abuseContacts", "");
    assertThat(row).containsEntry("whoisInquiryContacts", "");
    assertThat(row).containsEntry("legalContacts", "");
    assertThat(row).containsEntry("billingContacts", "");
    assertThat(row).containsEntry("contactsMarkedAsWhoisAdmin", "");
    assertThat(row).containsEntry("contactsMarkedAsWhoisTech", "");
    assertThat(row).containsEntry("emailAddress", "");
    assertThat(row).containsEntry("address.street", "UNKNOWN");
    assertThat(row).containsEntry("address.city", "UNKNOWN");
    assertThat(row).containsEntry("address.state", "");
    assertThat(row).containsEntry("address.zip", "");
    assertThat(row).containsEntry("address.countryCode", "US");
    assertThat(row).containsEntry("phoneNumber", "");
    assertThat(row).containsEntry("faxNumber", "");
    assertThat(row).containsEntry("allowedTlds", "");
    assertThat(row).containsEntry("whoisServer",
        ENVIRONMENT.config().getRegistrarDefaultWhoisServer());
    assertThat(row).containsEntry("blockPremiumNames", "false");
    assertThat(row).containsEntry("ipAddressWhitelist", "");
    assertThat(row).containsEntry("url", "");
    assertThat(row).containsEntry("referralUrl",
        ENVIRONMENT.config().getRegistrarDefaultReferralUrl().toString());
    assertThat(row).containsEntry("icannReferralEmail", "");
  }
}
