// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.tools.server.javascrap;

import static com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.testing.DatastoreHelper.newRegistry;

import com.google.appengine.api.NamespaceManager;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.index.DomainApplicationIndex;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.testing.DatastoreHelper;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.mapreduce.MapreduceTestCase;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import com.googlecode.objectify.VoidWork;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AnnihilateNonDefaultNamespacesAction}. */
@RunWith(JUnit4.class)
public class AnnihilateNonDefaultNamespacesActionTest
    extends MapreduceTestCase<AnnihilateNonDefaultNamespacesAction> {

  @Before
  public void init() {
    action = new AnnihilateNonDefaultNamespacesAction();
    action.datastoreService = getDatastoreService();
    action.mrRunner = new MapreduceRunner(Optional.<Integer>absent(), Optional.<Integer>absent());
    action.response = new FakeResponse();
  }

  private void runMapreduce() throws Exception {
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  private void runTest(boolean shouldBeDeleted) throws Exception {
    NamespaceManager.set("blah1");
    final DomainApplicationIndex toDelete1 =
        DomainApplicationIndex.createWithSpecifiedReferences(
            "foo.bar", ImmutableSet.of(Ref.create(Key.create(DomainApplication.class, 1L))));
    final Registry toDelete2 = newRegistry("bar", "BAR");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().<ImmutableObject>entities(toDelete1, toDelete2).now();
      }});

    NamespaceManager.set("");
    final HostResource dontDelete1 = DatastoreHelper.newHostResource("blah.test.bar");
    final Registry dontDelete2 = newRegistry("baz", "BAZ");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().<ImmutableObject>entities(dontDelete1, dontDelete2).now();
      }});
    ofy().clearSessionCache();
    runMapreduce();
    assertThat(ofy().load().entity(dontDelete1).now()).isNotNull();
    assertThat(ofy().load().entity(dontDelete2).now()).isNotNull();

    NamespaceManager.set("blah1");
    if (shouldBeDeleted) {
      assertThat(ofy().load().entity(toDelete1).now()).isNull();
      assertThat(ofy().load().entity(toDelete2).now()).isNull();
    } else {
      assertThat(ofy().load().entity(toDelete1).now()).isNotNull();
      assertThat(ofy().load().entity(toDelete2).now()).isNotNull();
    }
  }

  @Test
  public void test_deletesOnlyEntitesInNonDefaultNamespace() throws Exception {
    runTest(true);
  }

  @Test
  public void test_dryRunDoesntDeleteAnything() throws Exception {
    action.isDryRun = true;
    runTest(false);
  }
}
