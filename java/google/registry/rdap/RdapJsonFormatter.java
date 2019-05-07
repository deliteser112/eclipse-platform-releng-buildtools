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

package google.registry.rdap;

import static com.google.common.base.Predicates.not;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static google.registry.model.EppResourceUtils.isLinked;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.union;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Streams;
import com.google.common.net.InetAddresses;
import com.google.gson.JsonArray;
import com.googlecode.objectify.Key;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactPhoneNumber;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.PostalInfo;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DesignatedContact.Type;
import google.registry.model.domain.DomainBase;
import google.registry.model.eppcommon.Address;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarAddress;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.reporting.HistoryEntry;
import google.registry.rdap.RdapDataStructures.Event;
import google.registry.rdap.RdapDataStructures.EventAction;
import google.registry.rdap.RdapDataStructures.Link;
import google.registry.rdap.RdapDataStructures.Notice;
import google.registry.rdap.RdapDataStructures.Port43WhoisServer;
import google.registry.rdap.RdapDataStructures.PublicId;
import google.registry.rdap.RdapDataStructures.RdapStatus;
import google.registry.rdap.RdapObjectClasses.RdapDomain;
import google.registry.rdap.RdapObjectClasses.RdapEntity;
import google.registry.rdap.RdapObjectClasses.RdapNameserver;
import google.registry.rdap.RdapObjectClasses.Vcard;
import google.registry.rdap.RdapObjectClasses.VcardArray;
import google.registry.request.FullServletPath;
import google.registry.request.HttpException.InternalServerErrorException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeComparator;

/**
 * Helper class to create RDAP JSON objects for various registry entities and objects.
 *
 * <p>The JSON format specifies that entities should be supplied with links indicating how to fetch
 * them via RDAP, which requires the URL to the RDAP server. The linkBase parameter, passed to many
 * of the methods, is used as the first part of the link URL. For instance, if linkBase is
 * "http://rdap.org/dir/", the link URLs will look like "http://rdap.org/dir/domain/XXXX", etc.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7483">
 *        RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
 */
public class RdapJsonFormatter {


  @Inject @Config("rdapTos") ImmutableList<String> rdapTos;
  @Inject @Config("rdapTosStaticUrl") @Nullable String rdapTosStaticUrl;
  @Inject @FullServletPath String fullServletPath;
  @Inject RdapJsonFormatter() {}

  /**
   * What type of data to generate.
   *
   * <p>Summary data includes only information about the object itself, while full data includes
   * associated items (e.g. for domains, full data includes the hosts, contacts and history entries
   * connected with the domain).
   *
   * <p>Summary data is appropriate for search queries which return many results, to avoid load on
   * the system. According to the ICANN operational profile, a remark must be attached to the
   * returned object indicating that it includes only summary data.
   */
  public enum OutputDataType {
    FULL,
    SUMMARY
  }

  /** Map of EPP status values to the RDAP equivalents. */
  private static final ImmutableMap<StatusValue, RdapStatus> STATUS_TO_RDAP_STATUS_MAP =
      new ImmutableMap.Builder<StatusValue, RdapStatus>()
          // RdapStatus.ADD_PERIOD not defined in our system
          // RdapStatus.AUTO_RENEW_PERIOD not defined in our system
          .put(StatusValue.CLIENT_DELETE_PROHIBITED, RdapStatus.CLIENT_DELETE_PROHIBITED)
          .put(StatusValue.CLIENT_HOLD, RdapStatus.CLIENT_HOLD)
          .put(StatusValue.CLIENT_RENEW_PROHIBITED, RdapStatus.CLIENT_RENEW_PROHIBITED)
          .put(StatusValue.CLIENT_TRANSFER_PROHIBITED, RdapStatus.CLIENT_TRANSFER_PROHIBITED)
          .put(StatusValue.CLIENT_UPDATE_PROHIBITED, RdapStatus.CLIENT_UPDATE_PROHIBITED)
          .put(StatusValue.INACTIVE, RdapStatus.INACTIVE)
          .put(StatusValue.LINKED, RdapStatus.ASSOCIATED)
          .put(StatusValue.OK, RdapStatus.ACTIVE)
          .put(StatusValue.PENDING_CREATE, RdapStatus.PENDING_CREATE)
          .put(StatusValue.PENDING_DELETE, RdapStatus.PENDING_DELETE)
          // RdapStatus.PENDING_RENEW not defined in our system
          // RdapStatus.PENDING_RESTORE not defined in our system
          .put(StatusValue.PENDING_TRANSFER, RdapStatus.PENDING_TRANSFER)
          .put(StatusValue.PENDING_UPDATE, RdapStatus.PENDING_UPDATE)
          // RdapStatus.REDEMPTION_PERIOD not defined in our system
          // RdapStatus.RENEW_PERIOD not defined in our system
          .put(StatusValue.SERVER_DELETE_PROHIBITED, RdapStatus.SERVER_DELETE_PROHIBITED)
          .put(StatusValue.SERVER_HOLD, RdapStatus.SERVER_HOLD)
          .put(StatusValue.SERVER_RENEW_PROHIBITED, RdapStatus.SERVER_RENEW_PROHIBITED)
          .put(StatusValue.SERVER_TRANSFER_PROHIBITED, RdapStatus.SERVER_TRANSFER_PROHIBITED)
          .put(StatusValue.SERVER_UPDATE_PROHIBITED, RdapStatus.SERVER_UPDATE_PROHIBITED)
          // RdapStatus.TRANSFER_PERIOD not defined in our system
          .build();

