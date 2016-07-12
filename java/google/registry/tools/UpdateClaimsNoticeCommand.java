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
import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.VoidWork;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.domain.launch.LaunchNotice.InvalidChecksumException;
import google.registry.model.reporting.HistoryEntry;
import google.registry.tools.Command.RemoteApiCommand;
import org.joda.time.DateTime;

/** Command to update the claims notice on a domain application. */
@Parameters(separators = " =", commandDescription = "Update the claims notice on an application.")
final class UpdateClaimsNoticeCommand implements RemoteApiCommand {

  @Parameter(
      names = "--id",
      description = "Application ID to update.",
      required = true)
  private String id;

  @Parameter(
      names = "--tcn_id",
      description = "Trademark claims notice ID.",
      required = true)
  private String tcnId;

  @Parameter(
      names = "--validator_id",
      description = "Trademark claims validator.")
  private String validatorId = "tmch";

  @Parameter(
      names = "--expiration_time",
      description = "Expiration time of claims notice.",
      required = true)
  private String expirationTime;

  @Parameter(
      names = "--accepted_time",
      description = "Accepted time of claims notice.",
      required = true)
  private String acceptedTime;

  @Override
  public void run() throws Exception {
    final LaunchNotice launchNotice = LaunchNotice.create(
        tcnId, validatorId, DateTime.parse(expirationTime), DateTime.parse(acceptedTime));

    ofy().transact(new VoidWork() {
        @Override
        public void vrun() {
          try {
            updateClaimsNotice(id, launchNotice);
          } catch (InvalidChecksumException e) {
            throw new RuntimeException(e);
          }
        }});
  }

  private void updateClaimsNotice(String applicationId, LaunchNotice launchNotice)
      throws InvalidChecksumException {
    ofy().assertInTransaction();
    DateTime now = ofy().getTransactionTime();

    // Load the domain application.
    DomainApplication domainApplication =
        loadByUniqueId(DomainApplication.class, applicationId, now);
    checkArgument(domainApplication != null, "Domain application does not exist");

    // Make sure this isn't a sunrise application.
    checkArgument(domainApplication.getEncodedSignedMarks().isEmpty(),
        "Can't update claims notice on sunrise applications.");

    // Validate the new launch notice checksum.
    String domainLabel = InternetDomainName.from(domainApplication.getFullyQualifiedDomainName())
        .parts().get(0);
    launchNotice.validate(domainLabel);

    DomainApplication updatedApplication = domainApplication.asBuilder()
        .setLaunchNotice(launchNotice)
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
        .build();

    // Save entities to datastore.
    ofy().save().<Object>entities(updatedApplication, newHistoryEntry);
  }
}
