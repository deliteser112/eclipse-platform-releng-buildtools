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

import static google.registry.util.ResourceUtils.readResourceUtf8;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;

import com.beust.jcommander.ParameterException;

import org.junit.Test;

import java.io.ByteArrayInputStream;

/** Unit tests for {@link ExecuteEppCommand}. */
public class ExecuteEppCommandTest extends EppToolCommandTestCase<ExecuteEppCommand> {

  private String xmlInput;
  private String eppFile;

  @Override
  void initEppToolCommandTestCase() throws Exception {
    xmlInput = readResourceUtf8(ExecuteEppCommandTest.class, "testdata/contact_create.xml");
    eppFile = writeToNamedTmpFile("eppFile", xmlInput);
  }

  @Test
  public void testSuccess() throws Exception {
    runCommand("--client=NewRegistrar", "--force", eppFile);
    verifySent("testdata/contact_create.xml", false, false);
  }

  @Test
  public void testSuccess_dryRun() throws Exception {
    runCommand("--client=NewRegistrar", "--dry_run", eppFile);
    verifySent("testdata/contact_create.xml", true, false);
  }

  @Test
  public void testSuccess_withSuperuser() throws Exception {
    runCommand("--client=NewRegistrar", "--superuser", "--force", eppFile);
    verifySent("testdata/contact_create.xml", false, true);
  }

  @Test
  public void testSuccess_fromStdin() throws Exception {
    inject.setStaticField(
        ExecuteEppCommand.class, "stdin", new ByteArrayInputStream(xmlInput.getBytes(UTF_8)));
    runCommand("--client=NewRegistrar", "--force");
    verifySent("testdata/contact_create.xml", false, false);
  }

  @Test
  public void testSuccess_multipleFiles() throws Exception {
    String xmlInput2 = readResourceUtf8(ExecuteEppCommandTest.class, "testdata/domain_check.xml");
    String eppFile2 = writeToNamedTmpFile("eppFile2", xmlInput2);
    runCommand("--client=NewRegistrar", "--force", eppFile, eppFile2);
    verifySent(
        ImmutableList.of("testdata/contact_create.xml", "testdata/domain_check.xml"),
        false,
        false);
  }

  @Test
  public void testFailure_missingClientId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand("--force", "foo.xml");
  }

  @Test
  public void testFailure_forceAndDryRunIncompatible() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--client=NewRegistrar", "--force", "--dry_run", eppFile);
  }

  @Test
  public void testFailure_unknownFlag() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand("--client=NewRegistrar", "--unrecognized=foo", "--force", "foo.xml");
  }
}
