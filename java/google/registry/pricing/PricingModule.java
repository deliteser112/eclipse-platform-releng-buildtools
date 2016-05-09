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

import dagger.MapKey;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

import google.registry.model.pricing.PricingEngine;
import google.registry.model.pricing.StaticPremiumListPricingEngine;

/**
 * Dagger module for injecting pricing engines.
 *
 * <p>To add a new pricing engine, create a new class that implements {@link PricingEngine}, and add
 * a module that provides an instance of PricingEngine with a <code>PricingEngineClassKey</code>
 * annotation with the class of the implementation and also <code>@Provides @IntoMap</code>
 * annotations.
 */
@Module
public class PricingModule {

  /** The annotation used for PricingEngine implementation keys. */
  @MapKey
  @interface PricingEngineClassKey {
    Class<? extends PricingEngine> value();
  }

  @Provides @IntoMap
  @PricingEngineClassKey(StaticPremiumListPricingEngine.class)
  static PricingEngine provideStaticPremiumList(StaticPremiumListPricingEngine engine) {
    return engine;
  }
}
