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

package google.registry.flows.host;

import static com.google.common.base.MoreObjects.firstNonNull;
import static google.registry.flows.host.HostFlowUtils.lookupSuperordinateDomain;
import static google.registry.flows.host.HostFlowUtils.validateHostName;
import static google.registry.flows.host.HostFlowUtils.verifyDomainIsSameRegistrar;
import static google.registry.model.index.ForeignKeyIndex.loadAndGetReference;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.isNullOrEmpty;

import com.google.common.collect.ImmutableMap;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import google.registry.dns.DnsQueue;
import google.registry.flows.EppException;
import google.registry.flows.EppException.ObjectAlreadyExistsException;
import google.registry.flows.EppException.ParameterValueRangeErrorException;
import google.registry.flows.EppException.RequiredParameterMissingException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.flows.ResourceUpdateFlow;
import google.registry.flows.async.AsyncFlowUtils;
import google.registry.flows.async.DnsRefreshForHostRenameAction;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostCommand.Update;
import google.registry.model.host.HostResource;
import google.registry.model.host.HostResource.Builder;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.reporting.HistoryEntry;
import java.util.Objects;
import javax.inject.Inject;
import org.joda.time.Duration;

/**
 * An EPP flow that updates a host resource.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceMutateFlow.ResourceToMutateDoesNotExistException}
 * @error {@link google.registry.flows.ResourceUpdateFlow.ResourceHasClientUpdateProhibitedException}
 * @error {@link google.registry.flows.ResourceUpdateFlow.StatusNotClientSettableException}
 * @error {@link google.registry.flows.SingleResourceFlow.ResourceStatusProhibitsOperationException}
 * @error {@link HostFlowUtils.HostNameTooShallowException}
 * @error {@link HostFlowUtils.InvalidHostNameException}
 * @error {@link HostFlowUtils.SuperordinateDomainDoesNotExistException}
 * @error {@link CannotAddIpToExternalHostException}
 * @error {@link CannotRemoveSubordinateHostLastIpException}
 * @error {@link HostAlreadyExistsException}
 * @error {@link RenameHostToExternalRemoveIpException}
 * @error {@link RenameHostToSubordinateRequiresIpException}
 */
public class HostUpdateFlow extends ResourceUpdateFlow<HostResource, Builder, Update> {

  private Ref<DomainResource> superordinateDomain;

  private String oldHostName;
  private String newHostName;
  private boolean isHostRename;

  @Inject HostUpdateFlow() {}

  @Override
  protected void initResourceCreateOrMutateFlow() throws EppException {
    String suppliedNewHostName = command.getInnerChange().getFullyQualifiedHostName();
    isHostRename = suppliedNewHostName != null;
    oldHostName = targetId;
    newHostName = firstNonNull(suppliedNewHostName, oldHostName);
    superordinateDomain =
        lookupSuperordinateDomain(validateHostName(newHostName), now);
  }

  @Override
  protected void verifyUpdateIsAllowed() throws EppException {
    verifyDomainIsSameRegistrar(superordinateDomain, getClientId());
    if (isHostRename
        && loadAndGetReference(HostResource.class, newHostName, now) != null) {
      throw new HostAlreadyExistsException(newHostName);
    }
  }

  @Override
  protected void verifyNewUpdatedStateIsAllowed() throws EppException {
    boolean wasExternal = existingResource.getSuperordinateDomain() == null;
    boolean wasSubordinate = !wasExternal;
    boolean willBeExternal = superordinateDomain == null;
    boolean willBeSubordinate = !willBeExternal;
    boolean newResourceHasIps = !isNullOrEmpty(newResource.getInetAddresses());
    boolean commandAddsIps = !isNullOrEmpty(command.getInnerAdd().getInetAddresses());
    // These checks are order-dependent. For example a subordinate-to-external rename that adds new
    // ips should hit the first exception, whereas one that only fails to remove the existing ips
    // should hit the second.
    if (willBeExternal && commandAddsIps) {
      throw new CannotAddIpToExternalHostException();
    }
    if (wasSubordinate && willBeExternal && newResourceHasIps) {
      throw new RenameHostToExternalRemoveIpException();
    }
    if (wasExternal && willBeSubordinate && !commandAddsIps) {
      throw new RenameHostToSubordinateRequiresIpException();
    }
    if (willBeSubordinate && !newResourceHasIps) {
      throw new CannotRemoveSubordinateHostLastIpException();
    }
  }

