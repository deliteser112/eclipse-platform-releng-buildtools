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

package google.registry.flows;

import static google.registry.model.eppoutput.Result.Code.SUCCESS;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.flows.EppException.ParameterValueRangeErrorException;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.model.EppResource;
import google.registry.model.EppResource.Builder;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand.AddRemoveSameValueException;
import google.registry.model.eppinput.ResourceCommand.ResourceUpdate;
import google.registry.model.eppoutput.EppOutput;
import java.util.Set;

/**
 * An EPP flow that mutates a single stored resource.
 *
 * @param <R> the resource type being changed
 * @param <B> a builder for the resource
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class ResourceUpdateFlow
    <R extends EppResource, B extends Builder<R, ?>, C extends ResourceUpdate<?, ? super B, ?>>
    extends OwnedResourceMutateFlow<R, C> {

  /**
   * Note that CLIENT_UPDATE_PROHIBITED is intentionally not in this list. This is because it
   * requires special checking, since you must be able to clear the status off the object with an
   * update.
   */
  private static final Set<StatusValue> UPDATE_DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_UPDATE_PROHIBITED);

  @Override
  protected Set<StatusValue> getDisallowedStatuses() {
    return UPDATE_DISALLOWED_STATUSES;
  }

  @Override
  protected final void verifyMutationOnOwnedResourceAllowed() throws EppException {
    for (StatusValue statusValue : Sets.union(
        command.getInnerAdd().getStatusValues(),
        command.getInnerRemove().getStatusValues())) {
      if (!isSuperuser && !statusValue.isClientSettable()) {  // The superuser can set any status.
        throw new StatusNotClientSettableException(statusValue.getXmlName());
      }
    }
    verifyUpdateIsAllowed();
  }

  @Override
  protected final R createOrMutateResource() throws EppException {
    @SuppressWarnings("unchecked")
    B builder = (B) existingResource.asBuilder();
    try {
      command.applyTo(builder);
    } catch (AddRemoveSameValueException e) {
      throw new AddRemoveSameValueEppException();
    }
    builder.setLastEppUpdateTime(now).setLastEppUpdateClientId(getClientId());
    return setUpdateProperties(builder).build();
  }

  @Override
  protected final void verifyNewStateIsAllowed() throws EppException {
    // If the resource is marked with clientUpdateProhibited, and this update did not clear that
    // status, then the update must be disallowed (unless a superuser is requesting the change).
    if (!isSuperuser
        && existingResource.getStatusValues().contains(StatusValue.CLIENT_UPDATE_PROHIBITED)
        && newResource.getStatusValues().contains(StatusValue.CLIENT_UPDATE_PROHIBITED)) {
      throw new ResourceHasClientUpdateProhibitedException();
    }
    verifyNewUpdatedStateIsAllowed();
  }

  /** Subclasses may override this to do more specific checks on the new state after the update. */
  @SuppressWarnings("unused")
  protected void verifyNewUpdatedStateIsAllowed() throws EppException {}

  @SuppressWarnings("unused")
  protected void verifyUpdateIsAllowed() throws EppException {}

  @SuppressWarnings("unused")
  protected B setUpdateProperties(B builder) throws EppException {
    return builder;
  }

  @Override
  protected final EppOutput getOutput() {
    return createOutput(SUCCESS);
  }

  /** The specified status value cannot be set by clients. */
  public static class StatusNotClientSettableException extends ParameterValueRangeErrorException {
    public StatusNotClientSettableException(String statusValue) {
      super(String.format("Status value %s cannot be set by clients", statusValue));
    }
  }

  /** This resource has clientUpdateProhibited on it, and the update does not clear that status. */
  public static class ResourceHasClientUpdateProhibitedException
      extends StatusProhibitsOperationException {
    public ResourceHasClientUpdateProhibitedException() {
      super("Operation disallowed by status: clientUpdateProhibited");
    }
  }

  /** Cannot add and remove the same value. */
  public static class AddRemoveSameValueEppException extends ParameterValuePolicyErrorException {
    public AddRemoveSameValueEppException() {
      super("Cannot add and remove the same value");
    }
  }
}
