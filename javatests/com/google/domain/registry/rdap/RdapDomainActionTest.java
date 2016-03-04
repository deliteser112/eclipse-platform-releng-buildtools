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
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static com.google.domain.registry.testing.TestDataHelper.loadFileWithSubstitutions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.InjectRule;

import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

/** Unit tests for {@link RdapDomainAction}. */
@RunWith(JUnit4.class)
public class RdapDomainActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));

  private RdapDomainAction action;

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    // lol
    createTld("lol");
    Registrar registrarLol = persistResource(makeRegistrar(
        "evilregistrar", "Yes Virginia <script>", Registrar.State.ACTIVE));
    persistSimpleGlobalResources(makeRegistrarContacts(registrarLol));
    ContactResource registrant =
        persistResource(makeContactResource("5372808-ERL", "Goblin Market", "lol@cat.lol"));
    ContactResource adminContact =
        persistResource(makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.lol"));
    ContactResource techContact =
        persistResource(makeContactResource("5372808-TRL", "The Raven", "bog@cat.lol"));
    HostResource host1 =
        persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    HostResource host2 =
        persistResource(makeHostResource("ns2.cat.lol", "bad:f00d:cafe:0:0:0:15:beef"));
    persistResource(makeDomainResource("cat.lol",
        registrant,
        adminContact,
        techContact,
        host1,
        host2,
        registrarLol));
    // deleted domain in lol
    persistResource(makeDomainResource("dodo.lol",
        registrant,
        adminContact,
        techContact,
        host1,
        host2,
        registrarLol).asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    // xn--q9jyb4c
    createTld("xn--q9jyb4c");
    Registrar registrarIdn =
        persistResource(makeRegistrar("idnregistrar", "IDN Registrar", Registrar.State.ACTIVE));
    persistSimpleGlobalResources(makeRegistrarContacts(registrarIdn));
    persistResource(makeDomainResource("cat.みんな",
        registrant,
        adminContact,
        techContact,
        host1,
        host2,
        registrarIdn));
    createTld("1.tld");
    Registrar registrar1tld = persistResource(
        makeRegistrar("1tldregistrar", "Multilevel Registrar", Registrar.State.ACTIVE));
    persistSimpleGlobalResources(makeRegistrarContacts(registrar1tld));
    persistResource(makeDomainResource("cat.1.tld",
        registrant,
        adminContact,
        techContact,
        host1,
        host2,
        registrar1tld));
    action = new RdapDomainAction();
    action.clock = clock;
    action.response = response;
    action.rdapLinkBase = "https://example.com/rdap/";
    action.rdapWhoisServer = "whois.example.tld";
  }

  private Object generateActualJson(String domainName) {
    action.requestPath = RdapDomainAction.PATH + domainName;
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private Object generateExpectedJson(
      String name,
      String punycodeName,
      String handle,
      String expectedOutputFile) {
    return JSONValue.parse(loadFileWithSubstitutions(
        this.getClass(),
        expectedOutputFile,
        ImmutableMap.of(
            "NAME", name,
            "PUNYCODENAME", (punycodeName == null) ? name : punycodeName,
            "HANDLE", handle,
            "TYPE", "domain name")));
  }

  private Object generateExpectedJsonWithTopLevelEntries(
      String name,
      String punycodeName,
      String handle,
      String expectedOutputFile) {
    Object obj = generateExpectedJson(
        name, punycodeName, handle, expectedOutputFile);
    if (obj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) obj;
      ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
      builder.putAll(map);
      if (!map.containsKey("rdapConformance")) {
        builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
      }
      if (!map.containsKey("notices")) {
        RdapTestHelper.addTermsOfServiceNotice(builder, "https://example.com/rdap/");
      }
      if (!map.containsKey("remarks")) {
        RdapTestHelper.addDomainBoilerplateRemarks(builder);
      }
      if (!map.containsKey("port43")) {
        builder.put("port43", "whois.example.com");
      }
      obj = builder.build();
    }
    return obj;
  }

  @Test
  public void testInvalidDomain_returns400() throws Exception {
    assertThat(generateActualJson("invalid/domain/name")).isEqualTo(
        generateExpectedJson(
            "invalid/domain/name is not a valid domain name", null, "1", "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testUnknownDomain_returns404() throws Exception {
    assertThat(generateActualJson("missingdomain.com")).isEqualTo(
        generateExpectedJson("missingdomain.com not found", null, "1", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDeletedDomain_returns404() throws Exception {
    assertThat(generateActualJson("dodo.lol")).isEqualTo(
        generateExpectedJson("dodo.lol not found", null, "1", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testValidDomain_works() throws Exception {
    assertThat(generateActualJson("cat.lol")).isEqualTo(
        generateExpectedJsonWithTopLevelEntries("cat.lol", null, "7-LOL", "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testTrailingDot_ignored() throws Exception {
    assertThat(generateActualJson("cat.lol.")).isEqualTo(
        generateExpectedJsonWithTopLevelEntries("cat.lol", null, "7-LOL", "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testQueryParameter_ignored() throws Exception {
    assertThat(generateActualJson("cat.lol?key=value")).isEqualTo(
        generateExpectedJsonWithTopLevelEntries("cat.lol", null, "7-LOL", "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testIdnDomain_works() throws Exception {
    assertThat(generateActualJson("cat.みんな")).isEqualTo(
        generateExpectedJsonWithTopLevelEntries(
            "cat.みんな", "cat.xn--q9jyb4c", "A-Q9JYB4C", "rdap_domain_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testIdnDomainWithPercentEncoding_works() throws Exception {
    assertThat(generateActualJson("cat.%E3%81%BF%E3%82%93%E3%81%AA")).isEqualTo(
        generateExpectedJsonWithTopLevelEntries(
            "cat.みんな", "cat.xn--q9jyb4c", "A-Q9JYB4C", "rdap_domain_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testPunycodeDomain_works() throws Exception {
    assertThat(generateActualJson("cat.xn--q9jyb4c")).isEqualTo(
        generateExpectedJsonWithTopLevelEntries(
            "cat.みんな", "cat.xn--q9jyb4c", "A-Q9JYB4C", "rdap_domain_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testMultilevelDomain_works() throws Exception {
    assertThat(generateActualJson("cat.1.tld")).isEqualTo(
        generateExpectedJsonWithTopLevelEntries("cat.1.tld", null, "C-1.TLD", "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  // todo (b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testDomainInTestTld_notFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    generateActualJson("cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }
}
