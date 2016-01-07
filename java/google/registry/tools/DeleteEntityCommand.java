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

package google.registry.tools;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.tools.server.DeleteEntityAction;
import java.util.List;

/**
 * Command to delete an entity (or entities) in Datastore specified by raw key ids, which can be
 * found in Datastore Viewer in the AppEngine console -- it's the really long alphanumeric key that
 * is labeled "Entity key" on the page for an individual entity.
 *
 * <p><b>WARNING:</b> This command can be dangerous if used incorrectly as it can bypass checks on
 * deletion (including whether the entity is referenced by other entities) and it does not write
 * commit log entries for non-registered types. It should mainly be used for deleting testing or
 * malformed data that cannot be properly deleted using existing tools. Generally, if there already
 * exists an entity-specific deletion command, then use that one instead.
 */
@Parameters(separators = " =", commandDescription = "Delete entities from Datastore by raw key.")
public class DeleteEntityCommand extends ConfirmingCommand implements ServerSideCommand {

  @Parameter(description = "One or more raw keys of entities to delete.", required = true)
  private List<String> rawKeyStrings;

  private Connection connection;

  @Override
  public void setConnection(Connection connection) {
    this.connection = connection;
  }

  @Override
  protected String prompt() {
    if (rawKeyStrings.size() == 1) {
      return "You are about to delete the entity: \n" + rawKeyStrings.get(0);
    } else {
      return "You are about to delete the entities: \n" + rawKeyStrings;
    }
  }

  @Override
  protected String execute() throws Exception {
    String rawKeysJoined = Joiner.on(",").join(rawKeyStrings);
    return connection.send(
        DeleteEntityAction.PATH,
        ImmutableMap.of(DeleteEntityAction.PARAM_RAW_KEYS, rawKeysJoined),
        MediaType.PLAIN_TEXT_UTF_8,
        new byte[0]);
  }
}
