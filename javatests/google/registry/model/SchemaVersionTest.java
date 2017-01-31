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

package google.registry.model;

import static com.google.common.truth.Truth.assert_;
import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.google.common.base.Joiner;
import google.registry.testing.AppEngineRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link SchemaVersion}.
 *
 * <p>If the test breaks, the instructions below will be printed.
 */
@RunWith(JUnit4.class)
public class SchemaVersionTest {

  private static final String GOLDEN_SCHEMA_FILE = "schema.txt";

  private static final String UPDATE_COMMAND =
      "google.registry.tools.RegistryTool -e localhost get_schema "
      + ">javatests/google/registry/model/schema.txt";

  private static final String UPDATE_INSTRUCTIONS = Joiner.on('\n').join(
      "",
      "-------------------------------------------------------------------------------",
      "Your changes affect the datastore schema. To update the checked-in schema, run:",
      UPDATE_COMMAND,
      "");

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Test
  public void testGoldenSchemaFile() throws Exception {
    // Don't use Truth's isEqualTo() because the output is huge and unreadable for large files.
    if (!(SchemaVersion.getSchema()
        .equals(readResourceUtf8(SchemaVersionTest.class, GOLDEN_SCHEMA_FILE).trim()))) {
      assert_().fail(UPDATE_INSTRUCTIONS);
    }
  }
}
