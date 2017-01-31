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

import static google.registry.model.domain.launch.ApplicationStatus.REJECTED;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.util.DateTimeUtils.isAtOrAfter;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.internal.Sets;
import com.googlecode.objectify.Work;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import google.registry.util.Idn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.joda.time.DateTime;

/** Command to generate a list of all slds in a tld that have open applications. */
@Parameters(separators = " =", commandDescription = "Generate applied-for domains list")
final class GetAppliedLabelsCommand implements RemoteApiCommand {

  @Parameter(
      names = {"-t", "--tld"},
      description = "TLD to generate list for.",
      required = true)
  private String tld;

  @Parameter(
      names = {"-o", "--output"},
      description = "Output file.",
      validateWith = PathParameter.OutputFile.class)
  private Path output = Paths.get("/dev/stdout");

  @Override
  public void run() throws Exception {
    List<String> lines = new ArrayList<>();
    for (String label : getDomainApplicationMap(assertTldExists(tld))) {
      label = label.substring(0, label.lastIndexOf('.'));
      try {
        lines.add(Idn.toUnicode(label.toLowerCase()));
      } catch (IllegalArgumentException e) {
        // An invalid punycode label that we need to reject later.
        lines.add(label + " (invalid)");
      }
    }
    Files.write(output, lines, UTF_8);
  }

  /** Return a set of all fully-qualified domain names with open applications. */
  private static Set<String> getDomainApplicationMap(final String tld) {
    return ofy().transact(new Work<Set<String>>() {
      @Override
      public Set<String> run() {
        Set<String> labels = Sets.newHashSet();
        List<DomainApplication> domainApplications;
        domainApplications = ofy().load().type(DomainApplication.class).filter("tld", tld).list();
        for (DomainApplication domainApplication : domainApplications) {
          // Ignore deleted and rejected applications. They aren't under consideration.
          ApplicationStatus applicationStatus = domainApplication.getApplicationStatus();
          DateTime deletionTime = domainApplication.getDeletionTime();
          if (applicationStatus == REJECTED
              || isAtOrAfter(ofy().getTransactionTime(), deletionTime)) {
            continue;
          }
          labels.add(domainApplication.getFullyQualifiedDomainName());
        }
        return labels;
      }});
  }
}
