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

package google.registry.rdap;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistResources;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistHostResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeDomainResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeHostResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.googlecode.objectify.Key;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import javax.annotation.Nullable;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapNameserverSearchAction}. */
@RunWith(JUnit4.class)
public class RdapNameserverSearchActionTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Rule public final InjectRule inject = new InjectRule();

  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01T00:00:00Z"));

  private final RdapNameserverSearchAction action = new RdapNameserverSearchAction();

  private DomainResource domainCatLol;
  private HostResource hostNs1CatLol;
  private HostResource hostNs2CatLol;
  private HostResource hostNs1Cat2Lol;

  private Object generateActualJsonWithName(String name) {
    action.nameParam = Optional.of(name);
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private Object generateActualJsonWithIp(String ipString) {
    action.ipParam = Optional.of(InetAddresses.forString(ipString));
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  @Before
  public void setUp() throws Exception {
    // cat.lol and cat2.lol
    createTld("lol");
    Registrar registrar =
        persistResource(
            makeRegistrar("evilregistrar", "Yes Virginia <script>", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrar));
    hostNs1CatLol = makeAndPersistHostResource(
        "ns1.cat.lol", "1.2.3.4", clock.nowUtc().minusYears(1));
    hostNs2CatLol = makeAndPersistHostResource(
        "ns2.cat.lol", "bad:f00d:cafe::15:beef", clock.nowUtc().minusYears(1));
    hostNs1Cat2Lol = makeAndPersistHostResource(
        "ns1.cat2.lol", "1.2.3.3", "bad:f00d:cafe::15:beef", clock.nowUtc().minusYears(1));
    makeAndPersistHostResource("ns1.cat.external", null, null, clock.nowUtc().minusYears(1));

    // cat.みんな
    createTld("xn--q9jyb4c");
    registrar = persistResource(makeRegistrar("unicoderegistrar", "みんな", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrar));
    makeAndPersistHostResource("ns1.cat.みんな", "1.2.3.5", clock.nowUtc().minusYears(1));

    // cat.1.test
    createTld("1.test");
    registrar = persistResource(makeRegistrar("multiregistrar", "1.test", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrar));
    makeAndPersistHostResource("ns1.cat.1.test", "1.2.3.6", clock.nowUtc().minusYears(1));

    // create a domain so that we can use it as a test nameserver search string suffix
    domainCatLol = persistResource(
        makeDomainResource(
                "cat.lol",
                persistResource(
                    makeContactResource("5372808-ERL", "Goblin Market", "lol@cat.lol")),
                persistResource(
                    makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.lol")),
                persistResource(makeContactResource("5372808-TRL", "The Raven", "bog@cat.lol")),
                hostNs1CatLol,
                hostNs2CatLol,
                registrar)
            .asBuilder()
            .setSubordinateHosts(ImmutableSet.of("ns1.cat.lol", "ns2.cat.lol"))
            .build());
    persistResource(
        hostNs1CatLol.asBuilder().setSuperordinateDomain(Key.create(domainCatLol)).build());
    persistResource(
        hostNs2CatLol.asBuilder().setSuperordinateDomain(Key.create(domainCatLol)).build());

    inject.setStaticField(Ofy.class, "clock", clock);
    action.clock = clock;
    action.requestPath = RdapNameserverSearchAction.PATH;
    action.response = response;
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapResultSetMaxSize = 4;
    action.rdapLinkBase = "https://example.tld/rdap/";
    action.rdapWhoisServer = null;
    action.ipParam = Optional.absent();
    action.nameParam = Optional.absent();
  }

  private Object generateExpectedJson(String expectedOutputFile) {
    return generateExpectedJson(null, null, null, null, null, expectedOutputFile);
  }

  private Object generateExpectedJson(String name, String expectedOutputFile) {
    return generateExpectedJson(name, null, null, null, null, expectedOutputFile);
  }

  private Object generateExpectedJson(
      @Nullable String name,
      @Nullable String punycodeName,
      @Nullable String handle,
      @Nullable String ipAddressType,
      @Nullable String ipAddress,
      String expectedOutputFile) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    if (name != null) {
      builder.put("NAME", name);
    }
    if ((name != null) || (punycodeName != null)) {
      builder.put("PUNYCODENAME", (punycodeName == null) ? name : punycodeName);
    }
    if (handle != null) {
      builder.put("HANDLE", handle);
    }
    if (ipAddressType != null) {
      builder.put("ADDRESSTYPE", ipAddressType);
    }
    if (ipAddress != null) {
      builder.put("ADDRESS", ipAddress);
    }
    builder.put("TYPE", "nameserver");
    return JSONValue.parse(
        loadFileWithSubstitutions(this.getClass(), expectedOutputFile, builder.build()));
  }

  private Object generateExpectedJsonForNameserver(
      String name,
      String punycodeName,
      String handle,
      String ipAddressType,
      String ipAddress,
      String expectedOutputFile) {
    Object obj =
        generateExpectedJson(
            name, punycodeName, handle, ipAddressType, ipAddress, expectedOutputFile);
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("nameserverSearchResults", ImmutableList.of(obj));
    builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
    RdapTestHelper.addTermsOfServiceNotice(builder, "https://example.tld/rdap/");
    RdapTestHelper.addNonDomainBoilerplateRemarks(builder);
    return builder.build();
  }

  private void createManyHosts(int numHosts) {
    ImmutableList.Builder<HostResource> hostsBuilder = new ImmutableList.Builder<>();
    ImmutableSet.Builder<String> subordinateHostsBuilder = new ImmutableSet.Builder<>();
    for (int i = 1; i <= numHosts; i++) {
      String hostName = String.format("ns%d.cat.lol", i);
      subordinateHostsBuilder.add(hostName);
      hostsBuilder.add(makeHostResource(hostName, "5.5.5.1", "5.5.5.2"));
    }
    persistResources(hostsBuilder.build());
    domainCatLol = persistResource(
        domainCatLol.asBuilder()
            .setSubordinateHosts(subordinateHostsBuilder.build())
            .build());
  }

  @Test
  public void testInvalidPath_rejected() throws Exception {
    action.requestPath = RdapDomainSearchAction.PATH + "/path";
    action.run();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testInvalidRequest_rejected() throws Exception {
    action.run();
    assertThat(JSONValue.parse(response.getPayload()))
        .isEqualTo(
            generateExpectedJson(
                "You must specify either name=XXXX or ip=YYYY", "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testInvalidSuffix_rejected() throws Exception {
    assertThat(generateActualJsonWithName("exam*ple"))
        .isEqualTo(
            generateExpectedJson(
                "Suffix after wildcard must be one or more domain"
                    + " name labels, e.g. exam*.tld, ns*.example.tld",
                "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNonexistentDomainSuffix_notFound() throws Exception {
    assertThat(generateActualJsonWithName("exam*.foo.bar"))
        .isEqualTo(
            generateExpectedJson(
                "No domain found for specified nameserver suffix", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testMultipleWildcards_rejected() throws Exception {
    assertThat(generateActualJsonWithName("*.*"))
        .isEqualTo(generateExpectedJson("Only one wildcard allowed", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testFewerThanTwoCharactersToMatch_rejected() throws Exception {
    assertThat(generateActualJsonWithName("a*"))
        .isEqualTo(
            generateExpectedJson(
                "At least two characters must be specified", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNameMatch_ns1_cat_lol_found() throws Exception {
    assertThat(generateActualJsonWithName("ns1.cat.lol"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.lol", null, "2-ROID", "v4", "1.2.3.4", "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_ns2_cat_lol_found() throws Exception {
    assertThat(generateActualJsonWithName("ns2.cat.lol"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns2.cat.lol", null, "4-ROID", "v6", "bad:f00d:cafe::15:beef", "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_ns1_cat2_lol_found() throws Exception {
    // ns1.cat2.lol has two IP addresses; just test that we are able to find it
    generateActualJsonWithName("ns1.cat2.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_ns1_cat_external_found() throws Exception {
    assertThat(generateActualJsonWithName("ns1.cat.external"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.external", null, "8-ROID", null, null, "rdap_host_external.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_ns1_cat_idn_unicode_badRequest() throws Exception {
    // name must use punycode.
    generateActualJsonWithName("ns1.cat.みんな");
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testNameMatch_ns1_cat_idn_punycode_found() throws Exception {
    assertThat(generateActualJsonWithName("ns1.cat.xn--q9jyb4c"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.みんな",
                "ns1.cat.xn--q9jyb4c",
                "B-ROID",
                "v4",
                "1.2.3.5",
                "rdap_host_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_ns1_cat_1_test_found() throws Exception {
    assertThat(generateActualJsonWithName("ns1.cat.1.test"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.1.test", null, "E-ROID", "v4", "1.2.3.6", "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_nsstar_cat_lol_found() throws Exception {
    generateActualJsonWithName("ns*.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_nsstar_found() throws Exception {
    generateActualJsonWithName("ns*");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_ns1_cat_lstar_found() throws Exception {
    generateActualJsonWithName("ns1.cat.l*");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_ns1_castar_found() throws Exception {
    generateActualJsonWithName("ns1.ca*");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_dogstar_notFound() throws Exception {
    generateActualJsonWithName("dog*");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameMatch_nontruncatedResultSet() throws Exception {
    createManyHosts(4);
    assertThat(generateActualJsonWithName("ns*.cat.lol"))
      .isEqualTo(generateExpectedJson("rdap_nontruncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_truncatedResultSet() throws Exception {
    createManyHosts(5);
    assertThat(generateActualJsonWithName("ns*.cat.lol"))
      .isEqualTo(generateExpectedJson("rdap_truncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_reallyTruncatedResultSet() throws Exception {
    createManyHosts(9);
    assertThat(generateActualJsonWithName("ns*.cat.lol"))
      .isEqualTo(generateExpectedJson("rdap_truncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatchDeletedHost_foundTheOtherHost() throws Exception {
    persistResource(
        hostNs1Cat2Lol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJsonWithIp("bad:f00d:cafe::15:beef"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns2.cat.lol",
                null,
                "4-ROID",
                "v6",
                "bad:f00d:cafe::15:beef",
                "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchV4Address_found() throws Exception {
    assertThat(generateActualJsonWithIp("1.2.3.4"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.lol", null, "2-ROID", "v4", "1.2.3.4", "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchV6Address_foundMultiple() throws Exception {
    assertThat(generateActualJsonWithIp("bad:f00d:cafe::15:beef"))
        .isEqualTo(generateExpectedJson("rdap_multiple_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchLocalhost_notFound() throws Exception {
    generateActualJsonWithIp("127.0.0.1");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameMatchDeletedHost_notFound() throws Exception {
    persistResource(hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJsonWithName("ns1.cat.lol"))
        .isEqualTo(generateExpectedJson("No nameservers found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameMatchDeletedHostWithWildcard_notFound() throws Exception {
    persistResource(hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJsonWithName("cat.lo*"))
        .isEqualTo(generateExpectedJson("No nameservers found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testAddressMatchDeletedHost_notFound() throws Exception {
    persistResource(hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJsonWithIp("1.2.3.4"))
        .isEqualTo(generateExpectedJson("No nameservers found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testAddressMatch_nontruncatedResultSet() throws Exception {
    createManyHosts(4);
    assertThat(generateActualJsonWithIp("5.5.5.1"))
      .isEqualTo(generateExpectedJson("rdap_nontruncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatch_truncatedResultSet() throws Exception {
    createManyHosts(5);
    assertThat(generateActualJsonWithIp("5.5.5.1"))
      .isEqualTo(generateExpectedJson("rdap_truncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatch_reallyTruncatedResultSet() throws Exception {
    createManyHosts(9);
    assertThat(generateActualJsonWithIp("5.5.5.1"))
      .isEqualTo(generateExpectedJson("rdap_truncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }
}
