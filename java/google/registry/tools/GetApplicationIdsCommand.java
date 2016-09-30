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

import static google.registry.model.index.DomainApplicationIndex.loadActiveApplicationsByDomainName;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.model.registry.Registries.findTldForNameOrThrow;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InternetDomainName;
import google.registry.model.domain.DomainApplication;
import google.registry.tools.Command.RemoteApiCommand;
import java.util.List;
import org.joda.time.DateTime;

/** Command to generate a list of all applications for a given domain name(s). */
@Parameters(separators = " =",
    commandDescription = "Generate list of application IDs and sponsors for given domain name(s)")
final class GetApplicationIdsCommand implements RemoteApiCommand {

  @Parameter(
      description = "Fully qualified domain name(s)",
      required = true)
  private List<String> mainParameters;

  @Override
  public void run() {
    for (String domainName : mainParameters) {
      InternetDomainName tld = findTldForNameOrThrow(InternetDomainName.from(domainName));
      assertTldExists(tld.toString());
      System.out.printf("%s:%n", domainName);

      // Sample output:
      // example.tld:
      //    1 (NewRegistrar)
      //    2 (OtherRegistrar)
      // example2.tld:
      //    No applications exist for 'example2.tld'.
      ImmutableList<DomainApplication> applications = ImmutableList.copyOf(
          loadActiveApplicationsByDomainName(domainName, DateTime.now(UTC)));
      if (applications.isEmpty()) {
        System.out.printf("    No applications exist for \'%s\'.%n", domainName);
      } else {
        for (DomainApplication application : applications) {
          System.out.printf(
              "    %s (%s)%n",
              application.getForeignKey(),
              application.getCurrentSponsorClientId());
        }
      }
    }
  }
}
