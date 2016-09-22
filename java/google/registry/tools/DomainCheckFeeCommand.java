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
import com.google.common.collect.Multimap;
import com.google.template.soy.data.SoyMapData;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.soy.DomainCheckFeeSoyInfo;
import java.util.Collection;
import java.util.List;

/** A command to execute a domain check fees epp command. */
@Parameters(separators = " =", commandDescription = "Check domain fees (for a 1-year create)")
final class DomainCheckFeeCommand extends EppToolCommand implements GtechCommand {

  @Parameter(
      names = {"-c", "--client"},
      description = "Client identifier of the registrar to execute the command as",
      required = true)
  String clientId;

  @Parameter(
      description = "Domain(s) to check.",
      required = true)
  List<String> mainParameters;

  @Override
  void initEppToolCommand() {
    Multimap<String, String> domainNameMap = validateAndGroupDomainNamesByTld(mainParameters);
    for (Collection<String> values : domainNameMap.asMap().values()) {
      setSoyTemplate(DomainCheckFeeSoyInfo.getInstance(), DomainCheckFeeSoyInfo.DOMAINCHECKFEE);
      addSoyRecord(clientId, new SoyMapData("domainNames", values));
    }
  }
}
