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

import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.io.BaseEncoding.base64;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.xml.XmlTransformer.prettyPrint;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.googlecode.objectify.Work;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.DryRun;
import google.registry.flows.FlowModule.InputXml;
import google.registry.flows.FlowModule.Superuser;
import google.registry.flows.FlowModule.Transactional;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppoutput.EppOutput;
import google.registry.monitoring.whitebox.EppMetric;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;
import javax.inject.Provider;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;

/** Run a flow, either transactionally or not, with logging and retrying as needed. */
public class FlowRunner {

  /** Log format used by legacy ICANN reporting parsing - DO NOT CHANGE. */
  // TODO(b/20725722): remove this log format entirely once we've transitioned to using the
  //   JSON log line below instead, or change this one to be for human consumption only.
  private static final String COMMAND_LOG_FORMAT = "EPP Command" + Strings.repeat("\n\t%s", 7);

  /**
   * Log signature used by reporting pipelines to extract matching log lines.
   *
   * <p><b>WARNING:<b/> DO NOT CHANGE this value unless you want to break reporting.
   */
  private static final String REPORTING_LOG_SIGNATURE = "EPP-REPORTING-LOG-SIGNATURE";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @ClientId String clientId;
  @Inject Clock clock;
  @Inject TransportCredentials credentials;
  @Inject EppInput eppInput;
  @Inject EppRequestSource eppRequestSource;
  @Inject Provider<Flow> flowProvider;
  @Inject @InputXml byte[] inputXmlBytes;
  @Inject @DryRun boolean isDryRun;
  @Inject @Superuser boolean isSuperuser;
  @Inject @Transactional boolean isTransactional;
  @Inject EppMetric.Builder metric;
  @Inject SessionMetadata sessionMetadata;
  @Inject Trid trid;
  @Inject FlowRunner() {}

  public EppOutput run() throws EppException {
    String prettyXml = prettyPrint(inputXmlBytes);
    String xmlBase64 = base64().encode(inputXmlBytes);
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
    // WARNING: This JSON log statement is parsed by reporting pipelines - be careful when changing.
    // It should be safe to add new keys, but be very cautious in changing existing keys.
    logger.infofmt(
        "%s: %s",
        REPORTING_LOG_SIGNATURE,
        JSONValue.toJSONString(ImmutableMap.<String, Object>of(
            "trid", trid.getServerTransactionId(),
            "clientId", clientId,
            "xml", prettyXml,
            "xmlBytes", xmlBase64)));
    if (!isTransactional) {
      metric.incrementAttempts();
      return createAndInitFlow(clock.nowUtc()).run();
    }
    // We log the command in a structured format. Note that we do this before the transaction;
    // if we did it after, we might miss a transaction that committed successfully but then crashed
    // before it could log.
    logger.info("EPP_Mutation " + new JsonLogStatement(trid)
        .add("client", clientId)
        .add("privileges", isSuperuser ? "SUPERUSER" : "NORMAL")
        .add("xmlBytes", xmlBase64));
    try {
      EppOutput flowResult =
          ofy()
              .transact(
                  new Work<EppOutput>() {
                    @Override
                    public EppOutput run() {
                      metric.incrementAttempts();
                      try {
                        EppOutput output = createAndInitFlow(ofy().getTransactionTime()).run();
                        if (isDryRun) {
                          throw new DryRunException(output);
                        }
                        return output;
                      } catch (EppException e) {
                        throw new RuntimeException(e);
                      }
                    }
                  });
      logger.info("EPP_Mutation_Committed " + new JsonLogStatement(trid)
          .add("createdRepoId", flowResult.getResponse().getCreatedRepoId())
          .add("executionTime", flowResult.getResponse().getExecutionTime().getMillis()));
      return flowResult;
    } catch (DryRunException e) {
      return e.output;
    } catch (RuntimeException e) {
      logger.warning("EPP_Mutation_Failed " + new JsonLogStatement(trid));
      logger.warning(getStackTraceAsString(e));
      if (e.getCause() instanceof EppException) {
        throw (EppException) e.getCause();
      }
      throw e;
    }
  }

  private Flow createAndInitFlow(DateTime now) throws EppException {
      return flowProvider.get().init(
          eppInput,
          trid,
          sessionMetadata,
          credentials,
          isSuperuser,
          now);
  }

  /**
   * Helper for logging in json format.
   *
   * <p>This is needed because the usual json outputters perform normalizations that we don't want
   * or need, since we know that our values never need to be escaped - there are only strings and
   * numbers, and the strings are not allowed to contain quote characters.
   *
   * <p>An example output for an EPP_Mutation: {"trid":"abc-123", "client":"some_registrar",
   * "tld":"com", "xmlBytes":"abc123DEF"}
   *
   * <p>An example output for an EPP_Mutation_Committed that doesn't create a new resource:
   * {"trid":"abc-123", "executionTime":123456789}
   *
   * <p>An example output for an EPP_Mutation_Committed that creates a new resource:
   * {"trid":"abc-123", "executionRepoId":123, "executionTime":123456789}
   */
  private static class JsonLogStatement {

    StringBuilder message;

    JsonLogStatement(Trid trid) {
      message =
          new StringBuilder("{\"trid\":\"").append(trid.getServerTransactionId()).append('\"');
    }

    JsonLogStatement add(String key, Object value) {
      if (value != null) {
        String quote = value instanceof String ? "\"" : "";
        message.append(String.format(", \"%s\":%s%s%s", key, quote, value, quote));
      }
      return this;
    }

    @Override
    public String toString() {
      return message + "}";
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
