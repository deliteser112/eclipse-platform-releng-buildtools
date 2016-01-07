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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static google.registry.model.EppResourceUtils.loadDomainApplication;
import static google.registry.model.domain.launch.ApplicationStatus.ALLOCATED;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.VoidWork;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchInfoResponseExtension;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppcommon.Trid;
import google.registry.model.poll.PendingActionNotificationResponse.DomainPendingActionNotificationResponse;
import google.registry.model.poll.PollMessage;
import google.registry.model.registrar.Registrar;
import google.registry.model.reporting.HistoryEntry;
import java.util.List;
import org.joda.time.DateTime;

/** Command to manually update the status of a domain application. */
@Parameters(separators = " =", commandDescription = "Manually update domain application status.")
final class UpdateApplicationStatusCommand extends MutatingCommand {

  @Parameter(
      names = "--ids",
      description = "Comma-delimited list of application IDs to update the status.",
      required = true)
  private List<String> ids;

  @Parameter(
      names = "--msg",
      description = "Message shown to registrars in the poll message.",
      required = true)
  private String message;

  @Parameter(
      names = "--status",
      description = "The new application status.",
      required = true)
  private ApplicationStatus newStatus;

  @Parameter(
      names = "--history_client_id",
      description = "Client id that made this change. Only recorded in the history entry.")
  private String clientId = "CharlestonRoad";

  @Override
  protected void init() throws Exception {
    checkArgumentNotNull(
        Registrar.loadByClientId(clientId), "Registrar with client ID %s not found", clientId);
    for (final String applicationId : ids) {
      ofy().transact(new VoidWork() {
        @Override
        public void vrun() {
          updateApplicationStatus(applicationId);
        }
      });
    }
  }

  /**
   * Stages changes to update the status of an application and also enqueue a poll message for the
   * status change, which may contain a PendingActionNotificationResponse if this is a final status.
   *
   * <p>This method must be called from within a transaction.
   */
  private void updateApplicationStatus(String applicationId) {
    ofy().assertInTransaction();
    DateTime now = ofy().getTransactionTime();

    // Load the domain application.
    DomainApplication domainApplication = loadDomainApplication(applicationId, now);
    checkArgumentNotNull(domainApplication, "Domain application does not exist");

    // It's not an error if the application already has the intended status. We want the method
    // to be idempotent.
    if (domainApplication.getApplicationStatus() == newStatus) {
      System.err.printf("Domain application %s already has status %s\n", applicationId, newStatus);
      return;
    }

    // Ensure domain does not already have a final status which it should not be updated from.
    checkState(
        !domainApplication.getApplicationStatus().isFinalStatus(),
        "Domain application has final status %s",
        domainApplication.getApplicationStatus());

    // Update its status.
    DomainApplication.Builder applicationBuilder = domainApplication.asBuilder()
        .setApplicationStatus(newStatus)
        .setLastEppUpdateTime(now)
        // Use the current sponsor instead of the history client ID because the latter might be an
        // internal client ID that we don't want to expose.
        .setLastEppUpdateClientId(domainApplication.getCurrentSponsorClientId());

    // Create a history entry (with no XML or Trid) to record that we are updating the status on
    // this application.
    HistoryEntry newHistoryEntry = new HistoryEntry.Builder()
        .setType(HistoryEntry.Type.DOMAIN_APPLICATION_STATUS_UPDATE)
        .setParent(domainApplication)
        .setModificationTime(now)
        .setClientId(clientId)
        .setBySuperuser(true)
        .build();

    // Create a poll message informing the registrar that the application status was updated.
    PollMessage.OneTime.Builder pollMessageBuilder = new PollMessage.OneTime.Builder()
        .setClientId(domainApplication.getCurrentSponsorClientId())
        .setEventTime(ofy().getTransactionTime())
        .setMsg(message)
        .setParent(newHistoryEntry)
        .setResponseExtensions(ImmutableList.of(
            new LaunchInfoResponseExtension.Builder()
                .setApplicationId(domainApplication.getForeignKey())
                .setPhase(domainApplication.getPhase())
                .setApplicationStatus(newStatus)
                .build()));

    // If this is a final status (i.e. an end state), then we should remove pending create from the
    // application and include a pending action notification in the poll message. Conversely, if
    // this is not a final status, we should add pending create (which will already be there unless
    // we're resurrecting an application).
    if (newStatus.isFinalStatus()) {
      applicationBuilder.removeStatusValue(StatusValue.PENDING_CREATE);

      pollMessageBuilder.setResponseData(ImmutableList.of(
          DomainPendingActionNotificationResponse.create(
              domainApplication.getFullyQualifiedDomainName(),
              ALLOCATED.equals(newStatus),  // Whether the operation succeeded
              getCreationTrid(domainApplication),
              now)));
    } else {
      applicationBuilder.addStatusValue(StatusValue.PENDING_CREATE);
    }

    // Stage changes for all entities that need to be saved to datastore.
    stageEntityChange(domainApplication, applicationBuilder.build());
    stageEntityChange(null, pollMessageBuilder.build());
    stageEntityChange(null, newHistoryEntry);
  }

  /** Retrieve the transaction id of the operation which created this application. */
  static Trid getCreationTrid(DomainApplication domainApplication) {
    Trid creationTrid = domainApplication.getCreationTrid();
    if (creationTrid == null) {
      // If the creation TRID is not present on the application (this can happen for older
      // applications written before this field was added), then we must read the earliest history
      // entry for the application to retrieve it.
      return checkNotNull(checkNotNull(ofy()
          .load()
          .type(HistoryEntry.class)
          .ancestor(domainApplication)
          .order("modificationTime")
          .first()
          .now()).getTrid());
    }
    return creationTrid;
  }
}
