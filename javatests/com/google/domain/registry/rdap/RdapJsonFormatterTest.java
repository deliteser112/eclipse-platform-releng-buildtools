// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.rdap;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistSimpleGlobalResources;
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeContactResource;
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeDomainResource;
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeHostResource;
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static com.google.domain.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DesignatedContact;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.rdap.RdapJsonFormatter.MakeRdapJsonNoticeParameters;
import com.google.domain.registry.testing.AppEngineRule;

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

  private Registrar registrar;
  private DomainResource domainResourceFull;
  private DomainResource domainResourceNoRegistrant;
  private DomainResource domainResourceNoContacts;
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
  private static final String WHOIS_SERVER = "whois.google.com";

  @Before
  public void setUp() throws Exception {
    createTld("xn--q9jyb4c", TldState.GENERAL_AVAILABILITY);
    registrar = persistResource(makeRegistrar("unicoderegistrar", "みんな", Registrar.State.ACTIVE));
    persistSimpleGlobalResources(makeMoreRegistrarContacts(registrar));
    contactResourceRegistrant = persistResource(
        makeContactResource(
            "8372808-ERL",
            "(◕‿◕)",
            "lol@cat.みんな",
            null));
    contactResourceAdmin = persistResource(
        makeContactResource(
            "8372808-IRL",
            "Santa Claus",
            null,
            ImmutableList.of("Santa Claus Tower", "41st floor", "Suite みんな")));
    contactResourceTech = persistResource(
        makeContactResource(
            "8372808-TRL",
            "The Raven",
            "bog@cat.みんな",
            ImmutableList.of("Chamber Door", "upper level")));
    hostResourceIpv4 = persistResource(makeHostResource("ns1.cat.みんな", "1.2.3.4"));
    hostResourceIpv6 =
        persistResource(makeHostResource("ns2.cat.みんな", "bad:f00d:cafe:0:0:0:15:beef"));
    hostResourceBoth =
        persistResource(makeHostResource("ns3.cat.みんな", "1.2.3.4", "bad:f00d:cafe:0:0:0:15:beef"));
    hostResourceNoAddresses = persistResource(makeHostResource("ns4.cat.みんな", null));
    domainResourceFull = persistResource(
        makeDomainResource(
            "cat.みんな",
            contactResourceRegistrant,
            contactResourceAdmin,
            contactResourceTech,
            hostResourceIpv4,
            hostResourceIpv6,
            registrar));
    domainResourceNoRegistrant = persistResource(
        makeDomainResource(
            "dog.みんな",
            null,
            contactResourceAdmin,
            contactResourceTech,
            hostResourceBoth,
            hostResourceNoAddresses,
            registrar));
    domainResourceNoContacts = persistResource(
        makeDomainResource(
            "bird.みんな",
            null,
            null,
            null,
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
    assertThat(RdapJsonFormatter.makeRdapJsonForRegistrar(registrar, LINK_BASE, WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_registrar.json"));
  }

  @Test
  public void testHost_ipv4() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForHost(hostResourceIpv4, LINK_BASE, WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_host_ipv4.json"));
  }

  @Test
  public void testHost_ipv6() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForHost(hostResourceIpv6, LINK_BASE, WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_host_ipv6.json"));
  }

  @Test
  public void testHost_both() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForHost(hostResourceBoth, LINK_BASE, WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_host_both.json"));
  }

  @Test
  public void testHost_noAddresses() throws Exception {
    assertThat(RdapJsonFormatter.makeRdapJsonForHost(
            hostResourceNoAddresses, LINK_BASE, WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_host_no_addresses.json"));
  }

  @Test
  public void testRegistrant() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceRegistrant,
                Optional.of(DesignatedContact.Type.REGISTRANT),
                LINK_BASE,
                WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_registrant.json"));
  }

  @Test
  public void testRegistrant_baseHasNoTrailingSlash() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceRegistrant,
                Optional.of(DesignatedContact.Type.REGISTRANT),
                LINK_BASE_NO_TRAILING_SLASH,
                WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_registrant.json"));
  }

  @Test
  public void testRegistrant_noBase() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceRegistrant,
                Optional.of(DesignatedContact.Type.REGISTRANT),
                null,
                WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_registrant_nobase.json"));
  }

  @Test
  public void testAdmin() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceAdmin,
                Optional.of(DesignatedContact.Type.ADMIN),
                LINK_BASE,
                WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_admincontact.json"));
  }

  @Test
  public void testTech() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForContact(
                contactResourceTech,
                Optional.of(DesignatedContact.Type.TECH),
                LINK_BASE,
                WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_techcontact.json"));
  }

  @Test
  public void testDomain_full() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForDomain(domainResourceFull, LINK_BASE, WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_domain_full.json"));
  }

  @Test
  public void testDomain_noRegistrant() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForDomain(
                domainResourceNoRegistrant, LINK_BASE, WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_domain_no_registrant.json"));
  }

  @Test
  public void testDomain_noContacts() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForDomain(
                domainResourceNoContacts, LINK_BASE, WHOIS_SERVER))
        .isEqualTo(loadJson("rdapjson_domain_no_contacts.json"));
  }

  @Test
  public void testDomain_noNameservers() throws Exception {
    assertThat(
            RdapJsonFormatter.makeRdapJsonForDomain(
                domainResourceNoNameservers, LINK_BASE, WHOIS_SERVER))
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
    assertThat(
            RdapJsonFormatter.makeFinalRdapJson(ImmutableMap.<String, Object>of("key", "value")))
        .isEqualTo(loadJson("rdapjson_toplevel.json"));
  }
}
