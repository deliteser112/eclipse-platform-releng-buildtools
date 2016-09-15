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
import static google.registry.flows.ResourceFlowUtils.verifyNoDisallowedStatuses;
import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfoForResource;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.flows.host.HostFlowUtils.lookupSuperordinateDomain;
import static google.registry.flows.host.HostFlowUtils.validateHostName;
import static google.registry.flows.host.HostFlowUtils.verifyDomainIsSameRegistrar;
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.model.index.ForeignKeyIndex.loadAndGetKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.isNullOrEmpty;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.googlecode.objectify.Key;
import google.registry.dns.DnsQueue;
import google.registry.flows.EppException;
import google.registry.flows.EppException.ObjectAlreadyExistsException;
import google.registry.flows.EppException.ParameterValueRangeErrorException;
import google.registry.flows.EppException.RequiredParameterMissingException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.LoggedInFlow;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.async.AsyncFlowUtils;
import google.registry.flows.async.DnsRefreshForHostRenameAction;
import google.registry.flows.exceptions.AddRemoveSameValueEppException;
import google.registry.flows.exceptions.ResourceHasClientUpdateProhibitedException;
import google.registry.flows.exceptions.ResourceToMutateDoesNotExistException;
import google.registry.flows.exceptions.StatusNotClientSettableException;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppinput.ResourceCommand.AddRemoveSameValueException;
import google.registry.model.eppoutput.EppOutput;
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
 * @error {@link google.registry.flows.exceptions.ResourceHasClientUpdateProhibitedException}
 * @error {@link google.registry.flows.exceptions.ResourceStatusProhibitsOperationException}
 * @error {@link google.registry.flows.exceptions.ResourceToMutateDoesNotExistException}
 * @error {@link google.registry.flows.exceptions.StatusNotClientSettableException}
 * @error {@link HostFlowUtils.HostNameTooShallowException}
 * @error {@link HostFlowUtils.InvalidHostNameException}
 * @error {@link HostFlowUtils.SuperordinateDomainDoesNotExistException}
 * @error {@link CannotAddIpToExternalHostException}
 * @error {@link CannotRemoveSubordinateHostLastIpException}
 * @error {@link HostAlreadyExistsException}
 * @error {@link RenameHostToExternalRemoveIpException}
 * @error {@link RenameHostToSubordinateRequiresIpException}
 */
public class HostUpdateFlow extends LoggedInFlow implements TransactionalFlow {

