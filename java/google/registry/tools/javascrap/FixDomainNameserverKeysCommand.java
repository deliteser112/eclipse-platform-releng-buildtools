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

package google.registry.tools.javascrap;

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.model.EppResourceUtils.isDeleted;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.tools.MutatingCommand;
import java.util.List;
import org.joda.time.DateTime;

/**
 * Scrap tool to fix bad host keys on domains.
 *
 * <p>Some domains have been found to have keys to hosts that have been deleted (likely as part of
 * the merging of hosts across TLDs), so this command loads all hosts currently pointed to by the
 * nameserver keys, checks for any that are deleted, and replaces the deleted host's keys with
 * the non-deleted versions (as determined by loading the host's foreign key). See b/35258209.
 */
@Parameters(separators = " =", commandDescription = "Fix bad host keys on domains.")
public class FixDomainNameserverKeysCommand extends MutatingCommand {

  @Parameter(description = "Fully-qualified domain names", required = true)
  private List<String> mainParameters;

  @Override
  protected void init() throws Exception {
    DateTime now = DateTime.now(UTC);
    for (String domainName : mainParameters) {
      DomainResource domain = checkNotNull(loadByForeignKey(DomainResource.class, domainName, now));
      ImmutableSet.Builder<Key<HostResource>> nameservers = new ImmutableSet.Builder<>();
      for (Key<HostResource> hostKey : domain.getNameservers()) {
        HostResource existingHost = ofy().load().key(hostKey).now();
        if (isDeleted(existingHost, now)) {
          HostResource correctHost =
              checkNotNull(
                  loadByForeignKey(
                      HostResource.class,
                      existingHost.getFullyQualifiedHostName(),
                      now));
          System.out.printf(
              "Domain: %s, Host: %s, Old ROID: %s, New ROID: %s%n",
              domain.getFullyQualifiedDomainName(),
              existingHost.getFullyQualifiedHostName(),
              existingHost.getRepoId(),
              correctHost.getRepoId());
          nameservers.add(Key.create(correctHost));
        } else {
          nameservers.add(hostKey);
        }
      }
      stageEntityChange(domain, domain.asBuilder().setNameservers(nameservers.build()).build());
    }
  }
}
