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

package google.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.TestDataHelper.loadFile;
import static google.registry.xml.XmlTestUtils.assertXmlEqualsWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.flows.EppTestComponent.FakesAndMocksModule;
import google.registry.model.ofy.Ofy;
import google.registry.monitoring.whitebox.EppMetric;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeHttpSession;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.testing.ShardableTestCase;
import java.util.Map;
import javax.annotation.Nullable;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;

public class EppTestCase extends ShardableTestCase {

  private static final MediaType APPLICATION_EPP_XML_UTF8 =
      MediaType.create("application", "epp+xml").withCharset(UTF_8);

  @Rule
  public final InjectRule inject = new InjectRule();

  private final FakeClock clock = new FakeClock();

  private SessionMetadata sessionMetadata;
  private TransportCredentials credentials = new PasswordOnlyTransportCredentials();
  private EppMetric.Builder eppMetricBuilder;
  private boolean isSuperuser;

  @Before
  public void initTestCase() {
    // For transactional flows
    inject.setStaticField(Ofy.class, "clock", clock);
  }

  /**
   * Set the transport credentials.
   *
   * <p>When the credentials are null, the login flow still checks the EPP password from the xml,
   * which is sufficient for all tests that aren't explicitly testing a form of login credentials
   * such as {@link EppLoginUserTest}, {@link EppLoginAdminUserTest} and {@link EppLoginTlsTest}.
   * Therefore, only those tests should call this method.
   */
  protected void setTransportCredentials(TransportCredentials credentials) {
    this.credentials = credentials;
  }

  protected void setIsSuperuser(boolean isSuperuser) {
    this.isSuperuser = isSuperuser;
  }

  public class CommandAsserter {
    private final String inputFilename;
    private @Nullable final Map<String, String> inputSubstitutions;
    private DateTime now;

    private CommandAsserter(
        String inputFilename, @Nullable Map<String, String> inputSubstitutions) {
      this.inputFilename = inputFilename;
      this.inputSubstitutions = inputSubstitutions;
      this.now = DateTime.now(UTC);
    }

    public CommandAsserter atTime(DateTime now) {
      this.now = now;
      return this;
    }

    public CommandAsserter atTime(String now) {
      return atTime(DateTime.parse(now));
    }

    public String hasResponse(String outputFilename) throws Exception {
      return hasResponse(outputFilename, null);
    }

    public String hasResponse(
        String outputFilename, @Nullable Map<String, String> outputSubstitutions) throws Exception {
      return assertCommandAndResponse(
          inputFilename, inputSubstitutions, outputFilename, outputSubstitutions, now);
    }
  }

  protected CommandAsserter assertThatCommand(String inputFilename) {
    return assertThatCommand(inputFilename, null);
  }

  protected CommandAsserter assertThatCommand(
      String inputFilename, @Nullable Map<String, String> inputSubstitutions) {
    return new CommandAsserter(inputFilename, inputSubstitutions);
  }

  protected CommandAsserter assertThatLogin(String clientId, String password) {
    return assertThatCommand("login.xml", ImmutableMap.of("CLID", clientId, "PW", password));
  }

  protected void assertThatLoginSucceeds(String clientId, String password) throws Exception {
    assertThatLogin(clientId, password).hasResponse("generic_success_response.xml");
  }

  protected void assertThatLogoutSucceeds() throws Exception {
    assertThatCommand("logout.xml").hasResponse("logout_response.xml");
  }

  private String assertCommandAndResponse(
      String inputFilename,
      @Nullable Map<String, String> inputSubstitutions,
      String outputFilename,
      @Nullable Map<String, String> outputSubstitutions,
      DateTime now)
      throws Exception {
    clock.setTo(now);
    String input = loadFile(EppTestCase.class, inputFilename, inputSubstitutions);
    String expectedOutput = loadFile(EppTestCase.class, outputFilename, outputSubstitutions);
    if (sessionMetadata == null) {
      sessionMetadata =
          new HttpSessionMetadata(new FakeHttpSession()) {
            @Override
            public void invalidate() {
              // When a session is invalidated, reset the sessionMetadata field.
              super.invalidate();
              EppTestCase.this.sessionMetadata = null;
            }
          };
    }
    String actualOutput = executeXmlCommand(input);
    assertXmlEqualsWithMessage(
        expectedOutput,
        actualOutput,
        "Running " + inputFilename + " => " + outputFilename,
        "epp.response.resData.infData.roid",
        "epp.response.trID.svTRID");
    ofy().clearSessionCache(); // Clear the cache like OfyFilter would.
    return actualOutput;
  }

