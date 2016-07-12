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

package google.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static google.registry.xml.XmlTestUtils.assertXmlEqualsWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.net.MediaType;
import google.registry.flows.EppTestComponent.FakesAndMocksModule;
import google.registry.model.ofy.Ofy;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeHttpSession;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.testing.ShardableTestCase;
import java.util.Map;
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
  private boolean isSuperuser;

  @Before
  public void initTestCase() {
    inject.setStaticField(Ofy.class, "clock", clock);  // For transactional flows.
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

  String assertCommandAndResponse(String inputFilename, String outputFilename) throws Exception {
    return assertCommandAndResponse(inputFilename, outputFilename, DateTime.now(UTC));
  }

  String assertCommandAndResponse(String inputFilename, String outputFilename, DateTime now)
      throws Exception {
    return assertCommandAndResponse(inputFilename, null, outputFilename, null, now);
  }

  String assertCommandAndResponse(
      String inputFilename,
      Map<String, String> inputSubstitutions,
      String outputFilename,
      Map<String, String> outputSubstitutions,
      DateTime now) throws Exception {
    clock.setTo(now);
    String input = loadFileWithSubstitutions(getClass(), inputFilename, inputSubstitutions);
    String expectedOutput =
        loadFileWithSubstitutions(getClass(), outputFilename, outputSubstitutions);
    if (sessionMetadata == null) {
      sessionMetadata = new HttpSessionMetadata(new FakeHttpSession()) {
        @Override
        public void invalidate() {
          // When a session is invalidated, reset the sessionMetadata field.
          super.invalidate();
          EppTestCase.this.sessionMetadata = null;
        }};
    }
    String actualOutput = executeXmlCommand(input);
    assertXmlEqualsWithMessage(
        expectedOutput,
        actualOutput,
        "Running " + inputFilename + " => " + outputFilename,
        "epp.response.resData.infData.roid",
        "epp.response.trID.svTRID");
    ofy().clearSessionCache();  // Clear the cache like OfyFilter would.
    return actualOutput;
  }

  private String executeXmlCommand(String inputXml) throws Exception {
    EppRequestHandler handler = new EppRequestHandler();
    FakeResponse response = new FakeResponse();
    handler.response = response;
    handler.eppController = DaggerEppTestComponent.builder()
        .fakesAndMocksModule(new FakesAndMocksModule(clock))
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
}
