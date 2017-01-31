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
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;

import com.google.common.collect.ImmutableMap;
import google.registry.model.ofy.Ofy;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapHelpAction}. */
@RunWith(JUnit4.class)
public class RdapHelpActionTest {

  @Rule
  public final InjectRule inject = new InjectRule();

  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));

  private RdapHelpAction action;

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);

    action = new RdapHelpAction();
    action.clock = clock;
    action.response = response;
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapLinkBase = "https://example.tld/rdap/";
    action.rdapWhoisServer = null;
  }

  private Object generateActualJson(String helpPath) {
    action.requestPath = RdapHelpAction.PATH + helpPath;
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private Object generateExpectedJson(String name, String expectedOutputFile) {
    return JSONValue.parse(loadFileWithSubstitutions(
        this.getClass(), expectedOutputFile, ImmutableMap.of("NAME", name)));
  }

  @Test
  public void testHelpActionMaliciousPath_notFound() throws Exception {
    assertThat(generateActualJson("../passwd")).isEqualTo(
        generateExpectedJson(
            "no help found for ../passwd", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testHelpActionUnknownPath_notFound() throws Exception {
    assertThat(generateActualJson("hlarg")).isEqualTo(
        generateExpectedJson("no help found for hlarg", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testHelpActionIndex_works() throws Exception {
    assertThat(generateActualJson("/index"))
        .isEqualTo(generateExpectedJson("index", "rdap_help_index.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHelpActionDefault_getsIndex() throws Exception {
    assertThat(generateActualJson(""))
        .isEqualTo(generateExpectedJson("", "rdap_help_index.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHelpActionSlash_getsIndex() throws Exception {
    assertThat(generateActualJson("/"))
        .isEqualTo(generateExpectedJson("", "rdap_help_index.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHelpActionSyntax_works() throws Exception {
    generateActualJson("/syntax");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHelpActionTos_works() throws Exception {
    assertThat(generateActualJson("/tos"))
        .isEqualTo(generateExpectedJson("", "rdap_help_tos.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }
}
