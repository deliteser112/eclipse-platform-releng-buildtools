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

package google.registry.rdap;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.request.Action.Method.GET;
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
import static google.registry.testing.TestDataHelper.loadFile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.model.registrar.Registrar;
import google.registry.rdap.RdapMetrics.EndpointType;
import google.registry.rdap.RdapMetrics.SearchType;
import google.registry.rdap.RdapMetrics.WildcardType;
import google.registry.rdap.RdapSearchResults.IncompletenessWarningType;
import google.registry.testing.FakeResponse;
import java.net.URLDecoder;
import java.util.Optional;
import javax.annotation.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapNameserverSearchAction}. */
@RunWith(JUnit4.class)
public class RdapNameserverSearchActionTest
    extends RdapSearchActionTestCase<RdapNameserverSearchAction> {

  public RdapNameserverSearchActionTest() {
    super(RdapNameserverSearchAction.class, RdapNameserverSearchAction.PATH);
  }

  private DomainResource domainCatLol;
  private HostResource hostNs1CatLol;
  private HostResource hostNs2CatLol;

  private Object generateActualJsonWithName(String name) {
    return generateActualJsonWithName(name, null);
  }

  private Object generateActualJsonWithName(String name, String cursor) {
    metricSearchType = SearchType.BY_NAMESERVER_NAME;
    rememberWildcardType(name);
    action.nameParam = Optional.of(name);
    if (cursor == null) {
      action.parameterMap = ImmutableListMultimap.of("name", name);
      action.cursorTokenParam = Optional.empty();
    } else {
      action.parameterMap = ImmutableListMultimap.of("name", name, "cursor", cursor);
      action.cursorTokenParam = Optional.of(cursor);
    }
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private Object generateActualJsonWithIp(String ipString) {
    return generateActualJsonWithIp(ipString, null);
  }

  private Object generateActualJsonWithIp(String ipString, String cursor) {
    metricSearchType = SearchType.BY_NAMESERVER_ADDRESS;
    action.parameterMap = ImmutableListMultimap.of("ip", ipString);
    action.ipParam = Optional.of(ipString);
    if (cursor == null) {
      action.parameterMap = ImmutableListMultimap.of("ip", ipString);
      action.cursorTokenParam = Optional.empty();
    } else {
      action.parameterMap = ImmutableListMultimap.of("ip", ipString, "cursor", cursor);
      action.cursorTokenParam = Optional.of(cursor);
    }
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  @Before
  public void setUp() {
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
    makeAndPersistHostResource(
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
                    makeContactResource("5372808-ERL", "Goblin Market", "lol@cat.lol", registrar)),
                persistResource(
                    makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.lol", registrar)),
                persistResource(
                    makeContactResource("5372808-TRL", "The Raven", "bog@cat.lol", registrar)),
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

    action.ipParam = Optional.empty();
    action.nameParam = Optional.empty();
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
    builder.put("STATUS", "active");
    builder.put("TYPE", "nameserver");
    return JSONValue.parse(
        loadFile(this.getClass(), expectedOutputFile, builder.build()));
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
    RdapTestHelper.addNonDomainBoilerplateNotices(
        builder, RdapTestHelper.createNotices("https://example.tld/rdap/"));
    return builder.build();
  }

  private void createManyHosts(int numHosts) {
    ImmutableList.Builder<HostResource> hostsBuilder = new ImmutableList.Builder<>();
    ImmutableSet.Builder<String> subordinateHostsBuilder = new ImmutableSet.Builder<>();
    for (int i = 1; i <= numHosts; i++) {
      String hostName = String.format("nsx%d.cat.lol", i);
      subordinateHostsBuilder.add(hostName);
      hostsBuilder.add(makeHostResource(hostName, "5.5.5.1", "5.5.5.2"));
    }
    persistResources(hostsBuilder.build());
    domainCatLol = persistResource(
        domainCatLol.asBuilder()
            .setSubordinateHosts(subordinateHostsBuilder.build())
            .build());
  }

  private void createDeletedHost() {
    persistResource(
        makeAndPersistHostResource("nsdeleted.cat.lol", "4.3.2.1", clock.nowUtc().minusYears(1))
            .asBuilder()
            .setDeletionTime(clock.nowUtc().minusMonths(1))
            .build());
  }

  private void verifyMetrics(long numHostsRetrieved) {
    verifyMetrics(Optional.of(numHostsRetrieved), IncompletenessWarningType.COMPLETE);
  }

  private void verifyMetrics(
      Optional<Long> numHostsRetrieved, IncompletenessWarningType incompletenessWarningType) {
    verifyMetrics(
        EndpointType.NAMESERVERS,
        GET,
        action.includeDeletedParam.orElse(false),
        action.registrarParam.isPresent(),
        Optional.empty(),
        numHostsRetrieved,
        Optional.empty(),
        incompletenessWarningType);
  }

  private void verifyErrorMetrics() {
    metricStatusCode = 404;
    verifyMetrics(0);
  }

  private void verifyErrorMetrics(Optional<Long> numHostsRetrieved, int statusCode) {
    metricStatusCode = statusCode;
    verifyMetrics(numHostsRetrieved, IncompletenessWarningType.COMPLETE);
  }

  @Test
  public void testInvalidPath_rejected() {
    action.requestPath = RdapDomainSearchAction.PATH + "/path";
    action.run();
    assertThat(response.getStatus()).isEqualTo(400);
    verifyErrorMetrics(Optional.empty(), 400);
  }

  @Test
  public void testInvalidRequest_rejected() {
    action.run();
    assertThat(JSONValue.parse(response.getPayload()))
        .isEqualTo(
            generateExpectedJson(
                "You must specify either name=XXXX or ip=YYYY", "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
    verifyErrorMetrics(Optional.empty(), 400);
  }

  @Test
  public void testInvalidSuffix_rejected() {
    assertThat(generateActualJsonWithName("exam*ple"))
        .isEqualTo(
            generateExpectedJson(
                "Suffix after wildcard must be one or more domain"
                    + " name labels, e.g. exam*.tld, ns*.example.tld",
                "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
    verifyErrorMetrics(Optional.empty(), 422);
  }

  @Test
  public void testNonexistentDomainSuffix_unprocessable() {
    assertThat(generateActualJsonWithName("exam*.foo.bar"))
        .isEqualTo(
            generateExpectedJson(
                "A suffix after a wildcard in a nameserver lookup must be an in-bailiwick domain",
                "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
    verifyErrorMetrics(Optional.empty(), 422);
  }

  @Test
  public void testMultipleWildcards_rejected() {
    assertThat(generateActualJsonWithName("*.*"))
        .isEqualTo(generateExpectedJson("Only one wildcard allowed", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
    verifyErrorMetrics(Optional.empty(), 422);
  }

  @Test
  public void testNoCharactersToMatch_rejected() {
    assertThat(generateActualJsonWithName("*"))
        .isEqualTo(
            generateExpectedJson(
                "Initial search string must be at least 2 characters", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
    verifyErrorMetrics(Optional.empty(), 422);
  }

  @Test
  public void testFewerThanTwoCharactersToMatch_rejected() {
    assertThat(generateActualJsonWithName("a*"))
        .isEqualTo(
            generateExpectedJson(
                "Initial search string must be at least 2 characters", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
    verifyErrorMetrics(Optional.empty(), 422);
  }

  @Test
  public void testNameMatch_ns1_cat_lol_found() {
    assertThat(generateActualJsonWithName("ns1.cat.lol"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.lol", null, "2-ROID", "v4", "1.2.3.4", "rdap_host_linked.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatch_ns1_cat_lol_foundWithUpperCase() {
    assertThat(generateActualJsonWithName("Ns1.CaT.lOl"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.lol", null, "2-ROID", "v4", "1.2.3.4", "rdap_host_linked.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatch_ns1_cat_lol_found_sameRegistrarRequested() {
    action.registrarParam = Optional.of("TheRegistrar");
    generateActualJsonWithName("ns1.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatch_ns1_cat_lol_notFound_differentRegistrarRequested() {
    action.registrarParam = Optional.of("unicoderegistrar");
    generateActualJsonWithName("ns1.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics(Optional.of(0L), 404);
  }

  @Test
  public void testNameMatch_ns2_cat_lol_found() {
    assertThat(generateActualJsonWithName("ns2.cat.lol"))
        .isEqualTo(generateExpectedJsonForNameserver(
                "ns2.cat.lol",
                null,
                "4-ROID",
                "v6",
                "bad:f00d:cafe::15:beef",
                "rdap_host_linked.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatch_ns1_cat2_lol_found() {
    // ns1.cat2.lol has two IP addresses; just test that we are able to find it
    generateActualJsonWithName("ns1.cat2.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatch_ns1_cat_external_found() {
    assertThat(generateActualJsonWithName("ns1.cat.external"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.external", null, "8-ROID", null, null, "rdap_host_external.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatch_ns1_cat_idn_unicode_badRequest() {
    // name must use punycode.
    generateActualJsonWithName("ns1.cat.みんな");
    metricWildcardType = WildcardType.INVALID;
    metricPrefixLength = 0;
    assertThat(response.getStatus()).isEqualTo(400);
    verifyErrorMetrics(Optional.empty(), 400);
  }

  @Test
  public void testNameMatch_ns1_cat_idn_punycode_found() {
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
    verifyMetrics(1);
  }

  @Test
  public void testNameMatch_ns1_cat_1_test_found() {
    assertThat(generateActualJsonWithName("ns1.cat.1.test"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.1.test", null, "E-ROID", "v4", "1.2.3.6", "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatch_nsstar_cat_lol_found() {
    generateActualJsonWithName("ns*.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(2);
  }

  @Test
  public void testNameMatch_nsstar_cat_lol_found_sameRegistrarRequested() {
    action.registrarParam = Optional.of("TheRegistrar");
    generateActualJsonWithName("ns*.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(2);
  }

  @Test
  public void testNameMatch_nsstar_cat_lol_notFound_differentRegistrarRequested() {
    action.registrarParam = Optional.of("unicoderegistrar");
    generateActualJsonWithName("ns*.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics(Optional.of(2L), 404);
  }

  @Test
  public void testNameMatch_nstar_cat_lol_found() {
    generateActualJsonWithName("n*.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(2);
  }

  @Test
  public void testNameMatch_star_cat_lol_found() {
    generateActualJsonWithName("*.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(2);
  }

  @Test
  public void testNameMatch_star_cat_lol_found_sameRegistrarRequested() {
    action.registrarParam = Optional.of("TheRegistrar");
    generateActualJsonWithName("*.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(2);
  }

  @Test
  public void testNameMatch_star_cat_lol_notFound_differentRegistrarRequested() {
    action.registrarParam = Optional.of("unicoderegistrar");
    generateActualJsonWithName("*.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics(Optional.of(2L), 404);
  }

  @Test
  public void testNameMatch_nsstar_found() {
    generateActualJsonWithName("ns*");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(5);
  }

  @Test
  public void testNameMatch_ns1_cat_lstar_found() {
    generateActualJsonWithName("ns1.cat.l*");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatch_ns1_castar_found() {
    generateActualJsonWithName("ns1.ca*");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(5);
  }

  @Test
  public void testNameMatch_dogstar_notFound() {
    generateActualJsonWithName("dog*");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testNameMatch_nontruncatedResultSet() {
    createManyHosts(4);
    assertThat(generateActualJsonWithName("nsx*.cat.lol"))
      .isEqualTo(generateExpectedJson("rdap_nontruncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(4);
  }

  @Test
  public void testNameMatch_truncatedResultSet() {
    createManyHosts(5);
    assertThat(generateActualJsonWithName("nsx*.cat.lol"))
        .isEqualTo(
            generateExpectedJson(
                "name=nsx*.cat.lol&cursor=bnN4NC5jYXQubG9s", "rdap_truncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(5);
  }

  @Test
  public void testNameMatch_reallyTruncatedResultSet() {
    createManyHosts(9);
    assertThat(generateActualJsonWithName("nsx*.cat.lol"))
        .isEqualTo(
            generateExpectedJson(
                "name=nsx*.cat.lol&cursor=bnN4NC5jYXQubG9s", "rdap_truncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    // When searching names, we look for additional matches, in case some are not visible.
    verifyMetrics(9);
  }

  @Test
  public void testNameMatchDeletedHost_foundTheOtherHost() {
    persistResource(
        hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJsonWithName("ns*.cat.lol"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns2.cat.lol",
                null,
                "4-ROID",
                "v6",
                "bad:f00d:cafe::15:beef",
                "rdap_host_linked.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(2);
  }

  @Test
  public void testNameMatchDeletedHost_notFound() {
    persistResource(hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJsonWithName("ns1.cat.lol"))
        .isEqualTo(generateExpectedJson("No nameservers found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testNameMatchDeletedHostWithWildcard_notFound() {
    persistResource(hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJsonWithName("cat.lo*"))
        .isEqualTo(generateExpectedJson("No nameservers found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testNameMatchDeleted_notFound_includeDeletedNotSpecified() {
    createDeletedHost();
    generateActualJsonWithName("nsdeleted.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testNameMatchDeleted_notFound_notLoggedIn() {
    createDeletedHost();
    action.includeDeletedParam = Optional.of(true);
    generateActualJsonWithName("nsdeleted.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testNameMatchDeleted_notFound_loggedInAsDifferentRegistrar() {
    createDeletedHost();
    action.includeDeletedParam = Optional.of(true);
    login("unicoderegistrar");
    generateActualJsonWithName("nsdeleted.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics(Optional.of(1L), 404);
  }

  @Test
  public void testNameMatchDeleted_found_loggedInAsCorrectRegistrar() {
    createDeletedHost();
    action.includeDeletedParam = Optional.of(true);
    login("TheRegistrar");
    generateActualJsonWithName("nsdeleted.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatchDeleted_found_loggedInAsAdmin() {
    createDeletedHost();
    action.includeDeletedParam = Optional.of(true);
    loginAsAdmin();
    generateActualJsonWithName("nsdeleted.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatchDeleted_found_loggedInAndRequestingSameRegistrar() {
    createDeletedHost();
    action.registrarParam = Optional.of("TheRegistrar");
    action.includeDeletedParam = Optional.of(true);
    login("TheRegistrar");
    generateActualJsonWithName("nsdeleted.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testNameMatchDeleted_notFound_loggedInButRequestingDifferentRegistrar() {
    createDeletedHost();
    action.registrarParam = Optional.of("unicoderegistrar");
    action.includeDeletedParam = Optional.of(true);
    login("TheRegistrar");
    generateActualJsonWithName("nsdeleted.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  /**
   * Checks multi-page result set navigation using the cursor.
   *
   * <p>If there are more results than the max result set size, the RDAP code returns a cursor token
   * which can be used in a subsequent call to get the next chunk of results.
   *
   * @param byName true if we are searching by name; false if we are searching by address
   * @param paramValue the query string
   * @param expectedNames an immutable list of the host names we expect to retrieve
   */
  private void checkCursorNavigation(
      boolean byName, String paramValue, ImmutableList<String> expectedNames)
      throws Exception {
    String cursor = null;
    int expectedNameOffset = 0;
    int expectedPageCount =
        (expectedNames.size() + action.rdapResultSetMaxSize - 1) / action.rdapResultSetMaxSize;
    for (int pageNum = 0; pageNum < expectedPageCount; pageNum++) {
      Object results =
          byName
              ? generateActualJsonWithName(paramValue, cursor)
              : generateActualJsonWithIp(paramValue, cursor);
      assertThat(response.getStatus()).isEqualTo(200);
      String linkToNext = RdapTestHelper.getLinkToNext(results);
      if (pageNum == expectedPageCount - 1) {
        assertThat(linkToNext).isNull();
      } else {
        assertThat(linkToNext).isNotNull();
        int pos = linkToNext.indexOf("cursor=");
        assertThat(pos).isAtLeast(0);
        cursor = URLDecoder.decode(linkToNext.substring(pos + 7), "UTF-8");
        Object searchResults = ((JSONObject) results).get("nameserverSearchResults");
        assertThat(searchResults).isInstanceOf(JSONArray.class);
        assertThat(((JSONArray) searchResults)).hasSize(action.rdapResultSetMaxSize);
        for (Object item : ((JSONArray) searchResults)) {
          assertThat(item).isInstanceOf(JSONObject.class);
          Object name = ((JSONObject) item).get("ldhName");
          assertThat(name).isNotNull();
          assertThat(name).isInstanceOf(String.class);
          assertThat(name).isEqualTo(expectedNames.get(expectedNameOffset++));
        }
        response = new FakeResponse();
        action.response = response;
      }
    }
  }

  @Test
  public void testNameMatch_cursorNavigationWithSuperordinateDomain() throws Exception {
    createManyHosts(9);
    checkCursorNavigation(
        true,
        "ns*.cat.lol",
        ImmutableList.of(
            "nsx1.cat.lol",
            "nsx2.cat.lol",
            "nsx3.cat.lol",
            "nsx4.cat.lol",
            "nsx5.cat.lol",
            "nsx6.cat.lol",
            "nsx7.cat.lol",
            "nsx8.cat.lol",
            "nsx9.cat.lol"));
  }

  @Test
  public void testNameMatch_cursorNavigationWithPrefix() throws Exception {
    createManyHosts(9);
    checkCursorNavigation(
        true,
        "ns*",
        ImmutableList.of(
            "ns1.cat.1.test",
            "ns1.cat.external",
            "ns1.cat.lol",
            "ns1.cat.xn--q9jyb4c",
            "ns1.cat2.lol",
            "ns2.cat.lol",
            "nsx1.cat.lol",
            "nsx2.cat.lol",
            "nsx3.cat.lol",
            "nsx4.cat.lol",
            "nsx5.cat.lol",
            "nsx6.cat.lol",
            "nsx7.cat.lol",
            "nsx8.cat.lol",
            "nsx9.cat.lol"));
  }

  @Test
  public void testAddressMatch_invalidAddress() {
    generateActualJsonWithIp("It is to laugh");
    assertThat(response.getStatus()).isEqualTo(400);
    verifyErrorMetrics(Optional.empty(), 400);
  }

  @Test
  public void testAddressMatchV4Address_found() {
    assertThat(generateActualJsonWithIp("1.2.3.4"))
        .isEqualTo(
            generateExpectedJsonForNameserver(
                "ns1.cat.lol", null, "2-ROID", "v4", "1.2.3.4", "rdap_host_linked.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testAddressMatchV4Address_found_sameRegistrarRequested() {
    action.registrarParam = Optional.of("TheRegistrar");
    generateActualJsonWithIp("1.2.3.4");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testAddressMatchV4Address_notFound_differentRegistrarRequested() {
    action.registrarParam = Optional.of("unicoderegistrar");
    generateActualJsonWithIp("1.2.3.4");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testAddressMatchV6Address_foundMultiple() {
    assertThat(generateActualJsonWithIp("bad:f00d:cafe::15:beef"))
        .isEqualTo(generateExpectedJson("rdap_multiple_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(2);
  }

  @Test
  public void testAddressMatchLocalhost_notFound() {
    generateActualJsonWithIp("127.0.0.1");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testAddressMatchDeletedHost_notFound() {
    persistResource(hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJsonWithIp("1.2.3.4"))
        .isEqualTo(generateExpectedJson("No nameservers found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testAddressMatch_nontruncatedResultSet() {
    createManyHosts(4);
    assertThat(generateActualJsonWithIp("5.5.5.1"))
        .isEqualTo(generateExpectedJson("rdap_nontruncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(4);
  }

  @Test
  public void testAddressMatch_truncatedResultSet() {
    createManyHosts(5);
    assertThat(generateActualJsonWithIp("5.5.5.1"))
    .isEqualTo(
        generateExpectedJson(
            "ip=5.5.5.1&cursor=MTctUk9JRA%3D%3D", "rdap_truncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(5);
  }

  @Test
  public void testAddressMatch_reallyTruncatedResultSet() {
    createManyHosts(9);
    assertThat(generateActualJsonWithIp("5.5.5.1"))
        .isEqualTo(
            generateExpectedJson(
                "ip=5.5.5.1&cursor=MTctUk9JRA%3D%3D", "rdap_truncated_hosts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
    // When searching by address and not including deleted, we don't need to search for extra
    // matches.
    verifyMetrics(5);
  }

  @Test
  public void testAddressMatchDeleted_notFound_includeDeletedNotSpecified() {
    createDeletedHost();
    generateActualJsonWithIp("4.3.2.1");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testAddressMatchDeleted_notFound_notLoggedIn() {
    createDeletedHost();
    action.includeDeletedParam = Optional.of(true);
    generateActualJsonWithIp("4.3.2.1");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testAddressMatchDeleted_notFound_loggedInAsDifferentRegistrar() {
    createDeletedHost();
    action.includeDeletedParam = Optional.of(true);
    login("unicoderegistrar");
    generateActualJsonWithIp("4.3.2.1");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics(Optional.of(1L), 404);
  }

  @Test
  public void testAddressMatchDeleted_found_loggedInAsCorrectRegistrar() {
    createDeletedHost();
    action.includeDeletedParam = Optional.of(true);
    login("TheRegistrar");
    generateActualJsonWithIp("4.3.2.1");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testAddressMatchDeleted_found_loggedInAsAdmin() {
    createDeletedHost();
    action.includeDeletedParam = Optional.of(true);
    loginAsAdmin();
    generateActualJsonWithIp("4.3.2.1");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testAddressMatchDeleted_found_loggedInAndRequestingSameRegisrar() {
    createDeletedHost();
    action.registrarParam = Optional.of("TheRegistrar");
    action.includeDeletedParam = Optional.of(true);
    login("TheRegistrar");
    generateActualJsonWithIp("4.3.2.1");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyMetrics(1);
  }

  @Test
  public void testAddressMatchDeleted_notFound_loggedButRequestingDiffentRegistrar() {
    createDeletedHost();
    action.registrarParam = Optional.of("unicoderegistrar");
    action.includeDeletedParam = Optional.of(true);
    login("TheRegistrar");
    generateActualJsonWithIp("4.3.2.1");
    assertThat(response.getStatus()).isEqualTo(404);
    verifyErrorMetrics();
  }

  @Test
  public void testAddressMatch_cursorNavigation() throws Exception {
    createManyHosts(9);
    checkCursorNavigation(
        false,
        "5.5.5.1",
        ImmutableList.of(
            "nsx1.cat.lol",
            "nsx2.cat.lol",
            "nsx3.cat.lol",
            "nsx4.cat.lol",
            "nsx5.cat.lol",
            "nsx6.cat.lol",
            "nsx7.cat.lol",
            "nsx8.cat.lol",
            "nsx9.cat.lol"));
  }
}
