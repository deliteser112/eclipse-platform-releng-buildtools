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

package com.google.domain.registry.flows;

import static com.google.common.io.ByteStreams.toByteArray;
import static com.google.domain.registry.flows.EppServletUtils.handleEppCommandAndWriteResponse;

import com.google.domain.registry.util.FormattingLogger;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The {@link EppTlsServlet} class establishes a transport for EPP+TLS over* HTTP. All commands and
 * responses are EPP XML according to RFC 5730. Commands must must requested via POST.
 * <p>
 * There are a number of expected headers to this endpoint:
 * <dl>
 *   <dt>{@value #SSL_CLIENT_CERTIFICATE_HASH_FIELD}
 *   <dd>
 *     This field should contain a base64 encoded digest of the client's TLS certificate. It is
 *     validated during an EPP login command against a known good value that is transmitted out of
 *     band.
 *   <dt>{@value #FORWARDED_FOR_FIELD}
 *   <dd>
 *     This field should contain the host and port of the connecting client. It is validated during
 *     an EPP login command against an IP whitelist that is transmitted out of band.
 *   <dt>{@value #REQUESTED_SERVERNAME_VIA_SNI_FIELD}
 *   <dd>
 *     This field should contain the servername that the client requested during the TLS handshake.
 *     It is unused, but expected to be present in the GFE-proxied configuration.
 * </dl>
 */
public class EppTlsServlet extends HttpServlet {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  static final String REQUESTED_SERVERNAME_VIA_SNI_FIELD = "X-GFE-Requested-Servername-SNI";
  static final String FORWARDED_FOR_FIELD = "X-Forwarded-For";
  static final String SSL_CLIENT_CERTIFICATE_HASH_FIELD = "X-GFE-SSL-Certificate";

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    // Check that SNI header is present.  This is a signal that we're receiving traffic proxied by a
    // GFE, which is the expectation of this servlet.  The value is unused.
    TlsCredentials tlsCredentials = new TlsCredentials(req);
    if (!tlsCredentials.hasSni()) {
      logger.warning("Request did not include required SNI header.");
    }
    SessionMetadata sessionMetadata = new HttpSessionMetadata(tlsCredentials, req.getSession(true));
    // Note that we are using the raw input stream rather than the reader, which implies that we are
    // ignoring the HTTP-specified charset (if any) in favor of whatever charset the XML declares.
    // This is ok because this code is only called from the proxy, which can't specify a charset
    // (it blindly copies bytes off a socket).
    handleEppCommandAndWriteResponse(toByteArray(req.getInputStream()), rsp, sessionMetadata);
  }
}