  @Override
  protected Builder setUpdateProperties(Builder builder) {
    // The superordinateDomain can be null if the new name is external.
    // Note that the value of superordinateDomain is projected to the current time inside of
    // the lookupSuperordinateDomain(...) call above, so that it will never be stale.
    builder.setSuperordinateDomain(superordinateDomain);
    builder.setLastSuperordinateChange(superordinateDomain == null ? null : now);
    // Rely on the host's cloneProjectedAtTime() method to handle setting of transfer data.
    return builder.build().cloneProjectedAtTime(now).asBuilder();
  }

  /** Keep the {@link ForeignKeyIndex} for this host up to date. */
  @Override
  protected void modifyRelatedResources() {
    if (isHostRename) {
      // Update the foreign key for the old host name.
      ofy().save().entity(ForeignKeyIndex.create(existingResource, now));
      // Save the foreign key for the new host name.
      ofy().save().entity(ForeignKeyIndex.create(newResource, newResource.getDeletionTime()));
      updateSuperordinateDomains();
    }
  }

  @Override
  public void enqueueTasks() throws EppException {
    DnsQueue dnsQueue = DnsQueue.create();
    // Only update DNS for subordinate hosts. External hosts have no glue to write, so they
    // are only written as NS records from the referencing domain.
    if (existingResource.getSuperordinateDomain() != null) {
      dnsQueue.addHostRefreshTask(oldHostName);
    }
    // In case of a rename, there are many updates we need to queue up.
    if (isHostRename) {
      // If the renamed host is also subordinate, then we must enqueue an update to write the new
      // glue.
      if (newResource.getSuperordinateDomain() != null) {
        dnsQueue.addHostRefreshTask(newHostName);
      }
      // We must also enqueue updates for all domains that use this host as their nameserver so
      // that their NS records can be updated to point at the new name.
      AsyncFlowUtils.enqueueMapreduceAction(
          DnsRefreshForHostRenameAction.class,
          ImmutableMap.of(
              DnsRefreshForHostRenameAction.PARAM_HOST_KEY,
              Key.create(existingResource).getString()),
          Duration.ZERO);
    }
  }

  @Override
  protected final HistoryEntry.Type getHistoryEntryType() {
    return HistoryEntry.Type.HOST_UPDATE;
  }

  private void updateSuperordinateDomains() {
    Ref<DomainResource> oldSuperordinateDomain = existingResource.getSuperordinateDomain();
    if (oldSuperordinateDomain != null || superordinateDomain != null) {
      if (Objects.equals(oldSuperordinateDomain, superordinateDomain)) {
        ofy().save().entity(oldSuperordinateDomain.get().asBuilder()
            .removeSubordinateHost(oldHostName)
            .addSubordinateHost(newHostName)
            .build());
      } else {
        if (oldSuperordinateDomain != null) {
          ofy().save().entity(
              oldSuperordinateDomain.get()
                  .asBuilder()
                  .removeSubordinateHost(oldHostName)
                  .build());
        }
        if (superordinateDomain != null) {
          ofy().save().entity(
              superordinateDomain.get()
                  .asBuilder()
                  .addSubordinateHost(newHostName)
                  .build());
        }
      }
    }
  }

  /** Host with specified name already exists. */
  static class HostAlreadyExistsException extends ObjectAlreadyExistsException {
    public HostAlreadyExistsException(String hostName) {
      super(String.format("Object with given ID (%s) already exists", hostName));
    }
  }

  /** Cannot add ip addresses to an external host. */
  static class CannotAddIpToExternalHostException extends ParameterValueRangeErrorException {
    public CannotAddIpToExternalHostException() {
      super("Cannot add ip addresses to external hosts");
    }
  }

  /** Cannot remove all ip addresses from a subordinate host. */
  static class CannotRemoveSubordinateHostLastIpException
      extends StatusProhibitsOperationException {
    public CannotRemoveSubordinateHostLastIpException() {
      super("Cannot remove all ip addresses from a subordinate host");
    }
  }

  /** Host rename from external to subordinate must also add an ip addresses. */
  static class RenameHostToSubordinateRequiresIpException
      extends RequiredParameterMissingException {
    public RenameHostToSubordinateRequiresIpException() {
      super("Host rename from external to subordinate must also add an ip address");
    }
  }

  /** Host rename from subordinate to external must also remove all ip addresses. */
  static class RenameHostToExternalRemoveIpException extends ParameterValueRangeErrorException {
    public RenameHostToExternalRemoveIpException() {
      super("Host rename from subordinate to external must also remove all ip addresses");
    }
  }
}
