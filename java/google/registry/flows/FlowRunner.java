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

import static com.google.common.base.Throwables.getStackTraceAsString;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.xml.XmlTransformer.prettyPrint;

import com.google.common.base.Strings;
import com.googlecode.objectify.Work;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.DryRun;
import google.registry.flows.FlowModule.InputXml;
import google.registry.flows.FlowModule.Superuser;
import google.registry.flows.FlowModule.Transactional;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppoutput.EppOutput;
import google.registry.monitoring.whitebox.EppMetric;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;
import javax.inject.Provider;

/** Run a flow, either transactionally or not, with logging and retrying as needed. */
public class FlowRunner {

  /** Log format used by legacy ICANN reporting parsing - DO NOT CHANGE. */
  // TODO(b/20725722): remove this log format entirely once we've transitioned to using the
  //   JSON log line below instead, or change this one to be for human consumption only.
  private static final String COMMAND_LOG_FORMAT = "EPP Command" + Strings.repeat("\n\t%s", 7);

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @ClientId String clientId;
  @Inject TransportCredentials credentials;
  @Inject EppRequestSource eppRequestSource;
  @Inject Provider<Flow> flowProvider;
  @Inject Class<? extends Flow> flowClass;
  @Inject @InputXml byte[] inputXmlBytes;
  @Inject @DryRun boolean isDryRun;
  @Inject @Superuser boolean isSuperuser;
  @Inject @Transactional boolean isTransactional;
  @Inject SessionMetadata sessionMetadata;
  @Inject Trid trid;
  @Inject FlowReporter flowReporter;
  @Inject FlowRunner() {}

  /** Runs the EPP flow, and records metrics on the given builder. */
  public EppOutput run(final EppMetric.Builder eppMetricBuilder) throws EppException {
    String prettyXml = prettyPrint(inputXmlBytes);
    // This log line is very fragile since it's used for ICANN reporting - DO NOT CHANGE.
    // New data to be logged should be added only to the JSON log statement below.
    // TODO(b/20725722): remove this log statement entirely once we've transitioned to using the
    //   log line below instead, or change this one to be for human consumption only.
    logger.infofmt(
        COMMAND_LOG_FORMAT,
        trid.getServerTransactionId(),
        clientId,
        sessionMetadata,
        prettyXml.replaceAll("\n", "\n\t"),
        credentials,
        eppRequestSource,
        isDryRun ? "DRY_RUN" : "LIVE",
        isSuperuser ? "SUPERUSER" : "NORMAL");
    // Record information about this flow to the GAE request logs for reporting purposes.
    flowReporter.recordToLogs();
    eppMetricBuilder.setCommandNameFromFlow(flowClass.getSimpleName());
    if (!isTransactional) {
      eppMetricBuilder.incrementAttempts();
      return EppOutput.create(flowProvider.get().run());
    }
    try {
      return ofy().transact(new Work<EppOutput>() {
        @Override
        public EppOutput run() {
          eppMetricBuilder.incrementAttempts();
          try {
            EppOutput output = EppOutput.create(flowProvider.get().run());
            if (isDryRun) {
              throw new DryRunException(output);
            }
            return output;
          } catch (EppException e) {
            throw new RuntimeException(e);
          }
        }});
    } catch (DryRunException e) {
      return e.output;
    } catch (RuntimeException e) {
      logger.warning(getStackTraceAsString(e));
      if (e.getCause() instanceof EppException) {
        throw (EppException) e.getCause();
      }
      throw e;
    }
  }

  /** Exception for canceling a transaction while capturing what the output would have been. */
  private static class DryRunException extends RuntimeException {
    final EppOutput output;

    DryRunException(EppOutput output) {
      this.output = output;
    }
  }
}
