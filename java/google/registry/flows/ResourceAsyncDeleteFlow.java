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

import static google.registry.model.eppoutput.Result.Code.SuccessWithActionPending;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.googlecode.objectify.Ref;
import com.googlecode.objectify.Work;
import google.registry.flows.EppException.AssociationProhibitsOperationException;
import google.registry.model.EppResource;
import google.registry.model.EppResource.Builder;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.eppoutput.Result.Code;
import google.registry.model.index.ForeignKeyIndex;

/**
 * An EPP flow that deletes a resource asynchronously (i.e. via mapreduce).
 *
 * @param <R> the resource type being changed
 * @param <B> a builder for the resource
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class ResourceAsyncDeleteFlow
    <R extends EppResource, B extends Builder<R, ?>, C extends SingleResourceCommand>
    extends ResourceDeleteFlow<R, C> {

  @Override
  public void failfast() throws ResourceToDeleteIsReferencedException {
    // Enter a transactionless context briefly.
    boolean isLinked = ofy().doTransactionless(new Work<Boolean>() {
      @Override
      public Boolean run() {
        ForeignKeyIndex<R> fki = ForeignKeyIndex.load(resourceClass, targetId, now);
        if (fki == null) {
          // Don't failfast on non-existence. We could, but that would duplicate code paths in a way
          // that would be hard to reason about, and there's no real gain in doing so.
          return false;
        }
        return isLinkedForFailfast(fki.getReference());
      }
    });
    if (isLinked) {
      throw new ResourceToDeleteIsReferencedException();
    }
  }

  /** Subclasses must override this to check if the supplied reference has incoming links. */
  protected abstract boolean isLinkedForFailfast(Ref<R> ref);

  @Override
  protected final R createOrMutateResource() {
    @SuppressWarnings("unchecked")
    B builder = (B) existingResource.asBuilder().addStatusValue(StatusValue.PENDING_DELETE);
    return builder.build();
  }

  /** Subclasses can override this to return a different success result code. */
  @Override
  protected Code getDeleteResultCode() {
    return SuccessWithActionPending;
  }

  /** Resource to be deleted has active incoming references. */
  public static class ResourceToDeleteIsReferencedException
      extends AssociationProhibitsOperationException {
    public ResourceToDeleteIsReferencedException() {
      super("Resource to be deleted has active incoming references");
    }
  }
}
