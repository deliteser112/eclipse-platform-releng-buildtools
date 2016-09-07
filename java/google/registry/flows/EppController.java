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

import static google.registry.flows.EppXmlTransformer.unmarshal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import google.registry.flows.FlowModule.EppExceptionInProviderException;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.eppoutput.Result;
import google.registry.model.eppoutput.Result.Code;
import google.registry.monitoring.whitebox.BigQueryMetricsEnqueuer;
import google.registry.monitoring.whitebox.EppMetric;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;

/**
 * An implementation of the EPP command/response protocol.
 *
 * @see "http://tools.ietf.org/html/rfc5730"
 */
public final class EppController {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject Clock clock;
  @Inject FlowComponent.Builder flowComponentBuilder;
  @Inject EppMetric.Builder metric;
  @Inject BigQueryMetricsEnqueuer bigQueryMetricsEnqueuer;
  @Inject EppController() {}

  /** Read EPP XML, execute the matching flow, and return an {@link EppOutput}. */
  public EppOutput handleEppCommand(
      SessionMetadata sessionMetadata,
      TransportCredentials credentials,
      EppRequestSource eppRequestSource,
      boolean isDryRun,
      boolean isSuperuser,
      byte[] inputXmlBytes) {
    metric.setClientId(sessionMetadata.getClientId());
    metric.setPrivilegeLevel(isSuperuser ? "SUPERUSER" : "NORMAL");
    try {
      EppInput eppInput;
      try {
        eppInput = unmarshal(EppInput.class, inputXmlBytes);
      } catch (EppException e) {
        // Send the client an error message, with no clTRID since we couldn't unmarshal it.
        metric.setStatus(e.getResult().getCode());
        return getErrorResponse(clock, e.getResult(), Trid.create(null));
      }
      metric.setCommandName(eppInput.getCommandName());
      if (!eppInput.getTargetIds().isEmpty()) {
        metric.setEppTarget(Joiner.on(',').join(eppInput.getTargetIds()));
      }
      EppOutput output = runFlowConvertEppErrors(flowComponentBuilder
          .flowModule(new FlowModule.Builder()
              .setSessionMetadata(sessionMetadata)
              .setCredentials(credentials)
              .setEppRequestSource(eppRequestSource)
              .setIsDryRun(isDryRun)
              .setIsSuperuser(isSuperuser)
              .setInputXmlBytes(inputXmlBytes)
              .setEppInput(eppInput)
              .build())
          .build());
      if (output.isResponse()) {
        metric.setStatus(output.getResponse().getResult().getCode());
      }
      return output;
    } finally {
      bigQueryMetricsEnqueuer.export(metric.build());
    }
  }

  /** Run an EPP flow and convert known exceptions into EPP error responses. */
  private EppOutput runFlowConvertEppErrors(FlowComponent flowComponent) {
    try {
      return flowComponent.flowRunner().run();
    } catch (EppException | EppExceptionInProviderException e) {
      // The command failed. Send the client an error message.
      EppException eppEx = (EppException) (e instanceof EppException ? e : e.getCause());
      return getErrorResponse(clock, eppEx.getResult(), flowComponent.trid());
    } catch (Throwable e) {
      // Something bad and unexpected happened. Send the client a generic error, and log it.
      logger.severe(e, "Unexpected failure");
      return getErrorResponse(clock, Result.create(Code.CommandFailed), flowComponent.trid());
    }
  }

  /** Create a response indicating an EPP failure. */
  @VisibleForTesting
  static EppOutput getErrorResponse(Clock clock, Result result, Trid trid) {
    return EppOutput.create(new EppResponse.Builder()
        .setResult(result)
        .setTrid(trid)
        .setExecutionTime(clock.nowUtc())
        .build());
  }
}
