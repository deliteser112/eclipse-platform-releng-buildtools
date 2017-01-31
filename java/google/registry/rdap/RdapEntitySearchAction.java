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

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.rdap.RdapIcannStandardInformation.TRUNCATION_NOTICES;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import com.googlecode.objectify.Key;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.registrar.Registrar;
import google.registry.rdap.RdapJsonFormatter.BoilerplateType;
import google.registry.rdap.RdapJsonFormatter.OutputDataType;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.request.HttpException.UnprocessableEntityException;
import google.registry.request.Parameter;
import google.registry.util.Clock;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * RDAP (new WHOIS) action for entity (contact and registrar) search requests.
 *
 * <p>All commands and responses conform to the RDAP spec as defined in RFCs 7480 through 7485.
 *
 * @see <a href="http://tools.ietf.org/html/rfc7482">
 *        RFC 7482: Registration Data Access Protocol (RDAP) Query Format</a>
 * @see <a href="http://tools.ietf.org/html/rfc7483">
 *        RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
 */
@Action(path = RdapEntitySearchAction.PATH, method = {GET, HEAD}, isPrefix = true)
public class RdapEntitySearchAction extends RdapActionBase {

  public static final String PATH = "/rdap/entities";

  @Inject Clock clock;
  @Inject @Parameter("fn") Optional<String> fnParam;
  @Inject @Parameter("handle") Optional<String> handleParam;
  @Inject @Config("rdapResultSetMaxSize") int rdapResultSetMaxSize;
  @Inject RdapEntitySearchAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "entity search";
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  /** Parses the parameters and calls the appropriate search function. */
  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest, String linkBase) {
    DateTime now = clock.nowUtc();
    // RDAP syntax example: /rdap/entities?fn=Bobby%20Joe*.
    // The pathSearchString is not used by search commands.
    if (pathSearchString.length() > 0) {
      throw new BadRequestException("Unexpected path");
    }
    if (Booleans.countTrue(fnParam.isPresent(), handleParam.isPresent()) != 1) {
      throw new BadRequestException("You must specify either fn=XXXX or handle=YYYY");
    }
    RdapSearchResults results;
    if (fnParam.isPresent()) {
      // syntax: /rdap/entities?fn=Bobby%20Joe*
      // The name is the contact name or registrar name (not registrar contact name).
      results = searchByName(RdapSearchPattern.create(fnParam.get(), false), now);
    } else {
      // syntax: /rdap/entities?handle=12345-*
      // The handle is either the contact roid or the registrar clientId.
      results = searchByHandle(RdapSearchPattern.create(handleParam.get(), false), now);
    }
    if (results.jsonList().isEmpty()) {
      throw new NotFoundException("No entities found");
    }
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("entitySearchResults", results.jsonList());
    rdapJsonFormatter.addTopLevelEntries(
        jsonBuilder,
        BoilerplateType.ENTITY,
        results.isTruncated()
            ? TRUNCATION_NOTICES : ImmutableList.<ImmutableMap<String, Object>>of(),
        ImmutableList.<ImmutableMap<String, Object>>of(),
        rdapLinkBase);
    return jsonBuilder.build();
  }

  /**
   * Searches for entities by name, returning a JSON array of entity info maps.
   *
   * <p>As per Gustavo Lozano of ICANN, registrar name search should be by registrar name only, not
   * by registrar contact name:
   *
   * <p>The search is by registrar name only. The profile is supporting the functionality defined
   * in the Base Registry Agreement (see 1.6 of Section 4 of the Base Registry Agreement,
   * https://newgtlds.icann.org/sites/default/files/agreements/
   * agreement-approved-09jan14-en.htm).
   *
   * <p>According to RFC 7482 section 6.1, punycode is only used for domain name labels, so we can
   * assume that entity names are regular unicode.
   */
  private RdapSearchResults searchByName(final RdapSearchPattern partialStringQuery, DateTime now) {
    // Don't allow suffixes in entity name search queries.
    if (!partialStringQuery.getHasWildcard() && (partialStringQuery.getSuffix() != null)) {
      throw new UnprocessableEntityException("Suffixes not allowed in entity name searches");
    }
    // Get the registrar matches, depending on whether there's a wildcard.
    ImmutableList<Registrar> registrarMatches;
    if (!partialStringQuery.getHasWildcard()) {
      Registrar registrar = Registrar.loadByName(partialStringQuery.getInitialString());
      registrarMatches = (registrar == null)
          ? ImmutableList.<Registrar>of()
          : ImmutableList.of(registrar);
    } else {
      // Fetch an additional registrar, so we can detect result set truncation.
      registrarMatches = ImmutableList.copyOf(Registrar.loadByNameRange(
          partialStringQuery.getInitialString(),
          partialStringQuery.getNextInitialString(),
          rdapResultSetMaxSize + 1));
    }
    // Get the contact matches and return the results, fetching an additional contact to detect
    // truncation.
    return makeSearchResults(
      queryUndeleted(
          ContactResource.class, "searchName", partialStringQuery, rdapResultSetMaxSize + 1).list(),
      registrarMatches,
      now);
  }

  /** Searches for entities by handle, returning a JSON array of entity info maps. */
  private RdapSearchResults searchByHandle(
      final RdapSearchPattern partialStringQuery, DateTime now) {
    // Handle queries without a wildcard -- load by ID.
    if (!partialStringQuery.getHasWildcard()) {
      ContactResource contactResource = ofy().load()
          .type(ContactResource.class)
          .id(partialStringQuery.getInitialString())
          .now();
      ImmutableList<Registrar> registrars = getMatchingRegistrars(partialStringQuery);
      return makeSearchResults(
          ((contactResource == null) || !contactResource.getDeletionTime().isEqual(END_OF_TIME))
              ? ImmutableList.<ContactResource>of() : ImmutableList.of(contactResource),
          registrars,
          now);
    // Handle queries with a wildcard, but no suffix. For contact resources, the deletion time will
    // always be END_OF_TIME for non-deleted records; unlike domain resources, we don't need to
    // worry about deletion times in the future. That allows us to use an equality query for the
    // deletion time. Because the handle for registrars is the IANA identifier number, don't allow
    // wildcard searches for registrars, by simply not searching for registrars if a wildcard is
    // present. Fetch an extra contact to detect result set truncation.
    } else if (partialStringQuery.getSuffix() == null) {
      return makeSearchResults(
          ofy().load()
              .type(ContactResource.class)
              .filterKey(
                  ">=", Key.create(ContactResource.class, partialStringQuery.getInitialString()))
              .filterKey(
                  "<", Key.create(ContactResource.class, partialStringQuery.getNextInitialString()))
              .filter("deletionTime", END_OF_TIME)
              .limit(rdapResultSetMaxSize + 1)
              .list(),
          ImmutableList.<Registrar>of(),
          now);
    // Don't allow suffixes in entity handle search queries.
    } else {
      throw new UnprocessableEntityException("Suffixes not allowed in entity handle searches");
    }
  }

  /** Looks up registrars by handle (i.e. IANA identifier). */
  private ImmutableList<Registrar>
      getMatchingRegistrars(final RdapSearchPattern partialStringQuery) {
    Long ianaIdentifier;
    try {
      ianaIdentifier = Long.parseLong(partialStringQuery.getInitialString());
    } catch (NumberFormatException e) {
      return ImmutableList.of();
    }
    // Fetch an additional registrar to detect result set truncation.
    return ImmutableList.copyOf(Registrar.loadByIanaIdentifierRange(
        ianaIdentifier, ianaIdentifier + 1, rdapResultSetMaxSize + 1));
  }

  /** Builds a JSON array of entity info maps based on the specified contacts and registrars. */
  private RdapSearchResults makeSearchResults(
      List<ContactResource> contacts, List<Registrar> registrars, DateTime now) {

    // Determine what output data type to use, depending on whether more than one entity will be
    // returned.
    int numEntities = contacts.size();
    OutputDataType outputDataType;
    // If there's more than one contact, then we know already we need SUMMARY mode.
    if (numEntities > 1) {
      outputDataType = OutputDataType.SUMMARY;
    // If there are fewer than two contacts, loop through and compute the total number of contacts
    // and registrars, stopping as soon as we find two.
    } else {
      outputDataType = OutputDataType.FULL;
      for (Registrar registrar : registrars) {
        if (registrar.isActiveAndPubliclyVisible()) {
          numEntities++;
          if (numEntities > 1) {
            outputDataType = OutputDataType.SUMMARY;
            break;
          }
        }
      }
    }

    // There can be more results than our max size, partially because we have two pools to draw from
    // (contacts and registrars), and partially because we try to fetch one more than the max size,
    // so we can tell whether to display the truncation notification.
    List<ImmutableMap<String, Object>> jsonOutputList = new ArrayList<>();
    for (ContactResource contact : contacts) {
      if (jsonOutputList.size() >= rdapResultSetMaxSize) {
        return RdapSearchResults.create(ImmutableList.copyOf(jsonOutputList), true);
      }
      // As per Andy Newton on the regext mailing list, contacts by themselves have no role, since
      // they are global, and might have different roles for different domains.
      jsonOutputList.add(rdapJsonFormatter.makeRdapJsonForContact(
          contact,
          false,
          Optional.<DesignatedContact.Type>absent(),
          rdapLinkBase,
          rdapWhoisServer,
          now,
          outputDataType));
    }
    for (Registrar registrar : registrars) {
      if (registrar.isActiveAndPubliclyVisible()) {
        if (jsonOutputList.size() >= rdapResultSetMaxSize) {
          return RdapSearchResults.create(ImmutableList.copyOf(jsonOutputList), true);
        }
        jsonOutputList.add(rdapJsonFormatter.makeRdapJsonForRegistrar(
            registrar, false, rdapLinkBase, rdapWhoisServer, now, outputDataType));
      }
    }
    return RdapSearchResults.create(ImmutableList.copyOf(jsonOutputList));
  }
}
