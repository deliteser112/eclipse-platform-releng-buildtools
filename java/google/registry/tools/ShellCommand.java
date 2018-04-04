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

import static com.google.common.base.StandardSystemProperty.USER_HOME;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import jline.Completor;
import jline.ConsoleReader;
import jline.ConsoleReaderInputStream;
import jline.FileNameCompletor;
import jline.History;

/**
 * Implements a tiny shell interpreter for the nomulus tool.
 *
 * <p>Parses a very simple command grammar. Tokens are either whitespace delimited words or
 * double-quoted strings.
 */
@Parameters(commandDescription = "Run an interactive shell")
public class ShellCommand implements Command {

  private static final String HISTORY_FILE = ".nomulus_history";

  private final CommandRunner runner;
  private final BufferedReader lineReader;
  private final ConsoleReader consoleReader;

  public ShellCommand(CommandRunner runner) throws IOException {
    this.runner = runner;
    InputStream in = System.in;
    if (System.console() != null) {
      consoleReader = new ConsoleReader();
      // There are 104 different commands. We want the threshold to be more than that
      consoleReader.setAutoprintThreshhold(200);
      // Setting the prompt to a temporary value - will include the environment once that is set
      consoleReader.setDefaultPrompt("nom@??? > ");
      consoleReader.setHistory(new History(new File(USER_HOME.value(), HISTORY_FILE)));
      in = new ConsoleReaderInputStream(consoleReader);
    } else {
      consoleReader = null;
    }
    this.lineReader = new BufferedReader(new InputStreamReader(in, US_ASCII));
  }

  @VisibleForTesting
  ShellCommand(InputStream in, CommandRunner runner) {
    this.runner = runner;
    this.lineReader = new BufferedReader(new InputStreamReader(in, US_ASCII));
    this.consoleReader = null;
  }

  public ShellCommand setPrompt(String prompt) {
    if (consoleReader != null) {
      consoleReader.setDefaultPrompt(prompt);
    }
    return this;
  }

  public ShellCommand buildCompletions(JCommander jcommander) {
    if (consoleReader != null) {
      @SuppressWarnings("unchecked")
      ImmutableList<Completor> completors = ImmutableList.copyOf(consoleReader.getCompletors());
      completors
          .forEach(consoleReader::removeCompletor);
      consoleReader.addCompletor(new JCommanderCompletor(jcommander));
    }
    return this;
  }

  /** Run the shell until the user presses "Ctrl-D". */
  @Override
  public void run() {
    String line;
    while ((line = getLine()) != null) {
      String[] lineArgs = parseCommand(line);
      if (lineArgs.length == 0) {
        continue;
      }
      try {
        runner.run(lineArgs);
      } catch (Exception e) {
        System.err.println("Got an exception:\n" + e);
      }
    }
    System.err.println();
  }

  private String getLine() {
    try {
      return lineReader.readLine();
    } catch (IOException e) {
      return null;
    }
  }

  @VisibleForTesting
  static String[] parseCommand(String line) {
    ImmutableList.Builder<String> resultBuilder = new ImmutableList.Builder<>();

    // Create a tokenizer, make everything word characters except quoted strings and unprintable
    // ascii chars and space (just treat them all as whitespace).
    StreamTokenizer tokenizer = new StreamTokenizer(new StringReader(line));
    tokenizer.resetSyntax();
    tokenizer.whitespaceChars(0, ' ');
    tokenizer.wordChars('!', '~');
    tokenizer.quoteChar('"');

    try {
      while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
        resultBuilder.add(tokenizer.sval);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return resultBuilder.build().toArray(new String[0]);
  }

  @VisibleForTesting
  static class JCommanderCompletor implements Completor {

    private final ImmutableSet<String> commands;
    private final ImmutableSetMultimap<String, String> commandArguments;
    private final FileNameCompletor filenameCompletor = new FileNameCompletor();

    JCommanderCompletor(JCommander jcommander) {
      commands = ImmutableSet.copyOf(jcommander.getCommands().keySet());
      ImmutableSetMultimap.Builder<String, String> builder = new ImmutableSetMultimap.Builder<>();
      jcommander
          .getCommands()
          .entrySet()
          .forEach(
              entry -> {
                builder.putAll(
                    entry.getKey(),
                    entry
                        .getValue()
                        .getParameters()
                        .stream()
                        .flatMap(p -> Arrays.stream(p.getParameter().names()))
                        .collect(toImmutableList()));
              });
      commandArguments = builder.build();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public int complete(String buffer, int location, List completions) {
      return completeInternal(buffer, location, completions);
    }

    /**
     * Given a string, finds all the possible completions to the end of that string.
     *
     * @param buffer the command line.
     * @param location the location in the command line we want to complete
     * @param completions a list to fill with the completion results
     * @return the number of character back from the location that are part of the completions
     */
    int completeInternal(String buffer, int location, List<String> completions) {
      String truncatedBuffer = buffer.substring(0, location);
      String[] parsedBuffer = parseCommand(truncatedBuffer);
      int argumentIndex = parsedBuffer.length - 1;

      if (argumentIndex < 0 || !truncatedBuffer.endsWith(parsedBuffer[argumentIndex])) {
        argumentIndex += 1;
      }
      String argument = argumentIndex < parsedBuffer.length ? parsedBuffer[argumentIndex] : "";
      int argumentStart = location - argument.length();

      // Complete the first argument based on the jcommander commands
      if (argumentIndex == 0) {
        completions.addAll(getCommandCompletions(argument));
        return argumentStart;
      }
      String commandName = parsedBuffer[0];

      // For the "help" command, complete the second argument based on the jcommander commands, and
      // the rest of the arguments fail to complete
      if (commandName.equals("help")) {
        if (argumentIndex >= 2) {
          return argumentStart;
        }
        completions.addAll(getCommandCompletions(argument));
        return argumentStart;
      }

      // For existing commands, complete based on the command arguments
      if (argument.isEmpty() || argument.startsWith("-")) {
        completions.addAll(getArgumentCompletions(commandName, argument));
        return argumentStart;
      }

      // However, if it's obviously not an argument (starts with something that isn't "-"), default
      // to a filename.
      int offset = filenameCompletor.complete(argument, argument.length(), completions);
      if (offset < 0) {
        return argumentStart;
      }
      return argumentStart + offset;
    }

    private List<String> getCommandCompletions(String word) {
      return commands
          .stream()
          .filter(s -> s.startsWith(word))
          .map(s -> s + " ")
          .collect(toImmutableList());
    }

    private List<String> getArgumentCompletions(String command, String word) {
      return commandArguments.get(command)
          .stream()
          .filter(s -> s.startsWith(word))
          .map(s -> s + " ")
          .collect(toImmutableList());
    }
  }
}
