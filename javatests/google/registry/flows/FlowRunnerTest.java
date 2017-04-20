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
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.appengine.api.users.User;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.testing.TestLogHandler;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppoutput.EppOutput.ResponseOrGreeting;
import google.registry.model.eppoutput.EppResponse;
import google.registry.monitoring.whitebox.EppMetric;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeHttpSession;
import google.registry.testing.Providers;
import google.registry.testing.ShardableTestCase;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link FlowRunner}. */
@RunWith(JUnit4.class)
public class FlowRunnerTest extends ShardableTestCase {

  @Rule
  public final AppEngineRule appEngineRule = new AppEngineRule.Builder().build();

  private final FlowRunner flowRunner = new FlowRunner();
  private final EppMetric.Builder eppMetricBuilder =
      EppMetric.builderForRequest("request-id-1", new FakeClock());

  private final TestLogHandler handler = new TestLogHandler();

  static class TestCommandFlow implements Flow {
    @Override
    public ResponseOrGreeting run() throws EppException {
      return mock(EppResponse.class);
    }
  }

  @Before
  public void before() {
    Logger.getLogger(FlowRunner.class.getCanonicalName()).addHandler(handler);
    Logger.getLogger(FlowReporter.class.getCanonicalName()).addHandler(handler);
    flowRunner.clientId = "TheRegistrar";
    flowRunner.credentials = new PasswordOnlyTransportCredentials();
    flowRunner.eppRequestSource = EppRequestSource.UNIT_TEST;
    flowRunner.flowProvider = Providers.<Flow>of(new TestCommandFlow());
    flowRunner.flowClass = TestCommandFlow.class;
    flowRunner.inputXmlBytes = "<xml/>".getBytes(UTF_8);
    flowRunner.isDryRun = false;
    flowRunner.isSuperuser = false;
    flowRunner.isTransactional = false;
    flowRunner.sessionMetadata =
        new StatelessRequestSessionMetadata("TheRegistrar", ImmutableSet.<String>of());
    flowRunner.trid = Trid.create("client-123", "server-456");
    flowRunner.flowReporter = Mockito.mock(FlowReporter.class);
  }

  @Test
  public void testRun_nonTransactionalCommand_incrementsMetricAttempts() throws Exception {
    flowRunner.run(eppMetricBuilder);
    assertThat(eppMetricBuilder.build().getAttempts()).isEqualTo(1);
  }

  @Test
  public void testRun_transactionalCommand_incrementsMetricAttempts() throws Exception {
    flowRunner.isTransactional = true;
    flowRunner.run(eppMetricBuilder);
    assertThat(eppMetricBuilder.build().getAttempts()).isEqualTo(1);
  }

  @Test
  public void testRun_nonTransactionalCommand_setsCommandNameOnMetric() throws Exception {
    flowRunner.isTransactional = true;
    flowRunner.run(eppMetricBuilder);
    assertThat(eppMetricBuilder.build().getCommandName()).hasValue("TestCommand");
  }

  @Test
  public void testRun_transactionalCommand_setsCommandNameOnMetric() throws Exception {
    flowRunner.run(eppMetricBuilder);
    assertThat(eppMetricBuilder.build().getCommandName()).hasValue("TestCommand");
  }

  @Test
  public void testRun_callsFlowReporterOnce() throws Exception {
    flowRunner.run(eppMetricBuilder);
    verify(flowRunner.flowReporter).recordToLogs();
  }

  @Test
  public void testRun_legacyLoggingStatement_basic() throws Exception {
    flowRunner.run(eppMetricBuilder);
    assertThat(Splitter.on("\n\t").split(findLogMessageByPrefix(handler, "EPP Command\n\t")))
        .containsExactly(
            "server-456",
            "TheRegistrar",
            "StatelessRequestSessionMetadata"
                + "{clientId=TheRegistrar, failedLoginAttempts=0, serviceExtensionUris=}",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xml/>",
            "",  // Extra newline at the end of the XML.
            "PasswordOnlyTransportCredentials{}",
            "UNIT_TEST",
            "LIVE")
        .inOrder();
  }

  @Test
  public void testRun_legacyLoggingStatement_httpSessionMetadata() throws Exception {
    flowRunner.sessionMetadata = new HttpSessionMetadata(new FakeHttpSession());
    flowRunner.sessionMetadata.setClientId("TheRegistrar");
    flowRunner.run(eppMetricBuilder);
    assertThat(Splitter.on("\n\t").split(findLogMessageByPrefix(handler, "EPP Command\n\t")))
        .contains(
            "HttpSessionMetadata"
                + "{clientId=TheRegistrar, failedLoginAttempts=0, serviceExtensionUris=}");
  }

  @Test
  public void testRun_legacyLoggingStatement_gaeUserCredentials() throws Exception {
    flowRunner.credentials =
        GaeUserCredentials.forTestingUser(new User("user@example.com", "authDomain"), false);
    flowRunner.run(eppMetricBuilder);
    assertThat(Splitter.on("\n\t").split(findLogMessageByPrefix(handler, "EPP Command\n\t")))
        .contains("GaeUserCredentials{gaeUser=user@example.com, isAdmin=false}");
  }

  @Test
  public void testRun_legacyLoggingStatement_tlsCredentials() throws Exception {
    flowRunner.credentials = new TlsCredentials("abc123def", Optional.of("127.0.0.1"), "sni");
    flowRunner.run(eppMetricBuilder);
    assertThat(Splitter.on("\n\t").split(findLogMessageByPrefix(handler, "EPP Command\n\t")))
        .contains(
            "TlsCredentials{clientCertificateHash=abc123def, clientAddress=/127.0.0.1, sni=sni}");
  }

  @Test
  public void testRun_legacyLoggingStatement_dryRun() throws Exception {
    flowRunner.isDryRun = true;
    flowRunner.run(eppMetricBuilder);
    assertThat(Splitter.on("\n\t").split(findLogMessageByPrefix(handler, "EPP Command\n\t")))
        .contains("DRY_RUN");
  }

  @Test
  public void testRun_legacyLoggingStatement_complexEppInput() throws Exception {
    String domainCreateXml = loadFileWithSubstitutions(
        getClass(), "domain_create_prettyprinted.xml", ImmutableMap.<String, String>of());
    flowRunner.inputXmlBytes = domainCreateXml.getBytes(UTF_8);
    flowRunner.run(eppMetricBuilder);
    String logMessage = findLogMessageByPrefix(handler, "EPP Command\n\t");
    List<String> lines = Splitter.on("\n\t").splitToList(logMessage);
    assertThat(lines.size()).named("number of lines in log message").isAtLeast(9);
    String xml = Joiner.on('\n').join(lines.subList(3, lines.size() - 3));
    assertThat(xml).isEqualTo(domainCreateXml);
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
