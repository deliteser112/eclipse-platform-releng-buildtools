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

import static google.registry.flows.domain.DomainFlowUtils.handleFeeRequest;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.flows.EppException;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.DomainResource.Builder;
import google.registry.model.domain.fee06.FeeInfoCommandExtensionV06;
import google.registry.model.domain.fee06.FeeInfoResponseExtensionV06;
import google.registry.model.domain.flags.FlagsInfoResponseExtension;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.domain.rgp.RgpInfoExtension;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import java.util.List;
import javax.inject.Inject;

/**
 * An EPP flow that reads a domain.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceQueryFlow.ResourceToQueryDoesNotExistException}
 * @error {@link DomainFlowUtils.BadPeriodUnitException}
 * @error {@link DomainFlowUtils.CurrencyUnitMismatchException}
 * @error {@link DomainFlowUtils.FeeChecksDontSupportPhasesException}
 * @error {@link DomainFlowUtils.RestoresAreAlwaysForOneYearException}
 */
public class DomainInfoFlow extends BaseDomainInfoFlow<DomainResource, Builder> {

  @Inject DomainInfoFlow() {}

  @Override
  protected void initSingleResourceFlow() throws EppException {
    registerExtensions(FeeInfoCommandExtensionV06.class);
  }

  @Override
  protected final DomainResource getResourceInfo() {
    // If authInfo is non-null, then the caller is authorized to see the full information since we
    // will have already verified the authInfo is valid in ResourceQueryFlow.verifyIsAllowed().
    if (!getClientId().equals(existingResource.getCurrentSponsorClientId())
        && command.getAuthInfo() == null) {
      // Registrars can only see a few fields on unauthorized domains.
      // This is a policy decision that is left up to us by the rfcs.
      return new DomainResource.Builder()
          .setFullyQualifiedDomainName(existingResource.getFullyQualifiedDomainName())
          .setRepoId(existingResource.getRepoId())
          .setCurrentSponsorClientId(existingResource.getCurrentSponsorClientId())
          .setRegistrant(existingResource.getRegistrant())
          // If we didn't do this, we'd get implicit status values.
          .buildWithoutImplicitStatusValues();
    }
    Builder info = existingResource.asBuilder();
    if (!command.getHostsRequest().requestSubordinate()) {
      info.setSubordinateHosts(null);
    }
    if (!command.getHostsRequest().requestDelegated()) {
      // Delegated hosts are present by default, so clear them out if they aren't wanted.
      // This requires overriding the implicit status values so that we don't get INACTIVE added due
      // to the missing nameservers.
      return info.setNameservers(null).buildWithoutImplicitStatusValues();
    }
    return info.build();
  }

  @Override
  protected final ImmutableList<ResponseExtension> getDomainResponseExtensions()
      throws EppException {
    ImmutableList.Builder<ResponseExtension> extensions = new ImmutableList.Builder<>();
    // According to RFC 5910 section 2, we should only return this if the client specified the
    // "urn:ietf:params:xml:ns:rgp-1.0" when logging in. However, this is a "SHOULD" not a "MUST"
    // and we are going to ignore it; clients who don't care about rgp can just ignore it.
    ImmutableSet<GracePeriodStatus> gracePeriodStatuses = existingResource.getGracePeriodStatuses();
    if (!gracePeriodStatuses.isEmpty()) {
      extensions.add(RgpInfoExtension.create(gracePeriodStatuses));
    }
    FeeInfoCommandExtensionV06 feeInfo =
        eppInput.getSingleExtension(FeeInfoCommandExtensionV06.class);
    if (feeInfo != null) {  // Fee check was requested.
      FeeInfoResponseExtensionV06.Builder builder = new FeeInfoResponseExtensionV06.Builder();
      handleFeeRequest(
          feeInfo,
          builder,
          getTargetId(),
          existingResource.getTld(),
          null,
          now);
      extensions.add(builder.build());
    }
    // If the TLD uses the flags extension, add it to the info response.
    Optional<RegistryExtraFlowLogic> extraLogicManager =
        RegistryExtraFlowLogicProxy.newInstanceForTld(existingResource.getTld());
    if (extraLogicManager.isPresent()) {
      List<String> flags = extraLogicManager.get().getExtensionFlags(
          existingResource, this.getClientId(), now); // As-of date is always now for info commands.
      if (!flags.isEmpty()) {
        extensions.add(FlagsInfoResponseExtension.create(flags));
      }
    }

    return extensions.build();
  }
}
