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

package com.google.domain.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.domain.registry.model.registry.Registries.getTlds;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedMap;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.Registry.TldState;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import org.joda.money.Money;

import javax.annotation.Nullable;

/** Command to create a TLD. */
@Parameters(separators = " =", commandDescription = "Create new TLD(s)")
class CreateTldCommand extends CreateOrUpdateTldCommand {

  @Nullable
  @Parameter(
      names = "--initial_tld_state",
      description = "Initial state of the TLD (cannot be combined with a transitions list)")
  TldState initialTldState;

  @Nullable
  @Parameter(
      names = "--initial_renew_billing_cost",
      description = "Initial per-year billing cost for renewing a domain "
          + "(cannot be combined with a transitions list)")
  private Money initialRenewBillingCost;

  @Override
  protected void initTldCommand() throws Exception {
    checkArgument(initialTldState == null || tldStateTransitions.isEmpty(),
        "Don't pass both --initial_tld_state and --tld_state_transitions");
    if (initialTldState != null) {
      tldStateTransitions = ImmutableSortedMap.of(START_OF_TIME, initialTldState);
    }
    checkArgument(initialRenewBillingCost == null || renewBillingCostTransitions.isEmpty(),
        "Don't pass both --initial_renew_billing_cost and --renew_billing_cost_transitions");
    if (initialRenewBillingCost != null) {
      renewBillingCostTransitions = ImmutableSortedMap.of(START_OF_TIME, initialRenewBillingCost);
    }
    checkArgument(mainParameters.size() == 1, "Can't create more than one TLD at a time");
    checkArgument(
        !Strings.isNullOrEmpty(roidSuffix),
        "The roid suffix is required when creating a TLD");
  }

  @Override
  void setCommandSpecificProperties(Registry.Builder builder) {
    // Pick up the currency from the create cost. Since all costs must be in one currency, and that
    // condition is enforced by the builder, it doesn't matter which cost we choose it from.
    builder.setCurrency(createBillingCost != null
        ? createBillingCost.getCurrencyUnit()
        : Registry.DEFAULT_CURRENCY);
  }

  @Override
  Registry getOldRegistry(String tld) {
    checkState(!getTlds().contains(tld), "TLD already exists");
    return null;
  }
}
