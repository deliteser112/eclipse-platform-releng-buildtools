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
import static com.google.common.base.Preconditions.checkState;
import static google.registry.model.EppResourceUtils.isDeleted;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DiffUtils.prettyPrintEntityDeepDiff;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.model.reporting.HistoryEntry;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.ConfirmingCommand;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
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
public class FixDomainNameserverKeysCommand extends ConfirmingCommand implements RemoteApiCommand {

  @Parameter(description = "Fully-qualified domain names", required = true)
  private List<String> mainParameters;

  private final LinkedHashMap<DomainResource, DomainResource> domainUpdates = new LinkedHashMap<>();
  private final LinkedHashMap<DomainResource, HistoryEntry> historyEntries = new LinkedHashMap<>();

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
      DomainResource updatedDomain = domain.asBuilder().setNameservers(nameservers.build()).build();
      domainUpdates.put(domain, updatedDomain);
      historyEntries.put(updatedDomain, new HistoryEntry.Builder()
          .setClientId("CharlestonRoad")
          .setParent(updatedDomain)
          .setType(HistoryEntry.Type.DOMAIN_UPDATE)
          .setReason("Fixing keys to deleted host resources, see b/35258209")
          .build());
    }
  }

  /** Returns the changes that have been staged thus far. */
  @Override
  protected String prompt() {
    ImmutableList.Builder<String> updates = new ImmutableList.Builder<>();
    for (Entry<DomainResource, DomainResource> entry : domainUpdates.entrySet()) {
      updates.add(prettyPrintEntityDeepDiff(
          entry.getKey().toDiffableFieldMap(), entry.getValue().toDiffableFieldMap()));
      updates.add(historyEntries.get(entry.getValue()).toString());
    }
    return Joiner.on("\n").join(updates.build());
  }

  @Override
  protected String execute() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      @SuppressWarnings("unchecked")
      public void vrun() {
        for (Entry<DomainResource, DomainResource> entry : domainUpdates.entrySet()) {
          DomainResource existingDomain = entry.getKey();
          checkState(
              Objects.equals(existingDomain, ofy().load().entity(existingDomain).now()),
              "Domain %s changed since init() was called.",
              existingDomain.getFullyQualifiedDomainName());
          HistoryEntry historyEntryWithModificationTime =
              historyEntries.get(entry.getValue()).asBuilder()
                  .setModificationTime(ofy().getTransactionTime())
                  .build();
          ofy().save().entities(entry.getValue(), historyEntryWithModificationTime).now();
        }
      }});
    return String.format("Updated %d domains.", domainUpdates.size());
  }
}
