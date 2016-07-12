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

package google.registry.model.domain;

import static google.registry.model.ofy.Ofy.RECOMMENDED_MEMCACHE_EXPIRATION;
import static google.registry.util.CollectionUtils.nullToEmptyImmutableCopy;

import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.EntitySubclass;
import google.registry.model.annotations.ExternalMessagingName;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchPhase;
import google.registry.model.eppcommon.Trid;
import google.registry.model.smd.EncodedSignedMark;
import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import org.joda.money.Money;
import org.joda.time.DateTime;

/** An application to create a domain. */
@XmlRootElement(name = "infData")
@XmlType(propOrder = {
    "fullyQualifiedDomainName",
    "repoId",
    "status",
    "marshalledRegistrant",
    "marshalledContacts",
    "marshalledNameservers",
    "currentSponsorClientId",
    "creationClientId",
    "creationTime",
    "lastEppUpdateClientId",
    "lastEppUpdateTime",
    "authInfo"})
@Cache(expirationSeconds = RECOMMENDED_MEMCACHE_EXPIRATION)
@EntitySubclass(index = true)
@ExternalMessagingName("application")
public class DomainApplication extends DomainBase {

  /**
   * The transaction id of the EPP command that created this application. This is saved off so that
   * we can generate the poll message communicating the application result once it is rejected or
   * allocated.
   *
   * <p>This field may be null for applications that were created before the field was added.
   */
  @XmlTransient
  Trid creationTrid;

  /**
   * The phase which this application is registered for. We store this only so we can return it back
   * to the user on info commands.
   */
  @XmlTransient
  LaunchPhase phase;

  /** The current status of this application. */
  @XmlTransient
  ApplicationStatus applicationStatus;

  /** The encoded signed marks which were asserted when this application was created. */
  @XmlTransient
  List<EncodedSignedMark> encodedSignedMarks;

  /** The amount paid at auction for the right to register the domain. Used only for reporting. */
  @XmlTransient
  Money auctionPrice;

  @Override
  public String getFullyQualifiedDomainName() {
    return fullyQualifiedDomainName;
  }

  public Trid getCreationTrid() {
    return creationTrid;
  }

  public LaunchPhase getPhase() {
    return phase;
  }

  public ApplicationStatus getApplicationStatus() {
    return applicationStatus;
  }

  public ImmutableList<EncodedSignedMark> getEncodedSignedMarks() {
    return nullToEmptyImmutableCopy(encodedSignedMarks);
  }

  public Money getAuctionPrice() {
    return auctionPrice;
  }

  /** Domain applications don't expose transfer time, so override this and mark it xml transient. */
  @XmlTransient
  @Override
  public final DateTime getLastTransferTime() {
    return super.getLastTransferTime();
  }

  /**
   * The application id is the repoId.
   */
  @Override
  public String getForeignKey() {
    return getRepoId();
  }

  @Override
  public DomainApplication cloneProjectedAtTime(DateTime now) {
    // Applications have no grace periods and can't be transferred, so there is nothing to project.
    return this;
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for constructing {@link DomainApplication}, since it is immutable. */
  public static class Builder extends DomainBase.Builder<DomainApplication, Builder> {

    public Builder() {}

    private Builder(DomainApplication instance) {
      super(instance);
    }

    public Builder setCreationTrid(Trid creationTrid) {
      getInstance().creationTrid = creationTrid;
      return this;
    }

    public Builder setPhase(LaunchPhase phase) {
      getInstance().phase = phase;
      return this;
    }

    public Builder setApplicationStatus(ApplicationStatus applicationStatus) {
      getInstance().applicationStatus = applicationStatus;
      return this;
    }

    public Builder setEncodedSignedMarks(ImmutableList<EncodedSignedMark> encodedSignedMarks) {
      getInstance().encodedSignedMarks = encodedSignedMarks;
      return this;
    }

    public Builder setAuctionPrice(Money auctionPrice) {
      getInstance().auctionPrice = auctionPrice;
      return this;
    }
  }
}
