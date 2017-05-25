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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.backup.RestoreCommitLogsAction;
import org.joda.time.DateTime;

@Parameters(separators = " =", commandDescription = "Restore the commit logs.")
class RestoreCommitLogsCommand implements ServerSideCommand {
  private Connection connection;

  @Parameter(
    names = {"-d", "--dry_run"},
    description = "Don't actually make any changes, just show what you would do."
  )
  private boolean dryRun = false;

  @Parameter(
    names = {"-f", "--from_time"},
    description = "Time to start restoring from.",
    required = true
  )
  private DateTime fromTime;

  @Parameter(
    names = {"-t", "--to_time"},
    description = "Last commit diff timestamp to use when restoring."
  )
  private DateTime toTime;

  @Override
  public void setConnection(Connection connection) {
    this.connection = connection;
  }

  @Override
  public void run() throws Exception {
    ImmutableMap.Builder<String, Object> params = new ImmutableMap.Builder<>();
    params.put("dryRun", dryRun);
    params.put("fromTime", fromTime);
    if (toTime != null) {
      params.put("toTime", toTime);
    }
    String response =
        connection.send(
            RestoreCommitLogsAction.PATH, params.build(), MediaType.PLAIN_TEXT_UTF_8, new byte[0]);
    System.out.println(response);
  }
}
