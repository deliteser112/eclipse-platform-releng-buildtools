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

package com.google.domain.registry.tools.server;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.domain.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.request.Action.Method.POST;
import static com.google.domain.registry.util.PipelineUtils.createJobPath;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.collect.ImmutableList;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.mapreduce.MapreduceAction;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.mapreduce.inputs.NullInput;
import com.google.domain.registry.model.common.EntityGroupRoot;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.Response;

import com.googlecode.objectify.Key;

import javax.inject.Inject;

/**
 * Deletes all cross tld entities in datastore.
 *
 * <p>This doesn't really need to be a mapreduce, but doing so makes it consistent with the other
 * kill-all actions, and gives us retries, a dashboard and counters for free.
 */
@Action(path = "/_dr/task/killAllCrossTld", method = POST)
public class KillAllCrossTldEntitiesAction implements MapreduceAction {

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject KillAllCrossTldEntitiesAction() {}

  @Override
  public final void run() {
    checkArgument( // safety
        RegistryEnvironment.get() == RegistryEnvironment.CRASH
            || RegistryEnvironment.get() == RegistryEnvironment.UNITTEST,
        "DO NOT RUN ANYWHERE ELSE EXCEPT CRASH OR TESTS.");
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Delete all cross-tld entities")
        .setModuleName("tools")
        .runMapreduce(
            new KillAllCrossTldEntitiesMapper(),
            new KillAllEntitiesReducer(),
            ImmutableList.of(new NullInput<Object>()))));
  }

  /**
   * Mapper to delete all descendants of {@link EntityGroupRoot#getCrossTldKey()}.
   *
   * <p>This will delete:
   * <ul>
   *   <i>{@code ClaimsListShard}
   *   <i>{@code ClaimsListSingleton}
   *   <i>{@link EntityGroupRoot}
   *   <i>{@code LogsExportCursor}
   *   <i>{@code PremiumList}
   *   <i>{@code PremiumListEntry}
   *   <i>{@code Registrar}
   *   <i>{@code RegistrarBillingEntry}
   *   <i>{@code RegistrarContact}
   *   <i>{@code RegistrarCredit}
   *   <i>{@code RegistrarCreditBalance}
   *   <i>{@code Registry}
   *   <i>{@code RegistryCursor}
   *   <i>{@code ReservedList}
   *   <i>{@code ServerSecret}
   *   <i>{@code SignedMarkRevocationList}
   *   <i>{@code TmchCrl}
   * </ul>
   */
  static class KillAllCrossTldEntitiesMapper extends Mapper<Object, Key<?>, Key<?>> {

    private static final long serialVersionUID = 8343696167876596542L;

    @Override
    public void map(Object ignored) {
      // There will be exactly one input to the mapper, and we ignore it.
      Key<EntityGroupRoot> crossTldKey = getCrossTldKey();
      for (Key<Object> key : ofy().load().ancestor(crossTldKey).keys()) {
        emit(crossTldKey, key);
        getContext().incrementCounter("entities emitted");
        getContext().incrementCounter(String.format("%s emitted", key.getKind()));
     }
    }
  }
}

