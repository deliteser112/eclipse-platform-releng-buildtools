// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.model.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.billing.BillingEvent;
import com.google.domain.registry.model.domain.rgp.GracePeriodStatus;

import com.googlecode.objectify.Ref;
import com.googlecode.objectify.annotation.Embed;

import org.joda.time.DateTime;

import javax.annotation.Nullable;

/**
 * A domain grace period with an expiration time.
 * <p>
 * When a grace period expires, it is lazily removed from the {@link DomainResource} the next time
 * the resource is loaded from the datastore.
 */
@Embed
public class GracePeriod extends ImmutableObject {

  /** The type of grace period. */
  GracePeriodStatus type;

  /** When the grace period ends. */
  DateTime expirationTime;

  /** The registrar to bill. */
  String clientId;

  /**
   * The one-time billing event corresponding to the action that triggered this grace period, or
   * null if not applicable.  Not set for autorenew grace periods (which instead use the field
   * {@code billingEventRecurring}) or for redemption grace periods (since deletes have no cost).
   */
  // NB: Would @IgnoreSave(IfNull.class), but not allowed for @Embed collections.
  Ref<BillingEvent.OneTime> billingEventOneTime = null;

  /**
   * The recurring billing event corresponding to the action that triggered this grace period, if
   * applicable - i.e. if the action was an autorenew - or null in all other cases.
   */
  // NB: Would @IgnoreSave(IfNull.class), but not allowed for @Embed collections.
  Ref<BillingEvent.Recurring> billingEventRecurring = null;

  public GracePeriodStatus getType() {
    // NB: We implicitly convert SUNRUSH_ADD to ADD, since they should be functionally equivalent.
    return type == GracePeriodStatus.SUNRUSH_ADD ? GracePeriodStatus.ADD : type;
  }

  public boolean isSunrushAddGracePeriod() {
    return type == GracePeriodStatus.SUNRUSH_ADD;
  }

  public DateTime getExpirationTime() {
    return expirationTime;
  }

  public String getClientId() {
    return clientId;
  }

  /**
   * Returns the ref to the billing event associated with this grace period, or null if there is
   * no applicable billing event (i.e. this is a redemption grace period).
   */
  @Nullable
  public Ref<? extends BillingEvent> getBillingEvent() {
    return Optional.<Ref<? extends BillingEvent>>fromNullable(billingEventOneTime)
        .or(Optional.<Ref<? extends BillingEvent>>fromNullable(billingEventRecurring))
        .orNull();
  }

  /**
   * Returns the one time billing event. The value will only be non-null if the type of this grace
   * period is not AUTO_RENEW.
   */

  public Ref<BillingEvent.OneTime> getOneTimeBillingEvent() {
    return billingEventOneTime;
  }

  /**
   * Returns the recurring billing event. The value will only be non-null if the type of this grace
   * period is AUTO_RENEW.
   */
  public Ref<BillingEvent.Recurring> getRecurringBillingEvent() {
    return billingEventRecurring;
  }

  /**
   * Constructs a GracePeriod with some interpretation of the parameters.  In particular, selects
   * the field to store the billing event ref in based on the specified grace period status type.
   */
  @SuppressWarnings("unchecked")
  public static GracePeriod create(
      GracePeriodStatus type,
      DateTime expirationTime,
      String clientId,
      @Nullable Ref<? extends BillingEvent> billingEvent) {
    GracePeriod instance = new GracePeriod();
    instance.type = checkNotNull(type);
    instance.expirationTime = checkNotNull(expirationTime);
    instance.clientId = checkNotNull(clientId);
    if (GracePeriodStatus.AUTO_RENEW.equals(instance.type)) {
      instance.billingEventRecurring = (Ref<BillingEvent.Recurring>) billingEvent;
    } else {
      instance.billingEventOneTime = (Ref<BillingEvent.OneTime>) billingEvent;
    }
    return instance;
  }

  /** Constructs a GracePeriod of the given type from the provided one-time BillingEvent. */
  public static GracePeriod forBillingEvent(
      GracePeriodStatus type, BillingEvent.OneTime billingEvent) {
    return create(
        type, billingEvent.getBillingTime(), billingEvent.getClientId(), Ref.create(billingEvent));
  }
}
