// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.flows;

import static com.google.domain.registry.flows.EppXmlTransformer.marshalWithLenientRetry;
import static com.google.domain.registry.flows.EppXmlTransformer.unmarshal;
import static com.google.domain.registry.flows.FlowRegistry.getFlowClass;

import com.google.apphosting.api.ApiProxy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.domain.registry.flows.FlowRunner.CommitMode;
import com.google.domain.registry.flows.FlowRunner.UserPrivileges;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.eppinput.EppInput;
import com.google.domain.registry.model.eppoutput.EppOutput;
import com.google.domain.registry.model.eppoutput.Response;
import com.google.domain.registry.model.eppoutput.Result;
import com.google.domain.registry.model.eppoutput.Result.Code;
import com.google.domain.registry.monitoring.whitebox.EppMetrics;
import com.google.domain.registry.util.Clock;
import com.google.domain.registry.util.FormattingLogger;
import com.google.domain.registry.util.SystemClock;

/**
 * The EppController class, which implements the state machine for the EPP command/response
 * protocol.
 *
 * @see "http://tools.ietf.org/html/rfc5730"
 */
public final class EppController {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private static final Clock clock = new SystemClock();

  /**
   * Read an EPP envelope from the client, find the matching flow, execute it, and return
   * the response marshalled to a byte array.
   */
  public static byte[] handleEppCommand(byte[] inputXmlBytes, SessionMetadata sessionMetadata) {
    Trid trid = null;
    EppMetrics metrics = new EppMetrics();
    metrics.setRequestId(
        ApiProxy.getCurrentEnvironment().getAttributes().get(
            "com.google.appengine.runtime.request_log_id").toString());
    try {
      EppInput eppInput = unmarshal(inputXmlBytes);
      trid = Trid.create(eppInput.getCommandWrapper().getClTrid());
      ImmutableList<String> targetIds = eppInput.getTargetIds();
      metrics.setCommandName(eppInput.getCommandName());
      metrics.setClientId(sessionMetadata.getClientId());
      metrics.setPrivilegeLevel(
          sessionMetadata.isSuperuser()
              ? UserPrivileges.SUPERUSER.toString()
              : UserPrivileges.NORMAL.toString());
      if (!targetIds.isEmpty()) {
        metrics.setEppTarget(Joiner.on(",").join(targetIds));
      }

      FlowRunner flowRunner = new FlowRunner(
          getFlowClass(eppInput),
          eppInput,
          trid,
          sessionMetadata,
          inputXmlBytes,
          metrics);
      EppOutput eppOutput = flowRunner.run(
          sessionMetadata.isDryRun() ? CommitMode.DRY_RUN : CommitMode.LIVE,
          sessionMetadata.isSuperuser() ? UserPrivileges.SUPERUSER : UserPrivileges.NORMAL);
      if (eppOutput.isResponse()) {
        metrics.setEppStatus(eppOutput.getResponse().getResult().getCode());
      }
      return marshalWithLenientRetry(eppOutput);
    } catch (EppException e) {
      // The command failed. Send the client an error message.
      metrics.setEppStatus(e.getResult().getCode());
      return marshalWithLenientRetry(getErrorResponse(e.getResult(), trid));
    } catch (Throwable e) {
      // Something bad and unexpected happened. Send the client a generic error, and log it.
      logger.severe(e, "Unexpected failure");
      metrics.setEppStatus(Code.CommandFailed);
      return marshalWithLenientRetry(getErrorResponse(Result.create(Code.CommandFailed), trid));
    } finally {
      metrics.export();
    }
  }

  /** Create a response indicating an Epp failure. */
  @VisibleForTesting
  static EppOutput getErrorResponse(Result result, Trid trid) {
    // Create TRID (without a clTRID) if one hasn't been created yet, as it's necessary to construct
    // a valid response. This can happen if the error occurred before we could even parse out the
    // clTRID (e.g. if a syntax error occurred parsing the supplied XML).
    return EppOutput.create(new Response.Builder()
        .setTrid(trid == null ? Trid.create(null) : trid)
        .setResult(result)
        .setExecutionTime(clock.nowUtc())
        .build());
  }
}
