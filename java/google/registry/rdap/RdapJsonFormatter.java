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
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static google.registry.model.EppResourceUtils.isLinked;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.union;
import static google.registry.util.DomainNameUtils.ACE_PREFIX;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Streams;
import com.google.common.net.InetAddresses;
import com.googlecode.objectify.Key;
import google.registry.config.RdapNoticeDescriptor;
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
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.util.Idn;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
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
@Singleton
public class RdapJsonFormatter {

  @Inject @Config("rdapTosPath") String rdapTosPath;
  @Inject @Config("rdapHelpMap") ImmutableMap<String, RdapNoticeDescriptor> rdapHelpMap;
  @Inject RdapJsonFormatter() {}

  /**
   * What type of data to generate. Summary data includes only information about the object itself,
   * while full data includes associated items (e.g. for domains, full data includes the hosts,
   * contacts and history entries connected with the domain). Summary data is appropriate for search
   * queries which return many results, to avoid load on the system. According to the ICANN
   * operational profile, a remark must be attached to the returned object indicating that it
   * includes only summary data.
   */
  public enum OutputDataType {
    FULL,
    SUMMARY
  }

  /**
   * Indication of what type of boilerplate notices are required for the RDAP JSON messages. The
   * ICANN RDAP Profile specifies that, for instance, domain name responses should include a remark
   * about domain status codes. So we need to know when to include such boilerplate. On the other
   * hand, remarks are not allowed except in domain, nameserver and entity objects, so we need to
   * suppress them for other types of responses (e.g. help).
   */
  public enum BoilerplateType {
    DOMAIN,
    NAMESERVER,
    ENTITY,
    OTHER
  }

  private static final String RDAP_CONFORMANCE_LEVEL = "icann_rdap_response_profile_0";
  private static final String VCARD_VERSION_NUMBER = "4.0";
  static final String NOTICES = "notices";
  private static final String REMARKS = "remarks";

  private enum RdapStatus {

    // Status values specified in RFC 7483 § 10.2.2.
    VALIDATED("validated"),
    RENEW_PROHIBITED("renew prohibited"),
    UPDATE_PROHIBITED("update prohibited"),
    TRANSFER_PROHIBITED("transfer prohibited"),
    DELETE_PROHIBITED("delete prohibited"),
    PROXY("proxy"),
    PRIVATE("private"),
    REMOVED("removed"),
    OBSCURED("obscured"),
    ASSOCIATED("associated"),
    ACTIVE("active"),
    INACTIVE("inactive"),
    LOCKED("locked"),
    PENDING_CREATE("pending create"),
    PENDING_RENEW("pending renew"),
    PENDING_TRANSFER("pending transfer"),
    PENDING_UPDATE("pending update"),
    PENDING_DELETE("pending delete"),

    // Additional status values defined in
    // https://tools.ietf.org/html/draft-ietf-regext-epp-rdap-status-mapping-01.
    ADD_PERIOD("add period"),
    AUTO_RENEW_PERIOD("auto renew period"),
    CLIENT_DELETE_PROHIBITED("client delete prohibited"),
    CLIENT_HOLD("client hold"),
    CLIENT_RENEW_PROHIBITED("client renew prohibited"),
    CLIENT_TRANSFER_PROHIBITED("client transfer prohibited"),
    CLIENT_UPDATE_PROHIBITED("client update prohibited"),
    PENDING_RESTORE("pending restore"),
    REDEMPTION_PERIOD("redemption period"),
    RENEW_PERIOD("renew period"),
    SERVER_DELETE_PROHIBITED("server deleted prohibited"),
    SERVER_RENEW_PROHIBITED("server renew prohibited"),
    SERVER_TRANSFER_PROHIBITED("server transfer prohibited"),
    SERVER_UPDATE_PROHIBITED("server update prohibited"),
    SERVER_HOLD("server hold"),
    TRANSFER_PERIOD("transfer period");

    /** Value as it appears in RDAP messages. */
    private final String rfc7483String;

    RdapStatus(String rfc7483String) {
      this.rfc7483String = rfc7483String;
    }

    public String getDisplayName() {
      return rfc7483String;
    }
  }

  /** Map of EPP status values to the RDAP equivalents. */
  private static final ImmutableMap<StatusValue, RdapStatus> statusToRdapStatusMap =
      Maps.immutableEnumMap(
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
              .build());

  /** Role values specified in RFC 7483 § 10.2.4. */
  private enum RdapEntityRole {
    REGISTRANT("registrant"),
    TECH("technical"),
    ADMIN("administrative"),
    ABUSE("abuse"),
    BILLING("billing"),
    REGISTRAR("registrar"),
    RESELLER("reseller"),
    SPONSOR("sponsor"),
    PROXY("proxy"),
    NOTIFICATIONS("notifications"),
    NOC("noc");

    /** Value as it appears in RDAP messages. */
    final String rfc7483String;

    RdapEntityRole(String rfc7483String) {
      this.rfc7483String = rfc7483String;
    }
  }

  /** Status values specified in RFC 7483 § 10.2.2. */
  private enum RdapEventAction {
    REGISTRATION("registration"),
    REREGISTRATION("reregistration"),
    LAST_CHANGED("last changed"),
    EXPIRATION("expiration"),
    DELETION("deletion"),
    REINSTANTIATION("reinstantiation"),
    TRANSFER("transfer"),
    LOCKED("locked"),
    UNLOCKED("unlocked"),
    LAST_UPDATE_OF_RDAP_DATABASE("last update of RDAP database");

