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

import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.AssociationProhibitsOperationException;
import google.registry.flows.EppException.ParameterValueSyntaxErrorException;
import google.registry.model.domain.AllocationToken;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;

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

  /** Redeems an {@link AllocationToken}, returning the redeemed copy. */
  static AllocationToken redeemToken(
      AllocationToken token, Key<HistoryEntry> redemptionHistoryEntry) {
    return token.asBuilder().setRedemptionHistoryEntry(redemptionHistoryEntry).build();
  }

  /** The allocation token was already redeemed. */
  static class AlreadyRedeemedAllocationTokenException
      extends AssociationProhibitsOperationException {
    public AlreadyRedeemedAllocationTokenException() {
      super("The allocation token was already redeemed");
    }
  }

  /** The allocation token is invalid. */
  static class InvalidAllocationTokenException extends ParameterValueSyntaxErrorException {
    public InvalidAllocationTokenException() {
      super("The allocation token is invalid");
    }
  }
}
