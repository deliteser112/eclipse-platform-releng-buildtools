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
import org.junit.Test;

/** Unit tests for {@link DomainApplicationInfoCommand}. */
public class DomainApplicationInfoCommandTest
    extends EppToolCommandTestCase<DomainApplicationInfoCommand> {

  @Test
  public void testSuccess() throws Exception {
    runCommandForced("--client=NewRegistrar", "--domain_name=example.tld",
        "--phase=landrush", "--id=123");
    eppVerifier().verifySent("domain_info_landrush.xml");
  }

  @Test
  public void testSuccess_subphase() throws Exception {
    // Sunrush: phase=sunrise, subphase=landrush
    runCommandForced("--client=NewRegistrar", "--domain_name=example.tld",
        "--phase=sunrush", "--id=123");
    eppVerifier().verifySent("domain_info_sunrush.xml");
  }

  @Test
  public void testFailure_invalidPhase() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--client=NewRegistrar", "--domain_name=example.tld",
        "--phase=landrise", "--id=123");
  }

  @Test
  public void testFailure_missingClientId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--domain_name=example.tld", "--phase=sunrush", "--id=123");
  }

  @Test
  public void testFailure_missingPhase() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--domain_name=example.tld", "--id=123");
  }

  @Test
  public void testFailure_missingApplicationId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--domain_name=example.tld",
        "--phase=landrush");
  }

  @Test
  public void testFailure_mainParameter() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--domain_name=example.tld",
        "--phase=landrush", "--id=123", "foo");
  }

  @Test
  public void testFailure_unknownFlag() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--domain_name=example.tld",
        "--phase=landrush", "--id=123", "--foo=bar");
  }
}
