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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.backup.RestoreCommitLogsAction;
import google.registry.tools.ServerSideCommand.Connection;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

/** Unit tests for {@link CreateRegistrarCommand}. */
public class RestoreCommitLogsCommandTest extends CommandTestCase<RestoreCommitLogsCommand> {
  @Mock private Connection connection;

  @Before
  public void init() {
    command.setConnection(connection);
  }

  @Captor ArgumentCaptor<ImmutableMap<String, String>> urlParamCaptor;

  @Test
  public void testNormalForm() throws Exception {
    runCommand("--from_time=2017-05-19T20:30:00Z");
    verifySend(
        ImmutableMap.of("dryRun", false, "fromTime", DateTime.parse("2017-05-19T20:30:00.000Z")));
  }

  @Test
  public void testToTime() throws Exception {
    runCommand("--from_time=2017-05-19T20:30:00Z", "--to_time=2017-05-19T20:40:00Z");
    verifySend(
        ImmutableMap.of(
            "dryRun", false,
            "fromTime", DateTime.parse("2017-05-19T20:30:00.000Z"),
            "toTime", DateTime.parse("2017-05-19T20:40:00.000Z")));
  }

  @Test
  public void testDryRun() throws Exception {
    runCommand("--dry_run", "--from_time=2017-05-19T20:30:00Z", "--to_time=2017-05-19T20:40:00Z");
    verifySend(
        ImmutableMap.of(
            "dryRun", true,
            "fromTime", DateTime.parse("2017-05-19T20:30:00.000Z"),
            "toTime", DateTime.parse("2017-05-19T20:40:00.000Z")));
  }

  // Note that this is very similar to the one in CreateOrUpdatePremiumListCommandTestCase.java but
  // not identical.
  void verifySend(ImmutableMap<String, ?> parameters) throws Exception {
    verify(connection)
        .send(
            eq(RestoreCommitLogsAction.PATH),
            urlParamCaptor.capture(),
            eq(MediaType.PLAIN_TEXT_UTF_8),
            eq(new byte[0]));
    assertThat(urlParamCaptor.getValue()).containsExactlyEntriesIn(parameters);
  }
}
