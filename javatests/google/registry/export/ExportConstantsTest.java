// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.export;

import static com.google.common.io.Resources.getResource;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.re2j.Pattern;
import com.googlecode.objectify.annotation.Entity;
import google.registry.model.ImmutableObject;
import java.net.URL;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ExportConstants}. */
@RunWith(JUnit4.class)
public class ExportConstantsTest {

  private static final String GOLDEN_BACKUP_KINDS_FILENAME = "backup_kinds.txt";

  private static final String UPDATE_INSTRUCTIONS_TEMPLATE = Joiner.on('\n').join(
      "",
      "---------------------------------------------------------------------------------",
      "Your changes affect the list of backed-up kinds in the golden file:",
      "  %s",
      "If these changes are desired, update the golden file with the following contents:",
      "=================================================================================",
      "%s",
      "=================================================================================",
      "");

  @Test
  public void testBackupKinds_matchGoldenBackupKindsFile() throws Exception {
    URL goldenBackupKindsResource =
        getResource(ExportConstantsTest.class, GOLDEN_BACKUP_KINDS_FILENAME);
    final Pattern stripComments = Pattern.compile("\\s*#.*$");
    List<String> goldenKinds = FluentIterable
        .from(Splitter.on('\n').split(
            Resources.toString(goldenBackupKindsResource, UTF_8).trim()))
        .transform(
            new Function<String, String>() {
              @Override @Nullable public String apply(@Nullable String line) {
                return stripComments.matcher(line).replaceFirst("");
              }})
        .toList();
    ImmutableSet<String> actualKinds = ExportConstants.getBackupKinds();
    String updateInstructions = String.format(
        UPDATE_INSTRUCTIONS_TEMPLATE,
        goldenBackupKindsResource.toString(),
        Joiner.on('\n').join(actualKinds));
    assertWithMessage(updateInstructions)
        .that(actualKinds)
        .containsExactlyElementsIn(goldenKinds)
        .inOrder();
  }

  @Test
  public void testReportingKinds_areSubsetOfBackupKinds() throws Exception {
    assertThat(ExportConstants.getBackupKinds()).containsAllIn(ExportConstants.getReportingKinds());
  }

  @Test
  public void testReportingEntityClasses_areAllBaseEntityClasses() throws Exception {
    for (Class<? extends ImmutableObject> clazz : ExportConstants.REPORTING_ENTITY_CLASSES) {
      assertThat(clazz.isAnnotationPresent(Entity.class))
          .named(String.format("class %s is an @Entity", clazz.getSimpleName()))
          .isTrue();
    }
  }
}