    /** Value as it appears in RDAP messages. */
    private final String rfc7483String;

    RdapEventAction(String rfc7483String) {
      this.rfc7483String = rfc7483String;
    }

    public String getDisplayName() {
      return rfc7483String;
    }
  }

  /** Map of EPP event values to the RDAP equivalents. */
  private static final ImmutableMap<HistoryEntry.Type, RdapEventAction>
      historyEntryTypeToRdapEventActionMap =
          Maps.immutableEnumMap(
              new ImmutableMap.Builder<HistoryEntry.Type, RdapEventAction>()
                  .put(HistoryEntry.Type.CONTACT_CREATE, RdapEventAction.REGISTRATION)
                  .put(HistoryEntry.Type.CONTACT_DELETE, RdapEventAction.DELETION)
                  .put(HistoryEntry.Type.CONTACT_TRANSFER_APPROVE, RdapEventAction.TRANSFER)
                  .put(HistoryEntry.Type.DOMAIN_AUTORENEW, RdapEventAction.REREGISTRATION)
                  .put(HistoryEntry.Type.DOMAIN_CREATE, RdapEventAction.REGISTRATION)
                  .put(HistoryEntry.Type.DOMAIN_DELETE, RdapEventAction.DELETION)
                  .put(HistoryEntry.Type.DOMAIN_RENEW, RdapEventAction.REREGISTRATION)
                  .put(HistoryEntry.Type.DOMAIN_RESTORE, RdapEventAction.REINSTANTIATION)
                  .put(HistoryEntry.Type.DOMAIN_TRANSFER_APPROVE, RdapEventAction.TRANSFER)
                  .put(HistoryEntry.Type.HOST_CREATE, RdapEventAction.REGISTRATION)
                  .put(HistoryEntry.Type.HOST_DELETE, RdapEventAction.DELETION)
                  .build());

  private static final ImmutableList<String> CONFORMANCE_LIST =
      ImmutableList.of(RDAP_CONFORMANCE_LEVEL);

  private static final ImmutableList<String> STATUS_LIST_ACTIVE =
      ImmutableList.of(RdapStatus.ACTIVE.rfc7483String);
  private static final ImmutableList<String> STATUS_LIST_INACTIVE =
      ImmutableList.of(RdapStatus.INACTIVE.rfc7483String);
  private static final ImmutableMap<String, ImmutableList<String>> PHONE_TYPE_VOICE =
      ImmutableMap.of("type", ImmutableList.of("voice"));
  private static final ImmutableMap<String, ImmutableList<String>> PHONE_TYPE_FAX =
      ImmutableMap.of("type", ImmutableList.of("fax"));
  private static final ImmutableList<?> VCARD_ENTRY_VERSION =
      ImmutableList.of("version", ImmutableMap.of(), "text", VCARD_VERSION_NUMBER);

  /** Sets the ordering for hosts; just use the fully qualified host name. */
  private static final Ordering<HostResource> HOST_RESOURCE_ORDERING =
      Ordering.natural().onResultOf(HostResource::getFullyQualifiedHostName);

  /** Sets the ordering for designated contacts; order them in a fixed order by contact type. */
  private static final Ordering<DesignatedContact> DESIGNATED_CONTACT_ORDERING =
      Ordering.natural().onResultOf(DesignatedContact::getType);

  ImmutableMap<String, Object> getJsonTosNotice(String rdapLinkBase) {
    return getJsonHelpNotice(rdapTosPath, rdapLinkBase);
  }

  ImmutableMap<String, Object> getJsonHelpNotice(
      String pathSearchString, String rdapLinkBase) {
    if (pathSearchString.isEmpty()) {
      pathSearchString = "/";
    }
    if (!rdapHelpMap.containsKey(pathSearchString)) {
      throw new NotFoundException("no help found for " + pathSearchString);
    }
    try {
      return RdapJsonFormatter.makeRdapJsonNotice(rdapHelpMap.get(pathSearchString), rdapLinkBase);
    } catch (Exception e) {
      throw new InternalServerErrorException(
          String.format("Error reading RDAP help file: %s", pathSearchString), e);
    }
  }

