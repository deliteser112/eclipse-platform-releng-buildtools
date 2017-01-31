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

package google.registry.tools;

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.googlecode.objectify.Key;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.LrpTokenEntity;
import google.registry.model.reporting.HistoryEntry;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link GetLrpTokenCommand}. */
public class GetLrpTokenCommandTest extends CommandTestCase<GetLrpTokenCommand> {

  @Before
  public void before() {
    createTld("tld");
    DomainApplication lrpApplication = persistActiveDomainApplication("domain.tld");
    HistoryEntry applicationCreateHistoryEntry = persistResource(new HistoryEntry.Builder()
        .setParent(lrpApplication)
        .setType(HistoryEntry.Type.DOMAIN_APPLICATION_CREATE)
        .build());
    persistResource(
        new LrpTokenEntity.Builder()
            .setAssignee("domain.tld")
            .setToken("domain_token")
            .setRedemptionHistoryEntry(Key.create(applicationCreateHistoryEntry))
            .build());
  }

  @Test
  public void testSuccess_byAssignee() throws Exception {
    runCommand("--assignee=domain.tld");
    assertInStdout("domain_token");
  }

  @Test
  public void testSuccess_byToken() throws Exception {
    runCommand("--token=domain_token");
    assertInStdout("domain.tld");
    assertNotInStdout("fullyQualifiedDomainName=domain.tld"); // history param should be false
  }

  @Test
  public void testSuccess_iosByToken_withHistory() throws Exception {
    runCommand("--token=domain_token", "--history");
    assertInStdout("domain.tld");
    assertInStdout("fullyQualifiedDomainName=domain.tld");
    assertInStdout("type=DOMAIN_APPLICATION_CREATE");
  }

  @Test
  public void testSuccess_unknownAssignee() throws Exception {
    runCommand("--assignee=nobody");
    assertInStdout("Token not found");
  }

  @Test
  public void testSuccess_unknownToken() throws Exception {
    runCommand("--token=bogus_token");
    assertInStdout("Token not found");
  }

  @Test
  public void testFailure_noArgs() throws Exception {
    thrown.expect(
        IllegalArgumentException.class,
        "Exactly one of either token or assignee must be specified.");
    runCommand();
  }
}
