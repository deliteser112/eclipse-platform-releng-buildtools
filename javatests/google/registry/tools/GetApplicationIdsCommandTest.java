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

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import google.registry.model.domain.launch.LaunchPhase;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link GetApplicationIdsCommand}. */
public class GetApplicationIdsCommandTest extends CommandTestCase<GetApplicationIdsCommand> {

  DateTime now = DateTime.now(UTC);

  @Before
  public void initialize() {
    createTld("tld");
  }

  @Test
  public void testSuccess() throws Exception {
    persistDomainApplication("example.tld", "1-TLD");
    runCommand("example.tld");
    assertInStdout("1-TLD (TheRegistrar)");
  }

  @Test
  public void testSuccess_multipleArguments() throws Exception {
    persistDomainApplication("example.tld", "1-TLD");
    persistDomainApplication("example2.tld", "2-TLD");
    runCommand("example.tld", "example2.tld");
    assertInStdout("1-TLD (TheRegistrar)");
    assertInStdout("2-TLD (TheRegistrar)");
  }

  @Test
  public void testSuccess_domainDoesNotExist() throws Exception {
    runCommand("something.tld");
    assertInStdout("No applications exist for 'something.tld'.");
  }

  @Test
  public void testFailure_tldDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Domain name is not under a recognized TLD");
    runCommand("example.foo");
  }

  @Test
  public void testSuccess_deletedApplication() throws Exception {
    persistResource(
        newDomainApplication("example.tld").asBuilder().setDeletionTime(now.minusDays(1)).build());
    runCommand("example.tld");
    assertInStdout("No applications exist for 'example.tld'.");
  }

  @Test
  public void testFailure_noDomainName() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand();
  }

  private void persistDomainApplication(String domainName, String repoId) {
    persistResource(
        newDomainApplication(
            domainName, repoId, persistActiveContact("icn1001"), LaunchPhase.OPEN));
  }

  @Test
  public void testSuccess_oneDomainDoesNotExist() throws Exception {
    persistDomainApplication("example.tld", "1-TLD");
    createTld("com");
    runCommand("example.com", "example.tld", "example2.com");
    assertInStdout("1-TLD (TheRegistrar)");
    assertInStdout("No applications exist for 'example.com'.");
    assertInStdout("No applications exist for 'example2.com'.");
  }
}
