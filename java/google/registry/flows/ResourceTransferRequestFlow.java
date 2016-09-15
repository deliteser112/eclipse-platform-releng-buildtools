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

package google.registry.flows;

import static google.registry.flows.ResourceFlowUtils.createPendingTransferNotificationResponse;
import static google.registry.flows.ResourceFlowUtils.createTransferResponse;
import static google.registry.flows.ResourceFlowUtils.verifyAuthInfoForResource;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_WITH_ACTION_PENDING;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.union;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException.AuthorizationErrorException;
import google.registry.flows.EppException.CommandUseErrorException;
import google.registry.flows.EppException.ObjectPendingTransferException;
import google.registry.model.EppResource;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppinput.ResourceCommand.SingleResourceCommand;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.EppResponse.ResponseData;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import google.registry.model.poll.PollMessage;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferData.TransferServerApproveEntity;
import google.registry.model.transfer.TransferStatus;
import java.util.Set;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * An EPP flow that requests a transfer on a resource.
 *
 * @param <R> the resource type being manipulated
 * @param <C> the command type, marshalled directly from the epp xml
 */
 public abstract class ResourceTransferRequestFlow
    <R extends EppResource, C extends SingleResourceCommand> extends ResourceTransferFlow<R, C> {

  private static final Set<StatusValue> TRANSFER_DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.CLIENT_TRANSFER_PROHIBITED,
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_TRANSFER_PROHIBITED);

  private DateTime transferExpirationTime;

  /** Helper class to identify the two clients. */
  protected abstract static class Client {
    public abstract String getId();
  }

  /** The gaining client. */
  protected Client gainingClient = new Client() {
      @Override
      public String getId() {
        return getClientId();
      }};

  /** The losing client. */
  protected Client losingClient = new Client() {
      @Override
      public String getId() {
        return existingResource.getCurrentSponsorClientId();
      }};

  @Override
  protected final void initResourceCreateOrMutateFlow() throws EppException {
    initResourceTransferRequestFlow();
  }

  protected abstract Duration getAutomaticTransferLength();

  @Override
  protected final void verifyMutationAllowed() throws EppException {
    // Verify that the resource does not already have a pending transfer.
    if (TransferStatus.PENDING.equals(existingResource.getTransferData().getTransferStatus())) {
      throw new AlreadyPendingTransferException(targetId);
    }
    // Verify that this client doesn't already sponsor this resource.
    if (gainingClient.getId().equals(losingClient.getId())) {
      throw new ObjectAlreadySponsoredException();
    }
    if (command.getAuthInfo() == null) {
      throw new MissingTransferRequestAuthInfoException();
    }
    verifyAuthInfoForResource(command.getAuthInfo(), existingResource);
    verifyTransferRequestIsAllowed();
  }

  private TransferData.Builder
      createTransferDataBuilder(TransferStatus transferStatus) throws EppException {
    TransferData.Builder builder = new TransferData.Builder()
        .setGainingClientId(gainingClient.getId())
        .setTransferRequestTime(now)
        .setLosingClientId(losingClient.getId())
        .setPendingTransferExpirationTime(transferExpirationTime)
        .setTransferRequestTrid(trid)
        .setTransferStatus(transferStatus);
    setTransferDataProperties(builder);
    return builder;
  }

  private PollMessage createPollMessage(
      Client client, TransferStatus transferStatus, DateTime eventTime) throws EppException {
    ImmutableList.Builder<ResponseData> responseData = new ImmutableList.Builder<>();
    responseData.add(createTransferResponse(
        existingResource, createTransferDataBuilder(transferStatus).build(), now));
    if (client.getId().equals(gainingClient.getId())) {
      responseData.add(createPendingTransferNotificationResponse(
          existingResource, trid, true, now));
    }
    return new PollMessage.OneTime.Builder()
        .setClientId(client.getId())
        .setEventTime(eventTime)
        .setMsg(transferStatus.getMessage())
        .setResponseData(responseData.build())
        .setParent(historyEntry)
        .build();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected final R createOrMutateResource() throws EppException {
    // Figure out transfer expiration time once we've verified that the existingResource does in
    // fact exist (otherwise we won't know which TLD to get this figure off of).
    transferExpirationTime = now.plus(getAutomaticTransferLength());
    // When a transfer is requested, a poll message is created to notify the losing registrar.
    PollMessage requestPollMessage = createPollMessage(losingClient, TransferStatus.PENDING, now);
    // If the transfer is server approved, this message will be sent to the gaining registrar. */
    PollMessage serverApproveGainingPollMessage =
        createPollMessage(gainingClient, TransferStatus.SERVER_APPROVED, transferExpirationTime);
    // If the transfer is server approved, this message will be sent to the losing registrar. */
    PollMessage serverApproveLosingPollMessage =
        createPollMessage(losingClient, TransferStatus.SERVER_APPROVED, transferExpirationTime);
    ofy().save().entities(
        requestPollMessage, serverApproveGainingPollMessage, serverApproveLosingPollMessage);
    return (R) existingResource.asBuilder()
        .setTransferData(createTransferDataBuilder(TransferStatus.PENDING)
            .setServerApproveEntities(union(
                getTransferServerApproveEntities(),
                Key.create(serverApproveGainingPollMessage),
                Key.create(serverApproveLosingPollMessage)))
            .build())
        .addStatusValue(StatusValue.PENDING_TRANSFER)
        .build();
  }

  /** Subclasses can override this to do further initialization. */
  protected void initResourceTransferRequestFlow() throws EppException {}

  /**
   * Subclasses can override this to return the keys of any entities that need to be deleted if the
   * transfer ends in any state other than SERVER_APPROVED.
   */
  protected Set<Key<? extends TransferServerApproveEntity>> getTransferServerApproveEntities() {
    return ImmutableSet.of();
  }

  /** Check resource-specific invariants before allowing the transfer request to proceed. */
  @SuppressWarnings("unused")
  protected void verifyTransferRequestIsAllowed() throws EppException {}

  /** Subclasses can override this to modify fields on the transfer data builder. */
  @SuppressWarnings("unused") 
  protected void setTransferDataProperties(TransferData.Builder builder) throws EppException {}

  @Override
  protected final EppOutput getOutput() throws EppException {
    return createOutput(
        SUCCESS_WITH_ACTION_PENDING,
        createTransferResponse(newResource, newResource.getTransferData(), now),
        getTransferResponseExtensions());
  }

  /** Subclasses can override this to return response extensions. */
  protected ImmutableList<? extends ResponseExtension> getTransferResponseExtensions() {
    return null;
  }

  @Override
  protected final Set<StatusValue> getDisallowedStatuses() {
    return TRANSFER_DISALLOWED_STATUSES;
  }

  /** Authorization info is required to request a transfer. */
  public static class MissingTransferRequestAuthInfoException extends AuthorizationErrorException {
    public MissingTransferRequestAuthInfoException() {
      super("Authorization info is required to request a transfer");
    }
  }

  /** Registrar already sponsors the object of this transfer request. */
  public static class ObjectAlreadySponsoredException extends CommandUseErrorException {
    public ObjectAlreadySponsoredException() {
      super("Registrar already sponsors the object of this transfer request");
    }
  }

  /** The resource is already pending transfer. */
  public static class AlreadyPendingTransferException extends ObjectPendingTransferException {
    public AlreadyPendingTransferException(String targetId) {
      super(targetId);
    }
  }
}
