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

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.base.Optional;
import google.registry.testing.FakeClock;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ListHostsAction}.
 */
@RunWith(JUnit4.class)
public class ListHostsActionTest extends ListActionTestCase {

  ListHostsAction action;

  @Before
  public void init() throws Exception {
    createTld("foo");
    action = new ListHostsAction();
    action.clock = new FakeClock(DateTime.parse("2000-01-01TZ"));
  }

  @Test
  public void testRun_noParameters() throws Exception {
    testRunSuccess(
        action,
        null,
        null,
        null);
  }

  @Test
  public void testRun_twoLinesWithRepoId() throws Exception {
    persistActiveHost("example2.foo");
    persistActiveHost("example1.foo");
    testRunSuccess(
        action,
        Optional.of("repoId"),
        null,
        null,
        "^fullyQualifiedHostName\\s+repoId\\s*$",
        "^-+\\s+-+\\s*$",
        "^example1.foo\\s+3-ROID\\s*$",
        "^example2.foo\\s+2-ROID\\s*$");
  }

  @Test
  public void testRun_twoLinesWithWildcard() throws Exception {
    persistActiveHost("example2.foo");
    persistActiveHost("example1.foo");
    testRunSuccess(
        action,
        Optional.of("*"),
        null,
        null,
        "^fullyQualifiedHostName\\s+.*repoId",
        "^-+\\s+-+",
        "^example1.foo\\s+.*2",
        "^example2.foo\\s+.*1");
  }

  @Test
  public void testRun_twoLinesWithWildcardAndAnotherField() throws Exception {
    persistActiveHost("example2.foo");
    persistActiveHost("example1.foo");
    testRunSuccess(
        action,
        Optional.of("*,repoId"),
        null,
        null,
        "^fullyQualifiedHostName\\s+.*repoId",
        "^-+\\s+-+",
        "^example1.foo\\s+.*2",
        "^example2.foo\\s+.*1");
  }

  @Test
  public void testRun_withBadField_returnsError() throws Exception {
    persistActiveHost("example2.foo");
    persistActiveHost("example1.foo");
    testRunError(
        action,
        Optional.of("badfield"),
        null,
        null,
        "^Field 'badfield' not found - recognized fields are:",
        SC_BAD_REQUEST);
  }
}
