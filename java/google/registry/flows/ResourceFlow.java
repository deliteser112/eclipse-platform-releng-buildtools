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

import google.registry.model.EppResource;
import google.registry.model.eppinput.EppInput.ResourceCommandWrapper;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.util.TypeUtils.TypeInstantiator;

/**
 * An EPP flow that addresses a stored resource.
 *
 * @param <R> the resource type being manipulated
 * @param <C> the command type doing the manipulation.
 */
public abstract class ResourceFlow<R extends EppResource, C extends ResourceCommand>
    extends LoggedInFlow {

  protected C command;
  protected Class<R> resourceClass;

  @Override
  @SuppressWarnings("unchecked")
  protected final void initLoggedInFlow() throws EppException {
    this.command = (C) ((ResourceCommandWrapper) eppInput.getCommandWrapper().getCommand())
        .getResourceCommand();
    this.resourceClass = new TypeInstantiator<R>(getClass()){}.getExactType();
    initResourceFlow();
  }

  /** Resource flows can override this for custom initialization.*/
  protected abstract void initResourceFlow() throws EppException;

  /**
   * Loads the target resource and performs authorization and state allowance checks on it before
   * delegating to {@link #runResourceFlow()}.
   *
   * @throws EppException If an error occurred while manipulating the resource.
   */
  @Override
  public final EppOutput run() throws EppException {
    verifyIsAllowed();
    return runResourceFlow();
  }

  /**
   * Verifies that the command is allowed on the target resource.
   *
   * @throws EppException If the command is not allowed on this resource.
   */
  protected abstract void verifyIsAllowed() throws EppException;

  /**
   * Run the flow.
   *
   * @throws EppException If something fails while manipulating the resource.
   */
  protected abstract EppOutput runResourceFlow() throws EppException;
}
