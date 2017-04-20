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

import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.testing.TestLogHandler;
import google.registry.flows.annotations.ReportingSpec;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppoutput.EppOutput.ResponseOrGreeting;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.reporting.IcannReportingTypes.ActivityReportField;
import google.registry.testing.ShardableTestCase;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FlowReporter}. */
@RunWith(JUnit4.class)
public class FlowReporterTest extends ShardableTestCase {

  static class TestCommandFlow implements Flow {
    @Override
    public ResponseOrGreeting run() throws EppException {
      return mock(EppResponse.class);
    }
  }

  @ReportingSpec(ActivityReportField.CONTACT_CHECK)
  static class TestReportingSpecCommandFlow implements Flow {
    @Override
    public ResponseOrGreeting run() throws EppException {
      return mock(EppResponse.class);
    }
  }

  private final FlowReporter flowReporter = new FlowReporter();
  private final TestLogHandler handler = new TestLogHandler();

  @Before
  public void before() {
    Logger.getLogger(FlowReporter.class.getCanonicalName()).addHandler(handler);
    flowReporter.trid = Trid.create("client-123", "server-456");
    flowReporter.clientId = "TheRegistrar";
    flowReporter.inputXmlBytes = "<xml/>".getBytes(UTF_8);
    flowReporter.flowClass = TestCommandFlow.class;
  }

  @Test
  public void testRecordToLogs_eppInput_basic() throws Exception {
    flowReporter.recordToLogs();
    assertThat(parseJsonMap(findLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-EPPINPUT: ")))
        .containsExactly(
              "xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xml/>\n",
              "xmlBytes", "PHhtbC8+"); // Base64-encoding of "<xml/>".
  }

  @Test
  public void testRecordToLogs_eppInput_complex() throws Exception {
    String domainCreateXml = loadFileWithSubstitutions(
        getClass(), "domain_create_prettyprinted.xml", ImmutableMap.<String, String>of());
    flowReporter.inputXmlBytes = domainCreateXml.getBytes(UTF_8);
    flowReporter.recordToLogs();
    assertThat(parseJsonMap(findLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-EPPINPUT: ")))
        .containsExactly(
              "xml", domainCreateXml,
              "xmlBytes", base64().encode(domainCreateXml.getBytes(UTF_8)));
  }

  @Test
  public void testRecordToLogs_metadata_basic() throws Exception {
    flowReporter.recordToLogs();
    assertThat(parseJsonMap(findLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: ")))
        .containsExactly(
              "trid", "server-456",
              "clientId", "TheRegistrar",
              "icannActivityReportField", "");
  }

  @Test
  public void testRecordToLogs_metadata_withReportingSpec() throws Exception {
    flowReporter.flowClass = TestReportingSpecCommandFlow.class;
    flowReporter.recordToLogs();
    assertThat(parseJsonMap(findLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: ")))
        .containsExactly(
              "trid", "server-456",
              "clientId", "TheRegistrar",
              "icannActivityReportField", "srs-cont-check");
  }

  @Test
  public void testRecordToLogs_metadata_noClientId() throws Exception {
    flowReporter.clientId = "";
    flowReporter.recordToLogs();
    assertThat(parseJsonMap(findLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: ")))
        .containsExactly(
              "trid", "server-456",
              "clientId", "",
              "icannActivityReportField", "");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJsonMap(String json) throws Exception {
    return (Map<String, Object>) JSONValue.parseWithException(json);
  }

  /**
   * Find the first log message stored in the handler that has the provided prefix, and return
   * that message with the prefix stripped off.
   */
  private static String findLogMessageByPrefix(TestLogHandler handler, final String prefix) {
    return Iterables.find(
            handler.getStoredLogRecords(),
            new Predicate<LogRecord>() {
              @Override
              public boolean apply(LogRecord logRecord) {
                return logRecord.getMessage().startsWith(prefix);
              }
            })
        .getMessage()
        .replaceFirst("^" + prefix, "");
  }
}
