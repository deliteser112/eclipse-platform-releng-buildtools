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

package google.registry.tools.server;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.assertTldsExist;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.POST;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableSet;
import google.registry.model.EppResource;
import google.registry.model.EppResourceUtils;
import google.registry.model.domain.DomainResource;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.auth.Auth;
import google.registry.util.Clock;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** An action that lists domains, for use by the {@code nomulus list_domains} command. */
@Action(
  path = ListDomainsAction.PATH,
  method = {GET, POST},
  auth = Auth.AUTH_INTERNAL_OR_ADMIN
)
public final class ListDomainsAction extends ListObjectsAction<DomainResource> {

  /** An App Engine limitation on how many subqueries can be used in a single query. */
  private static final int MAX_NUM_SUBQUERIES = 30;
  public static final String PATH = "/_dr/admin/list/domains";

  @Inject @Parameter("tlds") ImmutableSet<String> tlds;
  @Inject @Parameter("limit") int limit;
  @Inject Clock clock;
  @Inject ListDomainsAction() {}

  @Override
  public ImmutableSet<String> getPrimaryKeyFields() {
    return ImmutableSet.of("fullyQualifiedDomainName");
  }

  @Override
  public ImmutableSet<DomainResource> loadObjects() {
    checkArgument(!tlds.isEmpty(), "Must specify TLDs to query");
    checkArgument(
        tlds.size() <= MAX_NUM_SUBQUERIES,
        "Cannot query more than %s TLDs simultaneously",
        MAX_NUM_SUBQUERIES);
    assertTldsExist(tlds);
    DateTime now = clock.nowUtc();
    return ofy()
        .load()
        .type(DomainResource.class)
        .filter("tld in", tlds)
        // Get the N most recently created domains (requires ordering in descending order).
        .order("-creationTime")
        .limit(limit)
        .list()
        .stream()
        .map(EppResourceUtils.transformAtTime(now))
        // Deleted entities must be filtered out post-query because queries don't allow ordering
        // with two filters.
        .filter(d -> d.getDeletionTime().isAfter(now))
        // Sort back to ascending order for nicer display.
        .sorted(comparing(EppResource::getCreationTime))
        .collect(toImmutableSet());
  }
}
