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

import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;

import com.google.common.collect.ImmutableMap;
import google.registry.model.host.HostResource;
import google.registry.request.Action;
import google.registry.request.HttpException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.util.Clock;
import javax.inject.Inject;

/**
 * RDAP (new WHOIS) action for nameserver requests.
 */
@Action(path = RdapNameserverAction.PATH, method = {GET, HEAD}, isPrefix = true)
public class RdapNameserverAction extends RdapActionBase {

  public static final String PATH = "/rdap/nameserver/";

  @Inject Clock clock;
  @Inject RdapNameserverAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "nameserver";
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest, String linkBase) throws HttpException {
    pathSearchString = canonicalizeName(pathSearchString);
    // The RDAP syntax is /rdap/nameserver/ns1.mydomain.com.
    validateDomainName(pathSearchString);
    HostResource hostResource =
        loadByUniqueId(HostResource.class, pathSearchString, clock.nowUtc());
    if (hostResource == null) {
      throw new NotFoundException(pathSearchString + " not found");
    }
    return RdapJsonFormatter.makeRdapJsonForHost(hostResource, true, rdapLinkBase, rdapWhoisServer);
  }
}
