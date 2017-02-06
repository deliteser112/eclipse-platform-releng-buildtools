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

package google.registry.dns;

import static google.registry.model.EppResourceUtils.loadByForeignKey;

import google.registry.dns.DnsConstants.TargetType;
import google.registry.model.EppResource;
import google.registry.model.EppResource.ForeignKeyedEppResource;
import google.registry.model.annotations.ExternalMessagingName;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.request.Parameter;
import google.registry.util.Clock;
import javax.inject.Inject;

/** Action that manually triggers refresh of DNS information. */
@Action(path = "/_dr/dnsRefresh", automaticallyPrintOk = true)
public final class RefreshDnsAction implements Runnable {

  @Inject Clock clock;
  @Inject DnsQueue dnsQueue;
  @Inject @Parameter("domainOrHostName") String domainOrHostName;
  @Inject @Parameter("type") TargetType type;
  @Inject RefreshDnsAction() {}

  @Override
  public void run() {
    if (!domainOrHostName.contains(".")) {
      throw new BadRequestException("URL parameter 'name' must be fully qualified");
    }
    switch (type) {
      case DOMAIN:
        loadAndVerifyExistence(DomainResource.class, domainOrHostName);
        dnsQueue.addDomainRefreshTask(domainOrHostName);
        break;
      case HOST:
        verifyHostIsSubordinate(loadAndVerifyExistence(HostResource.class, domainOrHostName));
        dnsQueue.addHostRefreshTask(domainOrHostName);
        break;
      default:
        throw new BadRequestException("Unsupported type: " + type);
    }
  }

  private <T extends EppResource & ForeignKeyedEppResource>
      T loadAndVerifyExistence(Class<T> clazz, String foreignKey) {
    T resource = loadByForeignKey(clazz, foreignKey, clock.nowUtc());
    if (resource == null) {
      String typeName = clazz.getAnnotation(ExternalMessagingName.class).value();
      throw new NotFoundException(
          String.format("%s %s not found", typeName, domainOrHostName));
    }
    return resource;
  }

  private static void verifyHostIsSubordinate(HostResource host) {
    if (!host.isSubordinate()) {
      throw new BadRequestException(
          String.format("%s isn't a subordinate hostname", host.getFullyQualifiedHostName()));
    }
  }
}
