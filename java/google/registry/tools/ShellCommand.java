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

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.io.StringReader;

/**
 * Implements a tiny shell interpreter for the nomulus tool.
 *
 * <p>Parses a very simple command grammar. Tokens are either whitespace delimited words or
 * double-quoted strings.
 */
@Parameters(commandDescription = "Run an interactive shell")
public class ShellCommand implements Command {

  private final CommandRunner runner;
  private final BufferedReader lineReader;

  ShellCommand(InputStream in, CommandRunner runner) {
    this.runner = runner;
    this.lineReader = new BufferedReader(new InputStreamReader(in, US_ASCII));
  }

  /** Run the shell until the user presses "Ctrl-D". */
  @Override
  public void run() {
    String line;
    while ((line = getLine()) != null) {
      String[] lineArgs = parseCommand(line);
      try {
        runner.run(lineArgs);
      } catch (Exception e) {
        System.err.println("Got an exception:\n" + e);
      }
    }
  }

  private String getLine() {
    if (System.console() != null) {
      System.err.print("nom> ");
    }
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
}
