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

package google.registry.tools.javascrap;

import static com.google.common.collect.Lists.partition;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameters;
import com.googlecode.objectify.Key;
import google.registry.model.domain.token.AllocationToken;
import google.registry.tools.MutatingCommand;
import java.util.List;

/** Scrap tool to load and save all {@link AllocationToken}s to populate on-load attributes. */
@Parameters(commandDescription = "Load and resave all allocation tokens")
public final class ResaveAllocationTokensCommand extends MutatingCommand {

  private static final int BATCH_SIZE = 20;

  @Override
  protected void init() {
    List<Key<AllocationToken>> tokenKeys = ofy().load().type(AllocationToken.class).keys().list();
    for (List<Key<AllocationToken>> partitionedKeys : partition(tokenKeys, BATCH_SIZE)) {
      for (Key<AllocationToken> key : partitionedKeys) {
        AllocationToken token = ofy().load().key(key).now();
        stageEntityChange(token, token);
      }
      flushTransaction();
    }
  }
}
