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

package google.registry.model.export;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import google.registry.model.EntityTestCase;
import google.registry.model.common.PersistedRangeLong;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link LogsExportCursor}. */
public class LogsExportCursorTest extends EntityTestCase {

  LogsExportCursor logsExportCursor;

  @Before
  public void setUp() throws Exception {
    logsExportCursor = new LogsExportCursor.Builder()
        .setExportedRanges(ImmutableSet.of(
            PersistedRangeLong.create(Range.<Long>all()),
            PersistedRangeLong.create(Range.<Long>lessThan(10L)),
            PersistedRangeLong.create(Range.<Long>atMost(10L)),
            PersistedRangeLong.create(Range.<Long>greaterThan(10L)),
            PersistedRangeLong.create(Range.<Long>atLeast(10L)),
            PersistedRangeLong.create(Range.<Long>open(5L, 10L)),
            PersistedRangeLong.create(Range.<Long>closed(5L, 10L)),
            PersistedRangeLong.create(Range.<Long>openClosed(5L, 10L)),
            PersistedRangeLong.create(Range.<Long>closedOpen(5L, 10L))))
        .setFilesPendingImport(ImmutableList.of(
            "gs://foo/bar",
            "gs://foo/baz",
            "gs://foo/bash"))
        .build();
    persistResource(logsExportCursor);
  }

  @Test
  public void testPersistence() throws Exception {
    assertThat(ofy().load().entity(logsExportCursor).now()).isEqualTo(logsExportCursor);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(logsExportCursor);
  }
}
