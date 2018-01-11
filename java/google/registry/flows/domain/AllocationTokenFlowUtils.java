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

package google.registry.flows.domain;

import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.AssociationProhibitsOperationException;
import google.registry.flows.EppException.ParameterValueSyntaxErrorException;
import google.registry.model.domain.AllocationToken;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import java.util.List;
import java.util.function.Function;

/** Static utility functions for dealing with {@link AllocationToken}s in domain flows. */
public class AllocationTokenFlowUtils {

  /**
   * Verifies that a given allocation token string is valid.
   *
   * @return the loaded {@link AllocationToken} for that string.
   * @throws InvalidAllocationTokenException if the token doesn't exist.
   */
  static AllocationToken verifyToken(
      InternetDomainName domainName, String token, Registry registry, String clientId)
      throws EppException {
    AllocationToken tokenEntity = ofy().load().key(Key.create(AllocationToken.class, token)).now();
    if (tokenEntity == null) {
      throw new InvalidAllocationTokenException();
    }
    if (tokenEntity.isRedeemed()) {
      throw new AlreadyRedeemedAllocationTokenException();
    }
    return tokenEntity;
  }

  /**
   * Checks if the allocation token applies to the given domain names, used for domain checks.
   *
   * @return A map of domain names to domain check error response messages. If a message is present
   *     for a a given domain then it does not validate with this allocation token; domains that do
   *     validate are not present in the map.
   */
  static ImmutableMap<String, String> checkDomainsWithToken(
      List<String> domainNames, String token, String clientId) {
    AllocationToken tokenEntity = ofy().load().key(Key.create(AllocationToken.class, token)).now();
    String result;
    if (tokenEntity == null) {
      result = new InvalidAllocationTokenException().getMessage();
    } else if (tokenEntity.isRedeemed()) {
      result = AlreadyRedeemedAllocationTokenException.ERROR_MSG_SHORT;
    } else {
      return ImmutableMap.of();
    }
    // TODO(b/70628322): For now all checks yield the same result, but custom logic will soon allow
    // them to differ, e.g. if tokens can only be used on certain TLDs.
    return domainNames
        .stream()
        .collect(ImmutableMap.toImmutableMap(Function.identity(), domainName -> result));
  }

  /** Redeems an {@link AllocationToken}, returning the redeemed copy. */
  static AllocationToken redeemToken(
      AllocationToken token, Key<HistoryEntry> redemptionHistoryEntry) {
    return token.asBuilder().setRedemptionHistoryEntry(redemptionHistoryEntry).build();
  }

  /** The allocation token was already redeemed. */
  static class AlreadyRedeemedAllocationTokenException
      extends AssociationProhibitsOperationException {

    public static final String ERROR_MSG_LONG = "The allocation token was already redeemed";

    /** A short error message fitting within 32 characters for use in domain check responses. */
    public static final String ERROR_MSG_SHORT = "Alloc token was already redeemed";

    public AlreadyRedeemedAllocationTokenException() {
      super(ERROR_MSG_LONG);
    }
  }

  /** The allocation token is invalid. */
  static class InvalidAllocationTokenException extends ParameterValueSyntaxErrorException {
    public InvalidAllocationTokenException() {
      super("The allocation token is invalid");
    }
  }
}
