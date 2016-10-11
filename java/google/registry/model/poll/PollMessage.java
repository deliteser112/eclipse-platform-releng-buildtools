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

package google.registry.model.poll;

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.util.CollectionUtils.forceEmptyToNull;
import static google.registry.util.CollectionUtils.isNullOrEmpty;
import static google.registry.util.CollectionUtils.nullToEmpty;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Converter;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.EntitySubclass;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Parent;
import google.registry.model.Buildable;
import google.registry.model.EppResource;
import google.registry.model.ImmutableObject;
import google.registry.model.annotations.ExternalMessagingName;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.DomainRenewData;
import google.registry.model.domain.launch.LaunchInfoResponseExtension;
import google.registry.model.eppoutput.EppResponse.ResponseData;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import google.registry.model.poll.PendingActionNotificationResponse.ContactPendingActionNotificationResponse;
import google.registry.model.poll.PendingActionNotificationResponse.DomainPendingActionNotificationResponse;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferData.TransferServerApproveEntity;
import google.registry.model.transfer.TransferResponse.ContactTransferResponse;
import google.registry.model.transfer.TransferResponse.DomainTransferResponse;
import java.util.List;
import org.joda.time.DateTime;

/**
 * A poll message that is pending for a registrar.
 *
 * <p>Poll messages are not delivered until their {@link #eventTime} has passed. Poll messages can
 * be speculatively enqueued for future delivery, and then modified or deleted before that date has
 * passed. Unlike most other entities in Datastore, which are marked as deleted but otherwise
 * retained for historical purposes, poll messages are truly deleted once they have been delivered
 * and ACKed.
 *
 * <p>Poll messages are parented off of the {@link HistoryEntry} that resulted in their creation.
 * This means that poll messages are contained in the Datastore entity group of the parent {@link
 * EppResource} (which can be a domain, application, contact, or host). It is thus possible to
 * perform a strongly consistent query to find all poll messages associated with a given EPP
 * resource.
 *
 * <p>Poll messages are identified externally by registrars using the format defined in {@link
 * PollMessageExternalKeyConverter}.
 *
 * @see "https://tools.ietf.org/html/rfc5730#section-2.9.2.3"
 */
