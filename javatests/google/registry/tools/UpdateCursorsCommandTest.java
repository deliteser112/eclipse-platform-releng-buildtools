// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;

import google.registry.model.common.Cursor;
import google.registry.model.common.Cursor.CursorType;
import google.registry.model.registry.Registry;
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
    assertThat(ofy().load().key(Cursor.createKey(CursorType.BRDA, registry)).now().getCursorTime())
        .isEqualTo(DateTime.parse("1984-12-18TZ"));
  }

  @Test
  public void testUpdateCursors_oldValueIsAbsent() throws Exception {
    assertThat(ofy().load().key(Cursor.createKey(CursorType.BRDA, registry)).now()).isNull();
    doUpdateTest();
 }

  @Test
  public void testUpdateCursors_hasOldValue() throws Exception {
    persistResource(Cursor.create(CursorType.BRDA, DateTime.parse("1950-12-18TZ"), registry));
    doUpdateTest();
  }
}
