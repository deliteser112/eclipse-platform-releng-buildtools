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

package google.registry.testing;

import static com.google.common.truth.Truth.assert_;
import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.google.common.base.Joiner;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Helper class to compare a string against a golden file and print out update instructions if
 * necessary.
 */
public class GoldenFileTestHelper {

  private static final String UPDATE_COMMAND =
      "google.registry.tools.RegistryTool -e localhost %1$s >javatests%2$s";

  private static final String UPDATE_INSTRUCTIONS =
      Joiner.on('\n')
          .join(
              "",
              "-------------------------------------------------------------------------------",
              "Your changes affect the %3$s. To update the checked-in version, run:",
              UPDATE_COMMAND,
              "");

  private static String getPathProper(URL url) throws MalformedURLException {
    String protocol = url.getProtocol();
    if (protocol.equals("jar")) {
      url = new URL(url.getPath());
      protocol = url.getProtocol();
    }
    if (protocol.equals("file")) {
      String[] components = url.getPath().split("!");
      if (components.length >= 2) {
        return components[1];
      }
    }
    return url.getPath();
  }

  public static void testGoldenFile(
      String actualValue,
      URL goldenFileUrl,
      String goldenFileDescription,
      String nomulusCommand)
      throws Exception {
    // Don't use Truth's isEqualTo() because the output is huge and unreadable for large files.
    if (!actualValue.equals(readResourceUtf8(goldenFileUrl).trim())) {
      assert_()
          .fail(
              String.format(
                  UPDATE_INSTRUCTIONS,
                  nomulusCommand,
                  getPathProper(goldenFileUrl),
                  goldenFileDescription));
    }
  }
}
