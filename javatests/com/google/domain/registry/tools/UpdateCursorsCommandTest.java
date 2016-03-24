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

package com.google.domain.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;

import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.RegistryCursor;
import com.google.domain.registry.model.registry.RegistryCursor.CursorType;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link UpdateCursorsCommand}. */
public class UpdateCursorsCommandTest extends CommandTestCase<UpdateCursorsCommand> {

  Registry registry;

  @Before
  public void before() {
    createTld("foo");
    registry = Registry.get("foo");
  }

  void doUpdateTest() throws Exception {
    runCommandForced("--type=brda", "--timestamp=1984-12-18T00:00:00Z", "foo");
    assertThat(RegistryCursor.load(registry, CursorType.BRDA))
        .hasValue(DateTime.parse("1984-12-18TZ"));
  }

  @Test
  public void testUpdateCursors_oldValueIsAbsent() throws Exception {
    assertThat(RegistryCursor.load(registry, CursorType.BRDA)).isAbsent();
    doUpdateTest();
 }

  @Test
  public void testUpdateCursors_hasOldValue() throws Exception {
    persistResource(
        RegistryCursor.create(registry, CursorType.BRDA, DateTime.parse("1950-12-18TZ")));
    doUpdateTest();
  }
}
