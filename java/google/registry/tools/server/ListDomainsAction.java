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

package google.registry.tools.server;

import static google.registry.model.EppResourceUtils.queryNotDeleted;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.POST;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import google.registry.model.domain.DomainResource;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.util.Clock;
import java.util.Comparator;
import javax.inject.Inject;

/** An action that lists domains, for use by the registry_tool list_domains command. */
@Action(path = ListDomainsAction.PATH, method = {GET, POST})
public final class ListDomainsAction extends ListObjectsAction<DomainResource> {

  public static final String PATH = "/_dr/admin/list/domains";
  public static final String TLD_PARAM = "tld";

  @Inject @Parameter("tld") String tld;
  @Inject Clock clock;
  @Inject ListDomainsAction() {}

  @Override
  public ImmutableSet<String> getPrimaryKeyFields() {
    return ImmutableSet.of("fullyQualifiedDomainName");
  }

  @Override
  public ImmutableSet<DomainResource> loadObjects() {
    return FluentIterable
        .from(queryNotDeleted(DomainResource.class, clock.nowUtc(), "tld", assertTldExists(tld)))
        .toSortedSet(new Comparator<DomainResource>() {
          @Override
          public int compare(DomainResource a, DomainResource b) {
            return a.getFullyQualifiedDomainName().compareTo(b.getFullyQualifiedDomainName());
          }});
  }
}
