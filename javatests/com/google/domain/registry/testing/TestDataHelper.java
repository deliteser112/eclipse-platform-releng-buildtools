// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.testing;

import static com.google.domain.registry.util.CollectionUtils.isNullOrEmpty;
import static com.google.domain.registry.util.ResourceUtils.readResourceUtf8;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Contains helper methods for dealing with test data.
 */
public final class TestDataHelper {

  /**
   * Loads a text file from the "testdata" directory relative to the location of the specified
   * context class, and substitutes in values for placeholders of the form <code>%tagname%</code>.
   */
  public static String loadFileWithSubstitutions(
      Class<?> context, String filename, Map<String, String> substitutions) {
    String fileContents = readResourceUtf8(context, "testdata/" + filename);
    if (!isNullOrEmpty(substitutions)) {
      for (Entry<String, String> entry : substitutions.entrySet()) {
        fileContents = fileContents.replaceAll("%" + entry.getKey() + "%", entry.getValue());
      }
    }
    return fileContents;
  }
}
