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

package google.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.flows.EppXmlTransformer.marshal;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.eppoutput.Result;
import google.registry.model.eppoutput.Result.Code;
import google.registry.monitoring.whitebox.BigQueryMetricsEnqueuer;
import google.registry.monitoring.whitebox.EppMetric;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.ShardableTestCase;
import google.registry.util.Clock;
import google.registry.xml.ValidationMode;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link EppController}. */
@RunWith(MockitoJUnitRunner.class)
public class EppControllerTest extends ShardableTestCase {

  @Rule
  public AppEngineRule appEngineRule = new AppEngineRule.Builder().build();

  @Mock SessionMetadata sessionMetadata;
  @Mock TransportCredentials transportCredentials;
  @Mock EppMetrics eppMetrics;
  @Mock BigQueryMetricsEnqueuer metricsEnqueuer;
  @Mock FlowComponent.Builder flowComponentBuilder;
  @Mock FlowComponent flowComponent;
  @Mock FlowRunner flowRunner;
  @Mock EppOutput eppOutput;
  @Mock EppResponse eppResponse;
  @Mock Result result;

  private static final DateTime startTime = DateTime.parse("2016-09-01T00:00:00Z");

  private final Clock clock = new FakeClock(startTime);

  private EppController eppController;

  @Before
  public void setUp() throws Exception {
    when(sessionMetadata.getClientId()).thenReturn("some-client");
    when(flowComponentBuilder.flowModule(Matchers.<FlowModule>any()))
        .thenReturn(flowComponentBuilder);
    when(flowComponentBuilder.build()).thenReturn(flowComponent);
    when(flowComponent.flowRunner()).thenReturn(flowRunner);
    when(flowRunner.run()).thenReturn(eppOutput);
    when(eppOutput.isResponse()).thenReturn(true);
    when(eppOutput.getResponse()).thenReturn(eppResponse);
    when(eppResponse.getResult()).thenReturn(result);
    when(result.getCode()).thenReturn(Code.SUCCESS_WITH_NO_MESSAGES);

    eppController = new EppController();
    eppController.metricBuilder = EppMetric.builderForRequest("request-id-1", clock);
    eppController.bigQueryMetricsEnqueuer = metricsEnqueuer;
    eppController.flowComponentBuilder = flowComponentBuilder;
    eppController.eppMetrics = eppMetrics;
  }

  @Test
  public void testMarshallingUnknownError() throws Exception {
    marshal(
        EppController.getErrorResponse(Result.create(Code.COMMAND_FAILED), Trid.create(null)),
        ValidationMode.STRICT);
  }

  @Test
  public void testHandleEppCommand_unmarshallableData_exportsMetric() {
    ArgumentCaptor<EppMetric> metricCaptor = ArgumentCaptor.forClass(EppMetric.class);
    eppController.handleEppCommand(
        sessionMetadata,
        transportCredentials,
        EppRequestSource.UNIT_TEST,
        false,
        false,
        new byte[0]);

    verify(metricsEnqueuer).export(metricCaptor.capture());
    EppMetric metric = metricCaptor.getValue();
    assertThat(metric.getRequestId()).isEqualTo("request-id-1");
    assertThat(metric.getStartTimestamp()).isEqualTo(startTime);
    assertThat(metric.getEndTimestamp()).isEqualTo(clock.nowUtc());
    assertThat(metric.getClientId()).hasValue("some-client");
    assertThat(metric.getPrivilegeLevel()).hasValue("NORMAL");
    assertThat(metric.getStatus()).hasValue(Code.SYNTAX_ERROR);
  }

  @Test
  public void testHandleEppCommand_regularEppCommand_exportsMetric() {
    ArgumentCaptor<EppMetric> metricCaptor = ArgumentCaptor.forClass(EppMetric.class);
    String domainCreateXml =
        loadFileWithSubstitutions(
            getClass(), "domain_create_prettyprinted.xml", ImmutableMap.<String, String>of());
    eppController.handleEppCommand(
        sessionMetadata,
        transportCredentials,
        EppRequestSource.UNIT_TEST,
        false,
        true,
        domainCreateXml.getBytes(UTF_8));

    verify(metricsEnqueuer).export(metricCaptor.capture());
    EppMetric metric = metricCaptor.getValue();
    assertThat(metric.getRequestId()).isEqualTo("request-id-1");
    assertThat(metric.getStartTimestamp()).isEqualTo(startTime);
    assertThat(metric.getEndTimestamp()).isEqualTo(clock.nowUtc());
    assertThat(metric.getClientId()).hasValue("some-client");
    assertThat(metric.getPrivilegeLevel()).hasValue("SUPERUSER");
    assertThat(metric.getStatus()).hasValue(Code.SUCCESS_WITH_NO_MESSAGES);
    assertThat(metric.getCommandName()).hasValue("Create");
    assertThat(metric.getEppTarget()).hasValue("example.tld");
  }
}
