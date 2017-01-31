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

import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.List;
import org.junit.Test;

/** Unit tests for {@link EppToolCommand}. */
public class EppToolCommandTest extends EppToolCommandTestCase<EppToolCommand> {

  /** Dummy implementation of EppToolCommand. */
  @Parameters(separators = " =", commandDescription = "Dummy EppToolCommand")
  static class TestEppToolCommand extends EppToolCommand {

    @Parameter(names = {"--client"})
    String clientId;

    @Parameter
    List<String> xmlPayloads;

    @Override
    void initEppToolCommand() throws Exception {
      for (String xmlData : xmlPayloads) {
        addXmlCommand(clientId, xmlData);
      }
    }
  }

  @Override
  protected EppToolCommand newCommandInstance() {
    return new TestEppToolCommand();
  }

  @Test
  public void testSuccess_singleXmlCommand() throws Exception {
    // The choice of xml file is arbitrary.
    runCommandForced(
        "--client=NewRegistrar",
        readResourceUtf8(getClass(), "testdata/contact_create.xml"));
    eppVerifier().verifySent("contact_create.xml");
  }

  @Test
  public void testSuccess_multipleXmlCommands() throws Exception {
    // The choice of xml files is arbitrary.
    runCommandForced(
        "--client=NewRegistrar",
        readResourceUtf8(getClass(), "testdata/contact_create.xml"),
        readResourceUtf8(getClass(), "testdata/domain_check.xml"),
        readResourceUtf8(getClass(), "testdata/domain_check_fee.xml"));
    eppVerifier().verifySent("contact_create.xml", "domain_check.xml", "domain_check_fee.xml");
  }

  @Test
  public void testFailure_nonexistentClientId() throws Exception {
    thrown.expect(IllegalArgumentException.class, "fakeclient");
    runCommandForced("--client=fakeclient", "fake-xml");
  }
}
