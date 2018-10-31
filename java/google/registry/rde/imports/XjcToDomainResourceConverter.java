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

package google.registry.rde.imports;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static google.registry.util.DomainNameUtils.canonicalizeDomainName;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Key;
import google.registry.model.billing.BillingEvent;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DomainAuthInfo;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.AuthInfo.PasswordAuth;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registries;
import google.registry.model.registry.Registry;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferStatus;
import google.registry.util.StringGenerator;
import google.registry.util.XmlToEnumMapper;
import google.registry.xjc.domain.XjcDomainContactType;
import google.registry.xjc.domain.XjcDomainNsType;
import google.registry.xjc.domain.XjcDomainStatusType;
import google.registry.xjc.rdedomain.XjcRdeDomain;
import google.registry.xjc.rdedomain.XjcRdeDomainElement;
import google.registry.xjc.rdedomain.XjcRdeDomainTransferDataType;
import google.registry.xjc.rgp.XjcRgpStatusType;
import google.registry.xjc.secdns.XjcSecdnsDsDataType;
import java.util.function.Function;
import org.joda.time.DateTime;

/** Utility class that converts an {@link XjcRdeDomainElement} into a {@link DomainResource}. */
final class XjcToDomainResourceConverter extends XjcToEppResourceConverter {

  private static final XmlToEnumMapper<TransferStatus> TRANSFER_STATUS_MAPPER =
      XmlToEnumMapper.create(TransferStatus.values());

  private static final Function<String, Key<HostResource>> HOST_OBJ_CONVERTER =
      fullyQualifiedHostName -> {
        // host names are always lower case
        fullyQualifiedHostName = canonicalizeDomainName(fullyQualifiedHostName);
        Key<HostResource> key =
            ForeignKeyIndex.loadAndGetKey(
                HostResource.class, fullyQualifiedHostName, DateTime.now(UTC));
        checkState(
            key != null,
            String.format("HostResource not found with name '%s'", fullyQualifiedHostName));
        return key;
      };

  /** Converts {@link XjcRgpStatusType} to {@link GracePeriod} */
  private static class GracePeriodConverter implements Function<XjcRgpStatusType, GracePeriod> {

    private final XjcRdeDomain domain;
    private final Key<BillingEvent.Recurring> autoRenewBillingEvent;
    private final Registry tld;

    GracePeriodConverter(XjcRdeDomain domain, Key<BillingEvent.Recurring> autoRenewBillingEvent) {
      this.domain = domain;
      this.autoRenewBillingEvent = autoRenewBillingEvent;
      this.tld =
          Registry.get(
              Registries.findTldForNameOrThrow(InternetDomainName.from(domain.getName()))
                  .toString());
    }

    @Override
    public GracePeriod apply(XjcRgpStatusType gracePeriodStatus) {
      switch (gracePeriodStatus.getS()) {
        case ADD_PERIOD:
          return GracePeriod.createWithoutBillingEvent(
              GracePeriodStatus.ADD,
              domain.getCrDate().plus(this.tld.getAddGracePeriodLength()),
              domain.getCrRr().getValue());
        case AUTO_RENEW_PERIOD:
          return GracePeriod.createForRecurring(
              GracePeriodStatus.AUTO_RENEW,
              domain.getUpDate().plus(this.tld.getAutoRenewGracePeriodLength()),
              domain.getClID(),
              autoRenewBillingEvent);
        case PENDING_DELETE:
          return GracePeriod.createWithoutBillingEvent(
              GracePeriodStatus.PENDING_DELETE,
              domain.getUpDate().plus(this.tld.getPendingDeleteLength()),
              domain.getClID());
        case REDEMPTION_PERIOD:
          return GracePeriod.createWithoutBillingEvent(
              GracePeriodStatus.REDEMPTION,
              domain.getUpDate().plus(this.tld.getRedemptionGracePeriodLength()),
              domain.getClID());
        case RENEW_PERIOD:
          return GracePeriod.createWithoutBillingEvent(
              GracePeriodStatus.RENEW,
              domain.getUpDate().plus(this.tld.getRenewGracePeriodLength()),
              domain.getClID());
        case TRANSFER_PERIOD:
          return GracePeriod.createWithoutBillingEvent(
              GracePeriodStatus.TRANSFER,
              domain.getUpDate().plus(this.tld.getTransferGracePeriodLength()),
              domain.getTrnData().getReRr().getValue());
        default:
          throw new IllegalArgumentException(
              "Unsupported grace period status: " + gracePeriodStatus.getS());
      }
    }
  }

