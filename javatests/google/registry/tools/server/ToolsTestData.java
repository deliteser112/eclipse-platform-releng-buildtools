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

package google.registry.tools.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import google.registry.testing.TestDataHelper;
import java.net.URL;
import java.util.Map;

/** Utility class providing easy access to contents of the {@code testdata/} directory. */
public final class ToolsTestData {

  /** Returns {@link ByteSource} for file in {@code tools/server/testdata/} directory. */
  public static ByteSource get(String filename) {
    return Resources.asByteSource(getUrl(filename));
  }

  /**
   * Loads data from file in {@code tools/server/testdata/} as a UTF-8 String.
   */
  public static String loadUtf8(String filename) {
    return loadUtf8(filename, ImmutableMap.of());
  }

  /**
   * Loads data from file in {@code tools/server/testdata/} as a UTF-8 String, with substitutions.
   */
  public static String loadUtf8(String filename, Map<String, String> substitutions) {
    return TestDataHelper.loadFileWithSubstitutions(ToolsTestData.class, filename, substitutions);
  }

  private static URL getUrl(String filename) {
    return Resources.getResource(ToolsTestData.class, "testdata/" + filename);
  }
}
