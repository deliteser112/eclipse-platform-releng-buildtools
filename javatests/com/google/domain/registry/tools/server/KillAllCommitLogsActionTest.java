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

import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.newContactResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistResourceWithCommitLog;
import static java.util.Arrays.asList;

import com.google.appengine.api.datastore.Entity;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.ofy.CommitLogBucket;
import com.google.domain.registry.model.ofy.CommitLogManifest;
import com.google.domain.registry.model.ofy.CommitLogMutation;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.mapreduce.MapreduceTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/** Tests for {@link KillAllCommitLogsAction}.*/
@RunWith(JUnit4.class)
public class KillAllCommitLogsActionTest extends MapreduceTestCase<KillAllCommitLogsAction> {

  static final List<Class<? extends ImmutableObject>> AFFECTED_TYPES = ImmutableList.of(
      CommitLogBucket.class,
      CommitLogMutation.class,
      CommitLogManifest.class);

  private void runMapreduce() throws Exception {
    action = new KillAllCommitLogsAction();
    action.mrRunner = new MapreduceRunner(Optional.<Integer>absent(), Optional.<Integer>absent());
    action.response = new FakeResponse();
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void testKill() throws Exception {
    int nextContactId = 5432;
    for (String tld : asList("tld1", "tld2")) {
      createTld(tld);
      persistResourceWithCommitLog(
          newContactResource(String.format("abc%d", nextContactId++)));
    }
    for (Class<?> clazz : AFFECTED_TYPES) {
      assertThat(ofy().load().type(clazz)).named("entities of type " + clazz).isNotEmpty();
    }
    ImmutableList<?> otherStuff = FluentIterable.from(ofy().load())
        .filter(new Predicate<Object>() {
          @Override
          public boolean apply(Object obj) {
            return !AFFECTED_TYPES.contains(obj.getClass());
          }})
        .toList();
    assertThat(otherStuff).isNotEmpty();
    runMapreduce();
    for (Class<?> clazz : AFFECTED_TYPES) {
      assertThat(ofy().load().type(clazz)).named("entities of type " + clazz).isEmpty();
    }
    // Filter out raw Entity objects created by the mapreduce.
    assertThat(filter(ofy().load(), not(instanceOf(Entity.class))))
        .containsExactlyElementsIn(otherStuff);
  }
}
