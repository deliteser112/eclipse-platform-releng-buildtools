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
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static google.registry.testing.DatastoreHelper.persistDeletedDomainApplication;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link GetApplicationCommand}. */
public class GetApplicationCommandTest extends CommandTestCase<GetApplicationCommand> {

  DateTime now = DateTime.now(UTC);

  @Before
  public void initialize() {
    createTld("tld");
  }

  @Test
  public void testSuccess() throws Exception {
    persistActiveDomainApplication("example.tld");
    runCommand("2-TLD");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("contact=Key<?>(ContactResource(\"3-ROID\"))");
  }

  @Test
  public void testSuccess_expand() throws Exception {
    persistActiveDomainApplication("example.tld");
    runCommand("2-TLD", "--expand");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("contactId=contact1234");
    assertNotInStdout("LiveRef");
  }

  @Test
  public void testSuccess_multipleArguments() throws Exception {
    persistActiveDomainApplication("example.tld");
    persistActiveDomainApplication("example2.tld");
    persistActiveDomainApplication("example3.tld");
    runCommand("2-TLD", "4-TLD", "6-TLD");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("fullyQualifiedDomainName=example2.tld");
    assertInStdout("fullyQualifiedDomainName=example3.tld");
  }

  @Test
  public void testSuccess_applicationDeletedInFuture() throws Exception {
    persistResource(
        newDomainApplication("example.tld").asBuilder().setDeletionTime(now.plusDays(1)).build());
    runCommand("--read_timestamp=" + now.plusMonths(1), "2-TLD");
    assertInStdout("Application '2-TLD' does not exist or is deleted");
  }

  @Test
  public void testSuccess_deletedApplication() throws Exception {
    persistDeletedDomainApplication("example.tld",  now.minusDays(1));
    runCommand("2-TLD");
    assertInStdout("Application '2-TLD' does not exist or is deleted");
  }

  @Test
  public void testSuccess_applicationDoesNotExist() throws Exception {
    runCommand("42-TLD");
    assertInStdout("Application '42-TLD' does not exist or is deleted");
  }

  @Test
  public void testFailure_noApplicationId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand();
  }

  @Test
  public void testSuccess_oneApplicationDoesNotExist() throws Exception {
    persistActiveDomainApplication("example.tld");
    persistActiveDomainApplication("example2.tld");
    runCommand("2-TLD", "4-TLD", "55-TLD");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("fullyQualifiedDomainName=example2.tld");
    assertInStdout("Application '55-TLD' does not exist or is deleted");
  }
}
