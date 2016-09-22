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

package google.registry.rdap;

import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;

import com.google.common.collect.ImmutableMap;
import google.registry.model.domain.DomainResource;
import google.registry.rdap.RdapJsonFormatter.OutputDataType;
import google.registry.request.Action;
import google.registry.request.HttpException.NotFoundException;
import google.registry.util.Clock;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * RDAP (new WHOIS) action for domain requests.
 */
@Action(path = RdapDomainAction.PATH, method = {GET, HEAD}, isPrefix = true)
public class RdapDomainAction extends RdapActionBase {

  public static final String PATH = "/rdap/domain/";

  @Inject Clock clock;
  @Inject RdapDomainAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "domain name";
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest, String linkBase) {
    DateTime now = clock.nowUtc();
    pathSearchString = canonicalizeName(pathSearchString);
    validateDomainName(pathSearchString);
    // The query string is not used; the RDAP syntax is /rdap/domain/mydomain.com.
    DomainResource domainResource = loadByForeignKey(DomainResource.class, pathSearchString, now);
    if (domainResource == null) {
      throw new NotFoundException(pathSearchString + " not found");
    }
    return RdapJsonFormatter.makeRdapJsonForDomain(
        domainResource, true, rdapLinkBase, rdapWhoisServer, now, OutputDataType.FULL);
  }
}
