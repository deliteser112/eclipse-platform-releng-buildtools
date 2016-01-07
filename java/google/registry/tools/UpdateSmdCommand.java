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

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.flows.domain.DomainFlowUtils.verifyEncodedSignedMark;
import static google.registry.model.EppResourceUtils.loadDomainApplication;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.tmch.TmchData.readEncodedSignedMark;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.VoidWork;
import google.registry.flows.EppException;
import google.registry.model.domain.DomainApplication;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.smd.EncodedSignedMark;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joda.time.DateTime;

/** Command to update the SMD on a domain application. */
@Parameters(separators = " =", commandDescription = "Update the SMD on an application.")
final class UpdateSmdCommand implements RemoteApiCommand {

  @Parameter(
      names = "--id",
      description = "Application ID to update.",
      required = true)
  private String id;

  @Parameter(
      names = "--smd",
      description = "File containing the updated encoded SMD.",
      validateWith = PathParameter.InputFile.class,
      required = true)
  private Path smdFile;

  @Parameter(
      names = "--reason",
      description = "Special reason for the SMD update to record in the history entry.")
  private String reason;

  @Override
  public void run() throws Exception {
    final EncodedSignedMark encodedSignedMark =
        readEncodedSignedMark(new String(Files.readAllBytes(smdFile), US_ASCII));

    ofy().transact(new VoidWork() {
        @Override
        public void vrun() {
          try {
            updateSmd(id, encodedSignedMark, reason);
          } catch (EppException e) {
            throw new RuntimeException(e);
          }
        }});
  }

  private static void updateSmd(
      String applicationId, EncodedSignedMark encodedSignedMark, String reason)
      throws EppException {
    ofy().assertInTransaction();
    DateTime now = ofy().getTransactionTime();

    // Load the domain application.
    DomainApplication domainApplication = loadDomainApplication(applicationId, now);
    checkArgument(domainApplication != null, "Domain application does not exist");

    // Make sure this is a sunrise application.
    checkArgument(!domainApplication.getEncodedSignedMarks().isEmpty(),
        "Can't update SMD on a landrush application.");

    // Verify the new SMD.
    String domainLabel = InternetDomainName.from(domainApplication.getFullyQualifiedDomainName())
        .parts().get(0);
    verifyEncodedSignedMark(encodedSignedMark, domainLabel, now);

    DomainApplication updatedApplication = domainApplication.asBuilder()
        .setEncodedSignedMarks(ImmutableList.of(encodedSignedMark))
        .setLastEppUpdateTime(now)
        .setLastEppUpdateClientId(domainApplication.getCurrentSponsorClientId())
        .build();

    // Create a history entry (with no XML or Trid) to record that we are updating the application.
    HistoryEntry newHistoryEntry = new HistoryEntry.Builder()
        .setType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE)
        .setParent(domainApplication)
        .setModificationTime(now)
        .setClientId(domainApplication.getCurrentSponsorClientId())
        .setBySuperuser(true)
        .setReason("UpdateSmdCommand" + (reason != null ? ": " + reason : ""))
        .build();

    // Save entities to datastore.
    ofy().save().<Object>entities(updatedApplication, newHistoryEntry);
  }
}
