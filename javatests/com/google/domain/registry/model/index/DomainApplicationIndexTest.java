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

package com.google.domain.registry.model.index;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.index.DomainApplicationIndex.createUpdatedInstance;
import static com.google.domain.registry.model.index.DomainApplicationIndex.createWithSpecifiedReferences;
import static com.google.domain.registry.model.index.DomainApplicationIndex.loadActiveApplicationsByDomainName;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainApplication;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistSimpleGlobalResource;

import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.EntityTestCase;
import com.google.domain.registry.model.domain.DomainApplication;
import com.google.domain.registry.testing.ExceptionRule;

import com.googlecode.objectify.Ref;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link DomainApplicationIndex}. */
public class DomainApplicationIndexTest extends EntityTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Before
  public void init() throws Exception {
    createTld("com");
  }

  @Test
  public void testFailure_create_nullReferences() {
    thrown.expect(IllegalArgumentException.class, "References must not be null or empty.");
    DomainApplicationIndex.createWithSpecifiedReferences("blah.com", null);
  }

  @Test
  public void testFailure_create_emptyReferences() {
    thrown.expect(IllegalArgumentException.class, "References must not be null or empty.");
    createWithSpecifiedReferences("blah.com", ImmutableSet.<Ref<DomainApplication>>of());
  }

  @Test
  public void testSuccess_singleApplication() {
    DomainApplication application =
        persistSimpleGlobalResource(newDomainApplication("example.com"));
    persistResource(createUpdatedInstance(application));
    DomainApplicationIndex savedIndex = DomainApplicationIndex.load("example.com");
    assertThat(savedIndex).isNotNull();
    assertThat(savedIndex.getReferences()).containsExactly(Ref.create(application));
    assertThat(loadActiveApplicationsByDomainName("example.com", DateTime.now()))
        .containsExactly(application);
  }

  @Test
  public void testSuccess_noApplications() {
    assertThat(DomainApplicationIndex.load("example.com")).isNull();
    assertThat(loadActiveApplicationsByDomainName("example.com", DateTime.now())).isEmpty();
  }

  @Test
  public void testSuccess_multipleApplications() {
    DomainApplication application1 =
        persistSimpleGlobalResource(newDomainApplication("example.com"));
    DomainApplication application2 =
        persistSimpleGlobalResource(newDomainApplication("example.com"));
    DomainApplication application3 =
        persistSimpleGlobalResource(newDomainApplication("example.com"));
    persistResource(createUpdatedInstance(application1));
    persistResource(createUpdatedInstance(application2));
    persistResource(createUpdatedInstance(application3));
    DomainApplicationIndex savedIndex = DomainApplicationIndex.load("example.com");
    assertThat(savedIndex).isNotNull();
    assertThat(savedIndex.getReferences()).containsExactly(
        Ref.create(application1), Ref.create(application2), Ref.create(application3));
    assertThat(loadActiveApplicationsByDomainName("example.com", DateTime.now()))
        .containsExactly(application1, application2, application3);
  }

  @Test
  public void testSuccess_doesntStoreSameApplicationMultipleTimes() {
    DomainApplication application1 =
        persistSimpleGlobalResource(newDomainApplication("example.com"));
    DomainApplication application2 =
        persistSimpleGlobalResource(newDomainApplication("example.com"));
    persistResource(createUpdatedInstance(application1));
    persistResource(createUpdatedInstance(application2));
    persistResource(createUpdatedInstance(application1));
    assertThat(loadActiveApplicationsByDomainName("example.com", DateTime.now()))
        .containsExactly(application1, application2);
  }

  @Test
  public void testSuccess_doesntIncludePastApplications() {
    DomainApplication application1 =
        persistSimpleGlobalResource(newDomainApplication("example.com"));
    DomainApplication application2 = persistSimpleGlobalResource(
        newDomainApplication("example.com")
            .asBuilder()
            .setDeletionTime(DateTime.now().minusDays(30))
            .build());
    persistResource(createUpdatedInstance(application1));
    persistResource(createUpdatedInstance(application2));
    DomainApplicationIndex savedIndex =
        DomainApplicationIndex.load(application1.getFullyQualifiedDomainName());
    assertThat(savedIndex.getReferences()).hasSize(2);
    assertThat(loadActiveApplicationsByDomainName("example.com", DateTime.now()))
        .containsExactly(application1);
  }
}
