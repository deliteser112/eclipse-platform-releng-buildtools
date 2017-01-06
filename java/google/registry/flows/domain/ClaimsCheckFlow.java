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
import static google.registry.flows.ResourceFlowUtils.verifyTargetIdCount;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.validateDomainName;
import static google.registry.flows.domain.DomainFlowUtils.validateDomainNameWithIdnTables;
import static google.registry.flows.domain.DomainFlowUtils.verifyClaimsPeriodNotEnded;
import static google.registry.flows.domain.DomainFlowUtils.verifyNotInPredelegation;
import static google.registry.model.domain.launch.LaunchPhase.CLAIMS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import google.registry.config.RegistryConfig.Config;
import google.registry.flows.EppException;
import google.registry.flows.EppException.CommandUseErrorException;
import google.registry.flows.ExtensionManager;
import google.registry.flows.Flow;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.Superuser;
import google.registry.model.domain.DomainCommand.Check;
import google.registry.model.domain.launch.LaunchCheckExtension;
import google.registry.model.domain.launch.LaunchCheckResponseExtension;
import google.registry.model.domain.launch.LaunchCheckResponseExtension.LaunchCheck;
import google.registry.model.domain.launch.LaunchCheckResponseExtension.LaunchCheckName;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.tmch.ClaimsListShard;
import google.registry.util.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * An EPP flow that checks whether strings are trademarked.
 *
 * @error {@link google.registry.flows.exceptions.TooManyResourceChecksException}
 * @error {@link DomainFlowUtils.BadCommandForRegistryPhaseException}
 * @error {@link DomainFlowUtils.ClaimsPeriodEndedException}
 * @error {@link DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link DomainFlowUtils.TldDoesNotExistException}
 * @error {@link ClaimsCheckNotAllowedInSunrise}
 */
public final class ClaimsCheckFlow implements Flow {

  @Inject ExtensionManager extensionManager;
  @Inject ResourceCommand resourceCommand;
  @Inject @ClientId String clientId;
  @Inject @Superuser boolean isSuperuser;
  @Inject Clock clock;
  @Inject @Config("maxChecks") int maxChecks;
  @Inject EppResponse.Builder responseBuilder;
  @Inject ClaimsCheckFlow() {}

  @Override
  public EppResponse run() throws EppException {
    extensionManager.register(LaunchCheckExtension.class);
    extensionManager.validate();
    validateClientIsLoggedIn(clientId);
    List<String> targetIds = ((Check) resourceCommand).getTargetIds();
    verifyTargetIdCount(targetIds, maxChecks);
    Set<String> seenTlds = new HashSet<>();
    ImmutableList.Builder<LaunchCheck> launchChecksBuilder = new ImmutableList.Builder<>();
    for (String targetId : ImmutableSet.copyOf(targetIds)) {
      InternetDomainName domainName = validateDomainName(targetId);
      validateDomainNameWithIdnTables(domainName);
      String tld = domainName.parent().toString();
      // Only validate access to a TLD the first time it is encountered.
      if (seenTlds.add(tld)) {
        checkAllowedAccessToTld(clientId, tld);
        Registry registry = Registry.get(tld);
        if (!isSuperuser) {
          DateTime now = clock.nowUtc();
          verifyNotInPredelegation(registry, now);
          if (registry.getTldState(now) == TldState.SUNRISE) {
            throw new ClaimsCheckNotAllowedInSunrise();
          }
          verifyClaimsPeriodNotEnded(registry, now);
        }
      }
      String claimKey = ClaimsListShard.get().getClaimKey(domainName.parts().get(0));
      launchChecksBuilder.add(
          LaunchCheck.create(
              LaunchCheckName.create(claimKey != null, targetId), claimKey));
    }
    return responseBuilder
        .setOnlyExtension(LaunchCheckResponseExtension.create(CLAIMS, launchChecksBuilder.build()))
        .build();
  }

  /** Claims checks are not allowed during sunrise. */
  static class ClaimsCheckNotAllowedInSunrise extends CommandUseErrorException {
    public ClaimsCheckNotAllowedInSunrise() {
      super("Claims checks are not allowed during sunrise");
    }
  }
}
