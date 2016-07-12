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

package google.registry.tools;

import com.beust.jcommander.ParameterException;
import org.junit.Test;

/** Unit tests for {@link DomainCheckClaimsCommand}. */
public class DomainCheckClaimsCommandTest extends EppToolCommandTestCase<DomainCheckClaimsCommand> {

  @Test
  public void testSuccess() throws Exception {
    runCommandForced("--client=NewRegistrar", "example.tld");
    eppVerifier().verifySent("domain_check_claims.xml");
  }

  @Test
  public void testSuccess_multipleTlds() throws Exception {
    runCommandForced("--client=NewRegistrar", "example.tld", "example.tld2");
    eppVerifier().verifySent(
        "domain_check_claims.xml",
        "domain_check_claims_second_tld.xml");
  }

  @Test
  public void testSuccess_multipleDomains() throws Exception {
    runCommandForced(
        "--client=NewRegistrar",
        "example.tld",
        "example2.tld",
        "example3.tld");
    eppVerifier().verifySent("domain_check_claims_multiple.xml");
  }

  @Test
  public void testSuccess_multipleDomainsAndTlds() throws Exception {
    runCommandForced(
        "--client=NewRegistrar",
        "example.tld",
        "example2.tld",
        "example3.tld",
        "example.tld2");
    eppVerifier().verifySent(
        "domain_check_claims_multiple.xml",
        "domain_check_claims_second_tld.xml");
  }

  @Test
  public void testFailure_missingClientId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("example.tld");
  }

  @Test
  public void testFailure_NoMainParameter() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar");
  }

  @Test
  public void testFailure_unknownFlag() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--unrecognized=foo", "example.tld");
  }
}
