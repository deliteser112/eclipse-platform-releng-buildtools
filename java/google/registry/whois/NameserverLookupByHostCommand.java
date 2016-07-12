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

package google.registry.whois;

import com.google.common.net.InternetDomainName;
import google.registry.model.host.HostResource;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/** Represents a WHOIS lookup on a nameserver based on its hostname. */
final class NameserverLookupByHostCommand extends DomainOrHostLookupCommand<HostResource> {

  NameserverLookupByHostCommand(InternetDomainName hostName) {
    this(hostName, null);
  }

  NameserverLookupByHostCommand(InternetDomainName hostName, @Nullable InternetDomainName tld) {
    super(hostName, tld, "Nameserver");
  }

  @Override
  WhoisResponse getSuccessResponse(HostResource host, DateTime now) {
    return new NameserverWhoisResponse(host, now);
  }
}
