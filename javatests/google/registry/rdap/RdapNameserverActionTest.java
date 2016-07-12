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
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistHostResource;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;

import com.google.appengine.api.NamespaceManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.model.ofy.Ofy;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import java.util.Map;
import javax.annotation.Nullable;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapNameserverAction}. */
@RunWith(JUnit4.class)
public class RdapNameserverActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final FakeResponse response = new FakeResponse();
  final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    // normal
    createTld("lol");
    makeAndPersistHostResource(
        "ns1.cat.lol", "1.2.3.4", clock.nowUtc().minusYears(1));
    // idn
    createTld("xn--q9jyb4c");
    makeAndPersistHostResource(
        "ns1.cat.xn--q9jyb4c", "bad:f00d:cafe:0:0:0:15:beef", clock.nowUtc().minusYears(1));
    // multilevel
    createTld("1.tld");
    makeAndPersistHostResource(
        "ns1.domain.1.tld", "5.6.7.8", clock.nowUtc().minusYears(1));
    NamespaceManager.set(null);
  }

  private RdapNameserverAction newRdapNameserverAction(String input) {
    RdapNameserverAction action = new RdapNameserverAction();
    action.clock = clock;
    action.response = response;
    action.requestPath = RdapNameserverAction.PATH.concat(input);
    action.rdapLinkBase = "https://example.tld/rdap/";
    action.rdapWhoisServer = "whois.example.tld";
    return action;
  }

  private Object generateActualJson(String name) {
    newRdapNameserverAction(name).run();
    return JSONValue.parse(response.getPayload());
  }

  private Object generateExpectedJson(
      String name,
      @Nullable ImmutableMap<String, String> otherSubstitutions,
      String expectedOutputFile) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    builder.put("TYPE", "nameserver");
    builder.put("NAME", name);
    boolean punycodeSet = false;
    if (otherSubstitutions != null) {
      builder.putAll(otherSubstitutions);
      if (otherSubstitutions.containsKey("PUNYCODENAME")) {
        punycodeSet = true;
      }
    }
    if (!punycodeSet) {
      builder.put("PUNYCODENAME", name);
    }
    return JSONValue.parse(loadFileWithSubstitutions(
        this.getClass(),
        expectedOutputFile,
        builder.build()));
  }

  private Object generateExpectedJsonWithTopLevelEntries(
      String name,
      @Nullable ImmutableMap<String, String> otherSubstitutions,
      String expectedOutputFile) {
    Object obj = generateExpectedJson(name, otherSubstitutions, expectedOutputFile);
    if (obj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) obj;
      ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
      builder.putAll(map);
      if (!map.containsKey("rdapConformance")) {
        builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
      }
      if (!map.containsKey("port43")) {
        builder.put("port43", "whois.example.tld");
      }
      if (!map.containsKey("notices")) {
        RdapTestHelper.addTermsOfServiceNotice(builder, "https://example.tld/rdap/");
      }
      if (!map.containsKey("remarks")) {
        RdapTestHelper.addNonDomainBoilerplateRemarks(builder);
      }
      obj = builder.build();
    }
    return obj;
  }

  @Test
  public void testInvalidNameserver_returns400() throws Exception {
    assertThat(generateActualJson("invalid/host/name")).isEqualTo(
        generateExpectedJson(
            "invalid/host/name is not a valid nameserver", null, "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testUnknownNameserver_returns404() throws Exception {
    assertThat(generateActualJson("ns1.missing.com")).isEqualTo(
        generateExpectedJson("ns1.missing.com not found", null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testValidNameserver_works() throws Exception {
    assertThat(generateActualJson("ns1.cat.lol"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.lol",
            ImmutableMap.of("HANDLE", "2-ROID", "ADDRESSTYPE", "v4", "ADDRESS", "1.2.3.4"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testTrailingDot_getsIgnored() throws Exception {
    assertThat(generateActualJson("ns1.cat.lol."))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.lol",
            ImmutableMap.of("HANDLE", "2-ROID", "ADDRESSTYPE", "v4", "ADDRESS", "1.2.3.4"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testQueryParameter_getsIgnored() throws Exception {
    assertThat(generateActualJson("ns1.cat.lol?key=value"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.lol",
            ImmutableMap.of("HANDLE", "2-ROID", "ADDRESSTYPE", "v4", "ADDRESS", "1.2.3.4"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testIdnNameserver_works() throws Exception {
    assertThat(generateActualJson("ns1.cat.みんな"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.みんな",
            ImmutableMap.of(
                "PUNYCODENAME", "ns1.cat.xn--q9jyb4c",
                "HANDLE", "5-ROID",
                "ADDRESSTYPE", "v6",
                "ADDRESS", "bad:f00d:cafe::15:beef"),
            "rdap_host_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testPunycodeNameserver_works() throws Exception {
    assertThat(generateActualJson("ns1.cat.xn--q9jyb4c"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.cat.みんな",
            ImmutableMap.of(
                "PUNYCODENAME", "ns1.cat.xn--q9jyb4c",
                "HANDLE", "5-ROID",
                "ADDRESSTYPE", "v6",
                "ADDRESS", "bad:f00d:cafe::15:beef"),
            "rdap_host_unicode.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testMultilevelNameserver_works() throws Exception {
    assertThat(generateActualJson("ns1.domain.1.tld"))
        .isEqualTo(generateExpectedJsonWithTopLevelEntries(
            "ns1.domain.1.tld",
            ImmutableMap.of("HANDLE", "8-ROID", "ADDRESSTYPE", "v4", "ADDRESS", "5.6.7.8"),
            "rdap_host.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }
}
