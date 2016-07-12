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

package google.registry.flows.domain;

import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.validateDomainName;
import static google.registry.flows.domain.DomainFlowUtils.validateDomainNameWithIdnTables;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import google.registry.flows.EppException;
import google.registry.flows.ResourceCheckFlow;
import google.registry.model.domain.DomainCommand.Check;
import google.registry.model.domain.DomainResource;
import java.util.Map;

/** An EPP flow that checks whether a domain can be provisioned. */
public abstract class BaseDomainCheckFlow extends ResourceCheckFlow<DomainResource, Check> {

  protected Map<String, InternetDomainName> domainNames;

  @Override
  protected final void initCheckResourceFlow() throws EppException {
    ImmutableMap.Builder<String, InternetDomainName> domains = new ImmutableMap.Builder<>();
    ImmutableSet.Builder<String> tlds = new ImmutableSet.Builder<>();
    for (String targetId : ImmutableSet.copyOf(targetIds)) {
      // This validation is moderately expensive, so cache the results for getCheckData to use too.
      InternetDomainName domainName = validateDomainName(targetId);
      tlds.add(domainName.parent().toString());
      validateDomainNameWithIdnTables(domainName);
      domains.put(targetId, domainName);
    }
    for (String tld : tlds.build()) {
      checkAllowedAccessToTld(getAllowedTlds(), tld);
      checkRegistryStateForTld(tld);
    }
    domainNames = domains.build();
    initDomainCheckFlow();
  }

  @SuppressWarnings("unused")
  protected void initDomainCheckFlow() throws EppException {}
}
