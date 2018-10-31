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

package google.registry.rdap;

import static google.registry.flows.domain.DomainFlowUtils.validateDomainName;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;
import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.collect.ImmutableMap;
import google.registry.flows.EppException;
import google.registry.model.domain.DomainResource;
import google.registry.rdap.RdapJsonFormatter.OutputDataType;
import google.registry.rdap.RdapMetrics.EndpointType;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.request.auth.Auth;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** RDAP (new WHOIS) action for domain requests. */
@Action(
  path = RdapDomainAction.PATH,
  method = {GET, HEAD},
  isPrefix = true,
  auth = Auth.AUTH_PUBLIC
)
public class RdapDomainAction extends RdapActionBase {

  public static final String PATH = "/rdap/domain/";

  @Inject public RdapDomainAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "domain name";
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  @Override
  public EndpointType getEndpointType() {
    return EndpointType.DOMAIN;
  }

  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest) {
    DateTime now = clock.nowUtc();
    pathSearchString = canonicalizeName(pathSearchString);
    try {
      validateDomainName(pathSearchString);
    } catch (EppException e) {
      throw new BadRequestException(
          String.format(
              "%s is not a valid %s: %s",
              pathSearchString, getHumanReadableObjectTypeName(), e.getMessage()));
    }
    // The query string is not used; the RDAP syntax is /rdap/domain/mydomain.com.
    DomainResource domainResource =
        loadByForeignKey(
            DomainResource.class, pathSearchString, shouldIncludeDeleted() ? START_OF_TIME : now);
    if ((domainResource == null) || !shouldBeVisible(domainResource, now)) {
      throw new NotFoundException(pathSearchString + " not found");
    }
    return rdapJsonFormatter.makeRdapJsonForDomain(
        domainResource,
        true,
        fullServletPath,
        rdapWhoisServer,
        now,
        OutputDataType.FULL,
        getAuthorization());
  }
}
