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

package google.registry.tools.server;

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.base.Optional;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservedList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ListReservedListsAction}.
 */
@RunWith(JUnit4.class)
public class ListReservedListsActionTest extends ListActionTestCase {

  ListReservedListsAction action;

  @Before
  public void init() throws Exception {
    ReservedList rl1 = persistReservedList("xn--q9jyb4c-published", true, "blah,FULLY_BLOCKED");
    ReservedList rl2 = persistReservedList("xn--q9jyb4c-private", false, "dugong,FULLY_BLOCKED");
    createTld("xn--q9jyb4c");
    persistResource(Registry.get("xn--q9jyb4c").asBuilder().setReservedLists(rl1, rl2).build());
    action = new ListReservedListsAction();
  }

  @Test
  public void testRun_noParameters() throws Exception {
    testRunSuccess(
        action,
        null,
        null,
        null,
        "^xn--q9jyb4c-private\\s*$",
        "^xn--q9jyb4c-published\\s*$");
  }

  @Test
  public void testRun_withParameters() throws Exception {
    testRunSuccess(
        action,
        Optional.of("shouldPublish"),
        null,
        null,
        "^name\\s+shouldPublish\\s*$",
        "^-+\\s+-+\\s*$",
        "^xn--q9jyb4c-private\\s+false\\s*$",
        "^xn--q9jyb4c-published\\s+true\\s*$");
  }

  @Test
  public void testRun_withWildcard() throws Exception {
    testRunSuccess(
        action,
        Optional.of("*"),
        null,
        null,
        "^name\\s+.*shouldPublish.*",
        "^-+\\s+-+",
        "^xn--q9jyb4c-private\\s+.*false",
        "^xn--q9jyb4c-published\\s+.*true");
  }

  @Test
  public void testRun_withBadField_returnsError() throws Exception {
    testRunError(
        action,
        Optional.of("badfield"),
        null,
        null,
        "^Field 'badfield' not found - recognized fields are:");
  }
}
