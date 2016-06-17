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

import com.googlecode.objectify.Work;

import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppoutput.EppOutput;
import google.registry.monitoring.whitebox.EppMetrics;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.TypeUtils;

import org.joda.time.DateTime;

/** Run a flow, either transactionally or not, with logging and retrying as needed. */
public class FlowRunner {

  private static final String COMMAND_LOG_FORMAT = "EPP Command" + Strings.repeat("\n\t%s", 4);

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private final Class<? extends Flow> flowClass;
  private final EppInput eppInput;
  private final Trid trid;
  private final SessionMetadata sessionMetadata;
  private final byte[] inputXmlBytes;
  private final EppMetrics metrics;
  private final Clock clock;

  public FlowRunner(
      Class<? extends Flow> flowClass,
      EppInput eppInput,
      Trid trid,
      SessionMetadata sessionMetadata,
      byte[] inputXmlBytes,
      final EppMetrics metrics,
      Clock clock) {
    this.flowClass = flowClass;
    this.eppInput = eppInput;
    this.trid = trid;
    this.sessionMetadata = sessionMetadata;
    this.inputXmlBytes = inputXmlBytes;
    this.metrics = metrics;
    this.clock = clock;
  }

  public EppOutput run() throws EppException {
    String clientId = sessionMetadata.getClientId();
    logger.infofmt(
        COMMAND_LOG_FORMAT,
        trid.getServerTransactionId(),
        clientId,
        sessionMetadata,
        prettyPrint(inputXmlBytes).replaceAll("\n", "\n\t"));
    if (!isTransactional()) {
      if (metrics != null) {
        metrics.incrementAttempts();
      }
      return createAndInitFlow(clock.nowUtc()).run();
    }
    // We log the command in a structured format. Note that we do this before the transaction;
    // if we did it after, we might miss a transaction that committed successfully but then crashed
    // before it could log.
    logger.info("EPP_Mutation " + new JsonLogStatement(trid)
        .add("client", clientId)
        .add("privileges", sessionMetadata.isSuperuser() ? "SUPERUSER" : "NORMAL")
        .add("xmlBytes", base64().encode(inputXmlBytes)));
    try {
      EppOutput flowResult = ofy().transact(new Work<EppOutput>() {
        @Override
        public EppOutput run() {
          if (metrics != null) {
            metrics.incrementAttempts();
          }
          try {
            EppOutput output = createAndInitFlow(ofy().getTransactionTime()).run();
            if (sessionMetadata.isDryRun()) {
              throw new DryRunException(output);
            }
            return output;
          } catch (EppException e) {
            throw new RuntimeException(e);
          }
        }});
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
      return TypeUtils.<Flow>instantiate(flowClass).init(
          eppInput,
          trid,
          sessionMetadata,
          now,
          inputXmlBytes);
  }

  public boolean isTransactional() {
    return TransactionalFlow.class.isAssignableFrom(flowClass);
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
