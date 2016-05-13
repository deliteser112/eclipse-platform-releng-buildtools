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

import static com.google.domain.registry.model.EntityClasses.CLASS_TO_KIND_FUNCTION;
import static java.util.Arrays.asList;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.domain.registry.mapreduce.MapreduceAction;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.model.billing.RegistrarBillingEntry;
import com.google.domain.registry.model.billing.RegistrarCredit;
import com.google.domain.registry.model.billing.RegistrarCreditBalance;
import com.google.domain.registry.model.common.EntityGroupRoot;
import com.google.domain.registry.model.export.LogsExportCursor;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.RegistryCursor;
import com.google.domain.registry.model.registry.label.PremiumList;
import com.google.domain.registry.model.registry.label.PremiumList.PremiumListEntry;
import com.google.domain.registry.model.registry.label.ReservedList;
import com.google.domain.registry.model.server.ServerSecret;
import com.google.domain.registry.model.smd.SignedMarkRevocationList;
import com.google.domain.registry.model.tmch.ClaimsListShard;
import com.google.domain.registry.model.tmch.ClaimsListShard.ClaimsListSingleton;
import com.google.domain.registry.model.tmch.TmchCrl;
import com.google.domain.registry.testing.FakeResponse;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link KillAllCommitLogsAction}.*/
@RunWith(JUnit4.class)
public class KillAllCrossTldEntitiesActionTest
    extends KillAllActionTestCase<KillAllCrossTldEntitiesAction> {

 public KillAllCrossTldEntitiesActionTest() {
    super(FluentIterable
        .from(asList(
            ClaimsListShard.class,
            ClaimsListSingleton.class,
            EntityGroupRoot.class,
            LogsExportCursor.class,
            PremiumList.class,
            PremiumListEntry.class,
            Registrar.class,
            RegistrarBillingEntry.class,
            RegistrarContact.class,
            RegistrarCredit.class,
            RegistrarCreditBalance.class,
            Registry.class,
            RegistryCursor.class,
            ReservedList.class,
            ServerSecret.class,
            SignedMarkRevocationList.class,
            TmchCrl.class))
        .transform(CLASS_TO_KIND_FUNCTION)
        .toSet());
  }

  @Override
  MapreduceAction createAction() {
    action = new KillAllCrossTldEntitiesAction();
    action.mrRunner = new MapreduceRunner(Optional.<Integer>absent(), Optional.<Integer>absent());
    action.response = new FakeResponse();
    return action;
  }
}
