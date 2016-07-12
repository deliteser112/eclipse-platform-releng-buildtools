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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Iterables;
import google.registry.export.DatastoreBackupInfo;
import google.registry.export.DatastoreBackupService;
import google.registry.tools.Command.RemoteApiCommand;

/**
 * Command to check the status of a datastore backup, or "snapshot".
 */
@Parameters(separators = " =", commandDescription = "Check the status of a datastore snapshot")
public class CheckSnapshotCommand implements RemoteApiCommand {

  @Parameter(
      names = {"-s", "--snapshot"},
      description = "Unique prefix of the snapshot to check",
      required = true)
  private String snapshotName;

  @Override
  public void run() throws Exception {
    Iterable<DatastoreBackupInfo> backups =
        DatastoreBackupService.get().findAllByNamePrefix(snapshotName);
    if (Iterables.isEmpty(backups)) {
      System.err.println("No snapshot found with name: " + snapshotName);
      return;
    }
    for (DatastoreBackupInfo backup : backups) {
      System.out.println(backup.getInformation());
    }
  }
}
