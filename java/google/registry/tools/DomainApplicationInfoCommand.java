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

import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Ascii;
import com.google.template.soy.data.SoyMapData;
import google.registry.model.domain.launch.LaunchPhase;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.soy.DomainApplicationInfoSoyInfo;

/** A command to execute a domain application info EPP command. */
@Parameters(separators = " =", commandDescription = "Get domain application EPP info")
final class DomainApplicationInfoCommand extends EppToolCommand implements GtechCommand {

  @Parameter(
      names = {"-c", "--client"},
      description = "Client identifier of the registrar to execute the command as",
      required = true)
  String clientId;

  @Parameter(
      names = {"--id"},
      description = "ID of the application.",
      required = true)
  private String id;

  @Parameter(
      names = {"-n", "--domain_name"},
      description = "Domain name to query.",
      required = true)
  private String domainName;

  @Parameter(
      names = {"--phase"},
      description = "Phase of the application to query.",
      required = true)
  private String phase;

  @Override
  void initEppToolCommand() {
    LaunchPhase launchPhase = checkArgumentNotNull(
        LaunchPhase.fromValue(Ascii.toLowerCase(phase)), "Illegal launch phase.");

    setSoyTemplate(
        DomainApplicationInfoSoyInfo.getInstance(),
        DomainApplicationInfoSoyInfo.DOMAINAPPLICATIONINFO);
    addSoyRecord(clientId, new SoyMapData(
        "domainName", domainName,
        "id", id,
        "phase", launchPhase.getPhase(),
        "subphase", launchPhase.getSubphase()));
  }
}
