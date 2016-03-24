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

package com.google.domain.registry.whois;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.tryFind;
import static com.google.domain.registry.util.CollectionUtils.isNullOrEmpty;
import static com.google.domain.registry.xml.UtcDateTimeAdapter.getFormattedString;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.contact.ContactPhoneNumber;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.contact.PostalInfo;
import com.google.domain.registry.model.domain.DesignatedContact;
import com.google.domain.registry.model.domain.DesignatedContact.Type;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.GracePeriod;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.translators.EnumToAttributeAdapter.EppEnum;
import com.google.domain.registry.util.FormattingLogger;

import org.joda.time.DateTime;

import java.util.Set;

import javax.annotation.Nullable;

/** Represents a WHOIS response to a domain query. */
final class DomainWhoisResponse extends WhoisResponseImpl {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** Prefix for status value URLs. */
  private static final String ICANN_STATUS_URL_PREFIX = "https://www.icann.org/epp#";

  /** Message required to be appended to all domain WHOIS responses. */
  private static final String ICANN_AWIP_INFO_MESSAGE =
      "For more information on Whois status codes, please visit https://icann.org/epp";

  /** Domain which was the target of this WHOIS command. */
  private final DomainResource domain;

  /** Creates new WHOIS domain response on the given domain. */
  DomainWhoisResponse(DomainResource domain, DateTime timestamp) {
    super(timestamp);
    this.domain = checkNotNull(domain, "domain");
  }

  @Override
  public String getPlainTextOutput(final boolean preferUnicode) {
    Registrar registrar = getRegistrar(domain.getCurrentSponsorClientId());
    return new DomainEmitter()
      .emitField("Domain Name",
          maybeFormatHostname(domain.getFullyQualifiedDomainName(), preferUnicode))
      .emitField("Registry Domain ID", domain.getRepoId())
      .emitField("Registrar WHOIS Server", registrar.getWhoisServer())
      .emitField("Registrar URL", registrar.getReferralUrl())
      .emitField("Updated Date", getFormattedString(domain.getLastEppUpdateTime()))
      .emitField("Creation Date", getFormattedString(domain.getCreationTime()))
      .emitField("Registrar Registration Expiration Date",
          getFormattedString(domain.getRegistrationExpirationTime()))
      .emitField("Registrar", registrar.getRegistrarName())
      .emitField("Sponsoring Registrar IANA ID",
          registrar.getIanaIdentifier() == null ? null : registrar.getIanaIdentifier().toString())
      .emitStatusValues(domain.getStatusValues(), domain.getGracePeriods())
      .emitContact("Registrant", domain.getRegistrant(), preferUnicode)
      .emitContact("Admin", getContactReference(Type.ADMIN), preferUnicode)
      .emitContact("Tech", getContactReference(Type.TECH), preferUnicode)
      .emitContact("Billing", getContactReference(Type.BILLING), preferUnicode)
      .emitSet(
          "Name Server",
          domain.loadNameservers(),
          new Function<HostResource, String>() {
            @Override
            public String apply(HostResource host) {
              return maybeFormatHostname(host.getFullyQualifiedHostName(), preferUnicode);
            }})
      .emitField("DNSSEC", isNullOrEmpty(domain.getDsData()) ? "unsigned" : "signedDelegation")
      .emitAwipMessage()
      .emitFooter(getTimestamp())
      .toString();
  }

  /** Returns the contact of the given type, or null if it does not exist. */
  @Nullable
  private ReferenceUnion<ContactResource> getContactReference(final Type type) {
    Optional<DesignatedContact> contactOfType = tryFind(domain.getContacts(),
        new Predicate<DesignatedContact>() {
          @Override
          public boolean apply(DesignatedContact d) {
            return d.getType() == type;
          }});
    return contactOfType.isPresent() ? contactOfType.get().getContactId() : null;
  }

  /** Output emitter with logic for domains. */
  class DomainEmitter extends Emitter<DomainEmitter> {
    DomainEmitter emitPhone(
        String contactType, String title, @Nullable ContactPhoneNumber phoneNumber) {
      return emitField(
              contactType, title, phoneNumber != null ? phoneNumber.getPhoneNumber() : null)
          .emitField(
              contactType, title, "Ext", phoneNumber != null ? phoneNumber.getExtension() : null);
    }

    /** Emit the contact entry of the given type. */
    DomainEmitter emitContact(
        String contactType,
        @Nullable ReferenceUnion<ContactResource> contact,
        boolean preferUnicode) {
      if (contact == null) {
        return this;
      }
      // If we refer to a contact that doesn't exist, that's a bug. It means referential integrity
      // has somehow been broken. We skip the rest of this contact, but log it to hopefully bring it
      // someone's attention.
      ContactResource contactResource = contact.getLinked().get();
      if (contactResource == null) {
        logger.severefmt("(BUG) Broken reference found from domain %s to contact %s",
            domain.getFullyQualifiedDomainName(), contact.getLinked());
        return this;
      }
      emitField("Registry " + contactType, "ID", contactResource.getContactId());
      PostalInfo postalInfo = chooseByUnicodePreference(
          preferUnicode,
          contactResource.getLocalizedPostalInfo(),
          contactResource.getInternationalizedPostalInfo());
      if (postalInfo != null) {
        emitField(contactType, "Name", postalInfo.getName());
        emitField(contactType, "Organization", postalInfo.getOrg());
        emitAddress(contactType, postalInfo.getAddress());
      }
      return emitPhone(contactType, "Phone", contactResource.getVoiceNumber())
          .emitPhone(contactType, "Fax", contactResource.getFaxNumber())
          .emitField(contactType, "Email", contactResource.getEmailAddress());
    }

    /** Emits status values and grace periods as a set, in the AWIP format. */
    DomainEmitter emitStatusValues(
        Set<StatusValue> statusValues, Set<GracePeriod> gracePeriods) {
      ImmutableSet.Builder<EppEnum> combinedStatuses = new ImmutableSet.Builder<>();
      combinedStatuses.addAll(statusValues);
      for (GracePeriod gracePeriod : gracePeriods) {
        combinedStatuses.add(gracePeriod.getType());
      }
      return emitSet(
          "Domain Status",
          combinedStatuses.build(),
          new Function<EppEnum, String>() {
            @Override
            public String apply(EppEnum status) {
              String xmlName = status.getXmlName();
              return String.format("%s %s%s", xmlName, ICANN_STATUS_URL_PREFIX, xmlName);
            }});
    }

    /** Emits the message that AWIP requires accompany all domain WHOIS responses. */
    DomainEmitter emitAwipMessage() {
      return emitRawLine(ICANN_AWIP_INFO_MESSAGE);
    }
  }
}
