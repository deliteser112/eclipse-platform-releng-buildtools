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

package google.registry.model.index;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.index.DomainApplicationIndex.createUpdatedInstance;
import static google.registry.model.index.DomainApplicationIndex.createWithSpecifiedKeys;
import static google.registry.model.index.DomainApplicationIndex.loadActiveApplicationsByDomainName;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResource;
import static google.registry.testing.JUnitBackports.expectThrows;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.EntityTestCase;
import google.registry.model.domain.DomainApplication;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainApplicationIndex}. */
public class DomainApplicationIndexTest extends EntityTestCase {
  @Before
  public void init() throws Exception {
    createTld("com");
  }

  @Test
  public void testFailure_create_nullReferences() {
    IllegalArgumentException thrown =
        expectThrows(
            IllegalArgumentException.class,
            () -> DomainApplicationIndex.createWithSpecifiedKeys("blah.com", null));
    assertThat(thrown).hasMessageThat().contains("Keys must not be null or empty.");
  }

  @Test
  public void testFailure_create_emptyReferences() {
    IllegalArgumentException thrown =
        expectThrows(
            IllegalArgumentException.class,
            () -> createWithSpecifiedKeys("blah.com", ImmutableSet.of()));
    assertThat(thrown).hasMessageThat().contains("Keys must not be null or empty.");
  }

  @Test
  public void testSuccess_singleApplication() {
    DomainApplication application = persistSimpleResource(newDomainApplication("example.com"));
    persistResource(createUpdatedInstance(application));
    DomainApplicationIndex savedIndex = DomainApplicationIndex.load("example.com");
    assertThat(savedIndex).isNotNull();
    assertThat(savedIndex.getKeys()).containsExactly(Key.create(application));
    assertThat(loadActiveApplicationsByDomainName("example.com", clock.nowUtc()))
        .containsExactly(application);
  }

  @Test
  public void testSuccess_noApplications() {
    assertThat(DomainApplicationIndex.load("example.com")).isNull();
    assertThat(loadActiveApplicationsByDomainName("example.com", clock.nowUtc())).isEmpty();
  }

  @Test
  public void testSuccess_multipleApplications() {
    DomainApplication application1 = persistSimpleResource(newDomainApplication("example.com"));
    DomainApplication application2 = persistSimpleResource(newDomainApplication("example.com"));
    DomainApplication application3 = persistSimpleResource(newDomainApplication("example.com"));
    persistResource(createUpdatedInstance(application1));
    persistResource(createUpdatedInstance(application2));
    persistResource(createUpdatedInstance(application3));
    DomainApplicationIndex savedIndex = DomainApplicationIndex.load("example.com");
    assertThat(savedIndex).isNotNull();
    assertThat(savedIndex.getKeys()).containsExactly(
        Key.create(application1), Key.create(application2), Key.create(application3));
    assertThat(loadActiveApplicationsByDomainName("example.com", clock.nowUtc()))
        .containsExactly(application1, application2, application3);
  }

  @Test
  public void testSuccess_doesntStoreSameApplicationMultipleTimes() {
    DomainApplication application1 = persistSimpleResource(newDomainApplication("example.com"));
    DomainApplication application2 = persistSimpleResource(newDomainApplication("example.com"));
    persistResource(createUpdatedInstance(application1));
    persistResource(createUpdatedInstance(application2));
    persistResource(createUpdatedInstance(application1));
    assertThat(loadActiveApplicationsByDomainName("example.com", clock.nowUtc()))
        .containsExactly(application1, application2);
  }

  @Test
  public void testSuccess_doesntIncludePastApplications() {
    DomainApplication application1 = persistSimpleResource(newDomainApplication("example.com"));
    DomainApplication application2 =
        persistSimpleResource(
            newDomainApplication("example.com")
                .asBuilder()
                .setDeletionTime(DateTime.now(UTC).minusDays(30))
                .build());
    persistResource(createUpdatedInstance(application1));
    persistResource(createUpdatedInstance(application2));
    DomainApplicationIndex savedIndex =
        DomainApplicationIndex.load(application1.getFullyQualifiedDomainName());
    assertThat(savedIndex.getKeys()).hasSize(2);
    assertThat(loadActiveApplicationsByDomainName("example.com", DateTime.now(UTC)))
        .containsExactly(application1);
  }

  /** Ensure loading over 25 applications still succeeds (despite being in a transaction.) */
  @Test
  public void testSuccess_overCrossTransactionLimit() {
    final ImmutableList.Builder<DomainApplication> applicationsBuilder =
        new ImmutableList.Builder<>();
    for (int i = 0; i < 30; i++) {
      DomainApplication application = persistSimpleResource(newDomainApplication("example.com"));
      persistResource(createUpdatedInstance(application));
      applicationsBuilder.add(application);
    }
    ofy()
        .transact(
            () -> {
              assertThat(DomainApplicationIndex.load("example.com")).isNotNull();
              assertThat(loadActiveApplicationsByDomainName("example.com", clock.nowUtc()))
                  .containsExactlyElementsIn(applicationsBuilder.build());
            });
  }
}
