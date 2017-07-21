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

package google.registry.reporting;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;

/** Utility class providing easy access to contents of the {@code testdata/} directory. */
public final class ReportingTestData {

  /** Returns {@link ByteSource} for file in {@code reporting/testdata/} directory. */
  public static ByteSource get(String filename) {
    return Resources.asByteSource(getUrl(filename));
  }

  /** Returns a {@link String} from a file in the {@code reporting/testdata/} directory. */
  public static String getString(String filename) throws IOException {
    return Resources.asCharSource(getUrl(filename), UTF_8).read();
  }

    private static URL getUrl(String filename) {
    return Resources.getResource(ReportingTestData.class, "testdata/" + filename);
  }
}
