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

package google.registry.rdap;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistHostResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeDomainResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeHistoryEntry;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.Period;
import google.registry.model.host.HostResource;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.reporting.HistoryEntry;
import google.registry.rdap.RdapJsonFormatter.MakeRdapJsonNoticeParameters;
import google.registry.rdap.RdapJsonFormatter.OutputDataType;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapJsonFormatter}. */
@RunWith(JUnit4.class)
public class RdapJsonFormatterTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();
  @Rule public final InjectRule inject = new InjectRule();

  private final FakeClock clock = new FakeClock(DateTime.parse("1999-01-01T00:00:00Z"));

  private Registrar registrar;
  private DomainResource domainResourceFull;
  private DomainResource domainResourceNoNameservers;
  private HostResource hostResourceIpv4;
  private HostResource hostResourceIpv6;
  private HostResource hostResourceBoth;
  private HostResource hostResourceNoAddresses;
  private ContactResource contactResourceRegistrant;
  private ContactResource contactResourceAdmin;
  private ContactResource contactResourceTech;

  private static final String LINK_BASE = "http://myserver.google.com/";
  private static final String LINK_BASE_NO_TRAILING_SLASH = "http://myserver.google.com";
  // Do not set a port43 whois server, as per Gustavo Lozano.
  private static final String WHOIS_SERVER = null;

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);

    // Create the registrar in 1999, then update it in 2000.
    clock.setTo(DateTime.parse("1999-01-01T00:00:00Z"));
    createTld("xn--q9jyb4c", TldState.GENERAL_AVAILABILITY);
    registrar = persistResource(makeRegistrar("unicoderegistrar", "みんな", Registrar.State.ACTIVE));
    clock.setTo(DateTime.parse("2000-01-01T00:00:00Z"));
    registrar = persistResource(registrar);

    persistSimpleResources(makeMoreRegistrarContacts(registrar));

    contactResourceRegistrant = makeAndPersistContactResource(
        "8372808-ERL",
        "(◕‿◕)",
        "lol@cat.みんな",
        null,
        clock.nowUtc().minusYears(1));
    contactResourceAdmin = makeAndPersistContactResource(
        "8372808-IRL",
        "Santa Claus",
        null,
        ImmutableList.of("Santa Claus Tower", "41st floor", "Suite みんな"),
        clock.nowUtc().minusYears(2));
    contactResourceTech = makeAndPersistContactResource(
        "8372808-TRL",
        "The Raven",
        "bog@cat.みんな",
        ImmutableList.of("Chamber Door", "upper level"),
        clock.nowUtc().minusYears(3));
    hostResourceIpv4 = makeAndPersistHostResource(
        "ns1.cat.みんな", "1.2.3.4", clock.nowUtc().minusYears(1));
    hostResourceIpv6 = makeAndPersistHostResource(
        "ns2.cat.みんな", "bad:f00d:cafe:0:0:0:15:beef", clock.nowUtc().minusYears(2));
    hostResourceBoth = makeAndPersistHostResource(
        "ns3.cat.みんな", "1.2.3.4", "bad:f00d:cafe:0:0:0:15:beef", clock.nowUtc().minusYears(3));
    hostResourceNoAddresses = makeAndPersistHostResource(
        "ns4.cat.みんな", null, clock.nowUtc().minusYears(4));
    domainResourceFull = persistResource(
        makeDomainResource(
            "cat.みんな",
            contactResourceRegistrant,
            contactResourceAdmin,
            contactResourceTech,
            hostResourceIpv4,
            hostResourceIpv6,
            registrar));
    domainResourceNoNameservers = persistResource(
        makeDomainResource(
            "fish.みんな",
            contactResourceRegistrant,
            contactResourceAdmin,
            contactResourceTech,
            null,
            null,
            registrar));

    // history entries
    persistResource(
        makeHistoryEntry(
            domainResourceFull,
            HistoryEntry.Type.DOMAIN_CREATE,
            Period.create(1, Period.Unit.YEARS),
            "created",
            clock.nowUtc()));
    persistResource(
        makeHistoryEntry(
            domainResourceNoNameservers,
            HistoryEntry.Type.DOMAIN_CREATE,
            Period.create(1, Period.Unit.YEARS),
            "created",
            clock.nowUtc()));
  }

  public static ImmutableList<RegistrarContact> makeMoreRegistrarContacts(Registrar registrar) {
    return ImmutableList.of(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Baby Doe")
            .setEmailAddress("babydoe@example.com")
            .setPhoneNumber("+1.2125551217")
            .setFaxNumber("+1.2125551218")
            .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
            .setVisibleInWhoisAsAdmin(false)
            .setVisibleInWhoisAsTech(false)
            .build(),
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("John Doe")
            .setEmailAddress("johndoe@example.com")
            .setFaxNumber("+1.2125551213")
            .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
            .setVisibleInWhoisAsAdmin(false)
            .setVisibleInWhoisAsTech(true)
            .build(),
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Jane Doe")
            .setEmailAddress("janedoe@example.com")
            .setPhoneNumber("+1.2125551215")
            .setTypes(ImmutableSet.of(RegistrarContact.Type.TECH, RegistrarContact.Type.ADMIN))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(false)
            .build(),
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Play Doe")
            .setEmailAddress("playdoe@example.com")
            .setPhoneNumber("+1.2125551217")
            .setFaxNumber("+1.2125551218")
            .setTypes(ImmutableSet.of(RegistrarContact.Type.BILLING))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(true)
            .build());
  }

  private Object loadJson(String expectedFileName) {
    return JSONValue.parse(loadFileWithSubstitutions(this.getClass(), expectedFileName, null));
  }

  @Test
  public void testRegistrar() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForRegistrar(
            registrar, false, LINK_BASE, WHOIS_SERVER, clock.nowUtc(), OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_registrar.json"));
  }

  @Test
  public void testRegistrar_summary() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForRegistrar(
            registrar, false, LINK_BASE, WHOIS_SERVER, clock.nowUtc(), OutputDataType.SUMMARY))
        .isEqualTo(loadJson("rdapjson_registrar_summary.json"));
  }

  @Test
  public void testHost_ipv4() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForHost(
            hostResourceIpv4, false, LINK_BASE, WHOIS_SERVER, clock.nowUtc(), OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_host_ipv4.json"));
  }

  @Test
  public void testHost_ipv6() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForHost(
            hostResourceIpv6, false, LINK_BASE, WHOIS_SERVER, clock.nowUtc(), OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_host_ipv6.json"));
  }

  @Test
  public void testHost_both() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForHost(
            hostResourceBoth, false, LINK_BASE, WHOIS_SERVER, clock.nowUtc(), OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_host_both.json"));
  }

  @Test
  public void testHost_both_summary() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForHost(
            hostResourceBoth,
            false,
            LINK_BASE,
            WHOIS_SERVER,
            clock.nowUtc(),
            OutputDataType.SUMMARY))
        .isEqualTo(loadJson("rdapjson_host_both_summary.json"));
  }

  @Test
  public void testHost_noAddresses() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForHost(
            hostResourceNoAddresses,
            false,
            LINK_BASE,
            WHOIS_SERVER,
            clock.nowUtc(),
            OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_host_no_addresses.json"));
  }

  @Test
  public void testRegistrant() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceRegistrant,
                false,
                Optional.of(DesignatedContact.Type.REGISTRANT),
                LINK_BASE,
                WHOIS_SERVER,
                clock.nowUtc(),
                OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_registrant.json"));
  }

  @Test
  public void testRegistrant_summary() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceRegistrant,
                false,
                Optional.of(DesignatedContact.Type.REGISTRANT),
                LINK_BASE,
                WHOIS_SERVER,
                clock.nowUtc(),
                OutputDataType.SUMMARY))
        .isEqualTo(loadJson("rdapjson_registrant_summary.json"));
  }

  @Test
  public void testRegistrant_baseHasNoTrailingSlash() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceRegistrant,
                false,
                Optional.of(DesignatedContact.Type.REGISTRANT),
                LINK_BASE_NO_TRAILING_SLASH,
                WHOIS_SERVER,
                clock.nowUtc(),
                OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_registrant.json"));
  }

  @Test
  public void testRegistrant_noBase() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceRegistrant,
                false,
                Optional.of(DesignatedContact.Type.REGISTRANT),
                null,
                WHOIS_SERVER,
                clock.nowUtc(),
                OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_registrant_nobase.json"));
  }

  @Test
  public void testAdmin() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceAdmin,
                false,
                Optional.of(DesignatedContact.Type.ADMIN),
                LINK_BASE,
                WHOIS_SERVER,
                clock.nowUtc(),
                OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_admincontact.json"));
  }

  @Test
  public void testTech() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceTech,
                false,
                Optional.of(DesignatedContact.Type.TECH),
                LINK_BASE,
                WHOIS_SERVER,
                clock.nowUtc(),
                OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_techcontact.json"));
  }

  @Test
  public void testRolelessContact() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceTech,
                false,
                Optional.<DesignatedContact.Type>absent(),
                LINK_BASE,
                WHOIS_SERVER,
                clock.nowUtc(),
                OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_rolelesscontact.json"));
  }

  @Test
  public void testDomain_full() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForDomain(
            domainResourceFull,
            false,
            LINK_BASE,
            WHOIS_SERVER,
            clock.nowUtc(),
            OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_domain_full.json"));
  }

  @Test
  public void testDomain_summary() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForDomain(
            domainResourceFull,
            false,
            LINK_BASE,
            WHOIS_SERVER,
            clock.nowUtc(),
            OutputDataType.SUMMARY))
        .isEqualTo(loadJson("rdapjson_domain_summary.json"));
  }

  @Test
  public void testDomain_noNameservers() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForDomain(
            domainResourceNoNameservers,
            false,
            LINK_BASE,
            WHOIS_SERVER,
            clock.nowUtc(),
            OutputDataType.FULL))
        .isEqualTo(loadJson("rdapjson_domain_no_nameservers.json"));
  }

  @Test
  public void testError() throws Exception {
    assertThat(
            RdapJsonFormatter
                .makeError(SC_BAD_REQUEST, "Invalid Domain Name", "Not a valid domain name"))
        .isEqualTo(loadJson("rdapjson_error.json"));
  }

  @Test
  public void testHelp_absoluteHtmlUrl() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonNotice(
            MakeRdapJsonNoticeParameters.builder()
                .title("RDAP Help")
                .description(ImmutableList.of(
                    "RDAP Help Topics (use /help/topic for information)",
                    "syntax",
                    "tos (Terms of Service)"))
                .linkValueSuffix("help/index")
                .linkHrefUrlString(LINK_BASE + "about/rdap/index.html")
                .build(),
            LINK_BASE))
        .isEqualTo(loadJson("rdapjson_notice_alternate_link.json"));
  }

  @Test
  public void testHelp_relativeHtmlUrlWithStartingSlash() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonNotice(
            MakeRdapJsonNoticeParameters.builder()
                .title("RDAP Help")
                .description(ImmutableList.of(
                    "RDAP Help Topics (use /help/topic for information)",
                    "syntax",
                    "tos (Terms of Service)"))
                .linkValueSuffix("help/index")
                .linkHrefUrlString("/about/rdap/index.html")
                .build(),
            LINK_BASE))
        .isEqualTo(loadJson("rdapjson_notice_alternate_link.json"));
  }

  @Test
  public void testHelp_relativeHtmlUrlWithoutStartingSlash() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonNotice(
            MakeRdapJsonNoticeParameters.builder()
                .title("RDAP Help")
                .description(ImmutableList.of(
                    "RDAP Help Topics (use /help/topic for information)",
                    "syntax",
                    "tos (Terms of Service)"))
                .linkValueSuffix("help/index")
                .linkHrefUrlString("about/rdap/index.html")
                .build(),
            LINK_BASE))
        .isEqualTo(loadJson("rdapjson_notice_alternate_link.json"));
  }

  @Test
  public void testHelp_noHtmlUrl() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonNotice(
            MakeRdapJsonNoticeParameters.builder()
                .title("RDAP Help")
                .description(ImmutableList.of(
                    "RDAP Help Topics (use /help/topic for information)",
                    "syntax",
                    "tos (Terms of Service)"))
                .linkValueSuffix("help/index")
                .build(),
            LINK_BASE))
        .isEqualTo(loadJson("rdapjson_notice_self_link.json"));
  }

  @Test
  public void testTopLevel() throws Exception {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    builder.put("key", "value");
    RdapJsonFormatter.addTopLevelEntries(
        builder,
        RdapJsonFormatter.BoilerplateType.OTHER,
        ImmutableList.<ImmutableMap<String, Object>>of(),
        ImmutableList.<ImmutableMap<String, Object>>of(),
        LINK_BASE);
    assertThat(builder.build()).isEqualTo(loadJson("rdapjson_toplevel.json"));
  }

  @Test
  public void testTopLevel_withTermsOfService() throws Exception {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    builder.put("key", "value");
    RdapJsonFormatter.addTopLevelEntries(
        builder,
        RdapJsonFormatter.BoilerplateType.OTHER,
        ImmutableList.of(RdapHelpAction.getJsonHelpNotice("/tos", LINK_BASE)),
        ImmutableList.<ImmutableMap<String, Object>>of(),
        LINK_BASE);
    assertThat(builder.build()).isEqualTo(loadJson("rdapjson_toplevel.json"));
  }

  @Test
  public void testTopLevel_domain() throws Exception {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    builder.put("key", "value");
    RdapJsonFormatter.addTopLevelEntries(
        builder,
        RdapJsonFormatter.BoilerplateType.DOMAIN,
        ImmutableList.<ImmutableMap<String, Object>>of(),
        ImmutableList.<ImmutableMap<String, Object>>of(),
        LINK_BASE);
    assertThat(builder.build()).isEqualTo(loadJson("rdapjson_toplevel_domain.json"));
  }

  @Test
  public void testTopLevel_domainWithTermsOfService() throws Exception {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    builder.put("key", "value");
    RdapJsonFormatter.addTopLevelEntries(
        builder,
        RdapJsonFormatter.BoilerplateType.DOMAIN,
        ImmutableList.of(RdapHelpAction.getJsonHelpNotice("/tos", LINK_BASE)),
        ImmutableList.<ImmutableMap<String, Object>>of(),
        LINK_BASE);
    assertThat(builder.build()).isEqualTo(loadJson("rdapjson_toplevel_domain.json"));
  }
}
