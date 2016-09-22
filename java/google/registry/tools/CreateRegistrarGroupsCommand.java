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

import static com.google.common.collect.Iterables.transform;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.model.registrar.Registrar;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.server.CreateGroupsAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command to create groups in Google Groups for all contact types for a registrar.
 */
@Parameters(separators = " =", commandDescription = "Create groups for a registrar.")
public class CreateRegistrarGroupsCommand extends ConfirmingCommand
    implements ServerSideCommand, GtechCommand {

  @Parameter(
      description = "Client identifier(s) of the registrar(s) to create groups for",
      required = true)
  private List<String> clientIds;

  private List<Registrar> registrars = new ArrayList<>();

  private Connection connection;

  @Override
  public void setConnection(Connection connection) {
    this.connection = connection;
  }

  @Override
  protected void init() throws IOException {
    for (String clientId : clientIds) {
      Registrar registrar = Registrar.loadByClientId(clientId);
      checkArgumentNotNull(registrar, "Could not load registrar with id " + clientId);
      registrars.add(registrar);
    }
  }

  @Override
  protected String prompt() {
    return String.format(
        "Create registrar contact groups for registrar(s) %s?",
        Joiner.on(", ").join(transform(registrars, new Function<Registrar, String>() {
          @Override
          public String apply(Registrar registrar) {
            return registrar.getRegistrarName();
          }})));
  }

  /** Calls the server endpoint to create groups for the specified registrar client id. */
  static void executeOnServer(Connection connection, String clientId) throws IOException {
    connection.send(
        CreateGroupsAction.PATH,
        ImmutableMap.of(CreateGroupsAction.CLIENT_ID_PARAM, clientId),
        MediaType.PLAIN_TEXT_UTF_8,
        new byte[0]);
  }

  @Override
  protected String execute() throws IOException {
    for (Registrar registrar : registrars) {
      connection.send(
          CreateGroupsAction.PATH,
          ImmutableMap.of(CreateGroupsAction.CLIENT_ID_PARAM, registrar.getClientId()),
          MediaType.PLAIN_TEXT_UTF_8,
          new byte[0]);
    }
    // Note: If any of the calls fail, then a 5XX response code is returned inside of send(), which
    // throws an exception yielding a stack trace.  If we get to this next line then we succeeded.
    return "Success!";
  }
}

