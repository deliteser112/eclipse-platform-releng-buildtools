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

import static google.registry.model.EppResourceUtils.loadByUniqueId;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import google.registry.flows.EppException.StatusProhibitsOperationException;
import google.registry.model.EppResource;
import google.registry.model.domain.launch.ApplicationIdTargetExtension;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import java.util.Set;

/**
 * An EPP flow that manipulates a single stored resource.
 *
 * @param <R> the resource type being manipulated
 * @param <C> the command type doing the manipulation.
 */
public abstract class SingleResourceFlow<R extends EppResource, C extends SingleResourceCommand>
    extends ResourceFlow<R, C> {

  protected R existingResource;
  protected String targetId;

  @Override
  protected final void initResourceFlow() throws EppException {
    targetId = getTargetId();
    // In a transactional flow, loading the resource will be expensive because it can't be cached.
    // Allow flows to optionally fail fast here before loading.
    failfast();
    // Loads the target resource if it exists
    // Some flows such as DomainApplicationInfoFlow have the id marked as optional in the schema.
    // We require it by policy in the relevant flow, but here we need to make sure not to NPE when
    // initializing the (obviously nonexistent) existing resource.
    existingResource = (targetId == null || !tryToLoadExisting())
        ? null
        : loadByUniqueId(resourceClass, targetId, now);
    if (existingResource != null) {
      Set<StatusValue> problems = Sets.intersection(
          existingResource.getStatusValues(), getDisallowedStatuses());
      if (!problems.isEmpty()) {
        throw new ResourceStatusProhibitsOperationException(problems);
      }
    }
    initSingleResourceFlow();
  }

  /**
   * Returns whether the resource flow should attempt to load an existing resource with the
   * matching targetId.  Defaults to true, but overriding flows can set to false to bypass loading
   * of existing resources.
   */
  protected boolean tryToLoadExisting() {
    return true;
  }

  /**
   * Get the target id from {@link SingleResourceCommand}. If there is a launch extension present,
   * it overrides that target id with its application id, so return that instead. There will never
   * be more than one launch extension.
   */
  protected final String getTargetId() {
    ApplicationIdTargetExtension extension =
        eppInput.getSingleExtension(ApplicationIdTargetExtension.class);
    return extension == null ? command.getTargetId() : extension.getApplicationId();
  }

  /** Subclasses can optionally override this to fail before loading {@link #existingResource}. */
  @SuppressWarnings("unused")
  protected void failfast() throws EppException {}

  /** Subclasses can optionally override this for further initialization. */
  @SuppressWarnings("unused")
  protected void initSingleResourceFlow() throws EppException {}

  protected Set<StatusValue> getDisallowedStatuses() {
    return ImmutableSet.of();
  }

  /** Resource status prohibits this operation. */
  public static class ResourceStatusProhibitsOperationException
      extends StatusProhibitsOperationException {
    public ResourceStatusProhibitsOperationException(Set<StatusValue> status) {
      super("Operation disallowed by status: " + Joiner.on(", ").join(status));
    }
  }
}
