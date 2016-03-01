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

package com.google.domain.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.EppResourceUtils.loadByUniqueId;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.model.tmch.ClaimsListShardTest.createTestClaimsListShard;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.flows.EppException.CommandUseErrorException;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.model.domain.launch.ApplicationIdTargetExtension;
import com.google.domain.registry.model.eppinput.EppInput.ResourceCommandWrapper;
import com.google.domain.registry.model.eppinput.ResourceCommand;
import com.google.domain.registry.model.index.EppResourceIndex;
import com.google.domain.registry.model.index.EppResourceIndexBucket;
import com.google.domain.registry.model.tmch.ClaimsListShard.ClaimsListRevision;
import com.google.domain.registry.model.tmch.ClaimsListShard.ClaimsListSingleton;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.util.TypeUtils.TypeInstantiator;

import com.googlecode.objectify.Key;

import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;

/**
 * Base class for resource flow unit tests.
 *
 * @param <F> the flow type
 * @param <R> the resource type
 */
public abstract class ResourceFlowTestCase<F extends Flow, R extends EppResource>
    extends FlowTestCase<F> {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private R reloadResourceByUniqueId(DateTime now) throws Exception {
    // Force the session to be cleared so that when we read it back, we read from the datastore and
    // not from the transaction cache or memcache.
    ofy().clearSessionCache();
    return loadByUniqueId(getResourceClass(), getUniqueIdFromCommand(), now);
  }

  protected R reloadResourceByUniqueId() throws Exception {
    return reloadResourceByUniqueId(clock.nowUtc());
  }

  protected R reloadResourceByUniqueIdYesterday() throws Exception {
    return reloadResourceByUniqueId(clock.nowUtc().minusDays(1));
  }

  protected <T extends EppResource> T reloadResourceAndCloneAtTime(T resource, DateTime now) {
    // Force the session to be cleared.
    ofy().clearSessionCache();
    @SuppressWarnings("unchecked")
    T refreshedResource = (T) ofy().load().entity(resource).now().cloneProjectedAtTime(now);
    return refreshedResource;
  }

  protected ResourceCommand.SingleResourceCommand getResourceCommand() throws Exception {
    return (ResourceCommand.SingleResourceCommand)
        ((ResourceCommandWrapper) eppLoader.getEpp().getCommandWrapper().getCommand())
            .getResourceCommand();
  }

  /**
   * We have to duplicate the logic from SingleResourceFlow.getTargetId() here. However, given the
   * choice between making that method public, and duplicating its logic, it seems better to muddy
   * the test code rather than the production code.
   */
  protected String getUniqueIdFromCommand() throws Exception {
    ApplicationIdTargetExtension extension =
        eppLoader.getEpp().getSingleExtension(ApplicationIdTargetExtension.class);
    return extension == null ? getResourceCommand().getTargetId() : extension.getApplicationId();
  }

  protected Class<R> getResourceClass() {
    return new TypeInstantiator<R>(getClass()){}.getExactType();
  }

  /**
   * Persists a testing claims list to Datastore that contains a single shard.
   */
  protected void persistClaimsList(ImmutableMap<String, String> labelsToKeys) {
    ClaimsListSingleton singleton = new ClaimsListSingleton();
    Key<ClaimsListRevision> revision = ClaimsListRevision.createKey(singleton);
    singleton.setActiveRevision(revision);
    ofy().saveWithoutBackup().entity(singleton).now();
    if (!labelsToKeys.isEmpty()) {
      ofy().saveWithoutBackup()
          .entity(createTestClaimsListShard(clock.nowUtc(), labelsToKeys, revision))
          .now();
    }
  }

  @Test
  public void testRequiresLogin() throws Exception {
    thrown.expect(CommandUseErrorException.class);
    sessionMetadata.setClientId(null);
    runFlow();
  }

  /**
   * Confirms that an EppResourceIndex entity exists in datastore for a given resource.
   */
  protected static <T extends EppResource> void assertEppResourceIndexEntityFor(final T resource) {
    ImmutableList<EppResourceIndex> indices = FluentIterable
        .from(ofy().load()
            .type(EppResourceIndex.class)
            .filter("kind", Key.getKind(resource.getClass())))
        .filter(new Predicate<EppResourceIndex>() {
            @Override
            public boolean apply(EppResourceIndex index) {
              return index.getReference().get().equals(resource);
            }})
        .toList();
    assertThat(indices).hasSize(1);
    assertThat(indices.get(0).getBucket())
        .isEqualTo(EppResourceIndexBucket.getBucketKey(Key.create(resource)));
  }
}
