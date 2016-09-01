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

import static com.google.common.base.Strings.isNullOrEmpty;
import static google.registry.flows.EppXmlTransformer.unmarshal;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.util.DateTimeUtils.isBeforeOrAt;
import static google.registry.util.DomainNameUtils.ACE_PREFIX;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.cmd.LoadType;
import com.googlecode.objectify.cmd.Query;
import google.registry.flows.EppException;
import google.registry.model.domain.DomainApplication;
import google.registry.model.smd.EncodedSignedMark;
import google.registry.model.smd.SignedMark;
import google.registry.model.smd.SignedMarkRevocationList;
import google.registry.model.tmch.ClaimsListShard;
import google.registry.tmch.TmchXmlSignature;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import google.registry.util.Clock;
import google.registry.util.Idn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckReturnValue;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** Command to generate a report of all domain applications. */
@Parameters(separators = " =", commandDescription = "Generate report of all domain applications.")
final class GenerateApplicationsReportCommand implements RemoteApiCommand, GtechCommand {

  @Parameter(
      names = {"-t", "--tld"},
      description = "TLD which contains the applications.")
  private String tld;

  @Parameter(
      names = "--ids",
      description = "Comma-delimited list of application IDs to include. "
          + "If not provided, all active application will be included.")
  private List<Long> ids = emptyList();

  @Parameter(
      names = {"-o", "--output"},
      description = "Output file.",
      validateWith = PathParameter.OutputFile.class)
  private Path output = Paths.get("/dev/stdout");

  @Inject
  Clock clock;

  @Override
  public void run() throws Exception {
    if (tld != null) {
      assertTldExists(tld);
    }
    DateTime now = clock.nowUtc();
    List<String> result = new ArrayList<>();
    if (!ids.isEmpty()) {
      for (Long applicationId : ids) {
        DomainApplication domainApplication = ofy().load()
            .type(DomainApplication.class)
            .id(applicationId)
            .now();
        if (domainApplication == null) {
          System.err.printf("Application ID %d not found\n", applicationId);
          continue;
        }
        result.addAll(validate(domainApplication, now).asSet());
      }
    } else {
      LoadType<DomainApplication> loader = ofy().load().type(DomainApplication.class);
      Query<DomainApplication> domainApplications =
          (tld == null) ? loader : loader.filter("tld", tld);
      for (DomainApplication domainApplication : domainApplications) {
        result.addAll(validate(domainApplication, now).asSet());
      }
    }
    Files.write(output, result, UTF_8);
  }

  /** Processes a domain application and adds report lines to {@code result}. */
  Optional<String> validate(DomainApplication domainApplication, DateTime now) {
    // Validate the label.
    List<String> nameParts =
        InternetDomainName.from(domainApplication.getFullyQualifiedDomainName()).parts();
    String label = nameParts.get(0);

    // Ignore deleted applications.
    if (isBeforeOrAt(domainApplication.getDeletionTime(), now)) {
      return Optional.absent();
    }

    // Defensive invalid punycode check.
    if (label.startsWith(ACE_PREFIX) && label.equals(Idn.toUnicode(label))) {
      return Optional.of(makeLine(domainApplication, "Invalid punycode"));
    }

    // Validate the SMD.
    for (EncodedSignedMark encodedSignedMark : domainApplication.getEncodedSignedMarks()) {
      byte[] signedMarkData;
      try {
        signedMarkData = encodedSignedMark.getBytes();
      } catch (IllegalStateException e) {
        return Optional.of(makeLine(domainApplication, "Incorrectly encoded SMD data"));
      }

      SignedMark signedMark;
      try {
        signedMark = unmarshal(SignedMark.class, signedMarkData);
      } catch (EppException e) {
        return Optional.of(makeLine(domainApplication, "Unparseable SMD"));
      }

      if (SignedMarkRevocationList.get().isSmdRevoked(signedMark.getId(),
            domainApplication.getCreationTime())) {
        return Optional.of(makeLine(domainApplication, "SMD revoked"));
      }

      try {
        TmchXmlSignature.verify(signedMarkData);
      } catch (Exception e) {
        return Optional.of(
            makeLine(domainApplication, String.format("Invalid SMD (%s)", e.getMessage())));
      }
    }

    // If this is a landrush application and has no claims notice, check to see if it should have
    // one.
    if (domainApplication.getEncodedSignedMarks().isEmpty()
        && (domainApplication.getLaunchNotice() == null
            || domainApplication.getLaunchNotice().getNoticeId() == null
            || isNullOrEmpty(domainApplication.getLaunchNotice().getNoticeId().getTcnId()))
        && ClaimsListShard.get().getClaimKey(label) != null) {
      return Optional.of(makeLine(domainApplication, "Missing claims notice"));
    }

    return Optional.of(makeLine(domainApplication, "Valid"));
  }

  @CheckReturnValue
  private String makeLine(DomainApplication domainApplication, String validityMessage) {
    return Joiner.on(',').join(
        domainApplication.getRepoId(),
        domainApplication.getFullyQualifiedDomainName(),
        domainApplication.getEncodedSignedMarks().isEmpty() ? "landrush" : "sunrise",
        domainApplication.getApplicationStatus(),
        domainApplication.getCurrentSponsorClientId(),
        ofy().load().key(domainApplication.getRegistrant()).now().getEmailAddress(),
        validityMessage);
  }
}