  /** Converts {@link XjcRdeDomain} to {@link DomainResource}. */
  static DomainResource convertDomain(
      XjcRdeDomain domain,
      BillingEvent.Recurring autoRenewBillingEvent,
      PollMessage.Autorenew autoRenewPollMessage,
      StringGenerator stringGenerator) {
    GracePeriodConverter gracePeriodConverter =
        new GracePeriodConverter(domain, Key.create(autoRenewBillingEvent));
    DomainResource.Builder builder =
        new DomainResource.Builder()
            .setFullyQualifiedDomainName(canonicalizeDomainName(domain.getName()))
            .setRepoId(domain.getRoid())
            .setIdnTableName(domain.getIdnTableId())
            .setPersistedCurrentSponsorClientId(domain.getClID())
            .setCreationClientId(domain.getCrRr().getValue())
            .setCreationTime(domain.getCrDate())
            .setAutorenewPollMessage(Key.create(autoRenewPollMessage))
            .setAutorenewBillingEvent(Key.create(autoRenewBillingEvent))
            .setRegistrationExpirationTime(domain.getExDate())
            .setLastEppUpdateTime(domain.getUpDate())
            .setLastEppUpdateClientId(domain.getUpRr() == null ? null : domain.getUpRr().getValue())
            .setLastTransferTime(domain.getTrDate())
            .setStatusValues(
                domain
                    .getStatuses()
                    .stream()
                    .map(XjcToDomainResourceConverter::convertStatusType)
                    .collect(toImmutableSet()))
            .setNameservers(convertNameservers(domain.getNs()))
            .setGracePeriods(
                domain
                    .getRgpStatuses()
                    .stream()
                    .map(gracePeriodConverter)
                    .collect(toImmutableSet()))
            .setContacts(
                domain
                    .getContacts()
                    .stream()
                    .map(XjcToDomainResourceConverter::convertContactType)
                    .collect(toImmutableSet()))
            .setDsData(
                domain.getSecDNS() == null
                    ? ImmutableSet.of()
                    : domain
                        .getSecDNS()
                        .getDsDatas()
                        .stream()
                        .map(XjcToDomainResourceConverter::convertSecdnsDsDataType)
                        .collect(toImmutableSet()))
            .setTransferData(convertDomainTransferData(domain.getTrnData()))
            // authInfo pw must be a token between 6 and 16 characters in length
            // generate a token of 16 characters as the default authInfo pw
            .setAuthInfo(
                DomainAuthInfo.create(
                    PasswordAuth.create(stringGenerator.createString(16), domain.getRoid())));
    checkArgumentNotNull(
        domain.getRegistrant(), "Registrant is missing for domain '%s'", domain.getName());
    builder = builder.setRegistrant(convertRegistrant(domain.getRegistrant()));
    return builder.build();
  }

  /** Returns {@link Key} for registrant from foreign key */
  private static Key<ContactResource> convertRegistrant(String contactId) {
    Key<ContactResource> key =
        ForeignKeyIndex.loadAndGetKey(ContactResource.class, contactId, DateTime.now(UTC));
    checkState(key != null, "Registrant not found: '%s'", contactId);
    return key;
  }

  /** Converts {@link XjcDomainNsType} to <code>ImmutableSet<Key<HostResource>></code>. */
  private static ImmutableSet<Key<HostResource>> convertNameservers(XjcDomainNsType ns) {
    // If no hosts are specified, return an empty set
    if (ns == null || (ns.getHostAttrs() == null && ns.getHostObjs() == null)) {
      return ImmutableSet.of();
    }
    // Domain linked hosts must be specified by host object, not host attributes.
    checkArgument(
        ns.getHostAttrs() == null || ns.getHostAttrs().isEmpty(),
        "Host attributes are not yet supported");
    return ns.getHostObjs().stream().map(HOST_OBJ_CONVERTER).collect(toImmutableSet());
  }

  /** Converts {@link XjcRdeDomainTransferDataType} to {@link TransferData}. */
  private static TransferData convertDomainTransferData(XjcRdeDomainTransferDataType data) {
    if (data == null) {
      return TransferData.EMPTY;
    }
    return new TransferData.Builder()
        .setTransferStatus(TRANSFER_STATUS_MAPPER.xmlToEnum(data.getTrStatus().value()))
        .setGainingClientId(data.getReRr().getValue())
        .setLosingClientId(data.getAcRr().getValue())
        .setTransferRequestTime(data.getReDate())
        .setPendingTransferExpirationTime(data.getAcDate())
        .setTransferredRegistrationExpirationTime(data.getExDate())
        .build();
  }

  /** Converts {@link XjcDomainStatusType} to {@link StatusValue}. */
  private static StatusValue convertStatusType(XjcDomainStatusType type) {
    return StatusValue.fromXmlName(type.getS().value());
  }

  /** Converts {@link XjcSecdnsDsDataType} to {@link DelegationSignerData}. */
  private static DelegationSignerData convertSecdnsDsDataType(XjcSecdnsDsDataType secdns) {
    return DelegationSignerData.create(
        secdns.getKeyTag(), secdns.getAlg(), secdns.getDigestType(), secdns.getDigest());
  }

  /** Converts {@link XjcDomainContactType} to {@link DesignatedContact}. */
  private static DesignatedContact convertContactType(XjcDomainContactType contact) {
    String contactId = contact.getValue();
    Key<ContactResource> key =
        ForeignKeyIndex.loadAndGetKey(ContactResource.class, contactId, DateTime.now(UTC));
    checkState(key != null, "Contact not found: '%s'", contactId);
    DesignatedContact.Type type =
        DesignatedContact.Type.valueOf(Ascii.toUpperCase(contact.getType().toString()));
    return DesignatedContact.create(type, key);
  }

  private XjcToDomainResourceConverter() {}
}
