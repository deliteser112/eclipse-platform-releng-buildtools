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

import static google.registry.flows.EppServletUtils.APPLICATION_EPP_XML_UTF8;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import org.junit.Test;

import java.util.List;

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
    runCommandForced("--client=NewRegistrar", "xml");
    ImmutableMap<String, Object> params = ImmutableMap.<String, Object>of(
        "clientIdentifier", "NewRegistrar",
        "superuser", false,
        "dryRun", false);
    verify(connection)
        .send(eq("/_dr/epptool"), eq(params), eq(APPLICATION_EPP_XML_UTF8), eq("xml".getBytes()));
  }

  @Test
  public void testSuccess_multipleXmlCommands() throws Exception {
    runCommandForced("--client=NewRegistrar", "one", "two", "three");
    ImmutableMap<String, Object> params = ImmutableMap.<String, Object>of(
        "clientIdentifier", "NewRegistrar",
        "superuser", false,
        "dryRun", false);
    verify(connection)
        .send(eq("/_dr/epptool"), eq(params), eq(APPLICATION_EPP_XML_UTF8), eq("one".getBytes()));
    verify(connection)
        .send(eq("/_dr/epptool"), eq(params), eq(APPLICATION_EPP_XML_UTF8), eq("two".getBytes()));
    verify(connection)
        .send(eq("/_dr/epptool"), eq(params), eq(APPLICATION_EPP_XML_UTF8), eq("three".getBytes()));
  }

  @Test
  public void testFailure_nonexistentClientId() throws Exception {
    thrown.expect(IllegalArgumentException.class, "fakeclient");
    runCommandForced("--client=fakeclient", "fake-xml");
  }
}
