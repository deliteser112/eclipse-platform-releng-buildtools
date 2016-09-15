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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.model.EppResource;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import google.registry.model.eppoutput.Result.Code;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * An EPP flow that deletes an {@link EppResource}.
 *
 * @param <R> the resource type being changed
 * @param <C> the command type, marshalled directly from the epp xml
 */
public abstract class ResourceDeleteFlow<R extends EppResource, C extends SingleResourceCommand>
    extends OwnedResourceMutateFlow<R, C> {

  private static final Set<StatusValue> DELETE_DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.LINKED,
      StatusValue.CLIENT_DELETE_PROHIBITED,
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_DELETE_PROHIBITED);

  /** This is intentionally non-final so that subclasses can override the disallowed statuses. */
  @Override
  protected Set<StatusValue> getDisallowedStatuses() {
    return DELETE_DISALLOWED_STATUSES;
  }

  @Override
  protected final EppOutput getOutput() {
    return createOutput(getDeleteResultCode(), null, getDeleteResponseExtensions());
  }

  /** Subclasses can override this to return a different success result code. */
  protected Code getDeleteResultCode() {
    return SUCCESS;
  }

  /** Subclasses can override this to return response extensions. */
  @Nullable
  protected ImmutableList<? extends ResponseExtension> getDeleteResponseExtensions() {
    return null;
  }
}
