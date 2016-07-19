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
import static google.registry.model.domain.fee.Fee.FEE_CHECK_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER;
import static google.registry.model.domain.fee.Fee.FEE_EXTENSION_URIS;
import static google.registry.model.index.DomainApplicationIndex.loadActiveApplicationsByDomainName;
import static google.registry.model.registry.label.ReservationType.UNRESERVED;
import static google.registry.pricing.PricingEngineProxy.getPricesForDomainName;
import static google.registry.util.CollectionUtils.nullToEmpty;
import static google.registry.util.DomainNameUtils.getTldFromDomainName;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.net.InternetDomainName;
import google.registry.flows.EppException;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.fee.FeeCheckCommandExtension;
import google.registry.model.domain.fee.FeeCheckCommandExtensionItem;
import google.registry.model.domain.fee.FeeCheckResponseExtension;
import google.registry.model.domain.fee.FeeCheckResponseExtensionItem;
import google.registry.model.domain.launch.LaunchCheckExtension;
import google.registry.model.eppoutput.CheckData;
import google.registry.model.eppoutput.CheckData.DomainCheck;
import google.registry.model.eppoutput.CheckData.DomainCheckData;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.registry.label.ReservationType;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import org.joda.money.CurrencyUnit;

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

  /**
   * The TLD states during which we want to report a domain with pending applications as
   * unavailable.
   */
  private static final Set<TldState> PENDING_ALLOCATION_TLD_STATES =
      Sets.immutableEnumSet(TldState.GENERAL_AVAILABILITY, TldState.QUIET_PERIOD);

  @Inject DomainCheckFlow() {}

  @Override
  protected void initDomainCheckFlow() throws EppException {
    registerExtensions(LaunchCheckExtension.class);
    registerExtensions(FEE_CHECK_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER);
  }

  private String getMessageForCheck(String targetId, Set<String> existingIds) {
    if (existingIds.contains(targetId)) {
      return "In use";
    }
    InternetDomainName domainName = domainNames.get(targetId);
    Registry registry = Registry.get(domainName.parent().toString());
    if (PENDING_ALLOCATION_TLD_STATES.contains(registry.getTldState(now))
        && FluentIterable.from(loadActiveApplicationsByDomainName(domainName.toString(), now))
            .anyMatch(new Predicate<DomainApplication>() {
              @Override
              public boolean apply(DomainApplication input) {
                return !input.getApplicationStatus().isFinalStatus();
              }})) {
      return "Pending allocation";
    }
    ReservationType reservationType = getReservationType(domainName);
    if (reservationType == UNRESERVED
        && getPricesForDomainName(domainName.toString(), now).isPremium()
        && registry.getPremiumPriceAckRequired()
        && Collections.disjoint(
            nullToEmpty(sessionMetadata.getServiceExtensionUris()),
            FEE_EXTENSION_URIS)) {
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

  /**
   * Return the domains to be checked for a particular fee check item. Some versions of the fee
   * extension specify the domain name in the extension item, while others use the list of domain
   * names from the regular check domain availability list.
   */
  private Set<String> getDomainNamesToCheck(FeeCheckCommandExtensionItem feeCheckItem)
      throws OnlyCheckedNamesCanBeFeeCheckedException {
    if (feeCheckItem.isDomainNameSupported()) {
      String domainNameInExtension = feeCheckItem.getDomainName();
      if (!domainNames.containsKey(domainNameInExtension)) {
        // Although the fee extension explicitly says it's ok to fee check a domain name that you
        // aren't also availability checking, we forbid it. This makes the experience simpler and
        // also means we can assume any domain names in the fee checks have been validated.
        throw new OnlyCheckedNamesCanBeFeeCheckedException();
      }
      return ImmutableSet.of(domainNameInExtension);
    } else {
      // If this version of the fee extension is nameless, use the full list of domains.
      return domainNames.keySet();
    }
  }   

  /** Handle the fee check extension. */
  @Override
  protected ImmutableList<ResponseExtension> getResponseExtensions() throws EppException {
    FeeCheckCommandExtension<
            ? extends FeeCheckCommandExtensionItem, ? extends FeeCheckResponseExtension<?>>
        feeCheck = eppInput.getFirstExtensionOfClasses(
            FEE_CHECK_COMMAND_EXTENSIONS_IN_PREFERENCE_ORDER);
    if (feeCheck == null) {
      return null;  // No fee checks were requested.
    }
    CurrencyUnit topLevelCurrency = feeCheck.isCurrencySupported() ? feeCheck.getCurrency() : null;
    ImmutableList.Builder<FeeCheckResponseExtensionItem> feeCheckResponseItemsBuilder =
        new ImmutableList.Builder<>();
    for (FeeCheckCommandExtensionItem feeCheckItem : feeCheck.getItems()) {
      for (String domainName : getDomainNamesToCheck(feeCheckItem)) {
        FeeCheckResponseExtensionItem.Builder builder = feeCheckItem.createResponseBuilder();
        handleFeeRequest(
            feeCheckItem,
            builder,
            domainName,
            getTldFromDomainName(domainName),
            topLevelCurrency,
            now);
        feeCheckResponseItemsBuilder
            .add(builder.setDomainNameIfSupported(domainName).build());
      }
    }
    return ImmutableList.<ResponseExtension>of(
        feeCheck.createResponse(feeCheckResponseItemsBuilder.build()));
  }
  
  /** By server policy, fee check names must be listed in the availability check. */
  static class OnlyCheckedNamesCanBeFeeCheckedException extends ParameterValuePolicyErrorException {
    OnlyCheckedNamesCanBeFeeCheckedException() {
      super("By server policy, fee check names must be listed in the availability check");
    }
  }
}
