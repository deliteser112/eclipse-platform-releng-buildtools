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

package com.google.domain.registry.tools;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RegistryToolEnvironment}. */
@RunWith(JUnit4.class)
public class RegistryToolEnvironmentTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testFromArgs_shortNotation_works() throws Exception {
    RegistryToolEnvironment.loadFromArgs(new String[] { "-e", "alpha" });
    assertThat(RegistryToolEnvironment.get()).isEqualTo(RegistryToolEnvironment.ALPHA);
  }

  @Test
  public void testFromArgs_longNotation_works() throws Exception {
    RegistryToolEnvironment.loadFromArgs(new String[] { "--environment", "alpha" });
    assertThat(RegistryToolEnvironment.get()).isEqualTo(RegistryToolEnvironment.ALPHA);
  }

  @Test
  public void testFromArgs_uppercase_works() throws Exception {
    RegistryToolEnvironment.loadFromArgs(new String[] { "-e", "QA" });
    assertThat(RegistryToolEnvironment.get()).isEqualTo(RegistryToolEnvironment.QA);
  }

  @Test
  public void testFromArgs_equalsNotation_works() throws Exception {
    RegistryToolEnvironment.loadFromArgs(new String[] { "-e=sandbox" });
    assertThat(RegistryToolEnvironment.get()).isEqualTo(RegistryToolEnvironment.SANDBOX);
    RegistryToolEnvironment.loadFromArgs(new String[] { "--environment=sandbox" });
    assertThat(RegistryToolEnvironment.get()).isEqualTo(RegistryToolEnvironment.SANDBOX);
  }

  @Test
  public void testFromArgs_envFlagAfterCommandName_getsIgnored() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    RegistryToolEnvironment.loadFromArgs(new String[] {
        "registrar_activity_report",
        "-e", "1406851199"});
  }

  @Test
  public void testFromArgs_missingEnvironmentFlag_throwsIae() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    RegistryToolEnvironment.loadFromArgs(new String[] {});
  }

  @Test
  public void testFromArgs_extraEnvFlagAfterCommandName_getsIgnored() throws Exception {
    RegistryToolEnvironment.loadFromArgs(new String[] {
        "-e", "alpha",
        "registrar_activity_report",
        "-e", "1406851199"});
    assertThat(RegistryToolEnvironment.get()).isEqualTo(RegistryToolEnvironment.ALPHA);
  }

  @Test
  public void testFromArgs_loggingFlagWithUnderscores_isntConsideredCommand() throws Exception {
    RegistryToolEnvironment.loadFromArgs(new String[] {
        "--logging_properties_file", "my_file.properties",
        "-e", "alpha",
        "list_tlds"});
    assertThat(RegistryToolEnvironment.get()).isEqualTo(RegistryToolEnvironment.ALPHA);
  }

  @Test
  public void testFromArgs_badName_throwsIae() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    RegistryToolEnvironment.loadFromArgs(new String[] { "-e", "alphaville" });
  }
}
