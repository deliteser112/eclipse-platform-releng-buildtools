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

package google.registry.pricing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.util.DomainNameUtils.getTldFromDomainName;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.InternetDomainName;

import google.registry.model.pricing.PricingEngine;
import google.registry.model.registry.Registry;

import org.joda.money.Money;
import org.joda.time.DateTime;

import java.util.Map;

/**
 * A global proxy providing static methods for getting premium prices that dispatches requests
 * correctly to the relevant {@link PricingEngine} implementation per TLD.
 */
public final class PricingEngineProxy {

  private static final Map<Class<? extends PricingEngine>, PricingEngine> pricingEngineClasses =
      DaggerPricingComponent.create().pricingEngines();

  // Dagger map keys have to be provided with constant values that are known at compile time, so it
  // can't be done using clazz.getCanonicalName().  So we construct the map by canonical name here,
  // at runtime.
  private static final ImmutableMap<String, PricingEngine> pricingEngines =
      Maps.uniqueIndex(
          pricingEngineClasses.values(),
          new Function<PricingEngine, String>() {
            @Override
            public String apply(PricingEngine pricingEngine) {
              return pricingEngine.getClass().getCanonicalName();
            }});

  /** Returns true if the given domain name is on the premium price list. */
  public static boolean isPremiumName(
      String domainName, DateTime priceTime, String clientIdentifier) {
    return isPremiumName(InternetDomainName.from(domainName), priceTime, clientIdentifier);
  }

  /** Returns true if the given domain name is on the premium price list. */
  public static boolean isPremiumName(
      InternetDomainName domainName, DateTime priceTime, String clientIdentifier) {
    return getPremiumPriceForDomainName(domainName, priceTime, clientIdentifier).isPresent();
  }

  /** Returns the billing cost for registering the specified domain name for this many years. */
  public static Money getDomainCreateCost(
      String domainName, DateTime priceTime, String clientIdentifier, int years) {
    checkArgument(years > 0, "Number of years must be positive");
    Optional<Money> annualCost =
        getPremiumPriceForDomainName(
            InternetDomainName.from(domainName), priceTime, clientIdentifier);
    return annualCost
        .or(Registry.get(getTldFromDomainName(domainName)).getStandardCreateCost())
        .multipliedBy(years);
  }

  /** Returns the billing cost for renewing the specified domain name for this many years. */
  public static Money getDomainRenewCost(
      String domainName, DateTime priceTime, String clientIdentifier, int years) {
    checkArgument(years > 0, "Number of years must be positive");
    Optional<Money> annualCost =
        getPremiumPriceForDomainName(
            InternetDomainName.from(domainName), priceTime, clientIdentifier);
    return annualCost
        .or(Registry.get(getTldFromDomainName(domainName)).getStandardRenewCost(priceTime))
        .multipliedBy(years);
  }

  /**
   * Returns whether the given domain name is premium by dispatching to the appropriate
   * {@link PricingEngine} based on what is configured for the TLD that the domain is under.
   */
  private static Optional<Money> getPremiumPriceForDomainName(
      InternetDomainName domainName, DateTime priceTime, String clientIdentifier) {
    String tld = assertTldExists(getTldFromDomainName(domainName));
    String clazz = Registry.get(tld).getPricingEngineClassName();
    PricingEngine engine = pricingEngines.get(clazz);
    checkState(engine != null, "Could not load pricing engine %s for TLD %s", clazz, tld);
    return engine.getPremiumPrice(domainName.toString(), priceTime, clientIdentifier);
  }

  private PricingEngineProxy() {}
}
