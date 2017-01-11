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

import static google.registry.flows.FlowUtils.validateClientIsLoggedIn;
import static google.registry.flows.ResourceFlowUtils.loadAndVerifyExistence;
import static google.registry.flows.ResourceFlowUtils.verifyOptionalAuthInfo;
import static google.registry.flows.domain.DomainFlowUtils.addSecDnsExtensionIfPresent;
import static google.registry.flows.domain.DomainFlowUtils.handleFeeRequest;
import static google.registry.flows.domain.DomainFlowUtils.loadForeignKeyedDesignatedContacts;
import static google.registry.flows.domain.DomainFlowUtils.prefetchReferencedResources;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import google.registry.flows.EppException;
import google.registry.flows.ExtensionManager;
import google.registry.flows.Flow;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.custom.DomainInfoFlowCustomLogic;
import google.registry.flows.custom.DomainInfoFlowCustomLogic.AfterValidationParameters;
import google.registry.flows.custom.DomainInfoFlowCustomLogic.BeforeResponseParameters;
import google.registry.flows.custom.DomainInfoFlowCustomLogic.BeforeResponseReturnData;
import google.registry.model.domain.DomainCommand.Info;
import google.registry.model.domain.DomainCommand.Info.HostsRequest;
import google.registry.model.domain.DomainInfoData;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.DomainResource.Builder;
import google.registry.model.domain.fee06.FeeInfoCommandExtensionV06;
import google.registry.model.domain.fee06.FeeInfoResponseExtensionV06;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.domain.rgp.RgpInfoExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import google.registry.util.Clock;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * An EPP flow that returns information about a domain.
 *
 * <p>The registrar that owns the domain, and any registrar presenting a valid authInfo for the
 * domain, will get a rich result with all of the domain's fields. All other requests will be
 * answered with a minimal result containing only basic information about the domain.
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException}
 * @error {@link DomainFlowUtils.BadPeriodUnitException}
 * @error {@link DomainFlowUtils.CurrencyUnitMismatchException}
 * @error {@link DomainFlowUtils.FeeChecksDontSupportPhasesException}
 * @error {@link DomainFlowUtils.RestoresAreAlwaysForOneYearException}
 */
public final class DomainInfoFlow implements Flow {

  @Inject ExtensionManager extensionManager;
  @Inject ResourceCommand resourceCommand;
  @Inject EppInput eppInput;
  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String clientId;
  @Inject @TargetId String targetId;
  @Inject Clock clock;
  @Inject EppResponse.Builder responseBuilder;
  @Inject DomainInfoFlowCustomLogic customLogic;
  @Inject DomainPricingLogic pricingLogic;

  @Inject
  DomainInfoFlow() {}

  @Override
  public final EppResponse run() throws EppException {
    extensionManager.register(FeeInfoCommandExtensionV06.class);
    customLogic.beforeValidation();
    extensionManager.validate();
    validateClientIsLoggedIn(clientId);
    DateTime now = clock.nowUtc();
    DomainResource domain = loadAndVerifyExistence(DomainResource.class, targetId, now);
    verifyOptionalAuthInfo(authInfo, domain);
    customLogic.afterValidation(AfterValidationParameters.newBuilder().setDomain(domain).build());
    BeforeResponseReturnData responseData =
        customLogic.beforeResponse(
            BeforeResponseParameters.newBuilder()
                .setDomain(domain)
                .setResData(getResourceInfo(domain))
                .setResponseExtensions(getDomainResponseExtensions(domain, now))
                .build());
    domain = responseData.resData();
    prefetchReferencedResources(domain);
    return responseBuilder
        .setResData(DomainInfoData.newBuilder()
            .setFullyQualifiedDomainName(domain.getFullyQualifiedDomainName())
            .setRepoId(domain.getRepoId())
            .setStatusValues(domain.getStatusValues())
            .setRegistrant(ofy().load().key(domain.getRegistrant()).now().getContactId())
            .setContacts(loadForeignKeyedDesignatedContacts(domain.getContacts()))
            .setNameservers(domain.loadNameserverFullyQualifiedHostNames())
            .setSubordinateHosts(domain.getSubordinateHosts())
            .setCurrentSponsorClientId(domain.getCurrentSponsorClientId())
            .setCreationClientId(domain.getCreationClientId())
            .setCreationTime(domain.getCreationTime())
            .setLastEppUpdateClientId(domain.getLastEppUpdateClientId())
            .setLastEppUpdateTime(domain.getLastEppUpdateTime())
            .setRegistrationExpirationTime(domain.getRegistrationExpirationTime())
            .setLastTransferTime(domain.getLastTransferTime())
            .setAuthInfo(domain.getAuthInfo())
            .build())
        .setExtensions(responseData.responseExtensions())
        .build();
  }

  private DomainResource getResourceInfo(DomainResource domain) {
    // If authInfo is non-null, then the caller is authorized to see the full information since we
    // will have already verified the authInfo is valid.
    if (!(clientId.equals(domain.getCurrentSponsorClientId()) || authInfo.isPresent())) {
      // Registrars can only see a few fields on unauthorized domains.
      // This is a policy decision that is left up to us by the rfcs.
      return new DomainResource.Builder()
          .setFullyQualifiedDomainName(domain.getFullyQualifiedDomainName())
          .setRepoId(domain.getRepoId())
          .setCurrentSponsorClientId(domain.getCurrentSponsorClientId())
          .setRegistrant(domain.getRegistrant())
          // If we didn't do this, we'd get implicit status values.
          .buildWithoutImplicitStatusValues();
    }
    HostsRequest hostsRequest = ((Info) resourceCommand).getHostsRequest();
    Builder info = domain.asBuilder();
    if (!hostsRequest.requestSubordinate()) {
      info.setSubordinateHosts(null);
    }
    if (!hostsRequest.requestDelegated()) {
      // Delegated hosts are present by default, so clear them out if they aren't wanted.
      // This requires overriding the implicit status values so that we don't get INACTIVE added due
      // to the missing nameservers.
      return info.setNameservers(null).buildWithoutImplicitStatusValues();
    }
    return info.build();
  }

  private ImmutableList<ResponseExtension> getDomainResponseExtensions(
      DomainResource domain, DateTime now) throws EppException {
    ImmutableList.Builder<ResponseExtension> extensions = new ImmutableList.Builder<>();
    addSecDnsExtensionIfPresent(extensions, domain.getDsData());
    ImmutableSet<GracePeriodStatus> gracePeriodStatuses = domain.getGracePeriodStatuses();
    if (!gracePeriodStatuses.isEmpty()) {
      extensions.add(RgpInfoExtension.create(gracePeriodStatuses));
    }
    FeeInfoCommandExtensionV06 feeInfo =
        eppInput.getSingleExtension(FeeInfoCommandExtensionV06.class);
    if (feeInfo != null) { // Fee check was requested.
      FeeInfoResponseExtensionV06.Builder builder = new FeeInfoResponseExtensionV06.Builder();
      handleFeeRequest(
          feeInfo,
          builder,
          InternetDomainName.from(targetId),
          null,
          now,
          pricingLogic);
      extensions.add(builder.build());
    }
    return extensions.build();
  }
}
