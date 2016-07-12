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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;
import static google.registry.model.domain.launch.ApplicationStatus.REJECTED;
import static google.registry.model.domain.launch.ApplicationStatus.VALIDATED;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.util.DateTimeUtils.isAtOrAfter;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import google.registry.model.contact.ContactAddress;
import google.registry.model.contact.ContactPhoneNumber;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.PostalInfo;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarAddress;
import google.registry.model.registrar.RegistrarContact;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/** Command to generate the auction data for a TLD. */
@Parameters(separators = " =", commandDescription = "Generate auction data")
final class GenerateAuctionDataCommand implements RemoteApiCommand, GtechCommand {

  @Parameter(
      description = "TLD(s) to generate auction data for",
      required = true)
  private List<String> mainParameters;

  @Parameter(
      names = {"-o", "--output"},
      description = "Output file.",
      validateWith = PathParameter.OutputFile.class)
  private Path output = Paths.get("/dev/stdout");

  @Parameter(
      names = "--skip_validated_check",
      description = "Skip the check that all contended applications are already validated.")
  private boolean skipValidatedCheck;

  /** This is the date format expected in the output file. */
  final DateTimeFormatter formatter = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss");

  @Override
  public void run() throws Exception {
    checkArgument(mainParameters.size() == 1,
        "Expected a single parameter with the TLD name. Actual: %s",
        Joiner.on(' ').join(mainParameters));
    String tld = mainParameters.get(0);
    assertTldExists(tld);

    List<String> result = new ArrayList<>();
    Set<String> registrars = new TreeSet<>();

    for (Map.Entry<String, Collection<DomainApplication>> entry :
        getDomainApplicationMap(tld).asMap().entrySet()) {
      String domainName = entry.getKey();
      List<DomainApplication> domainApplications = filterApplications(entry.getValue());

      // Skip the domain if there are no contentions. This can happen if there is only a single
      // sunrise applicant, or if there are no sunrise applicants and just a single landrush
      // application.
      if (domainApplications.size() < 2) {
        continue;
      }

      Set<String> emailAddresses = new HashSet<>();
      for (DomainApplication domainApplication : domainApplications) {
        checkState(skipValidatedCheck || domainApplication.getApplicationStatus() == VALIDATED, ""
            + "Can't process contending applications for %s because some applications "
            + "are not yet validated.", domainName);

        ContactResource registrant = checkNotNull(domainApplication.getRegistrant().get());
        result.add(emitApplication(domainApplication, registrant));

        // Ensure the registrant's email address is unique across the contending applications.
        if (!emailAddresses.add(registrant.getEmailAddress())) {
          System.err.printf(
              "Warning: Multiple applications found with email address %s for domain %s\n",
              registrant.getEmailAddress(),
              domainName);
        }

        // Add registrar for this application our set of registrars that we must output at the end.
        registrars.add(domainApplication.getCurrentSponsorClientId());
      }
    }

    // Output records for the registrars of any applications we emitted above.
    for (String clientId : registrars) {
      Registrar registrar =
          checkNotNull(Registrar.loadByClientId(clientId), "Registrar %s does not exist", clientId);
      result.add(emitRegistrar(registrar));
    }

    Files.write(output, result, UTF_8);
  }

  /** Return a map of all fully-qualified domain names mapped to the applications for that name. */
  private static Multimap<String, DomainApplication> getDomainApplicationMap(final String tld) {
    DateTime now = DateTime.now(UTC);
    Multimap<String, DomainApplication> domainApplicationMap = TreeMultimap.create(
        Ordering.natural(), new Comparator<DomainApplication>() {
          @Override
          public int compare(DomainApplication o1, DomainApplication o2) {
            return o1.getForeignKey().compareTo(o2.getForeignKey());
          }});
    Iterable<DomainApplication> domainApplications =
        ofy().load().type(DomainApplication.class).filter("tld", tld);
    for (DomainApplication domainApplication : domainApplications) {
      // Ignore deleted and rejected applications. They aren't under consideration.
      ApplicationStatus applicationStatus = domainApplication.getApplicationStatus();
      DateTime deletionTime = domainApplication.getDeletionTime();
      if (applicationStatus == REJECTED || isAtOrAfter(now, deletionTime)) {
        continue;
      }
      boolean result = domainApplicationMap.put(
          domainApplication.getFullyQualifiedDomainName(), domainApplication);
      checkState(result, "Domain application not added to map: %s", domainApplication);
    }
    return domainApplicationMap;
  }

