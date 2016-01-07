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

package google.registry.flows.domain;

import static google.registry.flows.EppXmlTransformer.unmarshal;
import static google.registry.flows.ResourceFlowUtils.verifyExistence;
import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfoForResource;
import static google.registry.flows.ResourceFlowUtils.verifyResourceOwnership;
import static google.registry.flows.domain.DomainFlowUtils.addSecDnsExtensionIfPresent;
import static google.registry.flows.domain.DomainFlowUtils.verifyApplicationDomainMatchesTargetId;
import static google.registry.model.EppResourceUtils.loadDomainApplication;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import google.registry.flows.EppException;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.flows.EppException.RequiredParameterMissingException;
import google.registry.flows.FlowModule.ApplicationId;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.LoggedInFlow;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainCommand.Info;
import google.registry.model.domain.launch.LaunchInfoExtension;
import google.registry.model.domain.launch.LaunchInfoResponseExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import google.registry.model.mark.Mark;
import google.registry.model.smd.EncodedSignedMark;
import google.registry.model.smd.SignedMark;
import javax.inject.Inject;

/**
 * An EPP flow that returns information about a domain application.
 *
 * <p>Only the registrar that owns the application can see its info. The flow can optionally include
 * delegated hosts in its response.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException}
 * @error {@link DomainFlowUtils.ApplicationDomainNameMismatchException}
 * @error {@link DomainApplicationInfoFlow.ApplicationLaunchPhaseMismatchException}
 * @error {@link MissingApplicationIdException}
 */
public final class DomainApplicationInfoFlow extends LoggedInFlow {

  @Inject ResourceCommand resourceCommand;
  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject @ApplicationId String applicationId;
  @Inject DomainApplicationInfoFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(LaunchInfoExtension.class);
  }

  @Override
  public final EppOutput run() throws EppException {
    if (applicationId.isEmpty()) {
      throw new MissingApplicationIdException();
    }
    DomainApplication application = verifyExistence(
        DomainApplication.class, applicationId, loadDomainApplication(applicationId, now));
    verifyApplicationDomainMatchesTargetId(application, targetId);
    verifyOptionalAuthInfoForResource(authInfo, application);
    LaunchInfoExtension launchInfo = eppInput.getSingleExtension(LaunchInfoExtension.class);
    if (!application.getPhase().equals(launchInfo.getPhase())) {
      throw new ApplicationLaunchPhaseMismatchException();
    }
    // We don't support authInfo for applications, so if it's another registrar always fail.
    verifyResourceOwnership(clientId, application);
    return createOutput(
        SUCCESS,
        getResourceInfo(application),
        getDomainResponseExtensions(application, launchInfo));
  }

  DomainApplication getResourceInfo(DomainApplication application) {
    if (!((Info) resourceCommand).getHostsRequest().requestDelegated()) {
      // Delegated hosts are present by default, so clear them out if they aren't wanted.
      // This requires overriding the implicit status values so that we don't get INACTIVE added due
      // to the missing nameservers.
      return application.asBuilder()
          .setNameservers(null)
          .buildWithoutImplicitStatusValues();
    }
    return application;
  }

  ImmutableList<ResponseExtension> getDomainResponseExtensions(
      DomainApplication application, LaunchInfoExtension launchInfo) {
    ImmutableList.Builder<Mark> marksBuilder = new ImmutableList.Builder<>();
    if (Boolean.TRUE.equals(launchInfo.getIncludeMark())) {  // Default to false.
      for (EncodedSignedMark encodedMark : application.getEncodedSignedMarks()) {
        try {
          marksBuilder.add(unmarshal(SignedMark.class, encodedMark.getBytes()).getMark());
        } catch (EppException e) {
          // This is a serious error; don't let the benign EppException propagate.
          throw new IllegalStateException("Could not decode a stored encoded signed mark", e);
        }
      }
    }
    ImmutableList.Builder<ResponseExtension> extensions = new ImmutableList.Builder<>();
    extensions.add(new LaunchInfoResponseExtension.Builder()
        .setPhase(application.getPhase())
        .setApplicationId(application.getForeignKey())
        .setApplicationStatus(application.getApplicationStatus())
        .setMarks(marksBuilder.build())
        .build());
    addSecDnsExtensionIfPresent(extensions, application.getDsData());
    return extensions.build();
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
