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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static google.registry.model.index.DomainApplicationIndex.loadActiveApplicationsByDomainName;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.findTldForName;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Work;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainApplication;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Command to check the status of domain applications. */
@Parameters(separators = " =", commandDescription = "Check auction status")
final class AuctionStatusCommand implements RemoteApiCommand, GtechCommand {

  @Parameter(
      description = "Domains(s) to check",
      required = true)
  private List<String> mainArguments;

  @Parameter(
      names = {"-o", "--output"},
      description = "Output file.",
      validateWith = PathParameter.OutputFile.class)
  private Path output = Paths.get("/dev/stdout");

  @Override
  public void run() throws Exception {
    final ImmutableSet<String> domains = ImmutableSet.copyOf(mainArguments);
    Files.write(output, FluentIterable
        .from(domains)
        .transformAndConcat(new Function<String, Iterable<String>>() {
            @Override
            public Iterable<String> apply(String fullyQualifiedDomainName) {
              checkState(
                  findTldForName(InternetDomainName.from(fullyQualifiedDomainName)).isPresent(),
                  "No tld found for %s", fullyQualifiedDomainName);
              return ofy().transactNewReadOnly(new Work<Iterable<String>>() {
                @Override
                public Iterable<String> run() {
                  ImmutableList.Builder<DomainApplication> applications =
                      new ImmutableList.Builder<>();
                  for (String domain : domains) {
                    applications.addAll(
                        loadActiveApplicationsByDomainName(domain, ofy().getTransactionTime()));
                  }
                  return Lists.transform(
                      FluentIterable.from(applications.build()).toSortedList(ORDERING),
                      APPLICATION_FORMATTER);
                }});
            }}), UTF_8);
  }

  private static final Ordering<DomainApplication> ORDERING = new Ordering<DomainApplication>() {
        @Override
        public int compare(DomainApplication left, DomainApplication right) {
          return ComparisonChain.start()
              .compare(left.getFullyQualifiedDomainName(), right.getFullyQualifiedDomainName())
              .compareTrueFirst(
                  left.getEncodedSignedMarks().isEmpty(), right.getEncodedSignedMarks().isEmpty())
              .compare(left.getApplicationStatus(), right.getApplicationStatus())
              .compare(left.getCreationTime(), right.getCreationTime())
              .result();
        }};

  private static final Function<DomainApplication, String> APPLICATION_FORMATTER =
      new Function<DomainApplication, String>() {
        @Override
        public String apply(DomainApplication app) {
          ContactResource registrant = checkNotNull(ofy().load().key(app.getRegistrant()).now());
          Object[] keysAndValues = new Object[] {
              "Domain", app.getFullyQualifiedDomainName(),
              "Type", app.getEncodedSignedMarks().isEmpty() ? "Landrush" : "Sunrise",
              "Application Status", app.getApplicationStatus(),
              "Application ID", app.getForeignKey(),
              "Application Timestamp", app.getCreationTime(),
              "Last Update", app.getLastEppUpdateTime(),
              "Registrar Name", app.getCurrentSponsorClientId(),
              "Registrant Email", registrant.getEmailAddress(),
              "Registrant Phone", registrant.getVoiceNumber().getPhoneNumber()
          };
          return String.format(
              Strings.repeat("%-25s= %s\n", keysAndValues.length / 2), keysAndValues);
        }};
}
