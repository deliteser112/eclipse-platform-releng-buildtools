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

package google.registry.security;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import google.registry.request.HttpException;
import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Secure servlet that speaks JSON for both input and output.
 *
 * <p>This servlet accepts only JSON inputs (using the payload) and returns only JSON
 * responses, using and various security best practices such as a parser breaker,
 * {@code Content-Disposition: attachment}, etc.
 *
 * @see JsonHttp
 */
public abstract class JsonTransportServlet extends XsrfProtectedServlet {

  protected JsonTransportServlet(String xsrfScope, boolean requireAdmin) {
    super(xsrfScope, requireAdmin);
  }

  /**
   * Verify that this is a well-formed request and then execute it. A well-formed request will have
   * either a JSON string in the "json" param that evaluates to a map, or nothing in "json".
   */
  @Override
  protected final void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    Map<String, ?> input = JsonHttp.read(req);
    if (input == null) {
      rsp.sendError(SC_BAD_REQUEST, "Malformed JSON");
      return;
    }
    Map<String, ?> output;
    try {
      output = doJsonPost(req, input);
    } catch (HttpException e) {
      e.send(rsp);
      return;
    }
    checkNotNull(output, "doJsonPost() returned null");
    rsp.setStatus(SC_OK);
    JsonHttp.write(rsp, output);
  }

  /**
   * Handler for HTTP POST requests.
   *
   * @param req Servlet request object.
   * @param input JSON request object or empty if none was provided.
   * @return an arbitrary JSON object. Must not be {@code null}.
   * @throws HttpException in order to send a non-200 status code / message to the client.
   */
  public abstract Map<String, Object> doJsonPost(HttpServletRequest req, Map<String, ?> input);
}
