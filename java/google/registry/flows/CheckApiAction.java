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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.io.Resources.getResource;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static google.registry.model.domain.fee.Fee.FEE_EXTENSION_URIS;
import static google.registry.model.eppcommon.ProtocolDefinition.ServiceExtension.FEE_0_11;
import static google.registry.model.eppcommon.ProtocolDefinition.ServiceExtension.FEE_0_12;
import static google.registry.model.eppcommon.ProtocolDefinition.ServiceExtension.FEE_0_6;
import static google.registry.model.registry.Registries.findTldForNameOrThrow;
import static google.registry.util.DomainNameUtils.canonicalizeDomainName;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.json.simple.JSONValue.toJSONString;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.net.InternetDomainName;
import com.google.common.net.MediaType;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.tofu.SoyTofu;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import google.registry.flows.soy.DomainCheckFeeEppSoyInfo;
import google.registry.model.domain.fee.FeeCheckResponseExtension;
import google.registry.model.eppoutput.CheckData.DomainCheck;
import google.registry.model.eppoutput.CheckData.DomainCheckData;
import google.registry.model.eppoutput.EppResponse;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.RequestParameters;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/**
 * A servlet that returns availability and premium checks as JSON.
 *
 * <p>This action returns plain JSON without a safety prefix, so it's vital that the output not be
 * user controlled, lest it open an XSS vector. Do not modify this to return the domain name in the
 * response.
 */
@Action(path = "/check")
public class CheckApiAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private static final SoyTofu TOFU =
      SoyFileSet.builder().add(getResource(DomainCheckFeeEppSoyInfo.class,
          DomainCheckFeeEppSoyInfo.getInstance().getFileName())).build().compileToTofu();

  @Inject @Parameter("domain") String domain;
  @Inject Response response;
  @Inject EppController eppController;
  @Inject @Config("checkApiServletRegistrarClientId") String checkApiServletRegistrarClientId;
  @Inject CheckApiAction() {}

  @Override
  public void run() {
    response.setHeader("Content-Disposition", "attachment");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    response.setContentType(MediaType.JSON_UTF_8);
    response.setPayload(toJSONString(doCheck()));
  }

  private Map<String, Object> doCheck() {
    String domainString;
    try {
      domainString = canonicalizeDomainName(nullToEmpty(domain));
      // Validate the TLD.
      findTldForNameOrThrow(InternetDomainName.from(domainString));
    } catch (IllegalStateException | IllegalArgumentException e) {
      return fail("Must supply a valid domain name on an authoritative TLD");
    }
    try {
      byte[] inputXml = TOFU
          .newRenderer(DomainCheckFeeEppSoyInfo.DOMAINCHECKFEE)
          .setData(ImmutableMap.of("domainName", domainString))
          .render()
          .getBytes(UTF_8);
      SessionMetadata sessionMetadata =
          new StatelessRequestSessionMetadata(checkApiServletRegistrarClientId, FEE_EXTENSION_URIS);
      EppResponse response = eppController
          .handleEppCommand(
              sessionMetadata,
              new PasswordOnlyTransportCredentials(),
              EppRequestSource.CHECK_API,
              false,  // This endpoint is never a dry run.
              false,  // This endpoint is never a superuser.
              inputXml)
          .getResponse();
      if (!response.getResult().getCode().isSuccess()) {
        return fail(response.getResult().getMsg());
      }
      DomainCheckData checkData = (DomainCheckData) response.getResponseData().get(0);
      DomainCheck check = (DomainCheck) checkData.getChecks().get(0);
      boolean available = check.getName().getAvail();
      ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
      builder
          .put("status", "success")
          .put("available", available);
      if (available) {
        FeeCheckResponseExtension<?> feeCheckResponseExtension =
            (FeeCheckResponseExtension<?>) response.getFirstExtensionOfType(
                FEE_0_12.getResponseExtensionClass(),
                FEE_0_11.getResponseExtensionClass(),
                FEE_0_6.getResponseExtensionClass());
        if (feeCheckResponseExtension != null) {
          builder.put("tier",
              firstNonNull(
                  Iterables.getOnlyElement(feeCheckResponseExtension.getItems()).getFeeClass(),
                  "standard"));
        }
      } else {
        builder.put("reason", check.getReason());
      }
      return builder.build();
    } catch (Exception e) {
      logger.warning(e, "Unknown error");
      return fail("Invalid request");
    }
  }

  private Map<String, Object> fail(String reason) {
    return ImmutableMap.<String, Object>of(
        "status", "error",
        "reason", reason);
  }

  /** Dagger module for the check api endpoint. */
  @Module
  public static final class CheckApiModule {
    @Provides
    @Parameter("domain")
    static String provideDomain(HttpServletRequest req) {
      return RequestParameters.extractRequiredParameter(req, "domain");
    }
  }
}
