// Copyright 2019 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.googlecode.objectify.Key;
import google.registry.model.domain.token.AllocationToken;
import java.util.List;

/** Command to show allocation tokens. */
@Parameters(separators = " =", commandDescription = "Show allocation token(s)")
final class GetAllocationTokenCommand implements CommandWithRemoteApi {

  @Parameter(
      description = "Allocation token(s)",
      required = true)
  private List<String> mainParameters;

  private static final int BATCH_SIZE = 20;

  @Override
  public void run() {
    ImmutableMap.Builder<String, AllocationToken> builder = new ImmutableMap.Builder<>();
    for (List<String> tokens : Lists.partition(mainParameters, BATCH_SIZE)) {
      ImmutableList<Key<AllocationToken>> keys =
          tokens.stream().map(t -> Key.create(AllocationToken.class, t)).collect(toImmutableList());
      ofy().load().keys(keys).forEach((k, v) -> builder.put(k.getName(), v));
    }
    ImmutableMap<String, AllocationToken> loadedTokens = builder.build();

    for (String token : mainParameters) {
      System.out.println(
          loadedTokens.containsKey(token)
              ? loadedTokens.get(token)
              : String.format("Token %s does not exist.", token));
    }
  }
}
