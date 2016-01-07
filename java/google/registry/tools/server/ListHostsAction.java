// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.POST;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import google.registry.model.EppResourceUtils;
import google.registry.model.host.HostResource;
import google.registry.request.Action;
import google.registry.util.Clock;
import java.util.Comparator;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** An action that lists hosts, for use by the {@code nomulus list_hosts} command. */
@Action(path = ListHostsAction.PATH, method = {GET, POST})
public final class ListHostsAction extends ListObjectsAction<HostResource> {

  public static final String PATH = "/_dr/admin/list/hosts";

  private static final Comparator<HostResource> comparator =
      new Comparator<HostResource>() {
          @Override
          public int compare(HostResource host1, HostResource host2) {
            return host1.getFullyQualifiedHostName()
                .compareTo(host2.getFullyQualifiedHostName());
          }};

  @Inject Clock clock;
  @Inject ListHostsAction() {}

  @Override
  public ImmutableSet<String> getPrimaryKeyFields() {
    return ImmutableSet.of("fullyQualifiedHostName");
  }

  @Override
  public ImmutableSet<HostResource> loadObjects() {
    final DateTime now = clock.nowUtc();
    return FluentIterable
        .from(ofy().load().type(HostResource.class))
        .filter(new Predicate<HostResource>() {
            @Override
            public boolean apply(HostResource host) {
              return EppResourceUtils.isActive(host, now);
            }})
        .toSortedSet(comparator);
  }
}
