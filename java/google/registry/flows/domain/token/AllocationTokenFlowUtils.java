// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.flows.domain.token;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.AssociationProhibitsOperationException;
import google.registry.flows.EppException.ParameterValueSyntaxErrorException;
import google.registry.model.domain.DomainCommand;
import google.registry.model.domain.token.AllocationToken;
import google.registry.model.domain.token.AllocationToken.TokenType;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** Utility functions for dealing with {@link AllocationToken}s in domain flows. */
public class AllocationTokenFlowUtils {

  final AllocationTokenCustomLogic tokenCustomLogic;

  @Inject
  AllocationTokenFlowUtils(AllocationTokenCustomLogic tokenCustomLogic) {
    this.tokenCustomLogic = tokenCustomLogic;
  }

  /**
   * Verifies that a given allocation token string is valid.
   *
   * @return the loaded {@link AllocationToken} for that string.
   * @throws InvalidAllocationTokenException if the token doesn't exist.
   */
  public AllocationToken loadAndVerifyToken(
      DomainCommand.Create command, String token, Registry registry, String clientId, DateTime now)
      throws EppException {
    AllocationToken tokenEntity = loadToken(token);
    return tokenCustomLogic.verifyToken(command, tokenEntity, registry, clientId, now);
  }

  /**
   * Checks if the allocation token applies to the given domain names, used for domain checks.
   *
   * @return A map of domain names to domain check error response messages. If a message is present
   *     for a a given domain then it does not validate with this allocation token; domains that do
   *     validate have blank messages (i.e. no error).
   */
  public AllocationTokenDomainCheckResults checkDomainsWithToken(
      List<InternetDomainName> domainNames, String token, String clientId, DateTime now) {
    try {
      AllocationToken tokenEntity = loadToken(token);
      // Only call custom logic if there wasn't a global allocation token error that applies to all
      // check results. The custom logic can only add errors, not override existing errors.
      return AllocationTokenDomainCheckResults.create(
          Optional.of(tokenEntity),
          tokenCustomLogic.checkDomainsWithToken(
              ImmutableList.copyOf(domainNames), tokenEntity, clientId, now));
    } catch (EppException e) {
      return AllocationTokenDomainCheckResults.create(
          Optional.empty(),
          ImmutableMap.copyOf(Maps.toMap(domainNames, ignored -> e.getMessage())));
    }
  }

  /**
   * Redeems a SINGLE_USE {@link AllocationToken}, returning the redeemed copy.
   */
  public AllocationToken redeemToken(
      AllocationToken token, Key<HistoryEntry> redemptionHistoryEntry) {
    checkArgument(
        TokenType.SINGLE_USE.equals(token.getTokenType()),
        "Only SINGLE_USE tokens can be marked as redeemed");
    return token.asBuilder().setRedemptionHistoryEntry(redemptionHistoryEntry).build();
  }

  private AllocationToken loadToken(String token) throws EppException {
    AllocationToken tokenEntity = ofy().load().key(Key.create(AllocationToken.class, token)).now();
    if (tokenEntity == null) {
      throw new InvalidAllocationTokenException();
    }
    if (tokenEntity.isRedeemed()) {
      throw new AlreadyRedeemedAllocationTokenException();
    }
    return tokenEntity;
  }

  /** The allocation token was already redeemed. */
  public static class AlreadyRedeemedAllocationTokenException
      extends AssociationProhibitsOperationException {
    public AlreadyRedeemedAllocationTokenException() {
      super("Alloc token was already redeemed");
    }
  }

  /** The allocation token is invalid. */
  public static class InvalidAllocationTokenException extends ParameterValueSyntaxErrorException {
    public InvalidAllocationTokenException() {
      super("The allocation token is invalid");
    }
  }
}
