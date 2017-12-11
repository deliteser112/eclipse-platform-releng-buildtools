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

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static org.mockito.Mockito.when;

import com.beust.jcommander.ParameterException;
import com.google.appengine.api.modules.ModulesService;
import google.registry.testing.InjectRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;

/** Unit tests for {@link GenerateEscrowDepositCommand}. */
public class GenerateEscrowDepositCommandTest
    extends CommandTestCase<GenerateEscrowDepositCommand> {

  @Rule
  public final InjectRule inject = new InjectRule();

  @Mock ModulesService modulesService;

  @Before
  public void before() throws Exception {
    createTld("tld");
    createTld("anothertld");
    command = new GenerateEscrowDepositCommand();
    command.modulesService = modulesService;
    command.queue = getQueue("rde-report");
    when(modulesService.getVersionHostname(Matchers.anyString(), Matchers.anyString()))
        .thenReturn("1.backend.test.localhost");
  }

  @Test
  public void testCommand_missingTld() throws Exception {
    thrown.expect(ParameterException.class);
    thrown.expectMessage("The following option is required: -t, --tld");
    runCommand("--watermark=2017-01-01T00:00:00Z", "--mode=thin", "-r 42", "-o test");
  }

  @Test
  public void testCommand_emptyTld() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Null or empty TLD specified");
    runCommand("--tld=", "--watermark=2017-01-01T00:00:00Z", "--mode=thin", "-r 42", "-o test");
  }

  @Test
  public void testCommand_invalidTld() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("TLDs do not exist: invalid");
    runCommand(
        "--tld=invalid", "--watermark=2017-01-01T00:00:00Z", "--mode=thin", "-r 42", "-o test");
  }

  @Test
  public void testCommand_missingWatermark() throws Exception {
    thrown.expect(ParameterException.class);
    thrown.expectMessage("The following option is required: -w, --watermark");
    runCommand("--tld=tld", "--mode=full", "-r 42", "-o test");
  }

  @Test
  public void testCommand_emptyWatermark() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid format: \"\"");
    runCommand("--tld=tld", "--watermark=", "--mode=full", "-r 42", "-o test");
  }

  @Test
  public void testCommand_missingOutdir() throws Exception {
    thrown.expect(ParameterException.class);
    thrown.expectMessage("The following option is required: -o, --outdir");
    runCommand("--tld=tld", "--watermark=2017-01-01T00:00:00Z", "--mode=thin", "-r 42");
  }

  @Test
  public void testCommand_emptyOutdir() throws Exception {
    thrown.expect(ParameterException.class);
    thrown.expectMessage("Output subdirectory must not be empty");
    runCommand(
        "--tld=tld", "--watermark=2017-01-01T00:00:00Z", "--mode=thin", "--outdir=", "-r 42");
  }

  @Test
  public void testCommand_invalidWatermark() throws Exception {
    thrown.expect(ParameterException.class);
    thrown.expectMessage("Each watermark date must be the start of a day");
    runCommand(
        "--tld=tld",
        "--watermark=2017-01-01T10:00:00Z,2017-01-02T00:00:00Z",
        "--mode=thin",
        "-r 42",
        "-o test");
  }

  @Test
  public void testCommand_invalidMode() throws Exception {
    thrown.expect(ParameterException.class);
    thrown.expectMessage("Invalid value for -m parameter. Allowed values:[FULL, THIN]");
    runCommand(
        "--tld=tld",
        "--watermark=2017-01-01T00:00:00Z",
        "--mode=thing",
        "-r 42",
        "-o test");
  }

  @Test
  public void testCommand_invalidRevision() throws Exception {
    thrown.expect(ParameterException.class);
    thrown.expectMessage("Revision must be greater than or equal to zero");
    runCommand(
        "--tld=tld",
        "--watermark=2017-01-01T00:00:00Z",
        "--mode=thin",
        "-r -1",
        "-o test");
  }

  @Test
  public void testCommand_success() throws Exception {
    runCommand("--tld=tld", "--watermark=2017-01-01T00:00:00Z", "--mode=thin", "-r 42", "-o test");

    assertTasksEnqueued("rde-report",
        new TaskMatcher()
            .url("/_dr/task/rdeStaging")
            .header("Host", "1.backend.test.localhost")
            .param("mode", "THIN")
            .param("watermark", "2017-01-01T00:00:00.000Z")
            .param("tld", "tld")
            .param("directory", "test")
            .param("manual", "true")
            .param("revision", "42"));
  }

  @Test
  public void testCommand_successWithDefaultRevision() throws Exception {
    runCommand("--tld=tld", "--watermark=2017-01-01T00:00:00Z", "--mode=thin", "-o test");

    assertTasksEnqueued("rde-report",
        new TaskMatcher()
            .url("/_dr/task/rdeStaging")
            .header("Host", "1.backend.test.localhost")
            .param("mode", "THIN")
            .param("watermark", "2017-01-01T00:00:00.000Z")
            .param("tld", "tld")
            .param("directory", "test")
            .param("manual", "true"));
  }

  @Test
  public void testCommand_successWithDefaultMode() throws Exception {
    runCommand("--tld=tld", "--watermark=2017-01-01T00:00:00Z", "-r=42", "-o test");

    assertTasksEnqueued("rde-report",
        new TaskMatcher()
            .url("/_dr/task/rdeStaging")
            .header("Host", "1.backend.test.localhost")
            .param("mode", "FULL")
            .param("watermark", "2017-01-01T00:00:00.000Z")
            .param("tld", "tld")
            .param("directory", "test")
            .param("manual", "true")
            .param("revision", "42"));
  }

  @Test
  public void testCommand_successWithMultipleArgumentValues() throws Exception {
    runCommand(
        "--tld=tld,anothertld",
        "--watermark=2017-01-01T00:00:00Z,2017-01-02T00:00:00Z",
        "--mode=thin",
        "-r 42",
        "-o test");

    assertTasksEnqueued("rde-report",
        new TaskMatcher()
            .url("/_dr/task/rdeStaging")
            .header("Host", "1.backend.test.localhost")
            .param("mode", "THIN")
            .param("watermark", "2017-01-01T00:00:00.000Z")
            .param("watermark", "2017-01-02T00:00:00.000Z")
            .param("tld", "tld")
            .param("tld", "anothertld")
            .param("directory", "test")
            .param("manual", "true")
            .param("revision", "42"));
  }
}
