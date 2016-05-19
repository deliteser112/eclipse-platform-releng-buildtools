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

import com.google.common.base.Optional;

import org.joda.money.Money;
import org.joda.time.DateTime;

/** A plugin interface for premium pricing engines. */
public interface PricingEngine {

  /**
   * Returns the premium price for the given domain name at the given time for the given registrar,
   * or absent if the domain name isn't premium.
   *
   * <p>Note that fullyQualifiedDomainName must not include any subdomains.  It should be a single
   * level above the TLD (which may be multi-part).
   */
  public Optional<Money> getPremiumPrice(
      String fullyQualifiedDomainName, DateTime priceTime, String clientIdentifier);
}
