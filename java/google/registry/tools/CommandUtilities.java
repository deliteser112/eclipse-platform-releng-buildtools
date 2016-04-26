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

import static google.registry.flows.EppXmlTransformer.marshalWithLenientRetry;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;

import google.registry.flows.EppException;
import google.registry.flows.FlowRunner;
import google.registry.flows.FlowRunner.CommitMode;
import google.registry.flows.FlowRunner.UserPrivileges;

/** Container class for static utility methods. */
class CommandUtilities {

  static String addHeader(String header, String body) {
    return String.format("%s:\n%s\n%s", header, Strings.repeat("-", header.length() + 1), body);
  }

  /** Prompts for yes/no input using promptText, defaulting to no. */
  static boolean promptForYes(String promptText) {
    return promptForYesOrNo(promptText, false);
  }

  /**
   * Prompts for yes/no input using promptText and returns true for yes and false for no, using
   * defaultResponse as the response for empty input.
   */
  static boolean promptForYesOrNo(String promptText, boolean defaultResponse) {
    String options = defaultResponse ? "Y/n" : "y/N";
    while (true) {
      String line = System.console().readLine(String.format("%s (%s): ", promptText, options));
      if (line.isEmpty()) {
        return defaultResponse;
      } else if ("Y".equalsIgnoreCase(line.substring(0, 1))) {
        return true;
      } else if ("N".equalsIgnoreCase(line.substring(0, 1))) {
        return false;
      }
    }
  }

  /** Prints the provided text with a trailing newline, if text is not null or empty. */
  static void printLineIfNotEmpty(String text) {
    if (!Strings.isNullOrEmpty(text)) {
      System.out.println(text);
    }
  }

  static String runFlow(
      FlowRunner flowRunner, CommitMode commitMode, UserPrivileges userPrivileges)
          throws EppException {
    return new String(marshalWithLenientRetry(flowRunner.run(commitMode, userPrivileges)), UTF_8);
  }
}
