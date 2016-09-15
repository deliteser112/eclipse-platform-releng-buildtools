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
import google.registry.config.RegistryEnvironment;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.model.EppResource;
import google.registry.model.eppinput.ResourceCommand.ResourceCheck;
import google.registry.model.eppoutput.CheckData;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import java.util.List;

/**
 * An EPP flow that checks whether resources can be provisioned.
 *
 * @param <R> the resource type being manipulated
 * @param <C> the overall command type doing the manipulation.
 */
public abstract class ResourceCheckFlow<R extends EppResource, C extends ResourceCheck>
    extends ResourceFlow<R, C> {

  protected List<String> targetIds;

  @Override
  protected final void initResourceFlow() throws EppException {
    this.targetIds = command.getTargetIds();
    initCheckResourceFlow();
  }

  @Override
  protected final EppOutput runResourceFlow() throws EppException {
    return createOutput(
        SUCCESS,
        getCheckData(),
        getResponseExtensions());
  }

  @Override
  protected final void verifyIsAllowed() throws EppException {
    if (targetIds.size() > RegistryEnvironment.get().config().getMaxChecks()) {
      throw new TooManyResourceChecksException();
    }
  }

  @SuppressWarnings("unused")
  protected void initCheckResourceFlow() throws EppException {}

  /** Subclasses must implement this to return the check data. */
  protected abstract CheckData getCheckData();

  /** Subclasses may override this to return extensions. */
  @SuppressWarnings("unused")
  protected ImmutableList<? extends ResponseExtension> getResponseExtensions() throws EppException {
    return null;
  }

  /** Too many resource checks requested in one check command. */
  public static class TooManyResourceChecksException extends ParameterValuePolicyErrorException {
    public TooManyResourceChecksException() {
      super(String.format(
          "No more than %s resources may be checked at a time",
          RegistryEnvironment.get().config().getMaxChecks()));
    }
  }
}

