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

package google.registry.tools;

import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import google.registry.tools.ServerSideCommand.Connection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Unit tests for {@link PublishDetailReportCommand}. */
public class PublishDetailReportCommandTest extends CommandTestCase<PublishDetailReportCommand> {

  @Mock
  private Connection connection;

  @Before
  public void init() throws Exception {
    command.setConnection(connection);
    when(connection.sendJson(anyString(), anyMapOf(String.class, Object.class)))
        .thenReturn(ImmutableMap.<String, Object>of("driveId", "some123id"));
  }

  @Test
  public void testSuccess_normalUsage() throws Exception {
    runCommandForced(
        "--registrar_id=TheRegistrar",
        "--report_name=report.csv",
        "--gcs_bucket=mah-buckit",
        "--gcs_folder=some/folder");
    verify(connection).sendJson(
        eq("/_dr/publishDetailReport"),
        eq(ImmutableMap.of(
            "registrar", "TheRegistrar",
            "report", "report.csv",
            "gcsFolder", "some/folder/",
            "bucket", "mah-buckit")));
    assertInStdout("Success!");
    assertInStdout("some123id");
  }

  @Test
  public void testSuccess_gcsFolderWithTrailingSlash() throws Exception {
    runCommandForced(
        "--registrar_id=TheRegistrar",
        "--report_name=report.csv",
        "--gcs_bucket=mah-buckit",
        "--gcs_folder=some/folder/");
    verify(connection).sendJson(
        eq("/_dr/publishDetailReport"),
        eq(ImmutableMap.of(
            "registrar", "TheRegistrar",
            "report", "report.csv",
            "gcsFolder", "some/folder/",
            "bucket", "mah-buckit")));
    assertInStdout("Success!");
    assertInStdout("some123id");
  }

  @Test
  public void testSuccess_emptyGcsFolder() throws Exception {
    runCommandForced(
        "--registrar_id=TheRegistrar",
        "--report_name=report.csv",
        "--gcs_bucket=mah-buckit",
        "--gcs_folder=");
    verify(connection).sendJson(
        eq("/_dr/publishDetailReport"),
        eq(ImmutableMap.of(
            "registrar", "TheRegistrar",
            "report", "report.csv",
            "gcsFolder", "",
            "bucket", "mah-buckit")));
    assertInStdout("Success!");
    assertInStdout("some123id");
  }
}