  private String executeXmlCommand(String inputXml) throws Exception {
    EppRequestHandler handler = new EppRequestHandler();
    FakeResponse response = new FakeResponse();
    handler.response = response;
    eppMetricBuilder = EppMetric.builderForRequest(clock);
    handler.eppController = DaggerEppTestComponent.builder()
        .fakesAndMocksModule(FakesAndMocksModule.create(clock, eppMetricBuilder))
        .build()
        .startRequest()
        .eppController();
    handler.executeEpp(
        sessionMetadata,
        credentials,
        EppRequestSource.UNIT_TEST,
        false,  // Not dryRun.
        isSuperuser,
        inputXml.getBytes(UTF_8));
    assertThat(response.getStatus()).isEqualTo(SC_OK);
    assertThat(response.getContentType()).isEqualTo(APPLICATION_EPP_XML_UTF8);
    String result = response.getPayload();
    // Run the resulting xml through the unmarshaller to verify that it was valid.
    EppXmlTransformer.validateOutput(result);
    return result;
  }

  protected EppMetric getRecordedEppMetric() {
    return eppMetricBuilder.build();
  }

  /** Create the two administrative contacts and two hosts. */
  protected void createContactsAndHosts() throws Exception {
    DateTime createTime = DateTime.parse("2000-06-01T00:00:00Z");
    createContacts(createTime);
    assertThatCommand("host_create.xml", ImmutableMap.of("HOSTNAME", "ns1.example.external"))
        .atTime(createTime.plusMinutes(2))
        .hasResponse(
            "host_create_response.xml",
            ImmutableMap.of(
                "HOSTNAME", "ns1.example.external",
                "CRDATE", createTime.plusMinutes(2).toString()));
    assertThatCommand("host_create.xml", ImmutableMap.of("HOSTNAME", "ns2.example.external"))
        .atTime(createTime.plusMinutes(3))
        .hasResponse(
            "host_create_response.xml",
            ImmutableMap.of(
                "HOSTNAME", "ns2.example.external",
                "CRDATE", createTime.plusMinutes(3).toString()));
  }

  protected void createContacts(DateTime createTime) throws Exception {
    assertThatCommand("contact_create_sh8013.xml")
        .atTime(createTime)
        .hasResponse(
            "contact_create_response_sh8013.xml", ImmutableMap.of("CRDATE", createTime.toString()));
    assertThatCommand("contact_create_jd1234.xml")
        .atTime(createTime.plusMinutes(1))
        .hasResponse(
            "contact_create_response_jd1234.xml",
            ImmutableMap.of("CRDATE", createTime.plusMinutes(1).toString()));
  }

  /** Creates the domain fakesite.example with two nameservers on it. */
  protected void createFakesite() throws Exception {
    createContactsAndHosts();
    assertThatCommand("domain_create_fakesite.xml")
        .atTime("2000-06-01T00:04:00Z")
        .hasResponse(
            "domain_create_response.xml",
            ImmutableMap.of(
                "DOMAIN", "fakesite.example",
                "CRDATE", "2000-06-01T00:04:00.0Z",
                "EXDATE", "2002-06-01T00:04:00.0Z"));
    assertThatCommand("domain_info_fakesite.xml")
        .atTime("2000-06-06T00:00:00Z")
        .hasResponse("domain_info_response_fakesite_ok.xml");
  }

  /** Creates ns3.fakesite.example as a host, then adds it to fakesite. */
  protected void createSubordinateHost() throws Exception {
    // Add the fakesite nameserver (requires that domain is already created).
    assertThatCommand("host_create_fakesite.xml")
        .atTime("2000-06-06T00:01:00Z")
        .hasResponse("host_create_response_fakesite.xml");
    // Add new nameserver to domain.
    assertThatCommand("domain_update_add_nameserver_fakesite.xml")
        .atTime("2000-06-08T00:00:00Z")
        .hasResponse("generic_success_response.xml");
    // Verify new nameserver was added.
    assertThatCommand("domain_info_fakesite.xml")
        .atTime("2000-06-08T00:01:00Z")
        .hasResponse("domain_info_response_fakesite_3_nameservers.xml");
    // Verify that nameserver's data was set correctly.
    assertThatCommand("host_info_fakesite.xml")
        .atTime("2000-06-08T00:02:00Z")
        .hasResponse("host_info_response_fakesite_linked.xml");
  }
}
