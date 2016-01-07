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
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.beust.jcommander.ParameterException;
import google.registry.model.common.Cursor;
import google.registry.model.common.Cursor.CursorType;
import google.registry.model.registry.Registry;
import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link ListCursorsCommand}. */
public class ListCursorsCommandTest extends CommandTestCase<ListCursorsCommand> {

  @Test
  public void testListCursors_noTlds_printsNothing() throws Exception {
    runCommand("--type=BRDA");
    assertThat(getStdoutAsString()).isEmpty();
  }

  @Test
  public void testListCursors_twoTldsOneAbsent_printsAbsentAndTimestampSorted() throws Exception {
    createTlds("foo", "bar");
    persistResource(
        Cursor.create(CursorType.BRDA, DateTime.parse("1984-12-18TZ"), Registry.get("bar")));
    runCommand("--type=BRDA");
    assertThat(getStdoutAsLines())
        .containsExactly(
            "1984-12-18T00:00:00.000Z bar",
            "absent                   foo")
        .inOrder();
  }

  @Test
  public void testListCursors_badCursor_throwsIae() throws Exception {
    thrown.expect(ParameterException.class, "Invalid value for --type parameter.");
    runCommand("--type=love");
  }

  @Test
  public void testListCursors_lowercaseCursor_isAllowed() throws Exception {
    runCommand("--type=brda");
  }

  @Test
  public void testListCursors_filterEscrowEnabled_doesWhatItSays() throws Exception {
    createTlds("foo", "bar");
    persistResource(Registry.get("bar").asBuilder().setEscrowEnabled(true).build());
    runCommand("--type=BRDA", "--escrow_enabled");
    assertThat(getStdoutAsLines()).containsExactly("absent                   bar");
  }
}
