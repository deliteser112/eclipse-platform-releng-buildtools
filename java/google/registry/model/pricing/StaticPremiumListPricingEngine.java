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

package google.registry.model.pricing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static google.registry.util.DomainNameUtils.getTldFromDomainName;

import com.google.common.base.Optional;
import com.google.common.net.InternetDomainName;

import google.registry.model.registry.Registry;
import google.registry.model.registry.label.PremiumList;

import org.joda.money.Money;
import org.joda.time.DateTime;

import javax.inject.Inject;

/** A premium list pricing engine that stores static pricing information in Datastore entities. */
public final class StaticPremiumListPricingEngine implements PricingEngine {

  @Inject StaticPremiumListPricingEngine() {}

  @Override
  public Optional<Money> getPremiumPrice(
      String fullyQualifiedDomainName, DateTime priceTime, String clientIdentifier) {
    // Note that clientIdentifier and priceTime are not used for determining premium pricing for
    // static premium lists.
    String tld = getTldFromDomainName(fullyQualifiedDomainName);
    Registry registry = Registry.get(checkNotNull(tld, "tld"));
    if (registry.getPremiumList() == null) {
      return Optional.<Money>absent();
    }
    String listName = registry.getPremiumList().getName();
    Optional<PremiumList> premiumList = PremiumList.get(listName);
    checkState(premiumList.isPresent(), "Could not load premium list: %s", listName);
    String label = InternetDomainName.from(fullyQualifiedDomainName).parts().get(0);
    return premiumList.get().getPremiumPrice(label);
  }
}
