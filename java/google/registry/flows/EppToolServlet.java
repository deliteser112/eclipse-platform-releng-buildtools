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

import static com.google.domain.registry.flows.EppServletUtils.handleEppCommandAndWriteResponse;

import com.google.common.io.ByteStreams;
import com.google.domain.registry.flows.SessionMetadata.SessionSource;
import com.google.domain.registry.model.eppcommon.ProtocolDefinition;
import com.google.domain.registry.security.XsrfProtectedServlet;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This servlet runs EPP commands directly without logging in. It verifies an XSRF token that could
 * only come from the tool.
 */
public class EppToolServlet extends XsrfProtectedServlet {

  /** Used to verify XSRF tokens. */
  public static final String XSRF_SCOPE = "admin";

  public EppToolServlet() {
    super(XSRF_SCOPE, true);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    byte[] xml = ByteStreams.toByteArray(req.getInputStream());
    handleEppCommandAndWriteResponse(
        xml, rsp, new StatelessRequestSessionMetadata(
            req.getParameter("clientIdentifier"),
            Boolean.parseBoolean(req.getParameter("superuser")),
            Boolean.parseBoolean(req.getParameter("dryRun")),
            ProtocolDefinition.getVisibleServiceExtensionUris(),
            SessionSource.TOOL));
  }
}