  /**
   * Adds the required top-level boilerplate. RFC 7483 specifies that the top-level object should
   * include an entry indicating the conformance level. The ICANN RDAP Profile document (dated 3
   * December 2015) mandates several additional entries, in sections 1.4.4, 1.4.10, 1.5.18 and
   * 1.5.20. Note that this method will only work if there are no object-specific remarks already in
   * the JSON object being built. If there are, the boilerplate must be merged in.
   *
   * @param jsonBuilder a builder for a JSON map object
   * @param boilerplateType type of boilerplate to be added; the ICANN RDAP Profile document
   *        mandates extra boilerplate for domain objects
   * @param notices a list of notices to be inserted before the boilerplate notices. If the TOS
   *        notice is in this list, the method avoids adding a second copy.
   * @param remarks a list of remarks to be inserted.
   * @param rdapLinkBase the base for link URLs
   */
  void addTopLevelEntries(
      ImmutableMap.Builder<String, Object> jsonBuilder,
      BoilerplateType boilerplateType,
      List<ImmutableMap<String, Object>> notices,
      List<ImmutableMap<String, Object>> remarks,
      String rdapLinkBase) {
    jsonBuilder.put("rdapConformance", CONFORMANCE_LIST);
    ImmutableList.Builder<ImmutableMap<String, Object>> noticesBuilder =
        new ImmutableList.Builder<>();
    ImmutableMap<String, Object> tosNotice = getJsonTosNotice(rdapLinkBase);
    boolean tosNoticeFound = false;
    if (!notices.isEmpty()) {
      noticesBuilder.addAll(notices);
      for (ImmutableMap<String, Object> notice : notices) {
        if (notice.equals(tosNotice)) {
          tosNoticeFound = true;
          break;
        }
      }
    }
    if (!tosNoticeFound) {
      noticesBuilder.add(tosNotice);
    }
    switch (boilerplateType) {
      case DOMAIN:
        noticesBuilder.addAll(RdapIcannStandardInformation.domainBoilerplateNotices);
        break;
      case NAMESERVER:
      case ENTITY:
        noticesBuilder.addAll(RdapIcannStandardInformation.nameserverAndEntityBoilerplateNotices);
        break;
      default: // things other than domains, nameservers and entities do not yet have boilerplate
        break;
    }
    jsonBuilder.put(NOTICES, noticesBuilder.build());
    if (!remarks.isEmpty()) {
      jsonBuilder.put(REMARKS, remarks);
    }
  }

