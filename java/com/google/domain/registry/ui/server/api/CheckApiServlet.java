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

package com.google.domain.registry.ui.server.api;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.domain.registry.model.domain.DomainUtils.getTldFromDomainName;
import static com.google.domain.registry.model.eppcommon.ProtocolDefinition.ServiceExtension.FEE_0_6;
import static com.google.domain.registry.ui.server.SoyTemplateUtils.createTofuSupplier;
import static com.google.domain.registry.util.DomainNameUtils.canonicalizeDomainName;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.json.simple.JSONValue.toJSONString;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.EppXmlTransformer;
import com.google.domain.registry.flows.FlowRunner;
import com.google.domain.registry.flows.FlowRunner.CommitMode;
import com.google.domain.registry.flows.FlowRunner.UserPrivileges;
import com.google.domain.registry.flows.SessionMetadata.SessionSource;
import com.google.domain.registry.flows.StatelessRequestSessionMetadata;
import com.google.domain.registry.flows.domain.DomainCheckFlow;
import com.google.domain.registry.model.domain.fee.FeeCheckResponseExtension;
import com.google.domain.registry.model.domain.fee.FeeCheckResponseExtension.FeeCheck;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.eppinput.EppInput;
import com.google.domain.registry.model.eppoutput.CheckData.DomainCheck;
import com.google.domain.registry.model.eppoutput.CheckData.DomainCheckData;
import com.google.domain.registry.model.eppoutput.Response;
import com.google.domain.registry.ui.soy.api.DomainCheckFeeEppSoyInfo;
import com.google.template.soy.tofu.SoyTofu;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that returns availability and premium checks as json.
 * <p>
 * This servlet returns plain JSON without a safety prefix, so it's vital that the output not be
 * user controlled, lest it open an XSS vector. Do not modify this to return the domain name in the
 * response.
 */
public class CheckApiServlet extends HttpServlet {

  private static final Supplier<SoyTofu> TOFU_SUPPLIER =
      createTofuSupplier(DomainCheckFeeEppSoyInfo.getInstance());

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    Map<String, ?> response = doCheck(req.getParameter("domain"));
    rsp.setHeader("Content-Disposition", "attachment");
    rsp.setHeader("X-Content-Type-Options", "nosniff");
    rsp.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    rsp.setContentType(MediaType.JSON_UTF_8.toString());
    rsp.getWriter().write(toJSONString(response));
  }

  private StatelessRequestSessionMetadata sessionMetadata = new StatelessRequestSessionMetadata(
      RegistryEnvironment.get().config().getCheckApiServletRegistrarClientId(),
      false,
      false,
      ImmutableSet.of(FEE_0_6.getUri()),
      SessionSource.HTTP);

  // TODO(rgr): add whitebox instrumentation for this?
  private Map<String, ?> doCheck(String domainString) {
    try {
      domainString = canonicalizeDomainName(nullToEmpty(domainString));
      // Validate the TLD.
      getTldFromDomainName(domainString);
    } catch (IllegalStateException | IllegalArgumentException e) {
      return fail("Must supply a valid second level domain name");
    }
    try {
      byte[] inputXmlBytes = TOFU_SUPPLIER.get()
          .newRenderer(DomainCheckFeeEppSoyInfo.DOMAINCHECKFEE)
          .setData(ImmutableMap.of("domainName", domainString))
          .render()
          .getBytes(UTF_8);
      Response response = new FlowRunner(
          DomainCheckFlow.class,
          EppXmlTransformer.<EppInput>unmarshal(inputXmlBytes),
          Trid.create(CheckApiServlet.class.getSimpleName()),
          sessionMetadata,
          inputXmlBytes,
          null)
              .run(CommitMode.LIVE, UserPrivileges.NORMAL)
              .getResponse();
      DomainCheckData checkData = (DomainCheckData) response.getResponseData().get(0);
      DomainCheck check = (DomainCheck) checkData.getChecks().get(0);
      boolean available = check.getName().getAvail();
      ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<String, Object>()
          .put("status", "success")
          .put("available", available);
      if (available) {
        FeeCheckResponseExtension feeCheckResponse =
            (FeeCheckResponseExtension) response.getExtensions().get(0);
        FeeCheck feeCheck = feeCheckResponse.getChecks().get(0);
        builder.put("tier", firstNonNull(feeCheck.getFeeClass(), "standard"));
      } else {
        builder.put("reason", check.getReason());
      }
      return builder.build();
    } catch (EppException e) {
      return fail(e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
      return fail("Invalid request");
    }
  }

  private Map<String, String> fail(String reason) {
    return ImmutableMap.of(
        "status", "error",
        "reason", reason);
  }
}
