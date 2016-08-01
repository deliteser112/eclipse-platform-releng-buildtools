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

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.reflect.Reflection.getPackageName;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.common.truth.Expect;
import google.registry.tools.Command.GtechCommand;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GtechTool}. */
@RunWith(JUnit4.class)
public class GtechToolTest {

  @Rule
  public final Expect expect = Expect.create();

  @Before
  public void init() {
    RegistryToolEnvironment.UNITTEST.setup();
  }

  @Test
  public void testThatAllCommandsAreInCliOptions() throws Exception {
    Set<Class<? extends GtechCommand>> commandMapClasses =
        ImmutableSet.copyOf(GtechTool.COMMAND_MAP.values());
    Set<Class<? extends GtechCommand>> commandsWithoutCliInvokers =
        Sets.difference(getAllCommandClasses(), commandMapClasses);
    String errorMsg =
        "These Command classes are missing from GtechTool.COMMAND_MAP: "
        + Joiner.on(", ").join(commandsWithoutCliInvokers);
    assertWithMessage(errorMsg).that(commandsWithoutCliInvokers).isEmpty();
  }

  @Test
  public void testThatCommandNamesAreDerivedFromClassNames() throws Exception {
    for (Map.Entry<String, ? extends Class<? extends Command>> commandEntry :
        GtechTool.COMMAND_MAP.entrySet()) {
      String className = commandEntry.getValue().getSimpleName();
      expect.that(commandEntry.getKey())
          // JCommander names should match the class name, up to "Command" and case formatting.
          .isEqualTo(UPPER_CAMEL.to(LOWER_UNDERSCORE, className.replaceFirst("Command$", "")));
    }
  }

  /**
   * Gets the set of all non-abstract classes implementing the {@link GtechCommand} interface
   * (abstract class and interface subtypes of Command aren't expected to have cli commands). Note
   * that this also filters out HelpCommand, which has special handling in {@link RegistryCli} and
   * isn't in the command map.
   *
   * @throws IOException if reading the classpath resources fails.
   */
  @SuppressWarnings("unchecked")
  private ImmutableSet<Class<? extends GtechCommand>> getAllCommandClasses() throws IOException {
    ImmutableSet.Builder<Class<? extends GtechCommand>> builder = new ImmutableSet.Builder<>();
    for (ClassInfo classInfo : ClassPath
        .from(getClass().getClassLoader())
        .getTopLevelClasses(getPackageName(getClass()))) {
      Class<?> clazz = classInfo.load();
      if (GtechCommand.class.isAssignableFrom(clazz)
          && !Modifier.isAbstract(clazz.getModifiers())
          && !Modifier.isInterface(clazz.getModifiers())
          && !clazz.equals(HelpCommand.class)) {
        builder.add((Class<? extends GtechCommand>) clazz);
      }
    }
    return builder.build();
  }
}
