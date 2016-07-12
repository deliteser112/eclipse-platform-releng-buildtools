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

import static google.registry.flows.EppXmlTransformer.marshalWithLenientRetry;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.net.MediaType;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;

/** Handle an EPP request and response. */
public class EppRequestHandler {

  private static final MediaType APPLICATION_EPP_XML =
      MediaType.create("application", "epp+xml").withCharset(UTF_8);

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject EppController eppController;
  @Inject Response response;
  @Inject EppRequestHandler() {}

  /** Handle an EPP request and write out a servlet response. */
  public void executeEpp(
      SessionMetadata sessionMetadata,
      TransportCredentials credentials,
      EppRequestSource eppRequestSource,
      boolean isDryRun,
      boolean isSuperuser,
      byte[] inputXmlBytes) {
    try {
      response.setPayload(new String(
          marshalWithLenientRetry(
              eppController.handleEppCommand(
                  sessionMetadata,
                  credentials,
                  eppRequestSource,
                  isDryRun,
                  isSuperuser,
                  inputXmlBytes)),
          UTF_8));
      response.setContentType(APPLICATION_EPP_XML);
      // Note that we always return 200 (OK) even if the EppController returns an error response.
      // This is because returning an non-OK HTTP status code will cause the proxy server to
      // silently close the connection without returning any data. The only time we will ever return
      // a non-OK status (400) is if we fail to muster even an EPP error response message. In that
      // case it's better to close the connection than to return garbage.
      response.setStatus(SC_OK);
    } catch (Exception e) {
      logger.warning(e, "handleEppCommand general exception");
      response.setStatus(SC_BAD_REQUEST);
    }
  }
}
