// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.model.domain.token;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;

import com.googlecode.objectify.Key;
import google.registry.model.EntityTestCase;
import google.registry.model.reporting.HistoryEntry;
import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link AllocationToken}. */
public class AllocationTokenTest extends EntityTestCase {

  @Test
  public void testPersistence() {
    AllocationToken token =
        persistResource(
            new AllocationToken.Builder()
                .setToken("abc123")
                .setRedemptionHistoryEntry(Key.create(HistoryEntry.class, 1L))
                .setDomainName("foo.example")
                .setCreationTimeForTest(DateTime.parse("2010-11-12T05:00:00Z"))
                .build());
    assertThat(ofy().load().entity(token).now()).isEqualTo(token);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(
        persistResource(
            new AllocationToken.Builder()
                .setToken("abc123")
                .setRedemptionHistoryEntry(Key.create(HistoryEntry.class, 1L))
                .setDomainName("blahdomain.fake")
                .setCreationTimeForTest(DateTime.parse("2010-11-12T05:00:00Z"))
                .build()),
        "token",
        "redemptionHistoryEntry",
        "domainName");
  }

  @Test
  public void testCreationTime_autoPopulates() {
    AllocationToken tokenBeforePersisting =
        new AllocationToken.Builder().setToken("abc123").build();
    assertThat(tokenBeforePersisting.getCreationTime()).isEmpty();
    AllocationToken tokenAfterPersisting = persistResource(tokenBeforePersisting);
    assertThat(tokenAfterPersisting.getCreationTime()).hasValue(clock.nowUtc());
  }

  @Test
  public void testSetCreationTime_cantCallMoreThanOnce() {
    AllocationToken.Builder builder =
        new AllocationToken.Builder()
            .setToken("foobar")
            .setCreationTimeForTest(DateTime.parse("2010-11-12T05:00:00Z"));
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> builder.setCreationTimeForTest(DateTime.parse("2010-11-13T05:00:00Z")));
    assertThat(thrown).hasMessageThat().isEqualTo("creationTime can only be set once");
  }

  @Test
  public void testSetToken_cantCallMoreThanOnce() {
    AllocationToken.Builder builder = new AllocationToken.Builder().setToken("foobar");
    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> builder.setToken("barfoo"));
    assertThat(thrown).hasMessageThat().isEqualTo("token can only be set once");
  }
}
