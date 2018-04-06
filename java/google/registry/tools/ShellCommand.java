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
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;
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
      consoleReader.setDefaultPrompt("nom > ");
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

    /**
     * Documentation for all the known command + argument combinations.
     *
     * <p>For every command, has documentation for all flags and for the "main" parameters (the ones
     * that don't have a flag)
     *
     * <p>The order is: row is the command, col is the flag.
     *
     * <p>The flag documentations are keyed to the full flag - including any dashes (so "--flag"
     * rather than "flag").
     *
     * <p>The "main" parameter documentation is keyed to "". Every command has documentation for the
     * main parameters, even if it doesn't accept any (if it doesn't accept any, the documentation
     * is "no documentation available"). That means every command has at least one full table cell
     * (the "" key, for the main parameter). THIS IS IMPORTANT - otherwise the command won't appear
     * in {@link ImmutableTable#rowKeySet}.
     */
    private final ImmutableTable<String, String, String> commandFlagDocs;

    private final FileNameCompletor filenameCompletor = new FileNameCompletor();

    /**
     * Populates the completions and documentation based on the JCommander.
     *
     * The input data is copied, so changing the jcommander after creation of the
     * JCommanderCompletor doesn't change the completions.
     */
    JCommanderCompletor(JCommander jcommander) {
      ImmutableTable.Builder<String, String, String> builder =
          new ImmutableTable.Builder<>();

      // Go over all the commands
      for (Entry<String, JCommander> entry : jcommander.getCommands().entrySet()) {
        String command = entry.getKey();
        JCommander subCommander = entry.getValue();

        // Add the "main" parameters documentation
        builder.put(command, "", createDocText(subCommander.getMainParameter()));

        // For each command - go over the parameters (arguments / flags)
        for (ParameterDescription parameter : subCommander.getParameters()) {
          String documentation = createDocText(parameter);

          // For each parameter - go over all the "flag" names of that parameter (e.g., -o and
          // --output being aliases of the same parameter) and populate each one
          Arrays.stream(parameter.getParameter().names())
              .forEach(flag -> builder.put(command, flag, documentation));
        }
      }
      commandFlagDocs = builder.build();
    }

    private static String createDocText(@Nullable ParameterDescription parameter) {
      if (parameter == null) {
        return "[None]";
      }
      String type = parameter.getParameterized().getGenericType().toString();
      if (type.startsWith("class ")) {
        type = type.substring(6);
      }
      return String.format("%s\n  (%s)", parameter.getDescription(), type);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public int complete(String buffer, int location, List completions) {
      // We just defer to the other function because of the warnings (the use of a naked List by
      // jline)
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
      // The argument we want to complete (only partially written, might even be empty)
      String partialArgument =
          argumentIndex < parsedBuffer.length ? parsedBuffer[argumentIndex] : "";
      int argumentStart = location - partialArgument.length();
      // The command name. Null if we're at the first argument
      String command = argumentIndex == 0 ? null : parsedBuffer[0];
      // The previous argument before it - used for context. Null if we're at the first argument
      String previousArgument = argumentIndex <= 1 ? null : parsedBuffer[argumentIndex - 1];

      // If it's obviously a file path (starts with something "file path like") - complete as a file
      if (partialArgument.startsWith("./")
          || partialArgument.startsWith("~/")
          || partialArgument.startsWith("/")) {
        int offset =
            filenameCompletor.complete(partialArgument, partialArgument.length(), completions);
        if (offset >= 0) {
          return argumentStart + offset;
        }
        return -1;
      }

      // Complete based on flag data
      completions.addAll(getCompletions(command, previousArgument, partialArgument));
      return argumentStart;
    }

    /**
     * Completes a (partial) word based on the command and context.
     *
     * @param command the name of the command we're running. Null if not yet known (it is in 'word')
     * @param context the previous argument for context. Null if we're the first.
     * @param word the (partial) word to complete. Can be the command, if "command" is null, or any
     *     "regular" argument, if "command" isn't null.
     * @return list of all possible completions to 'word'
     */
    private List<String> getCompletions(
        @Nullable String command, @Nullable String context, String word) {

      // Complete the first argument based on the jcommander commands
      if (command == null) {
        return getCommandCompletions(word);
      }

      // For the "help" command, complete the second argument based on the jcommander commands, and
      // the rest of the arguments fail to complete
      if (command.equals("help")) {
        // "help" only has completion for the first argument
        if (context != null) {
          return ImmutableList.of();
        }
        return getCommandCompletions(word);
      }

      // 'tab' on empty will show the documentation - either for the "current flag" or for the main
      // parameters, depending on the context (the "context" being the previous argument)
      if (word.isEmpty()) {
        return getParameterDocCompletions(command, context, word);
      }

      // For existing commands, complete based on the command arguments
      if (word.startsWith("-")) {
        return getFlagCompletions(command, word);
      }

      // We don't know how to complete based on context... :( So that's the best we can do
      return ImmutableList.of();
    }

    private List<String> getCommandCompletions(String word) {
      return commandFlagDocs
          .rowKeySet()
          .stream()
          .filter(s -> s.startsWith(word))
          .map(s -> s + " ")
          .collect(toImmutableList());
    }

    private List<String> getFlagCompletions(String command, String word) {
      return commandFlagDocs
          .row(command)
          .keySet()
          .stream()
          .filter(s -> s.startsWith(word))
          .map(s -> s + " ")
          .collect(toImmutableList());
    }

    private List<String> getParameterDocCompletions(
        String command, @Nullable String argument, String word) {
      if (!word.isEmpty()) {
        return ImmutableList.of();
      }
      return ImmutableList.of("", getParameterDoc(command, argument));
    }

    private String getParameterDoc(String command, @Nullable String previousArgument) {
      // First, check if we want the documentation for a specific flag, or for the "main"
      // parameters.
      //
      // We want documentation for a flag if the previous argument was a flag, but the value of the
      // flag wasn't set. So if the previous argument is "--flag" then we want documentation of that
      // flag, but if it's "--flag=value" then that flag is set and we want documentation of the
      // main parameters.
      boolean isFlagParameter =
          previousArgument != null
              && previousArgument.startsWith("-")
              && previousArgument.indexOf('=') == -1;
      return (isFlagParameter ? "Flag documentation: " : "Main parameter: ")
          + Optional.ofNullable(
                  commandFlagDocs.get(command, isFlagParameter ? previousArgument : ""))
              .orElse("[No documentation available]")
          + "\n";
    }
  }
}
