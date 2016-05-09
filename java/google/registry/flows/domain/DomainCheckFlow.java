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

import static google.registry.flows.domain.DomainFlowUtils.getReservationType;
import static google.registry.flows.domain.DomainFlowUtils.handleFeeRequest;
import static google.registry.model.EppResourceUtils.checkResourcesExist;
import static google.registry.model.registry.label.ReservationType.UNRESERVED;
import static google.registry.pricing.PricingEngineProxy.isPremiumName;
import static google.registry.util.CollectionUtils.nullToEmpty;
import static google.registry.util.DomainNameUtils.getTldFromSld;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InternetDomainName;

import google.registry.flows.EppException;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.model.domain.fee.FeeCheckExtension;
import google.registry.model.domain.fee.FeeCheckResponseExtension;
import google.registry.model.domain.fee.FeeCheckResponseExtension.FeeCheck;
import google.registry.model.domain.launch.LaunchCheckExtension;
import google.registry.model.eppcommon.ProtocolDefinition.ServiceExtension;
import google.registry.model.eppoutput.CheckData;
import google.registry.model.eppoutput.CheckData.DomainCheck;
import google.registry.model.eppoutput.CheckData.DomainCheckData;
import google.registry.model.eppoutput.Response.ResponseExtension;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservationType;

import java.util.Set;

/**
 * An EPP flow that checks whether a domain can be provisioned.
 *
 * @error {@link google.registry.flows.ResourceCheckFlow.TooManyResourceChecksException}
 * @error {@link google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link DomainFlowUtils.BadDomainNameCharacterException}
 * @error {@link DomainFlowUtils.BadDomainNamePartsCountException}
 * @error {@link DomainFlowUtils.BadPeriodUnitException}
 * @error {@link DomainFlowUtils.CurrencyUnitMismatchException}
 * @error {@link DomainFlowUtils.DashesInThirdAndFourthException}
 * @error {@link DomainFlowUtils.DomainLabelTooLongException}
 * @error {@link DomainFlowUtils.EmptyDomainNamePartException}
 * @error {@link DomainFlowUtils.FeeChecksDontSupportPhasesException}
 * @error {@link DomainFlowUtils.InvalidIdnDomainLabelException}
 * @error {@link DomainFlowUtils.InvalidPunycodeException}
 * @error {@link DomainFlowUtils.LeadingDashException}
 * @error {@link DomainFlowUtils.RestoresAreAlwaysForOneYearException}
 * @error {@link DomainFlowUtils.TldDoesNotExistException}
 * @error {@link DomainFlowUtils.TrailingDashException}
 * @error {@link DomainFlowUtils.UnknownFeeCommandException}
 * @error {@link DomainCheckFlow.OnlyCheckedNamesCanBeFeeCheckedException}
 */
public class DomainCheckFlow extends BaseDomainCheckFlow {

  @Override
  protected void initDomainCheckFlow() throws EppException {
    registerExtensions(LaunchCheckExtension.class, FeeCheckExtension.class);
  }

  private String getMessageForCheck(String targetId, Set<String> existingIds) {
    if (existingIds.contains(targetId)) {
      return "In use";
    }
    InternetDomainName domainName = domainNames.get(targetId);
    ReservationType reservationType = getReservationType(domainName);
    Registry registry = Registry.get(domainName.parent().toString());
    if (reservationType == UNRESERVED
        && isPremiumName(domainName, now, getClientId())
        && registry.getPremiumPriceAckRequired()
        && !nullToEmpty(sessionMetadata.getServiceExtensionUris()).contains(
            ServiceExtension.FEE_0_6.getUri())) {
      return "Premium names require EPP ext.";
    }
    return reservationType.getMessageForCheck();
  }

  @Override
  protected CheckData getCheckData() {
    Set<String> existingIds = checkResourcesExist(resourceClass, targetIds, now);
    ImmutableList.Builder<DomainCheck> checks = new ImmutableList.Builder<>();
    for (String id : targetIds) {
      String message = getMessageForCheck(id, existingIds);
      checks.add(DomainCheck.create(message == null, id, message));
    }
    return DomainCheckData.create(checks.build());
  }

  /** Handle the fee check extension. */
  @Override
  protected ImmutableList<? extends ResponseExtension> getResponseExtensions() throws EppException {
    FeeCheckExtension feeCheck = eppInput.getSingleExtension(FeeCheckExtension.class);
    if (feeCheck == null) {
      return null;  // No fee checks were requested.
    }
    ImmutableList.Builder<FeeCheck> feeChecksBuilder = new ImmutableList.Builder<>();
    for (FeeCheckExtension.DomainCheck domainCheck : feeCheck.getDomains()) {
      String domainName = domainCheck.getName();
      if (!domainNames.containsKey(domainName)) {
        // Although the fee extension explicitly says it's ok to fee check a domain name that you
        // aren't also availability checking, we forbid it. This makes the experience simpler and
        // also means we can assume any domain names in the fee checks have been validated.
        throw new OnlyCheckedNamesCanBeFeeCheckedException();
      }
      FeeCheck.Builder builder = new FeeCheck.Builder();
      handleFeeRequest(
          domainCheck, builder, domainName, getTldFromSld(domainName), getClientId(), now);
      feeChecksBuilder.add(builder.setName(domainName).build());
    }
    return ImmutableList.of(FeeCheckResponseExtension.create(feeChecksBuilder.build()));
  }

  /** By server policy, fee check names must be listed in the availability check. */
  static class OnlyCheckedNamesCanBeFeeCheckedException extends ParameterValuePolicyErrorException {
    OnlyCheckedNamesCanBeFeeCheckedException() {
      super("By server policy, fee check names must be listed in the availability check");
    }
  }
}