  /**
   * Map of EPP event values to the RDAP equivalents.
   *
   * <p>Only has entries for the events we care about, according to the RDAP Response Profile
   * 15feb19.
   *
   * There are additional events that don't have HistoryEntry equivalent and are created
   * differently. They will be in different locations in the code. These values are: EXPIRATION,
   * LAST_CHANGED, LAST_UPDATE_OF_RDAP_DATABASE.
   */
  private static final ImmutableMap<HistoryEntry.Type, EventAction>
      HISTORY_ENTRY_TYPE_TO_RDAP_EVENT_ACTION_MAP =
          new ImmutableMap.Builder<HistoryEntry.Type, EventAction>()
              .put(HistoryEntry.Type.CONTACT_CREATE, EventAction.REGISTRATION)
              .put(HistoryEntry.Type.CONTACT_DELETE, EventAction.DELETION)
              .put(HistoryEntry.Type.CONTACT_TRANSFER_APPROVE, EventAction.TRANSFER)

              /** Not in the Response Profile. */
              .put(HistoryEntry.Type.DOMAIN_AUTORENEW, EventAction.REREGISTRATION)
              /** Section 2.3.1.1, obligatory. */
              .put(HistoryEntry.Type.DOMAIN_CREATE, EventAction.REGISTRATION)
              /** Not in the Response Profile. */
              .put(HistoryEntry.Type.DOMAIN_DELETE, EventAction.DELETION)
              /** Not in the Response Profile. */
              .put(HistoryEntry.Type.DOMAIN_RENEW, EventAction.REREGISTRATION)
              /** Not in the Response Profile. */
              .put(HistoryEntry.Type.DOMAIN_RESTORE, EventAction.REINSTANTIATION)
              /** Section 2.3.2.3, optional. */
              .put(HistoryEntry.Type.DOMAIN_TRANSFER_APPROVE, EventAction.TRANSFER)

              .put(HistoryEntry.Type.HOST_CREATE, EventAction.REGISTRATION)
              .put(HistoryEntry.Type.HOST_DELETE, EventAction.DELETION)
              .build();

  private static final ImmutableList<RdapStatus> STATUS_LIST_ACTIVE =
      ImmutableList.of(RdapStatus.ACTIVE);
  private static final ImmutableList<RdapStatus> STATUS_LIST_INACTIVE =
      ImmutableList.of(RdapStatus.INACTIVE);
  private static final ImmutableMap<String, ImmutableList<String>> PHONE_TYPE_VOICE =
      ImmutableMap.of("type", ImmutableList.of("voice"));
  private static final ImmutableMap<String, ImmutableList<String>> PHONE_TYPE_FAX =
      ImmutableMap.of("type", ImmutableList.of("fax"));

  /** Sets the ordering for hosts; just use the fully qualified host name. */
  private static final Ordering<HostResource> HOST_RESOURCE_ORDERING =
      Ordering.natural().onResultOf(HostResource::getFullyQualifiedHostName);

  /** Sets the ordering for designated contacts; order them in a fixed order by contact type. */
  private static final Ordering<DesignatedContact> DESIGNATED_CONTACT_ORDERING =
      Ordering.natural().onResultOf(DesignatedContact::getType);

  /** Creates the TOS notice that is added to every reply. */
  Notice createTosNotice() {
    String linkValue = makeRdapServletRelativeUrl("help", RdapHelpAction.TOS_PATH);
    Link.Builder linkBuilder = Link.builder()
        .setValue(linkValue);
    if (rdapTosStaticUrl == null) {
      linkBuilder.setRel("self").setHref(linkValue).setType("application/rdap+json");
    } else {
      URI htmlBaseURI = URI.create(fullServletPath);
      URI htmlUri = htmlBaseURI.resolve(rdapTosStaticUrl);
      linkBuilder.setRel("alternate").setHref(htmlUri.toString()).setType("text/html");
    }
    return Notice.builder()
        .setTitle("RDAP Terms of Service")
        .setDescription(rdapTos)
        .addLink(linkBuilder.build())
        .build();
  }

