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

import static google.registry.flows.ResourceFlowUtils.verifyTargetIdCount;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.validateDomainName;
import static google.registry.flows.domain.DomainFlowUtils.validateDomainNameWithIdnTables;
import static google.registry.flows.domain.DomainFlowUtils.verifyNotInPredelegation;
import static google.registry.model.domain.launch.LaunchPhase.CLAIMS;
import static google.registry.model.eppoutput.Result.Code.SUCCESS;
import static google.registry.util.DateTimeUtils.isAtOrAfter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import google.registry.config.ConfigModule.Config;
import google.registry.flows.EppException;
import google.registry.flows.EppException.CommandUseErrorException;
import google.registry.flows.LoggedInFlow;
import google.registry.model.domain.DomainCommand.Check;
import google.registry.model.domain.launch.LaunchCheckExtension;
import google.registry.model.domain.launch.LaunchCheckResponseExtension;
import google.registry.model.domain.launch.LaunchCheckResponseExtension.LaunchCheck;
import google.registry.model.domain.launch.LaunchCheckResponseExtension.LaunchCheckName;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.tmch.ClaimsListShard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * An EPP flow that checks whether strings are trademarked.
 *
 * @error {@link google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link google.registry.flows.exceptions.TooManyResourceChecksException}
 * @error {@link DomainFlowUtils.BadCommandForRegistryPhaseException}
 * @error {@link DomainFlowUtils.TldDoesNotExistException}
 * @error {@link ClaimsCheckNotAllowedInSunrise}
 * @error {@link ClaimsPeriodEndedException}
 */
public final class ClaimsCheckFlow extends LoggedInFlow {

  @Inject ResourceCommand resourceCommand;
  @Inject @Config("maxChecks") int maxChecks;
  @Inject ClaimsCheckFlow() {}

  @Override
  protected final void initLoggedInFlow() throws EppException {
    registerExtensions(LaunchCheckExtension.class);
  }

  @Override
  public EppOutput run() throws EppException {
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
        checkAllowedAccessToTld(getAllowedTlds(), tld);
        Registry registry = Registry.get(tld);
        if (!isSuperuser) {
          verifyNotInPredelegation(registry, now);
          if (registry.getTldState(now) == TldState.SUNRISE) {
            throw new ClaimsCheckNotAllowedInSunrise();
          }
          if (isAtOrAfter(now, registry.getClaimsPeriodEnd())) {
            throw new ClaimsPeriodEndedException();
          }
        }
      }
      String claimKey = ClaimsListShard.get().getClaimKey(domainName.parts().get(0));
      launchChecksBuilder.add(
          LaunchCheck.create(
              LaunchCheckName.create(claimKey != null, targetId), claimKey));
    }
    return createOutput(
        SUCCESS,
        null,
        ImmutableList.of(LaunchCheckResponseExtension.create(CLAIMS, launchChecksBuilder.build())));
  }

  /** Claims checks are not allowed during sunrise. */
  static class ClaimsCheckNotAllowedInSunrise extends CommandUseErrorException {
    public ClaimsCheckNotAllowedInSunrise() {
      super("Claims checks are not allowed during sunrise");
    }
  }

  /** The claims period has ended. */
  static class ClaimsPeriodEndedException extends CommandUseErrorException {
    public ClaimsPeriodEndedException() {
      super("The claims period has ended");
    }
  }
}
