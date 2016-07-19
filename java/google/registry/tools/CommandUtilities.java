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

import com.google.common.base.Ascii;
import com.google.common.base.Strings;

/** Container class for static utility methods. */
class CommandUtilities {

  static String addHeader(String header, String body) {
    return String.format("%s:\n%s\n%s", header, Strings.repeat("-", header.length() + 1), body);
  }

  /** Prompts for yes/no input using promptText, defaulting to no. */
  static boolean promptForYes(String promptText) {
    return Ascii.toUpperCase(System.console().readLine(promptText + " (y/N): ")).startsWith("Y");
  }
}