  /**
   * Creates a JSON object for a {@link DomainBase}.
   *
   * @param domainBase the domain resource object from which the JSON object should be created
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   * @param authorization the authorization level of the request; if not authorized for the
   *        registrar owning the domain, no contact information is included
   */
  RdapDomain makeRdapJsonForDomain(
      DomainBase domainBase,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType,
      RdapAuthorization authorization) {
    RdapDomain.Builder builder = RdapDomain.builder();
    // RDAP Response Profile 15feb19 section 2.2:
    // The domain handle MUST be the ROID
    builder.setHandle(domainBase.getRepoId());
    builder.setLdhName(domainBase.getFullyQualifiedDomainName());
    builder.statusBuilder().addAll(
        makeStatusValueList(
            domainBase.getStatusValues(),
            false, // isRedacted
            domainBase.getDeletionTime().isBefore(now)));
    builder.linksBuilder().add(
        makeSelfLink("domain", domainBase.getFullyQualifiedDomainName()));
    boolean displayContacts =
        authorization.isAuthorizedForClientId(domainBase.getCurrentSponsorClientId());
    // If we are outputting all data (not just summary data), also add information about hosts,
    // contacts and events (history entries). If we are outputting summary data, instead add a
    // remark indicating that fact.
    if (outputDataType == OutputDataType.SUMMARY) {
      builder.remarksBuilder().add(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      ImmutableList<Event> events = makeEvents(domainBase, now);
      builder.eventsBuilder().addAll(events);
      // Kick off the database loads of the nameservers that we will need, so it can load
      // asynchronously while we load and process the contacts.
      Map<Key<HostResource>, HostResource> loadedHosts =
          ofy().load().keys(domainBase.getNameservers());
      // Load the registrant and other contacts and add them to the data.
      if (!displayContacts) {
        builder
            .remarksBuilder()
            .add(RdapIcannStandardInformation.DOMAIN_CONTACTS_HIDDEN_DATA_REMARK);
      } else {
        Map<Key<ContactResource>, ContactResource> loadedContacts =
            ofy().load().keys(domainBase.getReferencedContacts());
        Streams.concat(
            domainBase.getContacts().stream(),
            Stream.of(
                DesignatedContact.create(Type.REGISTRANT, domainBase.getRegistrant())))
            .sorted(DESIGNATED_CONTACT_ORDERING)
            .map(
                designatedContact ->
                makeRdapJsonForContact(
                    loadedContacts.get(designatedContact.getContactKey()),
                    Optional.of(designatedContact.getType()),
                    null,
                    now,
                    outputDataType,
                    authorization))
            .forEach(builder.entitiesBuilder()::add);
      }
      builder
          .entitiesBuilder()
          .add(
              createInternalRegistrarEntity(
                  domainBase.getCurrentSponsorClientId(), whoisServer, now));
      // Add the nameservers to the data; the load was kicked off above for efficiency.
      for (HostResource hostResource
          : HOST_RESOURCE_ORDERING.immutableSortedCopy(loadedHosts.values())) {
        builder.nameserversBuilder().add(makeRdapJsonForHost(
            hostResource, null, now, outputDataType));
      }
    }
    if (whoisServer != null) {
      builder.setPort43(Port43WhoisServer.create(whoisServer));
    }
    return builder.build();
  }

  /**
   * Creates a JSON object for the desired registrar to an existing list of JSON objects.
   *
   * @param clientId the registrar client ID
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *     port43 field; if null, port43 is not added to the object
   * @param now the as-date
   */
  RdapEntity createInternalRegistrarEntity(
      String clientId,
      @Nullable String whoisServer,
      DateTime now) {
    Optional<Registrar> registrar = Registrar.loadByClientIdCached(clientId);
    if (!registrar.isPresent()) {
      throw new InternalServerErrorException(
          String.format("Coudn't find registrar '%s'", clientId));
    }
    // TODO(b/130150723): we need to display the ABUSE contact for registrar object inside of Domain
    // responses. Currently, we use summary for any "internal" registrar.
    return makeRdapJsonForRegistrar(
        registrar.get(),
        whoisServer,
        now,
        OutputDataType.SUMMARY);
  }

  /**
   * Creates a JSON object for a {@link HostResource}.
   *
   * @param hostResource the host resource object from which the JSON object should be created
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   */
  RdapNameserver makeRdapJsonForHost(
      HostResource hostResource,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType) {
    RdapNameserver.Builder builder = RdapNameserver.builder()
        .setHandle(hostResource.getRepoId())
        .setLdhName(hostResource.getFullyQualifiedHostName());

    ImmutableSet.Builder<StatusValue> statuses = new ImmutableSet.Builder<>();
    statuses.addAll(hostResource.getStatusValues());
    if (isLinked(Key.create(hostResource), now)) {
      statuses.add(StatusValue.LINKED);
    }
    if (hostResource.isSubordinate()
        && ofy().load().key(hostResource.getSuperordinateDomain()).now().cloneProjectedAtTime(now)
            .getStatusValues()
                .contains(StatusValue.PENDING_TRANSFER)) {
      statuses.add(StatusValue.PENDING_TRANSFER);
    }
    builder
        .statusBuilder()
        .addAll(
            makeStatusValueList(
                statuses.build(),
                false, // isRedacted
                hostResource.getDeletionTime().isBefore(now)));
    builder
        .linksBuilder()
        .add(makeSelfLink("nameserver", hostResource.getFullyQualifiedHostName()));

    // If we are outputting all data (not just summary data), also add events taken from the history
    // entries. If we are outputting summary data, instead add a remark indicating that fact.
    if (outputDataType == OutputDataType.SUMMARY) {
      builder.remarksBuilder().add(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      builder.eventsBuilder().addAll(makeEvents(hostResource, now));
    }

    // We MUST have the ip addresses: RDAP Response Profile 4.2.
    for (InetAddress inetAddress : hostResource.getInetAddresses()) {
      if (inetAddress instanceof Inet4Address) {
        builder.ipv4Builder().add(InetAddresses.toAddrString(inetAddress));
      } else if (inetAddress instanceof Inet6Address) {
        builder.ipv6Builder().add(InetAddresses.toAddrString(inetAddress));
      }
    }
    builder.entitiesBuilder().add(createInternalRegistrarEntity(
        hostResource.getPersistedCurrentSponsorClientId(),
        whoisServer,
        now));
    if (whoisServer != null) {
      builder.setPort43(Port43WhoisServer.create(whoisServer));
    }
    return builder.build();
  }

  /**
   * Creates a JSON object for a {@link ContactResource} and associated contact type.
   *
   * @param contactResource the contact resource object from which the JSON object should be created
   * @param contactType the contact type to map to an RDAP role; if absent, no role is listed
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   * @param authorization the authorization level of the request; personal contact data is only
   *        shown if the contact is owned by a registrar for which the request is authorized
   */
  RdapEntity makeRdapJsonForContact(
      ContactResource contactResource,
      Optional<DesignatedContact.Type> contactType,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType,
      RdapAuthorization authorization) {
    boolean isAuthorized =
        authorization.isAuthorizedForClientId(contactResource.getCurrentSponsorClientId());

    RdapEntity.Builder entityBuilder =
        RdapEntity.builder()
            .setHandle(contactResource.getRepoId());
    entityBuilder
        .statusBuilder()
        .addAll(
            makeStatusValueList(
                isLinked(Key.create(contactResource), now)
                    ? union(contactResource.getStatusValues(), StatusValue.LINKED)
                    : contactResource.getStatusValues(),
                !isAuthorized,
                contactResource.getDeletionTime().isBefore(now)));

    contactType.ifPresent(
        type -> entityBuilder.rolesBuilder().add(convertContactTypeToRdapRole(type)));
    entityBuilder.linksBuilder().add(makeSelfLink("entity", contactResource.getRepoId()));
    // If we are logged in as the owner of this contact, create the vCard.
    if (isAuthorized) {
      VcardArray.Builder vcardBuilder = VcardArray.builder();
      PostalInfo postalInfo = contactResource.getInternationalizedPostalInfo();
      if (postalInfo == null) {
        postalInfo = contactResource.getLocalizedPostalInfo();
      }
      if (postalInfo != null) {
        if (postalInfo.getName() != null) {
          vcardBuilder.add(Vcard.create("fn", "text", postalInfo.getName()));
        }
        if (postalInfo.getOrg() != null) {
          vcardBuilder.add(Vcard.create("org", "text", postalInfo.getOrg()));
        }
        addVCardAddressEntry(vcardBuilder, postalInfo.getAddress());
      }
      ContactPhoneNumber voicePhoneNumber = contactResource.getVoiceNumber();
      if (voicePhoneNumber != null) {
        vcardBuilder.add(makePhoneEntry(PHONE_TYPE_VOICE, makePhoneString(voicePhoneNumber)));
      }
      ContactPhoneNumber faxPhoneNumber = contactResource.getFaxNumber();
      if (faxPhoneNumber != null) {
        vcardBuilder.add(makePhoneEntry(PHONE_TYPE_FAX, makePhoneString(faxPhoneNumber)));
      }
      String emailAddress = contactResource.getEmailAddress();
      if (emailAddress != null) {
        vcardBuilder.add(Vcard.create("email", "text", emailAddress));
      }
      entityBuilder.setVcardArray(vcardBuilder.build());
    } else {
      entityBuilder
          .remarksBuilder()
          .add(RdapIcannStandardInformation.CONTACT_PERSONAL_DATA_HIDDEN_DATA_REMARK);
    }
    // If we are outputting all data (not just summary data), also add events taken from the history
    // entries. If we are outputting summary data, instead add a remark indicating that fact.
    if (outputDataType == OutputDataType.SUMMARY) {
      entityBuilder.remarksBuilder().add(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      entityBuilder.eventsBuilder().addAll(makeEvents(contactResource, now));
    }
    if (whoisServer != null) {
      entityBuilder.setPort43(Port43WhoisServer.create(whoisServer));
    }
    return entityBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link Registrar}.
   *
   * @param registrar the registrar object from which the JSON object should be created
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   */
  RdapEntity makeRdapJsonForRegistrar(
      Registrar registrar,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType) {
    RdapEntity.Builder builder = RdapEntity.builder();
    Long ianaIdentifier = registrar.getIanaIdentifier();
    // the handle MUST be the ianaIdentifier, RDAP Response Profile 2.4.2.
    builder.setHandle((ianaIdentifier == null) ? "(none)" : ianaIdentifier.toString());
    builder.statusBuilder().addAll(registrar.isLive() ? STATUS_LIST_ACTIVE : STATUS_LIST_INACTIVE);
    builder.rolesBuilder().add(RdapEntity.Role.REGISTRAR);
    if (ianaIdentifier != null) {
      builder.linksBuilder().add(makeSelfLink("entity", ianaIdentifier.toString()));
      // We MUST have a publicId with the ianaIdentifier, RDAP Response Profile 2.4.3, 4.3
      builder
          .publicIdsBuilder()
          .add(PublicId.create(PublicId.Type.IANA_REGISTRAR_ID, ianaIdentifier.toString()));
    }
    // Create the vCard.
    VcardArray.Builder vcardBuilder = VcardArray.builder();
    String registrarName = registrar.getRegistrarName();
    if (registrarName != null) {
      // A valid fn member MUST be present: RDAP Response Profile 2.4.1.
      vcardBuilder.add(Vcard.create("fn", "text", registrarName));
    }
    RegistrarAddress address = registrar.getInternationalizedAddress();
    if (address == null) {
      address = registrar.getLocalizedAddress();
    }
    addVCardAddressEntry(vcardBuilder, address);
    String voicePhoneNumber = registrar.getPhoneNumber();
    if (voicePhoneNumber != null) {
      vcardBuilder.add(makePhoneEntry(PHONE_TYPE_VOICE, "tel:" + voicePhoneNumber));
    }
    String faxPhoneNumber = registrar.getFaxNumber();
    if (faxPhoneNumber != null) {
      vcardBuilder.add(makePhoneEntry(PHONE_TYPE_FAX, "tel:" + faxPhoneNumber));
    }
    String emailAddress = registrar.getEmailAddress();
    if (emailAddress != null) {
      vcardBuilder.add(Vcard.create("email", "text", emailAddress));
    }
    builder.setVcardArray(vcardBuilder.build());
    // If we are outputting all data (not just summary data), also add registrar contacts. If we are
    // outputting summary data, instead add a remark indicating that fact.
    if (outputDataType == OutputDataType.SUMMARY) {
      builder.remarksBuilder().add(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      builder.eventsBuilder().addAll(makeEvents(registrar, now));
      // include the registrar contacts as subentities
      ImmutableList<RdapEntity> registrarContacts =
          registrar.getContacts().stream()
          .map(registrarContact -> makeRdapJsonForRegistrarContact(registrarContact, null))
          .filter(optional -> optional.isPresent())
          .map(optional -> optional.get())
          .collect(toImmutableList());
      // TODO(b/117242274): add a warning (severe?) log if registrar has no ABUSE contact, as having
      // one is required by the RDAP response profile
      builder.entitiesBuilder().addAll(registrarContacts);
    }
    if (whoisServer != null) {
      builder.setPort43(Port43WhoisServer.create(whoisServer));
    }
    return builder.build();
  }

  /**
   * Creates a JSON object for a {@link RegistrarContact}.
   *
   * <p>Returns empty if this contact shouldn't be visible (doesn't have a role).
   *
   * @param registrarContact the registrar contact for which the JSON object should be created
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *     port43 field; if null, port43 is not added to the object
   */
  static Optional<RdapEntity> makeRdapJsonForRegistrarContact(
      RegistrarContact registrarContact, @Nullable String whoisServer) {
    ImmutableList<RdapEntity.Role> roles = makeRdapRoleList(registrarContact);
    if (roles.isEmpty()) {
      return Optional.empty();
    }
    RdapEntity.Builder builder = RdapEntity.builder();
    builder.statusBuilder().addAll(STATUS_LIST_ACTIVE);
    builder.rolesBuilder().addAll(roles);
    // Create the vCard.
    VcardArray.Builder vcardBuilder = VcardArray.builder();
    String name = registrarContact.getName();
    if (name != null) {
      vcardBuilder.add(Vcard.create("fn", "text", name));
    }
    String voicePhoneNumber = registrarContact.getPhoneNumber();
    if (voicePhoneNumber != null) {
      vcardBuilder.add(makePhoneEntry(PHONE_TYPE_VOICE, "tel:" + voicePhoneNumber));
    }
    String faxPhoneNumber = registrarContact.getFaxNumber();
    if (faxPhoneNumber != null) {
      vcardBuilder.add(makePhoneEntry(PHONE_TYPE_FAX, "tel:" + faxPhoneNumber));
    }
    String emailAddress = registrarContact.getEmailAddress();
    if (emailAddress != null) {
      vcardBuilder.add(Vcard.create("email", "text", emailAddress));
    }
    builder.setVcardArray(vcardBuilder.build());
    if (whoisServer != null) {
      builder.setPort43(Port43WhoisServer.create(whoisServer));
    }
    return Optional.of(builder.build());
  }

  /** Converts a domain registry contact type into a role as defined by RFC 7483. */
  private static RdapEntity.Role convertContactTypeToRdapRole(DesignatedContact.Type contactType) {
    switch (contactType) {
      case REGISTRANT:
        return RdapEntity.Role.REGISTRANT;
      case TECH:
        return RdapEntity.Role.TECH;
      case BILLING:
        return RdapEntity.Role.BILLING;
      case ADMIN:
        return RdapEntity.Role.ADMIN;
      default:
        throw new AssertionError();
    }
  }

  /**
   * Creates the list of RDAP roles for a registrar contact, using the visibleInWhoisAs* flags.
   *
   * <p>Only contacts with a non-empty role list should be visible.
   *
   * <p>The RDAP response profile only mandates the "abuse" entity:
   *
   * <p>2.4.5. Abuse Contact (email, phone) - an RDAP server MUST include an *entity* with the
   * *abuse* role within the registrar *entity* which MUST include *tel* and *email*, and MAY
   * include other members
   */
  private static ImmutableList<RdapEntity.Role> makeRdapRoleList(
      RegistrarContact registrarContact) {
    ImmutableList.Builder<RdapEntity.Role> rolesBuilder = new ImmutableList.Builder<>();
    if (registrarContact.getVisibleInWhoisAsAdmin()) {
      rolesBuilder.add(RdapEntity.Role.ADMIN);
    }
    if (registrarContact.getVisibleInWhoisAsTech()) {
      rolesBuilder.add(RdapEntity.Role.TECH);
    }
    if (registrarContact.getVisibleInDomainWhoisAsAbuse()) {
      rolesBuilder.add(RdapEntity.Role.ABUSE);
    }
    return rolesBuilder.build();
  }

  /**
   * Creates an event list for a domain, host or contact resource.
   */
  private static ImmutableList<Event> makeEvents(EppResource resource, DateTime now) {
    HashMap<EventAction, HistoryEntry> lastEntryOfType = Maps.newHashMap();
    // Events (such as transfer, but also create) can appear multiple times. We only want the last
    // time they appeared.
    //
    // We can have multiple create historyEntries if a domain was deleted, and then someone new
    // bought it.
    //
    // From RDAP response profile
    // 2.3.2 The domain object in the RDAP response MAY contain the following events:
    // 2.3.2.3 An event of *eventAction* type *transfer*, with the last date and time that the
    // domain was transferred. The event of *eventAction* type *transfer* MUST be omitted if the
    // domain name has not been transferred since it was created.
    for (HistoryEntry historyEntry :
        ofy().load().type(HistoryEntry.class).ancestor(resource).order("modificationTime")) {
      EventAction rdapEventAction =
          HISTORY_ENTRY_TYPE_TO_RDAP_EVENT_ACTION_MAP.get(historyEntry.getType());
      // Only save the historyEntries if this is a type we care about.
      if (rdapEventAction == null) {
        continue;
      }
      lastEntryOfType.put(rdapEventAction, historyEntry);
    }
    ImmutableList.Builder<Event> eventsBuilder = new ImmutableList.Builder<>();
    // There are 2 possibly conflicting values for the creation time - either the
    // resource.getCreationTime, or the REGISTRATION event created from a HistoryEntry
    //
    // We favor the HistoryEntry if it exists, since we show that value as REGISTRATION time in the
    // reply, so the reply will be self-consistent.
    //
    // This is mostly an issue in the tests as in "reality" these two values should be the same.
    //
    DateTime creationTime =
        Optional.ofNullable(lastEntryOfType.get(EventAction.REGISTRATION))
            .map(historyEntry -> historyEntry.getModificationTime())
            .orElse(resource.getCreationTime());
    // TODO(b/129849684) remove this and use the events List defined above once we have Event
    // objects
    ImmutableList.Builder<DateTime> changeTimesBuilder = new ImmutableList.Builder<>();
    // The order of the elements is stable - it's the order in which the enum elements are defined
    // in EventAction
    for (EventAction rdapEventAction : EventAction.values()) {
      HistoryEntry historyEntry = lastEntryOfType.get(rdapEventAction);
      // Check if there was any entry of this type
      if (historyEntry == null) {
        continue;
      }
      DateTime modificationTime = historyEntry.getModificationTime();
      // We will ignore all events that happened before the "creation time", since these events are
      // from a "previous incarnation of the domain" (for a domain that was owned by someone,
      // deleted, and then bought by someone else)
      if (modificationTime.isBefore(creationTime)) {
        continue;
      }
      eventsBuilder.add(
          Event.builder()
              .setEventAction(rdapEventAction)
              .setEventActor(historyEntry.getClientId())
              .setEventDate(modificationTime)
              .build());
      changeTimesBuilder.add(modificationTime);
    }
    if (resource instanceof DomainBase) {
      DateTime expirationTime = ((DomainBase) resource).getRegistrationExpirationTime();
      if (expirationTime != null) {
        eventsBuilder.add(
            Event.builder()
                .setEventAction(EventAction.EXPIRATION)
                .setEventDate(expirationTime)
                .build());
        changeTimesBuilder.add(expirationTime);
      }
    }
    if (resource.getLastEppUpdateTime() != null) {
      changeTimesBuilder.add(resource.getLastEppUpdateTime());
    }
    // The last change time might not be the lastEppUpdateTime, since some changes happen without
    // any EPP update (for example, by the passage of time).
    DateTime lastChangeTime =
        changeTimesBuilder.build().stream()
        .filter(changeTime -> changeTime.isBefore(now))
        .max(DateTimeComparator.getInstance())
        .orElse(null);
    if (lastChangeTime != null && lastChangeTime.isAfter(creationTime)) {
      eventsBuilder.add(makeEvent(EventAction.LAST_CHANGED, null, lastChangeTime));
    }
    eventsBuilder.add(makeEvent(EventAction.LAST_UPDATE_OF_RDAP_DATABASE, null, now));
    // TODO(b/129849684): sort events by their time once we return a list of Events instead of JSON
    // objects.
    return eventsBuilder.build();
  }

  /**
   * Creates an event list for a {@link Registrar}.
   */
  private static ImmutableList<Event> makeEvents(Registrar registrar, DateTime now) {
    ImmutableList.Builder<Event> eventsBuilder = new ImmutableList.Builder<>();
    Long ianaIdentifier = registrar.getIanaIdentifier();
    eventsBuilder.add(makeEvent(
        EventAction.REGISTRATION,
        (ianaIdentifier == null) ? "(none)" : ianaIdentifier.toString(),
        registrar.getCreationTime()));
    if ((registrar.getLastUpdateTime() != null)
        && registrar.getLastUpdateTime().isAfter(registrar.getCreationTime())) {
      eventsBuilder.add(makeEvent(
          EventAction.LAST_CHANGED, null, registrar.getLastUpdateTime()));
    }
    eventsBuilder.add(makeEvent(EventAction.LAST_UPDATE_OF_RDAP_DATABASE, null, now));
    return eventsBuilder.build();
  }

  /**
   * Creates an RDAP event object as defined by RFC 7483.
   */
  private static Event makeEvent(
      EventAction eventAction,
      @Nullable String eventActor,
      DateTime eventDate) {
    Event.Builder builder = Event.builder()
        .setEventAction(eventAction)
        .setEventDate(eventDate);
    if (eventActor != null) {
      builder.setEventActor(eventActor);
    }
    return builder.build();
  }

  /**
   * Creates a vCard address entry: array of strings specifying the components of the address.
   *
   * @see <a href="https://tools.ietf.org/html/rfc7095">
   *        RFC 7095: jCard: The JSON Format for vCard</a>
   */
  private static void addVCardAddressEntry(VcardArray.Builder vcardArrayBuilder, Address address) {
    if (address == null) {
      return;
    }
    JsonArray addressArray = new JsonArray();
    addressArray.add(""); // PO box
    addressArray.add(""); // extended address

    // The vCard spec allows several different ways to handle multiline street addresses. Per
    // Gustavo Lozano of ICANN, the one we should use is an embedded array of street address lines
    // if there is more than one line:
    //
    //   RFC7095 provides two examples of structured addresses, and one of the examples shows a
    //   street JSON element that contains several data elements. The example showing (see below)
    //   several data elements is the expected output when two or more <contact:street> elements
    //   exists in the contact object.
    //
    //   ["adr", {}, "text",
    //    [
    //    "", "",
    //    ["My Street", "Left Side", "Second Shack"],
    //    "Hometown", "PA", "18252", "U.S.A."
    //    ]
    //   ]
    //
    // Gustavo further clarified that the embedded array should only be used if there is more than
    // one line:
    //
    //   My reading of RFC 7095 is that if only one element is known, it must be a string. If
    //   multiple elements are known (e.g. two or three street elements were provided in the case of
    //   the EPP contact data model), an array must be used.
    //
    //   I don’t think that one street address line nested in a single-element array is valid
    //   according to RFC 7095.
    ImmutableList<String> street = address.getStreet();
    if (street.isEmpty()) {
      addressArray.add("");
    } else if (street.size() == 1) {
      addressArray.add(street.get(0));
    } else {
      JsonArray streetArray = new JsonArray();
      street.forEach(streetArray::add);
      addressArray.add(streetArray);
    }
    addressArray.add(nullToEmpty(address.getCity()));
    addressArray.add(nullToEmpty(address.getState()));
    addressArray.add(nullToEmpty(address.getZip()));
    addressArray.add(
        new Locale("en", address.getCountryCode()).getDisplayCountry(new Locale("en")));
    vcardArrayBuilder.add(Vcard.create(
        "adr",
        "text",
        addressArray));
  }

  /** Creates a vCard phone number entry. */
  private static Vcard makePhoneEntry(
      ImmutableMap<String, ImmutableList<String>> type, String phoneNumber) {

    return Vcard.create("tel", type, "uri", phoneNumber);
  }

  /** Creates a phone string in URI format, as per the vCard spec. */
  private static String makePhoneString(ContactPhoneNumber phoneNumber) {
    String phoneString = String.format("tel:%s", phoneNumber.getPhoneNumber());
    if (phoneNumber.getExtension() != null) {
      phoneString = phoneString + ";ext=" + phoneNumber.getExtension();
    }
    return phoneString;
  }

  /**
   * Creates a string array of status values.
   *
   * <p>The spec indicates that OK should be listed as "active". We use the "inactive" status to
   * indicate deleted objects, and as directed by the profile, the "removed" status to indicate
   * redacted objects.
   */
  private static ImmutableList<RdapStatus> makeStatusValueList(
      ImmutableSet<StatusValue> statusValues, boolean isRedacted, boolean isDeleted) {
    Stream<RdapStatus> stream =
        statusValues
            .stream()
            .map(status -> STATUS_TO_RDAP_STATUS_MAP.getOrDefault(status, RdapStatus.OBSCURED));
    if (isRedacted) {
      stream = Streams.concat(stream, Stream.of(RdapStatus.REMOVED));
    }
    if (isDeleted) {
      stream =
          Streams.concat(
              stream.filter(not(RdapStatus.ACTIVE::equals)),
              Stream.of(RdapStatus.INACTIVE));
    }
    return stream
        .sorted(Ordering.natural().onResultOf(RdapStatus::getDisplayName))
        .collect(toImmutableList());
  }

  /**
   * Create a link relative to the RDAP server endpoint.
   */
  String makeRdapServletRelativeUrl(String part, String... moreParts) {
    String relativePath = Paths.get(part, moreParts).toString();
    if (fullServletPath.endsWith("/")) {
      return fullServletPath + relativePath;
    }
    return fullServletPath + "/" + relativePath;
  }

  /**
   * Creates a self link as directed by the spec.
   *
   * @see <a href="https://tools.ietf.org/html/rfc7483">RFC 7483: JSON Responses for the
   *     Registration Data Access Protocol (RDAP)</a>
   */
  private Link makeSelfLink(String type, String name) {
    String url = makeRdapServletRelativeUrl(type, name);
    return Link.builder()
        .setValue(url)
        .setRel("self")
        .setHref(url)
        .setType("application/rdap+json")
        .build();
  }
}
