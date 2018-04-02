// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.JUnitBackports.assertThrows;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.mockito.Mockito.mock;

import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ShellCommandTest {

  CommandRunner cli = mock(CommandRunner.class);

  public ShellCommandTest() {}

  @Test
  public void testParsing() {
    assertThat(ShellCommand.parseCommand("foo bar 123 baz+ // comment \"string data\""))
        .isEqualTo(new String[] {"foo", "bar", "123", "baz+", "//", "comment", "string data"});
    assertThat(ShellCommand.parseCommand("\"got \\\" escapes?\""))
        .isEqualTo(new String[] {"got \" escapes?"});
    assertThat(ShellCommand.parseCommand("")).isEqualTo(new String[0]);
  }

  @Test
  public void testCommandProcessing() {
    String testData = "test1 foo bar\ntest2 foo bar\n";
    ImmutableMap<String, Class<? extends Command>> commandMap = ImmutableMap.of();
    MockCli cli = new MockCli();
    ShellCommand shellCommand =
        new ShellCommand(new ByteArrayInputStream(testData.getBytes(US_ASCII)), cli);
    shellCommand.run();
    assertThat(cli.calls)
        .containsExactly(
            ImmutableList.of("test1", "foo", "bar"), ImmutableList.of("test2", "foo", "bar"))
        .inOrder();
  }

  static class MockCli implements CommandRunner {
    public ArrayList<ImmutableList<String>> calls = new ArrayList<>();

    @Override
    public void run(String[] args) throws Exception {
      calls.add(ImmutableList.copyOf(args));
    }
  }

  @Test
  public void testMultipleCommandInvocations() throws Exception {
    RegistryCli cli =
        new RegistryCli("unittest", ImmutableMap.of("test_command", TestCommand.class));
    RegistryToolEnvironment.UNITTEST.setup();
    cli.setEnvironment(RegistryToolEnvironment.UNITTEST);
    cli.run(new String[] {"test_command", "-x", "xval", "arg1", "arg2"});
    cli.run(new String[] {"test_command", "-x", "otherxval", "arg3"});
    cli.run(new String[] {"test_command"});
    assertThat(TestCommand.commandInvocations)
        .containsExactly(
            ImmutableList.of("xval", "arg1", "arg2"),
            ImmutableList.of("otherxval", "arg3"),
            ImmutableList.of("default value"));
  }

  @Test
  public void testNonExistentCommand() throws Exception {
    RegistryCli cli =
        new RegistryCli("unittest", ImmutableMap.of("test_command", TestCommand.class));

    cli.setEnvironment(RegistryToolEnvironment.UNITTEST);
    assertThrows(MissingCommandException.class, () -> cli.run(new String[] {"bad_command"}));
  }

  @Parameters(commandDescription = "Test command")
  static class TestCommand implements Command {
    @Parameter(
      names = {"-x", "--xparam"},
      description = "test parameter"
    )
    String xparam = "default value";

    // List for recording command invocations by run().
    //
    // This has to be static because it gets populated by multiple TestCommand instances, which are
    // created in RegistryCli by using reflection to call the constructor.
    static final List<List<String>> commandInvocations = new ArrayList<>();

    @Parameter(description = "normal argument")
    List<String> args;

    public TestCommand() {}

    @Override
    public void run() {
      ImmutableList.Builder<String> callRecord = new ImmutableList.Builder<>();
      callRecord.add(xparam);
      if (args != null) {
        callRecord.addAll(args);
      }
      commandInvocations.add(callRecord.build());
    }
  }
}