  /**
   * Filter applications by their priority. If there are any sunrise applications, then those will
   * be returned; otherwise just the landrush applications will be returned.
   */
  private static List<DomainApplication> filterApplications(
      Iterable<DomainApplication> domainApplications) {
    // Sort the applications into sunrise and landrush applications.
    List<DomainApplication> sunriseApplications = new ArrayList<>();
    List<DomainApplication> landrushApplications = new ArrayList<>();
    for (DomainApplication domainApplication : domainApplications) {
      if (!domainApplication.getEncodedSignedMarks().isEmpty()) {
        sunriseApplications.add(domainApplication);
      } else {
        landrushApplications.add(domainApplication);
      }
    }

    return !sunriseApplications.isEmpty() ? sunriseApplications : landrushApplications;
  }

  /** Return a record line for the given application. */
  private String emitApplication(DomainApplication domainApplication, ContactResource registrant) {
    Optional<PostalInfo> postalInfo =
        Optional.fromNullable(registrant.getInternationalizedPostalInfo())
            .or(Optional.fromNullable(registrant.getLocalizedPostalInfo()));
    Optional<ContactAddress> address =
        Optional.fromNullable(postalInfo.isPresent() ? postalInfo.get().getAddress() : null);
    List<String> street =
        address.isPresent() ? address.get().getStreet() : ImmutableList.<String>of();
    Optional<ContactPhoneNumber> phoneNumber = Optional.fromNullable(registrant.getVoiceNumber());

    // Each line containing an auction participant has the following format:
    //
    // Domain|Application ID|Application timestamp|Last update date|Registrar Name|
    // Registrant Name|Registrant Company|Registrant Address 1|Registrant Address 2|
    // Registrant City|Registrant Province|Registrant Postal Code|Registrant Country|
    // Registrant Email|Registrant Telephone|Reserve|Application Type
    return Joiner.on('|').join(ImmutableList.of(
        domainApplication.getFullyQualifiedDomainName(),
        domainApplication.getForeignKey(),
        formatter.print(domainApplication.getCreationTime()),
        domainApplication.getLastEppUpdateTime() != null
            ? formatter.print(domainApplication.getLastEppUpdateTime()) : "",
        domainApplication.getCurrentSponsorClientId(),
        nullToEmpty(postalInfo.isPresent() ? postalInfo.get().getName() : ""),
        nullToEmpty(postalInfo.isPresent() ? postalInfo.get().getOrg() : ""),
        Iterables.getFirst(street, ""),
        Joiner.on(' ').skipNulls().join(Iterables.skip(street, 1)),
        nullToEmpty(address.isPresent() ? address.get().getCity() : ""),
        nullToEmpty(address.isPresent() ? address.get().getState() : ""),
        nullToEmpty(address.isPresent() ? address.get().getZip() : ""),
        nullToEmpty(address.isPresent() ? address.get().getCountryCode() : ""),
        nullToEmpty(registrant.getEmailAddress()),
        nullToEmpty(phoneNumber.isPresent() ? phoneNumber.get().toPhoneString() : ""),
        "",
        domainApplication.getEncodedSignedMarks().isEmpty() ? "Landrush" : "Sunrise"));
  }

  /** Return a record line for the given registrar. */
  private static String emitRegistrar(Registrar registrar) {
    // TODO(b/19016140): Determine if this set-up is required.
    Optional<RegistrarContact> contact =
        Optional.fromNullable(Iterables.getFirst(registrar.getContacts(), null));
    Optional<RegistrarAddress> address = Optional.fromNullable(registrar.getLocalizedAddress())
        .or(Optional.fromNullable(registrar.getInternationalizedAddress()));
    List<String> street =
        address.isPresent() ? address.get().getStreet() : ImmutableList.<String>of();

    // Each line containing the registrar of an auction participant has the following format:
    //
    // Registrar Name|Registrar Contact Name|Registrar Full Company|Registrar Address 1|
    // Registrar Address 2|Registrar City|Registrar Province|Registrar Postal Code|
    // Registrar Country|Registrar Email|Registrar Telephone
    return Joiner.on('|').join(ImmutableList.of(
        registrar.getClientIdentifier(),
        contact.isPresent() ? contact.get().getName() : "N/A",
        nullToEmpty(registrar.getRegistrarName()),
        Iterables.getFirst(street, ""),
        Iterables.get(street, 1, ""),
        address.isPresent() ? nullToEmpty(address.get().getCity()) : "",
        address.isPresent() ? nullToEmpty(address.get().getState()) : "",
        address.isPresent() ? nullToEmpty(address.get().getZip()) : "",
        address.isPresent() ? nullToEmpty(address.get().getCountryCode()) : "",
        nullToEmpty(registrar.getEmailAddress()),
        nullToEmpty(registrar.getPhoneNumber())));
  }
}
