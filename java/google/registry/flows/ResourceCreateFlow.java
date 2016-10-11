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

package google.registry.flows;

import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.annotations.VisibleForTesting;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException.ObjectAlreadyExistsException;
import google.registry.model.EppResource;
import google.registry.model.EppResource.Builder;
import google.registry.model.EppResource.ForeignKeyedEppResource;
import google.registry.model.domain.DomainApplication;
import google.registry.model.eppinput.ResourceCommand.ResourceCreateOrChange;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.index.DomainApplicationIndex;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.util.TypeUtils.TypeInstantiator;
import javax.annotation.Nullable;

/**
 * An EPP flow that creates a storable resource.
 *
 * @param <R> the resource type being changed
 * @param <B> a builder for the resource
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class ResourceCreateFlow
    <R extends EppResource,
     B extends Builder<R, ?>,
     C extends ResourceCreateOrChange<? super B> & SingleResourceCommand>
    extends ResourceCreateOrMutateFlow<R, C> {

  @Override
  protected void initRepoId() {
    repoId = createFlowRepoId();
  }

  @Nullable
  protected abstract String createFlowRepoId();

  @Override
  protected final void verifyIsAllowed() throws EppException {
    if (existingResource != null) {
      throw new ResourceAlreadyExistsException(targetId);
    }
    verifyCreateIsAllowed();
  }

  @Override
  protected final R createOrMutateResource() throws EppException {
    B builder = new TypeInstantiator<B>(getClass()){}.instantiate();
    command.applyTo(builder);
    builder
        .setCreationClientId(getClientId())
        .setCurrentSponsorClientId(getClientId())
        .setRepoId(getResourceKey().getName());
    setCreateProperties(builder);
    return builder.build();
  }

  /**
   * Save a new or updated {@link ForeignKeyIndex} and {@link EppResourceIndex} pointing to what we
   * created.
   */
  @Override
  protected final void modifyRelatedResources() {
    if (newResource instanceof ForeignKeyedEppResource) {
      ofy().save().entity(ForeignKeyIndex.create(newResource, newResource.getDeletionTime()));
    } else if (newResource instanceof DomainApplication) {
      ofy().save().entity(
          DomainApplicationIndex.createUpdatedInstance((DomainApplication) newResource));
    }
    ofy().save().entity(EppResourceIndex.create(Key.create(newResource)));
    modifyCreateRelatedResources();
  }

  /** Modify any other resources that need to be informed of this create. */
  protected void modifyCreateRelatedResources() {}

  /** Check resource-specific invariants before allowing the create to proceed. */
  @SuppressWarnings("unused")
  protected void verifyCreateIsAllowed() throws EppException {}

  /** Set any resource-specific properties before creating. */
  @SuppressWarnings("unused")
  protected void setCreateProperties(B builder) throws EppException {}

  /** Resource with this id already exists. */
  public static class ResourceAlreadyExistsException extends ObjectAlreadyExistsException {

    /** Whether this was thrown from a "failfast" context. Useful for testing. */
    final boolean failfast;

    public ResourceAlreadyExistsException(String resourceId, boolean failfast) {
      super(String.format("Object with given ID (%s) already exists", resourceId));
      this.failfast = failfast;
    }

    public ResourceAlreadyExistsException(String resourceId) {
      this(resourceId, false);
    }

    @VisibleForTesting
    public boolean isFailfast() {
      return failfast;
    }
  }
}
