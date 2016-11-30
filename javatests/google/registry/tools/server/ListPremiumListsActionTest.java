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

package google.registry.tools.server;

import static google.registry.testing.DatastoreHelper.persistPremiumList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.base.Optional;
import google.registry.model.registry.label.PremiumList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ListPremiumListsAction}.
 */
@RunWith(JUnit4.class)
public class ListPremiumListsActionTest extends ListActionTestCase {

  ListPremiumListsAction action;

  @Before
  public void init() throws Exception {
    persistPremiumList("xn--q9jyb4c", "rich,USD 100");
    persistPremiumList("how", "richer,JPY 5000");
    PremiumList.get("how").get().asBuilder()
        .setDescription("foobar")
        .build()
        .saveAndUpdateEntries();
    action = new ListPremiumListsAction();
  }

  @Test
  public void testRun_noParameters() throws Exception {
    testRunSuccess(
        action,
        null,
        null,
        null,
        "^how        $",
        "^xn--q9jyb4c$");
  }

  @Test
  public void testRun_withParameters() throws Exception {
    testRunSuccess(
        action,
        Optional.of("revisionKey,description"),
        null,
        null,
        "^name\\s+revisionKey\\s+description\\s*$",
        "^-+\\s+-+\\s+-+\\s*$",
        "^how\\s+.*PremiumList.*$",
        "^xn--q9jyb4c\\s+.*PremiumList.*$");
  }

  @Test
  public void testRun_withWildcard() throws Exception {
    testRunSuccess(
        action,
        Optional.of("*"),
        null,
        null,
        "^name\\s+.*revisionKey",
        "^-+\\s+-+.*",
        "^how\\s+.*PremiumList",
        "^xn--q9jyb4c\\s+.*PremiumList");
  }

  @Test
  public void testRun_withBadField_returnsError() throws Exception {
    testRunError(
        action,
        Optional.of("badfield"),
        null,
        null,
        "^Field 'badfield' not found - recognized fields are:",
        SC_BAD_REQUEST);
  }
}
