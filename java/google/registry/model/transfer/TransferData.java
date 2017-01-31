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

package google.registry.model.transfer;

import static google.registry.util.CollectionUtils.nullToEmptyImmutableCopy;

import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Embed;
import com.googlecode.objectify.annotation.IgnoreSave;
import com.googlecode.objectify.annotation.Unindex;
import com.googlecode.objectify.condition.IfNull;
import google.registry.model.Buildable;
import google.registry.model.EppResource;
import google.registry.model.billing.BillingEvent;
import google.registry.model.eppcommon.Trid;
import google.registry.model.poll.PollMessage;
import java.util.Set;

/**
 * Common transfer data for {@link EppResource} types. Only applies to domains and contacts;
 * hosts are implicitly transferred with their superordinate domain.
 */
@Embed
@Unindex
public class TransferData extends BaseTransferObject implements Buildable {

  public static final TransferData EMPTY = new TransferData();

  /**
   * The billing event and poll messages associated with a server-approved transfer.
   *
   * <p>This field should be null if there is not currently a pending transfer or if the object
   * being transferred is not a domain. If there is a pending transfer for a domain there should be
   * a number of poll messages and billing events for both the gaining and losing registrars. If the
   * pending transfer is explicitly approved, rejected or cancelled, the referenced entities should
   * be deleted.
   */
  @IgnoreSave(IfNull.class)
  Set<Key<? extends TransferServerApproveEntity>> serverApproveEntities;

  /**
   * The regular one-time billing event that will be charged for a server-approved transfer.
   *
   * <p>This field should be null if there is not currently a pending transfer or if the object
   * being transferred is not a domain.
   */
  @IgnoreSave(IfNull.class)
  Key<BillingEvent.OneTime> serverApproveBillingEvent;

  /**
   * The autorenew billing event that should be associated with this resource after the transfer.
   *
   * <p>This field should be null if there is not currently a pending transfer or if the object
   * being transferred is not a domain.
   */
  @IgnoreSave(IfNull.class)
  Key<BillingEvent.Recurring> serverApproveAutorenewEvent;

  /**
   * The autorenew poll message that should be associated with this resource after the transfer.
   *
   * <p>This field should be null if there is not currently a pending transfer or if the object
   * being transferred is not a domain.
   */
  @IgnoreSave(IfNull.class)
  Key<PollMessage.Autorenew> serverApproveAutorenewPollMessage;

  /** The transaction id of the most recent transfer request (or null if there never was one). */
  Trid transferRequestTrid;

  /**
   * The number of years to add to the registration expiration time if this transfer is approved.
   * Can be null if never transferred, or for resource types where it's not applicable.
   */
  Integer extendedRegistrationYears;

  public ImmutableSet<Key<? extends TransferServerApproveEntity>> getServerApproveEntities() {
    return nullToEmptyImmutableCopy(serverApproveEntities);
  }

  public Key<BillingEvent.OneTime> getServerApproveBillingEvent() {
    return serverApproveBillingEvent;
  }

  public Key<BillingEvent.Recurring> getServerApproveAutorenewEvent() {
    return serverApproveAutorenewEvent;
  }

  public Key<PollMessage.Autorenew> getServerApproveAutorenewPollMessage() {
    return serverApproveAutorenewPollMessage;
  }

  public Trid getTransferRequestTrid() {
    return transferRequestTrid;
  }

  public Integer getExtendedRegistrationYears() {
    return extendedRegistrationYears;
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** Builder for {@link TransferData} because it is immutable. */
  public static class Builder extends BaseTransferObject.Builder<TransferData, Builder> {

    /** Create a {@link Builder} wrapping a new instance. */
    public Builder() {}

    /** Create a {@link Builder} wrapping the given instance. */
    private Builder(TransferData instance) {
      super(instance);
    }

    public Builder setServerApproveEntities(
        ImmutableSet<Key<? extends TransferServerApproveEntity>> serverApproveEntities) {
      getInstance().serverApproveEntities = serverApproveEntities;
      return this;
    }

    public Builder setServerApproveBillingEvent(
        Key<BillingEvent.OneTime> serverApproveBillingEvent) {
      getInstance().serverApproveBillingEvent = serverApproveBillingEvent;
      return this;
    }

    public Builder setServerApproveAutorenewEvent(
        Key<BillingEvent.Recurring> serverApproveAutorenewEvent) {
      getInstance().serverApproveAutorenewEvent = serverApproveAutorenewEvent;
      return this;
    }

    public Builder setServerApproveAutorenewPollMessage(
        Key<PollMessage.Autorenew> serverApproveAutorenewPollMessage) {
      getInstance().serverApproveAutorenewPollMessage = serverApproveAutorenewPollMessage;
      return this;
    }

    public Builder setTransferRequestTrid(Trid transferRequestTrid) {
      getInstance().transferRequestTrid = transferRequestTrid;
      return this;
    }

    /** Set the years to add to the registration if this transfer completes. */
    public Builder setExtendedRegistrationYears(Integer extendedRegistrationYears) {
      getInstance().extendedRegistrationYears = extendedRegistrationYears;
      return thisCastToDerived();
    }
  }

  /**
   * Marker interface for objects that are written in anticipation of a server approval, and
   * therefore need to be deleted under any other outcome.
   */
  public interface TransferServerApproveEntity {}
}
