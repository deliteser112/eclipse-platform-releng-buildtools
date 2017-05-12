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

import com.beust.jcommander.ParameterException;
import google.registry.testing.DeterministicStringGenerator;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link CreateDomainCommand}. */
public class CreateDomainCommandTest extends EppToolCommandTestCase<CreateDomainCommand> {

  @Before
  public void initCommand() {
    command.passwordGenerator = new DeterministicStringGenerator("abcdefghijklmnopqrstuvwxyz");
  }

  @Test
  public void testSuccess_complete() throws Exception {
    runCommandForced(
        "--client=NewRegistrar",
        "--period=1",
        "--nameservers=ns1.zdns.google,ns2.zdns.google,ns3.zdns.google,ns4.zdns.google",
        "--registrant=crr-admin",
        "--admin=crr-admin",
        "--tech=crr-tech",
        "--password=2fooBAR",
        "example.tld");
    eppVerifier().verifySent("domain_create_complete.xml");
  }

  @Test
  public void testSuccess_minimal() throws Exception {
    // Test that each optional field can be omitted. Also tests the auto-gen password.
    runCommandForced(
        "--client=NewRegistrar",
        "--registrant=crr-admin",
        "--admin=crr-admin",
        "--tech=crr-tech",
        "example.tld");
    eppVerifier().verifySent("domain_create_minimal.xml");
  }

  @Test
  public void testSuccess_multipleDomains() throws Exception {
    runCommandForced(
        "--client=NewRegistrar",
        "--registrant=crr-admin",
        "--admin=crr-admin",
        "--tech=crr-tech",
        "example.tld",
        "example.abc");
    eppVerifier().verifySent("domain_create_minimal.xml", "domain_create_minimal_abc.xml");
  }

  @Test
  public void testFailure_duplicateDomains() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Duplicate arguments found: \'example.tld\'");
    runCommandForced(
        "--client=NewRegistrar",
        "--registrant=crr-admin",
        "--admin=crr-admin",
        "--tech=crr-tech",
        "example.tld",
        "example.tld");
  }

  @Test
  public void testFailure_missingDomain() throws Exception {
    thrown.expect(ParameterException.class, "Main parameters are required");
    runCommandForced(
        "--client=NewRegistrar", "--registrant=crr-admin", "--admin=crr-admin", "--tech=crr-tech");
  }

  @Test
  public void testFailure_missingClientId() throws Exception {
    thrown.expect(ParameterException.class, "--client");
    runCommandForced(
        "--admin=crr-admin", "--tech=crr-tech", "--registrant=crr-admin", "example.tld");
  }

  @Test
  public void testFailure_missingRegistrant() throws Exception {
    thrown.expect(ParameterException.class, "--registrant");
    runCommandForced(
        "--client=NewRegistrar", "--admin=crr-admin", "--tech=crr-tech", "example.tld");
  }

  @Test
  public void testFailure_missingAdmin() throws Exception {
    thrown.expect(ParameterException.class, "--admin");
    runCommandForced(
        "--client=NewRegistrar", "--registrant=crr-admin", "--tech=crr-tech", "example.tld");
  }

  @Test
  public void testFailure_missingTech() throws Exception {
    thrown.expect(ParameterException.class, "--tech");
    runCommandForced(
        "--client=NewRegistrar", "--registrant=crr-admin", "--admin=crr-admin", "example.tld");
  }

  @Test
  public void testFailure_tooManyNameServers() throws Exception {
    thrown.expect(IllegalArgumentException.class, "There can be at most 13 nameservers");
    runCommandForced(
        "--client=NewRegistrar",
        "--registrant=crr-admin",
        "--admin=crr-admin",
        "--tech=crr-tech",
        "--nameservers=ns1.zdns.google,ns2.zdns.google,ns3.zdns.google,ns4.zdns.google,"
            + "ns5.zdns.google,ns6.zdns.google,ns7.zdns.google,ns8.zdns.google,"
            + "ns9.zdns.google,ns10.zdns.google,ns11.zdns.google,ns12.zdns.google,"
            + "ns13.zdns.google,ns14.zdns.google",
        "example.tld");
  }

  @Test
  public void testFailure_badPeriod() throws Exception {
    thrown.expect(ParameterException.class, "--period");
    runCommandForced(
        "--client=NewRegistrar",
        "--registrant=crr-admin",
        "--admin=crr-admin",
        "--tech=crr-tech",
        "--period=x",
        "--domain=example.tld");
  }
}
