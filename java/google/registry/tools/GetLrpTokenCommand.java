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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.domain.LrpTokenEntity;
import google.registry.tools.Command.RemoteApiCommand;

/** Command to show token information for LRP participants. */
@Parameters(
    separators = " =",
    commandDescription = "Show token information for LRP participants by matching on a "
        + "known token or a unique ID (assignee).")
public final class GetLrpTokenCommand implements RemoteApiCommand {

  @Parameter(
      names = {"-t", "--token"},
      description = "LRP access token (auth code) to check")
  private String tokenString;

  @Parameter(
      names = {"-a", "--assignee"},
      description = "LRP token assignee")
  private String assignee;

  @Parameter(
      names = {"-h", "--history"},
      description = "Return expanded history entry (including domain application)")
  private boolean includeHistory = false;

  @Override
  public void run() throws Exception {
    checkArgument(
        (tokenString == null) == (assignee != null),
        "Exactly one of either token or assignee must be specified.");
    ImmutableSet.Builder<LrpTokenEntity> tokensBuilder = new ImmutableSet.Builder<>();
    if (tokenString != null) {
      LrpTokenEntity token =
          ofy().load().key(Key.create(LrpTokenEntity.class, tokenString)).now();
      if (token != null) {
        tokensBuilder.add(token);
      }
    } else {
      tokensBuilder.addAll(ofy().load().type(LrpTokenEntity.class).filter("assignee", assignee));
    }

    ImmutableSet<LrpTokenEntity> tokens = tokensBuilder.build();
    if (!tokens.isEmpty()) {
      for (LrpTokenEntity token : tokens) {
        System.out.println(token);
        if (includeHistory && token.getRedemptionHistoryEntry() != null) {
          System.out.println(
              ofy().load().key(token.getRedemptionHistoryEntry()).now().toHydratedString());
        }
      }
    } else {
      System.out.println("Token not found.");
    }
  }
}

