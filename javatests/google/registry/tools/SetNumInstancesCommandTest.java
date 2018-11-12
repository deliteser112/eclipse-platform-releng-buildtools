// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.JUnitBackports.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.beust.jcommander.ParameterException;
import google.registry.testing.InjectRule;
import google.registry.util.AppEngineServiceUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

/** Unit tests for {@link SetNumInstancesCommand}. */
public class SetNumInstancesCommandTest extends CommandTestCase<SetNumInstancesCommand> {

  @Rule public final InjectRule inject = new InjectRule();

  @Mock AppEngineServiceUtils appEngineServiceUtils;

  @Before
  public void before() {
    command = new SetNumInstancesCommand();
    command.appEngineServiceUtils = appEngineServiceUtils;
  }

  @Test
  public void test_missingService_throwsException() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class, () -> runCommand("--version=version", "--numInstances=5"));
    assertThat(thrown).hasMessageThat().contains("The following option is required: --service");
  }

  @Test
  public void test_emptyService_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> runCommand("--service=", "--version=version", "--numInstances=5"));
    assertThat(thrown).hasMessageThat().contains("Service must be specified");
  }

  @Test
  public void test_missingVersion_throwsException() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class, () -> runCommand("--service=service", "--numInstances=5"));
    assertThat(thrown).hasMessageThat().contains("The following option is required: --version");
  }

  @Test
  public void test_emptyVersion_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> runCommand("--service=service", "--version=", "--numInstances=5"));
    assertThat(thrown).hasMessageThat().contains("Version must be specified");
  }

  @Test
  public void test_missingNumInstances_throwsException() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class, () -> runCommand("--service=service", "--version=version"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("The following option is required: --numInstances");
  }

  @Test
  public void test_invalidNumInstances_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> runCommand("--service=service", "--version=version", "--numInstances=-5"));
    assertThat(thrown).hasMessageThat().contains("Number of instances must be greater than zero");
  }

  @Test
  public void test_validParameters_succeeds() throws Exception {
    runCommand("--service=service", "--version=version", "--numInstances=10");
    verify(appEngineServiceUtils, times(1)).setNumInstances("service", "version", 10L);
  }
}
