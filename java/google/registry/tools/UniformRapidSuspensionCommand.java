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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.difference;
import static google.registry.model.EppResourceUtils.checkResourcesExist;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.data.SoyMapData;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import google.registry.model.domain.DomainResource;
import google.registry.model.domain.ReferenceUnion;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.soy.UniformRapidSuspensionSoyInfo;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A command to suspend a domain for the Uniform Rapid Suspension process. */
@Parameters(separators = " =",
    commandDescription = "Suspend a domain for Uniform Rapid Suspension.")
final class UniformRapidSuspensionCommand extends MutatingEppToolCommand implements GtechCommand {

  private static final ImmutableSet<String> URS_LOCKS = ImmutableSet.of(
      StatusValue.SERVER_DELETE_PROHIBITED.getXmlName(),
      StatusValue.SERVER_TRANSFER_PROHIBITED.getXmlName(),
      StatusValue.SERVER_UPDATE_PROHIBITED.getXmlName());

  /** Client id that made this change. Only recorded in the history entry. **/
  private static final String CLIENT_ID = "CharlestonRoad";

  @Parameter(
      names = {"-n", "--domain_name"},
      description = "Domain to suspend.",
      required = true)
  private String domainName;

  @Parameter(
      names = {"-h", "--hosts"},
      description = "Comma-delimited set of fully qualified host names to replace the current hosts"
          + " on the domain.")
  private List<String> newHosts = new ArrayList<>();

  @Parameter(
      names = {"-p", "--preserve"},
      description = "Comma-delimited set of locks to preserve (only valid with --undo). "
          + "Valid locks: serverDeleteProhibited, serverTransferProhibited, serverUpdateProhibited")
  private List<String> locksToPreserve = new ArrayList<>();

  @Parameter(
      names = {"--undo"},
      description = "Flag indicating that is is an undo command, which removes locks.")
  private boolean undo;

  /** Set of existing locks that need to be preserved during undo, sorted for nicer output. */
  ImmutableSortedSet<String> existingLocks;

  /** Set of existing nameservers that need to be restored during undo, sorted for nicer output. */
  ImmutableSortedSet<String> existingNameservers;

  @Override
  protected void initMutatingEppToolCommand() {
    superuser = true;
    DateTime now = DateTime.now(UTC);
    ImmutableSet<String> newHostsSet = ImmutableSet.copyOf(newHosts);
    DomainResource domain = loadByUniqueId(DomainResource.class, domainName, now);
    checkArgument(domain != null, "Domain '%s' does not exist", domainName);
    Set<String> missingHosts =
        difference(newHostsSet, checkResourcesExist(HostResource.class, newHosts, now));
    checkArgument(missingHosts.isEmpty(), "Hosts do not exist: %s", missingHosts);
    checkArgument(
        locksToPreserve.isEmpty() || undo,
        "Locks can only be preserved when running with --undo");
    existingNameservers = getExistingNameservers(domain);
    existingLocks = getExistingLocks(domain);
    setSoyTemplate(
        UniformRapidSuspensionSoyInfo.getInstance(),
        UniformRapidSuspensionSoyInfo.UNIFORMRAPIDSUSPENSION);
    addSoyRecord(CLIENT_ID, new SoyMapData(
        "domainName", domainName,
        "hostsToAdd", difference(newHostsSet, existingNameservers),
        "hostsToRemove", difference(existingNameservers, newHostsSet),
        "locksToApply", undo ? ImmutableSet.of() : URS_LOCKS,
        "locksToRemove",
            undo ? difference(URS_LOCKS, ImmutableSet.copyOf(locksToPreserve)) : ImmutableSet.of(),
        "reason", (undo ? "Undo " : "") + "Uniform Rapid Suspension"));
  }

  private ImmutableSortedSet<String> getExistingNameservers(DomainResource domain) {
    ImmutableSortedSet.Builder<String> nameservers = ImmutableSortedSet.naturalOrder();
    for (ReferenceUnion<HostResource> nameserverRef : domain.getNameservers()) {
      nameservers.add(nameserverRef.getLinked().get().getForeignKey());
    }
    return nameservers.build();
  }

  private ImmutableSortedSet<String> getExistingLocks(DomainResource domain) {
    ImmutableSortedSet.Builder<String> locks = ImmutableSortedSet.naturalOrder();
    for (StatusValue lock : domain.getStatusValues()) {
      if (URS_LOCKS.contains(lock.getXmlName())) {
        locks.add(lock.getXmlName());
      }
    }
    return locks.build();
  }

  @Override
  protected String postExecute() throws Exception {
    if (undo) {
      return "";
    }
    StringBuilder undoBuilder = new StringBuilder("UNDO COMMAND:\n\ngtech_tool -e ")
        .append(RegistryToolEnvironment.get())
        .append(" uniform_rapid_suspension --undo --domain_name ")
        .append(domainName);
    if (!existingNameservers.isEmpty()) {
      undoBuilder.append(" --hosts ").append(Joiner.on(',').join(existingNameservers));
    }
    if (!existingLocks.isEmpty()) {
      undoBuilder.append(" --preserve ").append(Joiner.on(',').join(existingLocks));
    }
    return undoBuilder.toString();
  }
}
