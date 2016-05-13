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

package com.google.domain.registry.tools;

import com.google.common.collect.ImmutableList;

import com.beust.jcommander.ParameterException;

import org.junit.Test;

/** Unit tests for {@link DomainCheckCommand}. */
public class DomainCheckCommandTest extends EppToolCommandTestCase<DomainCheckCommand> {

  @Test
  public void testSuccess() throws Exception {
    runCommandForced("--client=NewRegistrar", "example.tld");
    verifySent("testdata/domain_check.xml", false, false);
  }

  @Test
  public void testSuccess_multipleTlds() throws Exception {
    runCommandForced("--client=NewRegistrar", "example.tld", "example.tld2");
    verifySent(
        ImmutableList.of(
            "testdata/domain_check.xml",
            "testdata/domain_check_second_tld.xml"),
        false,
        false);
  }

  @Test
  public void testSuccess_multipleDomains() throws Exception {
    runCommandForced(
        "--client=NewRegistrar",
        "example.tld",
        "example2.tld",
        "example3.tld");
    verifySent("testdata/domain_check_multiple.xml", false, false);
  }

  @Test
  public void testSuccess_multipleDomainsAndTlds() throws Exception {
    runCommandForced(
        "--client=NewRegistrar",
        "example.tld",
        "example2.tld",
        "example3.tld",
        "example.tld2");
    verifySent(
        ImmutableList.of(
            "testdata/domain_check_multiple.xml",
            "testdata/domain_check_second_tld.xml"),
        false,
        false);
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
