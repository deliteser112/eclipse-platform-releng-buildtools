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

package google.registry.flows.domain;

import static google.registry.flows.EppXmlTransformer.unmarshal;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.flows.domain.DomainFlowUtils.verifyLaunchApplicationIdMatchesDomain;

import com.google.common.collect.ImmutableList;
import google.registry.flows.EppException;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.flows.EppException.RequiredParameterMissingException;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainApplication.Builder;
import google.registry.model.domain.launch.LaunchInfoExtension;
import google.registry.model.domain.launch.LaunchInfoResponseExtension;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import google.registry.model.mark.Mark;
import google.registry.model.smd.EncodedSignedMark;
import google.registry.model.smd.SignedMark;
import javax.inject.Inject;

/**
 * An EPP flow that reads a domain application.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link google.registry.flows.ResourceQueryFlow.ResourceToQueryDoesNotExistException}
 * @error {@link DomainFlowUtils.ApplicationDomainNameMismatchException}
 * @error {@link DomainApplicationInfoFlow.ApplicationLaunchPhaseMismatchException}
 * @error {@link DomainApplicationInfoFlow.MissingApplicationIdException}
 */
public class DomainApplicationInfoFlow extends BaseDomainInfoFlow<DomainApplication, Builder> {

  private boolean includeMarks;

  @Inject DomainApplicationInfoFlow() {}

  @Override
  protected final void initSingleResourceFlow() throws EppException {
    registerExtensions(LaunchInfoExtension.class);
    // We need to do this in init rather than verify or we'll get the generic "object not found".
    LaunchInfoExtension extension = eppInput.getSingleExtension(LaunchInfoExtension.class);
    if (extension.getApplicationId() == null) {
      throw new MissingApplicationIdException();
    }
    includeMarks = Boolean.TRUE.equals(extension.getIncludeMark());  // Default to false.
  }

  @Override
  protected final void verifyQueryIsAllowed() throws EppException {
    verifyLaunchApplicationIdMatchesDomain(command, existingResource);
    if (!existingResource.getPhase().equals(
        eppInput.getSingleExtension(LaunchInfoExtension.class).getPhase())) {
      throw new ApplicationLaunchPhaseMismatchException();
    }
  }

  @Override
  protected final DomainApplication getResourceInfo() throws EppException {
    // We don't support authInfo for applications, so if it's another registrar always fail.
    verifyResourceOwnership(getClientId(), existingResource);
    if (!command.getHostsRequest().requestDelegated()) {
      // Delegated hosts are present by default, so clear them out if they aren't wanted.
      // This requires overriding the implicit status values so that we don't get INACTIVE added due
      // to the missing nameservers.
      return existingResource.asBuilder()
          .setNameservers(null)
          .buildWithoutImplicitStatusValues();
    }
    return existingResource;
  }

  @Override
  protected final ImmutableList<? extends ResponseExtension> getDomainResponseExtensions()
      throws EppException {
    ImmutableList.Builder<Mark> marksBuilder = new ImmutableList.Builder<>();
    if (includeMarks) {
      for (EncodedSignedMark encodedMark : existingResource.getEncodedSignedMarks()) {
        try {
          marksBuilder.add(unmarshal(SignedMark.class, encodedMark.getBytes()).getMark());
        } catch (EppException e) {
          // This is a serious error; don't let the benign EppException propagate.
          throw new IllegalStateException("Could not decode a stored encoded signed mark");
        }
      }
    }
    return ImmutableList.of(new LaunchInfoResponseExtension.Builder()
        .setPhase(existingResource.getPhase())
        .setApplicationId(existingResource.getForeignKey())
        .setApplicationStatus(existingResource.getApplicationStatus())
        .setMarks(marksBuilder.build())
        .build());
  }

  /** Application id is required. */
  static class MissingApplicationIdException extends RequiredParameterMissingException {
    public MissingApplicationIdException() {
      super("Application id is required");
    }
  }

  /** Declared launch extension phase does not match phase of the application. */
  static class ApplicationLaunchPhaseMismatchException extends ParameterValuePolicyErrorException {
    public ApplicationLaunchPhaseMismatchException() {
      super("Declared launch extension phase does not match the phase of the application");
    }
  }
}
