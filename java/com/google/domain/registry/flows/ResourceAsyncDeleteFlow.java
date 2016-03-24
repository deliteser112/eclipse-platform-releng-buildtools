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

package com.google.domain.registry.flows;

import static com.google.domain.registry.model.eppoutput.Result.Code.SuccessWithActionPending;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;

import com.google.domain.registry.flows.EppException.AssociationProhibitsOperationException;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.EppResource.Builder;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import com.google.domain.registry.model.eppoutput.Result.Code;
import com.google.domain.registry.model.index.ForeignKeyIndex;

import com.googlecode.objectify.Work;

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
        return isLinkedForFailfast(ReferenceUnion.create(fki.getReference()));
      }
    });
    if (isLinked) {
      throw new ResourceToDeleteIsReferencedException();
    }
  }

  /** Subclasses must override this to check if the supplied reference has incoming links. */
  protected abstract boolean isLinkedForFailfast(ReferenceUnion<R> ref);

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