@Entity
@ExternalMessagingName("message")
public abstract class PollMessage extends ImmutableObject
    implements Buildable, TransferServerApproveEntity {


  public static final Converter<Key<PollMessage>, String> EXTERNAL_KEY_CONVERTER =
      new PollMessageExternalKeyConverter();

  /** Entity id. */
  @Id
  long id;

  @Parent
  @DoNotHydrate
  Key<HistoryEntry> parent;

  /** The registrar that this poll message will be delivered to. */
  @Index
  String clientId;

  /** The time when the poll message should be delivered. May be in the future. */
  @Index
  DateTime eventTime;

  /** Human readable message that will be returned with this poll message. */
  String msg;

  public Key<HistoryEntry> getParentKey() {
    return parent;
  }

  public Long getId() {
    return id;
  }

  public String getClientId() {
    return clientId;
  }

  public DateTime getEventTime() {
    return eventTime;
  }

  public String getMsg() {
    return msg;
  }

  public abstract ImmutableList<ResponseData> getResponseData();

  public abstract ImmutableList<ResponseExtension> getResponseExtensions();

  /** Override Buildable.asBuilder() to give this method stronger typing. */
  @Override
  public abstract Builder<?, ?> asBuilder();

  /** Builder for {@link PollMessage} because it is immutable. */
  @VisibleForTesting
  public abstract static class Builder<T extends PollMessage, B extends Builder<?, ?>>
      extends GenericBuilder<T, B> {

    protected Builder() {}

    protected Builder(T instance) {
      super(instance);
    }

    @VisibleForTesting
    public B setId(Long id) {
      getInstance().id = id;
      return thisCastToDerived();
    }

    public B setClientId(String clientId) {
      getInstance().clientId = clientId;
      return thisCastToDerived();
    }

    public B setEventTime(DateTime eventTime) {
      getInstance().eventTime = eventTime;
      return thisCastToDerived();
    }

    public B setMsg(String msg) {
      getInstance().msg = msg;
      return thisCastToDerived();
    }

    public B setParent(HistoryEntry parent) {
      getInstance().parent = Key.create(parent);
      return thisCastToDerived();
    }

    public B setParentKey(Key<HistoryEntry> parentKey) {
      getInstance().parent = parentKey;
      return thisCastToDerived();
    }

    @Override
    public T build() {
      T instance = getInstance();
      checkNotNull(instance.clientId);
      checkNotNull(instance.eventTime);
      checkNotNull(instance.parent);
      return super.build();
    }
  }

  /**
   * A one-time poll message.
   *
   * <p>One-time poll messages are deleted from Datastore once they have been delivered and ACKed.
   */
  @EntitySubclass(index = false)
  public static class OneTime extends PollMessage {

    // Response data. Objectify cannot persist a base class type, so we must have a separate field
    // to hold every possible derived type of ResponseData that we might store.
    List<ContactPendingActionNotificationResponse> contactPendingActionNotificationResponses;
    List<ContactTransferResponse> contactTransferResponses;
    List<DomainPendingActionNotificationResponse> domainPendingActionNotificationResponses;
    List<DomainTransferResponse> domainTransferResponses;

    // Extensions. Objectify cannot persist a base class type, so we must have a separate field
    // to hold every possible derived type of ResponseExtensions that we might store.
    //
    // Note that we cannot store a list of LaunchInfoResponseExtension objects since it contains a
    // list of embedded Mark objects, and embedded lists of lists are not allowed. This shouldn't
    // matter since there's no scenario where multiple launch info response extensions are ever
    // returned.
    LaunchInfoResponseExtension launchInfoResponseExtension;

    @Override
    public Builder asBuilder() {
      return new Builder(clone(this));
    }

    @Override
    public ImmutableList<ResponseData> getResponseData() {
      return new ImmutableList.Builder<ResponseData>()
          .addAll(nullToEmpty(contactPendingActionNotificationResponses))
          .addAll(nullToEmpty(contactTransferResponses))
          .addAll(nullToEmpty(domainPendingActionNotificationResponses))
          .addAll(nullToEmpty(domainTransferResponses))
          .build();
    }

    /**
     * Returns the class of the parent EppResource that this poll message is associated with,
     * either DomainBase or ContactResource.
     */
    public Class<? extends EppResource> getParentResourceClass() {
      if (!isNullOrEmpty(domainPendingActionNotificationResponses)
          || !isNullOrEmpty(domainTransferResponses)) {
        return DomainBase.class;
      } else if (!isNullOrEmpty(contactPendingActionNotificationResponses)
          || !isNullOrEmpty(contactTransferResponses)) {
        return ContactResource.class;
      } else {
        throw new IllegalStateException(String.format(
            "PollMessage.OneTime %s does not correspond with an EppResource of a known type",
            Key.create(this)));
      }
    }

    @Override
    public ImmutableList<ResponseExtension> getResponseExtensions() {
      return (launchInfoResponseExtension == null) ? ImmutableList.<ResponseExtension>of() :
          ImmutableList.<ResponseExtension>of(launchInfoResponseExtension);
    }

    /** A builder for {@link OneTime} since it is immutable. */
    @VisibleForTesting
    public static class Builder extends PollMessage.Builder<OneTime, Builder> {

      public Builder() {}

      private Builder(OneTime instance) {
        super(instance);
      }

      public Builder setResponseData(ImmutableList<? extends ResponseData> responseData) {
        FluentIterable<? extends ResponseData> iterable = FluentIterable.from(responseData);
        getInstance().contactPendingActionNotificationResponses = forceEmptyToNull(
            iterable.filter(ContactPendingActionNotificationResponse.class).toList());
        getInstance().contactTransferResponses = forceEmptyToNull(
            iterable.filter(ContactTransferResponse.class).toList());
        getInstance().domainPendingActionNotificationResponses = forceEmptyToNull(
            iterable.filter(DomainPendingActionNotificationResponse.class).toList());
        getInstance().domainTransferResponses = forceEmptyToNull(
            iterable.filter(DomainTransferResponse.class).toList());
        return this;
      }

      public Builder setResponseExtensions(
          ImmutableList<? extends ResponseExtension> responseExtensions) {
        getInstance().launchInfoResponseExtension = FluentIterable
            .from(responseExtensions)
            .filter(LaunchInfoResponseExtension.class)
            .first()
            .orNull();
        return this;
      }
    }
  }

  /**
   * An auto-renew poll message which recurs annually.
   *
   * <p>Auto-renew poll messages are not deleted until the registration of their parent domain has
   * been canceled, because there will always be a speculative renewal for next year until that
   * happens.
   */
  @EntitySubclass(index = false)
  public static class Autorenew extends PollMessage {

    /** The target id of the autorenew event. */
    String targetId;

    /** The autorenew recurs annually between {@link #eventTime} and this time. */
    @Index
    DateTime autorenewEndTime;

    public String getTargetId() {
      return targetId;
    }

    public DateTime getAutorenewEndTime() {
      return autorenewEndTime;
    }

    @Override
    public ImmutableList<ResponseData> getResponseData() {
      // Note that the event time is when the auto-renew occured, so the expiration time in the
      // response should be 1 year past that, since it denotes the new expiration time.
      return ImmutableList.<ResponseData>of(
          DomainRenewData.create(getTargetId(), getEventTime().plusYears(1)));
    }

    @Override
    public ImmutableList<ResponseExtension> getResponseExtensions() {
      return ImmutableList.of();
    }

    @Override
    public Builder asBuilder() {
      return new Builder(clone(this));
    }

    /** A builder for {@link Autorenew} since it is immutable. */
    @VisibleForTesting
    public static class Builder extends PollMessage.Builder<Autorenew, Builder> {

      public Builder() {}

      private Builder(Autorenew instance) {
        super(instance);
      }

      public Builder setTargetId(String targetId) {
        getInstance().targetId = targetId;
        return this;
      }

      public Builder setAutorenewEndTime(DateTime autorenewEndTime) {
        getInstance().autorenewEndTime = autorenewEndTime;
        return this;
      }

      @Override
      public Autorenew build() {
        Autorenew instance = getInstance();
        instance.autorenewEndTime =
            Optional.fromNullable(instance.autorenewEndTime).or(END_OF_TIME);
        return super.build();
      }
    }
  }
}