  /**
   * Note that CLIENT_UPDATE_PROHIBITED is intentionally not in this list. This is because it
   * requires special checking, since you must be able to clear the status off the object with an
   * update.
   */
  private static final ImmutableSet<StatusValue> DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_UPDATE_PROHIBITED);

  @Inject ResourceCommand resourceCommand;
  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject HostUpdateFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(MetadataExtension.class);
  }

  @Override
  public final EppOutput run() throws EppException {
    Update command = (Update) resourceCommand;
    String suppliedNewHostName = command.getInnerChange().getFullyQualifiedHostName();
    String targetId = command.getTargetId();
    HostResource existingResource = loadByUniqueId(HostResource.class, targetId, now);
    if (existingResource == null) {
      throw new ResourceToMutateDoesNotExistException(HostResource.class, targetId);
    }
    boolean isHostRename = suppliedNewHostName != null;
    String oldHostName = targetId;
    String newHostName = firstNonNull(suppliedNewHostName, oldHostName);
    Optional<DomainResource> superordinateDomain =
        Optional.fromNullable(lookupSuperordinateDomain(validateHostName(newHostName), now));
    verifyUpdateAllowed(command, existingResource, superordinateDomain.orNull());
    if (isHostRename && loadAndGetKey(HostResource.class, newHostName, now) != null) {
      throw new HostAlreadyExistsException(newHostName);
    }
    Builder builder = existingResource.asBuilder();
    try {
      command.applyTo(builder);
    } catch (AddRemoveSameValueException e) {
      throw new AddRemoveSameValueEppException();
    }
    builder
        .setLastEppUpdateTime(now)
        .setLastEppUpdateClientId(clientId)
        // The superordinateDomain can be missing if the new name is external.
        // Note that the value of superordinateDomain is projected to the current time inside of
        // the lookupSuperordinateDomain(...) call above, so that it will never be stale.
        .setSuperordinateDomain(
            superordinateDomain.isPresent() ? Key.create(superordinateDomain.get()) : null)
        .setLastSuperordinateChange(superordinateDomain == null ? null : now);
    // Rely on the host's cloneProjectedAtTime() method to handle setting of transfer data.
    HostResource newResource = builder.build().cloneProjectedAtTime(now);
    verifyHasIpsIffIsExternal(command, existingResource, newResource);
    ImmutableSet.Builder<ImmutableObject> entitiesToSave = new ImmutableSet.Builder<>();
    entitiesToSave.add(newResource);
    // Keep the {@link ForeignKeyIndex} for this host up to date.
    if (isHostRename) {
      // Update the foreign key for the old host name and save one for the new host name.
      entitiesToSave.add(
          ForeignKeyIndex.create(existingResource, now),
          ForeignKeyIndex.create(newResource, newResource.getDeletionTime()));
      updateSuperordinateDomains(existingResource, newResource);
    }
    enqueueTasks(existingResource, newResource);
    entitiesToSave.add(historyBuilder
        .setType(HistoryEntry.Type.HOST_UPDATE)
        .setModificationTime(now)
        .setParent(Key.create(existingResource))
        .build());
    ofy().save().entities(entitiesToSave.build());
    return createOutput(SUCCESS);
  }

  private void verifyUpdateAllowed(
      Update command, HostResource existingResource, DomainResource superordinateDomain)
      throws EppException {
    verifyOptionalAuthInfoForResource(authInfo, existingResource);
    if (!isSuperuser) {
      verifyResourceOwnership(clientId, existingResource);
      // If the resource is marked with clientUpdateProhibited, and this update does not clear that
      // status, then the update must be disallowed (unless a superuser is requesting the change).
      if (!isSuperuser
          && existingResource.getStatusValues().contains(StatusValue.CLIENT_UPDATE_PROHIBITED)
          && !command.getInnerRemove().getStatusValues()
              .contains(StatusValue.CLIENT_UPDATE_PROHIBITED)) {
        throw new ResourceHasClientUpdateProhibitedException();
      }
   }
    for (StatusValue statusValue : Sets.union(
        command.getInnerAdd().getStatusValues(),
        command.getInnerRemove().getStatusValues())) {
      if (!isSuperuser && !statusValue.isClientSettable()) {  // The superuser can set any status.
        throw new StatusNotClientSettableException(statusValue.getXmlName());
      }
    }
    verifyDomainIsSameRegistrar(superordinateDomain, clientId);
    verifyNoDisallowedStatuses(existingResource, DISALLOWED_STATUSES);
  }

  void verifyHasIpsIffIsExternal(
      Update command, HostResource existingResource, HostResource newResource) throws EppException {
    boolean wasExternal = existingResource.getSuperordinateDomain() == null;
    boolean wasSubordinate = !wasExternal;
    boolean willBeExternal = newResource.getSuperordinateDomain() == null;
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

  public void enqueueTasks(HostResource existingResource, HostResource newResource) {
    // Only update DNS for subordinate hosts. External hosts have no glue to write, so they
    // are only written as NS records from the referencing domain.
    if (existingResource.getSuperordinateDomain() != null) {
      DnsQueue.create().addHostRefreshTask(existingResource.getFullyQualifiedHostName());
    }
    // In case of a rename, there are many updates we need to queue up.
    if (((Update) resourceCommand).getInnerChange().getFullyQualifiedHostName() != null) {
      // If the renamed host is also subordinate, then we must enqueue an update to write the new
      // glue.
      if (newResource.getSuperordinateDomain() != null) {
        DnsQueue.create().addHostRefreshTask(newResource.getFullyQualifiedHostName());
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

  private void updateSuperordinateDomains(HostResource existingResource, HostResource newResource) {
    Key<DomainResource> oldSuperordinateDomain = existingResource.getSuperordinateDomain();
    Key<DomainResource> newSuperordinateDomain = newResource.getSuperordinateDomain();
    if (oldSuperordinateDomain != null || newSuperordinateDomain != null) {
      if (Objects.equals(oldSuperordinateDomain, newSuperordinateDomain)) {
        ofy().save().entity(
            ofy().load().key(oldSuperordinateDomain).now().asBuilder()
                .removeSubordinateHost(existingResource.getFullyQualifiedHostName())
                .addSubordinateHost(newResource.getFullyQualifiedHostName())
                .build());
      } else {
        if (oldSuperordinateDomain != null) {
          ofy().save().entity(
              ofy().load().key(oldSuperordinateDomain).now()
                  .asBuilder()
                  .removeSubordinateHost(existingResource.getFullyQualifiedHostName())
                  .build());
        }
        if (newSuperordinateDomain != null) {
          ofy().save().entity(
              ofy().load().key(newSuperordinateDomain).now()
                  .asBuilder()
                  .addSubordinateHost(newResource.getFullyQualifiedHostName())
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