  /**
   * Creates a JSON object containing a notice or remark object, as defined by RFC 7483 § 4.3.
   * The object should then be inserted into a notices or remarks array. The builder fields are:
   *
   * <p>title: the title of the notice; if null, the notice will have no title
   *
   * <p>description: objects which will be converted to strings to form the description of the
   * notice (this is the only required field; all others are optional)
   *
   * <p>typeString: the notice or remark type as defined in § 10.2.1; if null, no type
   *
   * <p>linkValueSuffix: the path at the end of the URL used in the value field of the link,
   * without any initial slash (e.g. a suffix of help/toc equates to a URL of
   * http://example.net/help/toc); if null, no link is created; if it is not null, a single link is
   * created; this method never creates more than one link)
   *
   * <p>htmlUrlString: the path, if any, to be used in the href value of the link; if the URL is
   * absolute, it is used as is; if it is relative, starting with a slash, it is appended to the
   * protocol and host of the link base; if it is relative, not starting with a slash, it is
   * appended to the complete link base; if null, a self link is generated instead, using the link
   * link value
   *
   * <p>linkBase: the base for the link value and href; if null, it is assumed to be the empty
   * string
   *
   * @see <a href="https://tools.ietf.org/html/rfc7483">
   *     RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
   */
  static ImmutableMap<String, Object> makeRdapJsonNotice(
      RdapNoticeDescriptor parameters, @Nullable String linkBase) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    if (parameters.getTitle() != null) {
      jsonBuilder.put("title", parameters.getTitle());
    }
    ImmutableList.Builder<String> descriptionBuilder = new ImmutableList.Builder<>();
    for (String line : parameters.getDescription()) {
      descriptionBuilder.add(nullToEmpty(line));
    }
    jsonBuilder.put("description", descriptionBuilder.build());
    if (parameters.getTypeString() != null) {
      jsonBuilder.put("typeString", parameters.getTypeString());
    }
    String linkBaseNotNull = nullToEmpty(linkBase);
    String linkValueSuffixNotNull = nullToEmpty(parameters.getLinkValueSuffix());
    String linkValueString =
        String.format(
            "%s%s%s",
            linkBaseNotNull,
            (linkBaseNotNull.endsWith("/") || linkValueSuffixNotNull.startsWith("/")) ? "" : "/",
            linkValueSuffixNotNull);
    if (parameters.getLinkHrefUrlString() == null) {
      jsonBuilder.put("links", ImmutableList.of(ImmutableMap.of(
          "value", linkValueString,
          "rel", "self",
          "href", linkValueString,
          "type", "application/rdap+json")));
    } else {
      URI htmlBaseURI = URI.create(nullToEmpty(linkBase));
      URI htmlUri = htmlBaseURI.resolve(parameters.getLinkHrefUrlString());
      jsonBuilder.put("links", ImmutableList.of(ImmutableMap.of(
          "value", linkValueString,
          "rel", "alternate",
          "href", htmlUri.toString(),
          "type", "text/html")));
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object containing a notice with a next page navigation link, which can then be
   * inserted into a notices array.
   *
   * <p>At the moment, only a next page link is supported. Other types of links (e.g. previous page)
   * could be added in the future, but it's not clear how to generate such links, given the way we
   * are querying the database.
   *
   * @param nextPageUrl URL string used to navigate to next page, or empty if there is no next
   */
  static ImmutableMap<String, Object> makeRdapJsonNavigationLinkNotice(
      Optional<String> nextPageUrl) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("title", "Navigation Links");
    jsonBuilder.put("description", ImmutableList.of("Links to related pages."));
    if (nextPageUrl.isPresent()) {
      jsonBuilder.put(
          "links",
          ImmutableList.of(
              ImmutableMap.of(
                  "rel", "next",
                  "href", nextPageUrl.get(),
                  "type", "application/rdap+json")));
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link DomainBase}.
   *
   * @param domainBase the domain resource object from which the JSON object should be created
   * @param isTopLevel if true, the top-level boilerplate will be added
   * @param linkBase the URL base to be used when creating links
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   * @param authorization the authorization level of the request; if not authorized for the
   *        registrar owning the domain, no contact information is included
   */
  ImmutableMap<String, Object> makeRdapJsonForDomain(
      DomainBase domainBase,
      boolean isTopLevel,
      @Nullable String linkBase,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType,
      RdapAuthorization authorization) {
    // Start with the domain-level information.
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("objectClassName", "domain");
    jsonBuilder.put("handle", domainBase.getRepoId());
    jsonBuilder.put("ldhName", domainBase.getFullyQualifiedDomainName());
    // Only include the unicodeName field if there are unicode characters.
    if (hasUnicodeComponents(domainBase.getFullyQualifiedDomainName())) {
      jsonBuilder.put("unicodeName", Idn.toUnicode(domainBase.getFullyQualifiedDomainName()));
    }
    jsonBuilder.put(
        "status",
        makeStatusValueList(
            domainBase.getStatusValues(),
            false, // isRedacted
            domainBase.getDeletionTime().isBefore(now)));
    jsonBuilder.put("links", ImmutableList.of(
        makeLink("domain", domainBase.getFullyQualifiedDomainName(), linkBase)));
    boolean displayContacts =
        authorization.isAuthorizedForClientId(domainBase.getCurrentSponsorClientId());
    // If we are outputting all data (not just summary data), also add information about hosts,
    // contacts and events (history entries). If we are outputting summary data, instead add a
    // remark indicating that fact.
    List<ImmutableMap<String, Object>> remarks;
    if (outputDataType == OutputDataType.SUMMARY) {
      remarks = ImmutableList.of(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      remarks = displayContacts
        ? ImmutableList.of()
        : ImmutableList.of(RdapIcannStandardInformation.DOMAIN_CONTACTS_HIDDEN_DATA_REMARK);
      ImmutableList<Object> events = makeEvents(domainBase, now);
      if (!events.isEmpty()) {
        jsonBuilder.put("events", events);
      }
      // Kick off the database loads of the nameservers that we will need, so it can load
      // asynchronously while we load and process the contacts.
      Map<Key<HostResource>, HostResource> loadedHosts =
          ofy().load().keys(domainBase.getNameservers());
      // Load the registrant and other contacts and add them to the data.
      ImmutableList<ImmutableMap<String, Object>> entities;
      if (!displayContacts) {
        entities = ImmutableList.of();
      } else {
        Map<Key<ContactResource>, ContactResource> loadedContacts =
            ofy().load().keys(domainBase.getReferencedContacts());
        entities =
            Streams.concat(
                    domainBase.getContacts().stream(),
                    Stream.of(
                        DesignatedContact.create(Type.REGISTRANT, domainBase.getRegistrant())))
                .sorted(DESIGNATED_CONTACT_ORDERING)
                .map(
                    designatedContact ->
                        makeRdapJsonForContact(
                            loadedContacts.get(designatedContact.getContactKey()),
                            false,
                            Optional.of(designatedContact.getType()),
                            linkBase,
                            null,
                            now,
                            outputDataType,
                            authorization))
                .collect(toImmutableList());
      }
      entities =
          addRegistrarEntity(
              entities, domainBase.getCurrentSponsorClientId(), linkBase, whoisServer, now);
      if (!entities.isEmpty()) {
        jsonBuilder.put("entities", entities);
      }
      // Add the nameservers to the data; the load was kicked off above for efficiency.
      ImmutableList.Builder<Object> nsBuilder = new ImmutableList.Builder<>();
      for (HostResource hostResource
          : HOST_RESOURCE_ORDERING.immutableSortedCopy(loadedHosts.values())) {
        nsBuilder.add(makeRdapJsonForHost(
            hostResource, false, linkBase, null, now, outputDataType));
      }
      ImmutableList<Object> ns = nsBuilder.build();
      if (!ns.isEmpty()) {
        jsonBuilder.put("nameservers", ns);
      }
    }
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    if (isTopLevel) {
      addTopLevelEntries(
          jsonBuilder,
          BoilerplateType.DOMAIN,
          remarks,
          ImmutableList.of(), linkBase);
    } else if (!remarks.isEmpty()) {
      jsonBuilder.put(REMARKS, remarks);
    }
    return jsonBuilder.build();
  }

  /**
   * Adds a JSON object for the desired registrar to an existing list of JSON objects.
   *
   * @param entities list of entities to which the desired registrar should be added
   * @param clientId the registrar client ID
   * @param linkBase the URL base to be used when creating links
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *     port43 field; if null, port43 is not added to the object
   * @param now the as-date
   */
  ImmutableList<ImmutableMap<String, Object>> addRegistrarEntity(
      ImmutableList<ImmutableMap<String, Object>> entities,
      @Nullable String clientId,
      @Nullable String linkBase,
      @Nullable String whoisServer,
      DateTime now) {
    if (clientId == null) {
      return entities;
    }
    Optional<Registrar> registrar = Registrar.loadByClientIdCached(clientId);
    if (!registrar.isPresent()) {
      return entities;
    }
    ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
    builder.addAll(entities);
    builder.add(
        makeRdapJsonForRegistrar(
            registrar.get(),
            false /* isTopLevel */,
            linkBase,
            whoisServer,
            now,
            OutputDataType.SUMMARY));
    return builder.build();
  }

  /**
   * Creates a JSON object for a {@link HostResource}.
   *
   * @param hostResource the host resource object from which the JSON object should be created
   * @param isTopLevel if true, the top-level boilerplate will be added
   * @param linkBase the URL base to be used when creating links
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   */
  ImmutableMap<String, Object> makeRdapJsonForHost(
      HostResource hostResource,
      boolean isTopLevel,
      @Nullable String linkBase,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("objectClassName", "nameserver");
    jsonBuilder.put("handle", hostResource.getRepoId());
    jsonBuilder.put("ldhName", hostResource.getFullyQualifiedHostName());
    // Only include the unicodeName field if there are unicode characters.
    if (hasUnicodeComponents(hostResource.getFullyQualifiedHostName())) {
      jsonBuilder.put("unicodeName", Idn.toUnicode(hostResource.getFullyQualifiedHostName()));
    }

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
    jsonBuilder.put(
        "status",
        makeStatusValueList(
            statuses.build(),
            false, // isRedacted
            hostResource.getDeletionTime().isBefore(now)));
    jsonBuilder.put("links", ImmutableList.of(
        makeLink("nameserver", hostResource.getFullyQualifiedHostName(), linkBase)));
    List<ImmutableMap<String, Object>> remarks;
    // If we are outputting all data (not just summary data), also add events taken from the history
    // entries. If we are outputting summary data, instead add a remark indicating that fact.
    if (outputDataType == OutputDataType.SUMMARY) {
      remarks = ImmutableList.of(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      remarks = ImmutableList.of();
      ImmutableList<Object> events = makeEvents(hostResource, now);
      if (!events.isEmpty()) {
        jsonBuilder.put("events", events);
      }
    }
    ImmutableSet<InetAddress> inetAddresses = hostResource.getInetAddresses();
    if (!inetAddresses.isEmpty()) {
      ImmutableList.Builder<String> v4AddressesBuilder = new ImmutableList.Builder<>();
      ImmutableList.Builder<String> v6AddressesBuilder = new ImmutableList.Builder<>();
      for (InetAddress inetAddress : inetAddresses) {
        if (inetAddress instanceof Inet4Address) {
          v4AddressesBuilder.add(InetAddresses.toAddrString(inetAddress));
        } else if (inetAddress instanceof Inet6Address) {
          v6AddressesBuilder.add(InetAddresses.toAddrString(inetAddress));
        }
      }
      ImmutableMap.Builder<String, ImmutableList<String>> ipAddressesBuilder =
          new ImmutableMap.Builder<>();
      ImmutableList<String> v4Addresses = v4AddressesBuilder.build();
      if (!v4Addresses.isEmpty()) {
        ipAddressesBuilder.put("v4", Ordering.natural().immutableSortedCopy(v4Addresses));
      }
      ImmutableList<String> v6Addresses = v6AddressesBuilder.build();
      if (!v6Addresses.isEmpty()) {
        ipAddressesBuilder.put("v6", Ordering.natural().immutableSortedCopy(v6Addresses));
      }
      ImmutableMap<String, ImmutableList<String>> ipAddresses = ipAddressesBuilder.build();
      if (!ipAddresses.isEmpty()) {
        jsonBuilder.put("ipAddresses", ipAddressesBuilder.build());
      }
    }
    ImmutableList<ImmutableMap<String, Object>> entities =
        addRegistrarEntity(
            ImmutableList.of(),
            hostResource.getPersistedCurrentSponsorClientId(),
            linkBase,
            whoisServer,
            now);
    if (!entities.isEmpty()) {
      jsonBuilder.put("entities", entities);
    }
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    if (isTopLevel) {
      addTopLevelEntries(
          jsonBuilder,
          BoilerplateType.NAMESERVER,
          remarks,
          ImmutableList.of(), linkBase);
    } else if (!remarks.isEmpty()) {
      jsonBuilder.put(REMARKS, remarks);
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link ContactResource} and associated contact type.
   *
   * @param contactResource the contact resource object from which the JSON object should be created
   * @param isTopLevel if true, the top-level boilerplate will be added
   * @param contactType the contact type to map to an RDAP role; if absent, no role is listed
   * @param linkBase the URL base to be used when creating links
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   * @param authorization the authorization level of the request; personal contact data is only
   *        shown if the contact is owned by a registrar for which the request is authorized
   */
  ImmutableMap<String, Object> makeRdapJsonForContact(
      ContactResource contactResource,
      boolean isTopLevel,
      Optional<DesignatedContact.Type> contactType,
      @Nullable String linkBase,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType,
      RdapAuthorization authorization) {
    boolean isAuthorized =
        authorization.isAuthorizedForClientId(contactResource.getCurrentSponsorClientId());
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    ImmutableList.Builder<ImmutableMap<String, Object>> remarksBuilder
        = new ImmutableList.Builder<>();
    jsonBuilder.put("objectClassName", "entity");
    jsonBuilder.put("handle", contactResource.getRepoId());
    jsonBuilder.put(
        "status",
        makeStatusValueList(
            isLinked(Key.create(contactResource), now)
                ? union(contactResource.getStatusValues(), StatusValue.LINKED)
                : contactResource.getStatusValues(),
            !isAuthorized,
            contactResource.getDeletionTime().isBefore(now)));
    contactType.ifPresent(
        type -> jsonBuilder.put("roles", ImmutableList.of(convertContactTypeToRdapRole(type))));
    jsonBuilder.put("links",
        ImmutableList.of(makeLink("entity", contactResource.getRepoId(), linkBase)));
    // If we are logged in as the owner of this contact, create the vCard.
    if (isAuthorized) {
      ImmutableList.Builder<Object> vcardBuilder = new ImmutableList.Builder<>();
      vcardBuilder.add(VCARD_ENTRY_VERSION);
      PostalInfo postalInfo = contactResource.getInternationalizedPostalInfo();
      if (postalInfo == null) {
        postalInfo = contactResource.getLocalizedPostalInfo();
      }
      if (postalInfo != null) {
        if (postalInfo.getName() != null) {
          vcardBuilder.add(ImmutableList.of("fn", ImmutableMap.of(), "text", postalInfo.getName()));
        }
        if (postalInfo.getOrg() != null) {
          vcardBuilder.add(ImmutableList.of("org", ImmutableMap.of(), "text", postalInfo.getOrg()));
        }
        ImmutableList<Object> addressEntry = makeVCardAddressEntry(postalInfo.getAddress());
        if (addressEntry != null) {
          vcardBuilder.add(addressEntry);
        }
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
        vcardBuilder.add(ImmutableList.of("email", ImmutableMap.of(), "text", emailAddress));
      }
      jsonBuilder.put("vcardArray", ImmutableList.of("vcard", vcardBuilder.build()));
    } else {
      remarksBuilder.add(RdapIcannStandardInformation.CONTACT_PERSONAL_DATA_HIDDEN_DATA_REMARK);
    }
    // If we are outputting all data (not just summary data), also add events taken from the history
    // entries. If we are outputting summary data, instead add a remark indicating that fact.
    if (outputDataType == OutputDataType.SUMMARY) {
      remarksBuilder.add(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      ImmutableList<Object> events = makeEvents(contactResource, now);
      if (!events.isEmpty()) {
        jsonBuilder.put("events", events);
      }
    }
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    if (isTopLevel) {
      addTopLevelEntries(
          jsonBuilder,
          BoilerplateType.ENTITY,
          remarksBuilder.build(),
          ImmutableList.of(),
          linkBase);
    } else {
      ImmutableList<ImmutableMap<String, Object>> remarks = remarksBuilder.build();
      if (!remarks.isEmpty()) {
        jsonBuilder.put(REMARKS, remarks);
      }
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link Registrar}.
   *
   * @param registrar the registrar object from which the JSON object should be created
   * @param isTopLevel if true, the top-level boilerplate will be added
   * @param linkBase the URL base to be used when creating links
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   */
  ImmutableMap<String, Object> makeRdapJsonForRegistrar(
      Registrar registrar,
      boolean isTopLevel,
      @Nullable String linkBase,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("objectClassName", "entity");
    Long ianaIdentifier = registrar.getIanaIdentifier();
    jsonBuilder.put("handle", (ianaIdentifier == null) ? "(none)" : ianaIdentifier.toString());
    jsonBuilder.put("status", registrar.isLive() ? STATUS_LIST_ACTIVE : STATUS_LIST_INACTIVE);
    jsonBuilder.put("roles", ImmutableList.of(RdapEntityRole.REGISTRAR.rfc7483String));
    if (ianaIdentifier != null) {
      jsonBuilder.put("links",
          ImmutableList.of(makeLink("entity", ianaIdentifier.toString(), linkBase)));
      jsonBuilder.put(
          "publicIds",
          ImmutableList.of(
              ImmutableMap.of(
                  "type", "IANA Registrar ID", "identifier", ianaIdentifier.toString())));
    }
    // Create the vCard.
    ImmutableList.Builder<Object> vcardBuilder = new ImmutableList.Builder<>();
    vcardBuilder.add(VCARD_ENTRY_VERSION);
    String registrarName = registrar.getRegistrarName();
    if (registrarName != null) {
      vcardBuilder.add(ImmutableList.of("fn", ImmutableMap.of(), "text", registrarName));
    }
    RegistrarAddress address = registrar.getInternationalizedAddress();
    if (address == null) {
      address = registrar.getLocalizedAddress();
    }
    if (address != null) {
      ImmutableList<Object> addressEntry = makeVCardAddressEntry(address);
      if (addressEntry != null) {
        vcardBuilder.add(addressEntry);
      }
    }
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
      vcardBuilder.add(ImmutableList.of("email", ImmutableMap.of(), "text", emailAddress));
    }
    jsonBuilder.put("vcardArray", ImmutableList.of("vcard", vcardBuilder.build()));
    // If we are outputting all data (not just summary data), also add registrar contacts. If we are
    // outputting summary data, instead add a remark indicating that fact.
    List<ImmutableMap<String, Object>> remarks;
    if (outputDataType == OutputDataType.SUMMARY) {
      remarks = ImmutableList.of(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      remarks = ImmutableList.of();
      ImmutableList<Object> events = makeEvents(registrar, now);
      if (!events.isEmpty()) {
        jsonBuilder.put("events", events);
      }
      // include the registrar contacts as subentities
      ImmutableList<ImmutableMap<String, Object>> registrarContacts =
          registrar
              .getContacts()
              .stream()
              .filter(RdapJsonFormatter::isVisible)
              .map(registrarContact -> makeRdapJsonForRegistrarContact(registrarContact, null))
              .collect(toImmutableList());
      if (!registrarContacts.isEmpty()) {
        jsonBuilder.put("entities", registrarContacts);
      }
    }
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    if (isTopLevel) {
      addTopLevelEntries(
          jsonBuilder,
          BoilerplateType.ENTITY,
          remarks,
          ImmutableList.of(),
          linkBase);
    } else if (!remarks.isEmpty()) {
      jsonBuilder.put(REMARKS, remarks);
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link RegistrarContact}.
   *
   * @param registrarContact the registrar contact for which the JSON object should be created
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   */
  static ImmutableMap<String, Object> makeRdapJsonForRegistrarContact(
      RegistrarContact registrarContact, @Nullable String whoisServer) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("objectClassName", "entity");
    String gaeUserId = registrarContact.getGaeUserId();
    if (gaeUserId != null) {
      jsonBuilder.put("handle", registrarContact.getGaeUserId());
    }
    jsonBuilder.put("status", STATUS_LIST_ACTIVE);
    jsonBuilder.put("roles", makeRdapRoleList(registrarContact));
    // Create the vCard.
    ImmutableList.Builder<Object> vcardBuilder = new ImmutableList.Builder<>();
    vcardBuilder.add(VCARD_ENTRY_VERSION);
    String name = registrarContact.getName();
    if (name != null) {
      vcardBuilder.add(ImmutableList.of("fn", ImmutableMap.of(), "text", name));
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
      vcardBuilder.add(ImmutableList.of("email", ImmutableMap.of(), "text", emailAddress));
    }
    jsonBuilder.put("vcardArray", ImmutableList.of("vcard", vcardBuilder.build()));
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    return jsonBuilder.build();
  }

  /** Converts a domain registry contact type into a role as defined by RFC 7483. */
  private static String convertContactTypeToRdapRole(DesignatedContact.Type contactType) {
    switch (contactType) {
      case REGISTRANT:
        return RdapEntityRole.REGISTRANT.rfc7483String;
      case TECH:
        return RdapEntityRole.TECH.rfc7483String;
      case BILLING:
        return RdapEntityRole.BILLING.rfc7483String;
      case ADMIN:
        return RdapEntityRole.ADMIN.rfc7483String;
      default:
        throw new AssertionError();
    }
  }

  /**
   * Creates the list of RDAP roles for a registrar contact, using the visibleInWhoisAs* flags.
   */
  private static ImmutableList<String> makeRdapRoleList(RegistrarContact registrarContact) {
    ImmutableList.Builder<String> rolesBuilder = new ImmutableList.Builder<>();
    if (registrarContact.getVisibleInWhoisAsAdmin()) {
      rolesBuilder.add(RdapEntityRole.ADMIN.rfc7483String);
    }
    if (registrarContact.getVisibleInWhoisAsTech()) {
      rolesBuilder.add(RdapEntityRole.TECH.rfc7483String);
    }
    return rolesBuilder.build();
  }

  /** Checks whether the registrar contact should be visible (because it has visible roles). */
  private static boolean isVisible(RegistrarContact registrarContact) {
    return registrarContact.getVisibleInWhoisAsAdmin()
        || registrarContact.getVisibleInWhoisAsTech();
  }

  /**
   * Creates an event list for a domain, host or contact resource.
   */
  private static ImmutableList<Object> makeEvents(EppResource resource, DateTime now) {
    HashMap<RdapEventAction, HistoryEntry> lastEntryOfType = Maps.newHashMap();
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
      RdapEventAction rdapEventAction =
          historyEntryTypeToRdapEventActionMap.get(historyEntry.getType());
      // Only save the historyEntries if this is a type we care about.
      if (rdapEventAction == null) {
        continue;
      }
      lastEntryOfType.put(rdapEventAction, historyEntry);
    }
    ImmutableList.Builder<Object> eventsBuilder = new ImmutableList.Builder<>();
    // There are 2 possibly conflicting values for the creation time - either the
    // resource.getCreationTime, or the REGISTRATION event created from a HistoryEntry
    //
    // We favor the HistoryEntry if it exists, since we show that value as REGISTRATION time in the
    // reply, so the reply will be self-consistent.
    //
    // This is mostly an issue in the tests as in "reality" these two values should be the same.
    //
    DateTime creationTime =
        Optional.ofNullable(lastEntryOfType.get(RdapEventAction.REGISTRATION))
            .map(historyEntry -> historyEntry.getModificationTime())
            .orElse(resource.getCreationTime());
    // TODO(b/129849684) remove this and use the events List defined above once we have Event
    // objects
    ImmutableList.Builder<DateTime> changeTimesBuilder = new ImmutableList.Builder<>();
    // The order of the elements is stable - it's the order in which the enum elements are defined
    // in RdapEventAction
    for (RdapEventAction rdapEventAction : RdapEventAction.values()) {
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
      eventsBuilder.add(makeEvent(rdapEventAction, historyEntry.getClientId(), modificationTime));
      changeTimesBuilder.add(modificationTime);
    }
    if (resource instanceof DomainBase) {
      DateTime expirationTime = ((DomainBase) resource).getRegistrationExpirationTime();
      if (expirationTime != null) {
        eventsBuilder.add(makeEvent(RdapEventAction.EXPIRATION, null, expirationTime));
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
      eventsBuilder.add(makeEvent(RdapEventAction.LAST_CHANGED, null, lastChangeTime));
    }
    eventsBuilder.add(makeEvent(RdapEventAction.LAST_UPDATE_OF_RDAP_DATABASE, null, now));
    // TODO(b/129849684): sort events by their time once we return a list of Events instead of JSON
    // objects.
    return eventsBuilder.build();
  }

  /**
   * Creates an event list for a {@link Registrar}.
   */
  private static ImmutableList<Object> makeEvents(Registrar registrar, DateTime now) {
    ImmutableList.Builder<Object> eventsBuilder = new ImmutableList.Builder<>();
    Long ianaIdentifier = registrar.getIanaIdentifier();
    eventsBuilder.add(makeEvent(
        RdapEventAction.REGISTRATION,
        (ianaIdentifier == null) ? "(none)" : ianaIdentifier.toString(),
        registrar.getCreationTime()));
    if ((registrar.getLastUpdateTime() != null)
        && registrar.getLastUpdateTime().isAfter(registrar.getCreationTime())) {
      eventsBuilder.add(makeEvent(
          RdapEventAction.LAST_CHANGED, null, registrar.getLastUpdateTime()));
    }
    eventsBuilder.add(makeEvent(RdapEventAction.LAST_UPDATE_OF_RDAP_DATABASE, null, now));
    return eventsBuilder.build();
  }

  /**
   * Creates an RDAP event object as defined by RFC 7483.
   */
  private static ImmutableMap<String, Object> makeEvent(
      RdapEventAction eventAction, @Nullable String eventActor, DateTime eventDate) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("eventAction", eventAction.getDisplayName());
    if (eventActor != null) {
      jsonBuilder.put("eventActor", eventActor);
    }
    jsonBuilder.put("eventDate", eventDate.toString());
    return jsonBuilder.build();
  }

  /**
   * Creates a vCard address entry: array of strings specifying the components of the address.
   *
   * @see <a href="https://tools.ietf.org/html/rfc7095">
   *        RFC 7095: jCard: The JSON Format for vCard</a>
   */
  private static ImmutableList<Object> makeVCardAddressEntry(Address address) {
    if (address == null) {
      return null;
    }
    ImmutableList.Builder<Object> jsonBuilder = new ImmutableList.Builder<>();
    jsonBuilder.add(""); // PO box
    jsonBuilder.add(""); // extended address

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
      jsonBuilder.add("");
    } else if (street.size() == 1) {
      jsonBuilder.add(street.get(0));
    } else {
      jsonBuilder.add(street);
    }
    jsonBuilder.add(nullToEmpty(address.getCity()));
    jsonBuilder.add(nullToEmpty(address.getState()));
    jsonBuilder.add(nullToEmpty(address.getZip()));
    jsonBuilder.add(new Locale("en", address.getCountryCode()).getDisplayCountry(new Locale("en")));
    return ImmutableList.of(
        "adr",
        ImmutableMap.of(),
        "text",
        jsonBuilder.build());
  }

  /** Creates a vCard phone number entry. */
  private static ImmutableList<Object> makePhoneEntry(
      ImmutableMap<String, ImmutableList<String>> type, String phoneNumber) {
    return ImmutableList.of("tel", type, "uri", phoneNumber);
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
  private static ImmutableList<String> makeStatusValueList(
      ImmutableSet<StatusValue> statusValues, boolean isRedacted, boolean isDeleted) {
    Stream<RdapStatus> stream =
        statusValues
            .stream()
            .map(status -> statusToRdapStatusMap.getOrDefault(status, RdapStatus.OBSCURED));
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
        .map(RdapStatus::getDisplayName)
        .collect(toImmutableSortedSet(Ordering.natural()))
        .asList();
  }

  /**
   * Creates a self link as directed by the spec.
   *
   * @see <a href="https://tools.ietf.org/html/rfc7483">
   *        RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
   */
  private static ImmutableMap<String, String> makeLink(
      String type, String name, @Nullable String linkBase) {
    String url;
    if (linkBase == null) {
      url = type + '/' + name;
    } else if (linkBase.endsWith("/")) {
      url = linkBase + type + '/' + name;
    } else {
      url = linkBase + '/' + type + '/' + name;
    }
    return ImmutableMap.of(
        "value", url,
        "rel", "self",
        "href", url,
        "type", "application/rdap+json");
  }

  /**
   * Creates a JSON error indication.
   *
   * @see <a href="https://tools.ietf.org/html/rfc7483">
   *        RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
   */
  ImmutableMap<String, Object> makeError(int status, String title, String description) {
    return ImmutableMap.of(
        "rdapConformance", CONFORMANCE_LIST,
        "lang", "en",
        "errorCode", (long) status,
        "title", title,
        "description", ImmutableList.of(description));
  }

  private static boolean hasUnicodeComponents(String fullyQualifiedName) {
    return fullyQualifiedName.startsWith(ACE_PREFIX)
        || fullyQualifiedName.contains("." + ACE_PREFIX);
  }
}
