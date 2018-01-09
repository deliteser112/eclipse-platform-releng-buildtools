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

package google.registry.model.domain;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.googlecode.objectify.Key;
import google.registry.model.EntityTestCase;
import google.registry.model.reporting.HistoryEntry;
import org.junit.Test;

/** Unit tests for {@link AllocationToken}. */
public class AllocationTokenTest extends EntityTestCase {

  @Test
  public void testPersistence() throws Exception {
    AllocationToken token =
        persistResource(
            new AllocationToken.Builder()
                .setToken("abc123")
                .setRedemptionHistoryEntry(Key.create(HistoryEntry.class, 1L))
                .build());
    assertThat(ofy().load().entity(token).now()).isEqualTo(token);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(new AllocationToken.Builder().setToken("abc123").build(), "token");
  }
}
