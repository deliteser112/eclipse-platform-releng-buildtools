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
import com.google.domain.registry.model.ofy.CommitLogBucket;
import com.google.domain.registry.model.ofy.CommitLogCheckpoint;
import com.google.domain.registry.model.ofy.CommitLogCheckpointRoot;
import com.google.domain.registry.model.ofy.CommitLogManifest;
import com.google.domain.registry.model.ofy.CommitLogMutation;
import com.google.domain.registry.testing.FakeResponse;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link KillAllCommitLogsAction}.*/
@RunWith(JUnit4.class)
public class KillAllCommitLogsActionTest extends KillAllActionTestCase<KillAllCommitLogsAction> {

  public KillAllCommitLogsActionTest() {
    super(FluentIterable
        .from(asList(
            CommitLogBucket.class,
            CommitLogCheckpoint.class,
            CommitLogCheckpointRoot.class,
            CommitLogMutation.class,
            CommitLogManifest.class))
        .transform(CLASS_TO_KIND_FUNCTION)
        .toSet());
  }

  @Override
  MapreduceAction createAction() {
    action = new KillAllCommitLogsAction();
    action.mrRunner = new MapreduceRunner(Optional.<Integer>absent(), Optional.<Integer>absent());
    action.response = new FakeResponse();
    return action;
  }
}
