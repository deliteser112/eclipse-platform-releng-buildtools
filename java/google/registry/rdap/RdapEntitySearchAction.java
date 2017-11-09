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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.rdap.RdapUtils.getRegistrarByIanaIdentifier;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Longs;
import com.googlecode.objectify.cmd.Query;
import google.registry.model.contact.ContactResource;
import google.registry.model.registrar.Registrar;
import google.registry.rdap.RdapJsonFormatter.BoilerplateType;
import google.registry.rdap.RdapJsonFormatter.OutputDataType;
import google.registry.rdap.RdapMetrics.EndpointType;
import google.registry.rdap.RdapSearchResults.IncompletenessWarningType;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.request.HttpException.UnprocessableEntityException;
import google.registry.request.Parameter;
import google.registry.request.auth.Auth;
import google.registry.util.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * RDAP (new WHOIS) action for entity (contact and registrar) search requests.
 *
 * <p>All commands and responses conform to the RDAP spec as defined in RFCs 7480 through 7485.
 *
 * @see <a href="http://tools.ietf.org/html/rfc7482">RFC 7482: Registration Data Access Protocol
 *     (RDAP) Query Format</a>
 * @see <a href="http://tools.ietf.org/html/rfc7483">RFC 7483: JSON Responses for the Registration
 *     Data Access Protocol (RDAP)</a>
 */
@Action(
  path = RdapEntitySearchAction.PATH,
  method = {GET, HEAD},
  auth = Auth.AUTH_PUBLIC
)
public class RdapEntitySearchAction extends RdapActionBase {

  public static final String PATH = "/rdap/entities";

  private static final int RESULT_SET_SIZE_SCALING_FACTOR = 30;

  @Inject Clock clock;
  @Inject @Parameter("fn") Optional<String> fnParam;
  @Inject @Parameter("handle") Optional<String> handleParam;
  @Inject RdapEntitySearchAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "entity search";
  }

  @Override
  public EndpointType getEndpointType() {
    return EndpointType.ENTITIES;
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  /** Parses the parameters and calls the appropriate search function. */
  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest) {
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
        results.getIncompletenessWarnings(),
        ImmutableList.of(),
        fullServletPath);
    return jsonBuilder.build();
  }

  /**
   * Searches for entities by name, returning a JSON array of entity info maps.
   *
   * <p>As per Gustavo Lozano of ICANN, registrar name search should be by registrar name only, not
   * by registrar contact name:
   *
   * <p>The search is by registrar name only. The profile is supporting the functionality defined
   * in the Base Registry Agreement.
   *
   * <p>According to RFC 7482 section 6.1, punycode is only used for domain name labels, so we can
   * assume that entity names are regular unicode.
   *
   * <p>The includeDeleted flag is ignored when searching for contacts, because contact names are
   * set to null when the contact is deleted, so a deleted contact can never have a name.
   *
   * <p>Since we are restricting access to contact names, we don't want name searches to return
   * contacts whose names are not visible. That would allow unscrupulous users to query by name
   * and infer that all returned contacts contain that name string. So we check the authorization
   * level to determine what to do.
   *
   * @see <a href="https://newgtlds.icann.org/sites/default/files/agreements/agreement-approved-09jan14-en.htm">1.6
   * of Section 4 of the Base Registry Agreement</a>
   */
  private RdapSearchResults searchByName(final RdapSearchPattern partialStringQuery, DateTime now) {
    // For wildcard searches, make sure the initial string is long enough, and don't allow suffixes.
    if (partialStringQuery.getHasWildcard() && (partialStringQuery.getSuffix() != null)) {
      throw new UnprocessableEntityException(
          partialStringQuery.getHasWildcard()
              ? "Suffixes not allowed in wildcard entity name searches"
              : "Suffixes not allowed when searching for deleted entities");
    }
    if (partialStringQuery.getHasWildcard()
        && (partialStringQuery.getInitialString().length()
            < RdapSearchPattern.MIN_INITIAL_STRING_LENGTH)) {
      throw new UnprocessableEntityException(
          partialStringQuery.getHasWildcard()
              ? "Initial search string required in wildcard entity name searches"
              : "Initial search string required when searching for deleted entities");
    }
    // Get the registrar matches.
    ImmutableList<Registrar> registrars =
        Streams.stream(Registrar.loadAllCached())
            .filter(
                registrar ->
                    partialStringQuery.matches(registrar.getRegistrarName())
                        && shouldBeVisible(registrar))
            .limit(rdapResultSetMaxSize + 1)
            .collect(toImmutableList());
    // Get the contact matches and return the results, fetching an additional contact to detect
    // truncation. Don't bother searching for contacts by name if the request would not be able to
    // see any names anyway.
    RdapResultSet<ContactResource> resultSet;
    RdapAuthorization authorization = getAuthorization();
    if (authorization.role() == RdapAuthorization.Role.PUBLIC) {
      resultSet = RdapResultSet.create(ImmutableList.of());
    } else {
      Query<ContactResource> query =
          queryItems(
              ContactResource.class,
              "searchName",
              partialStringQuery,
              false,
              rdapResultSetMaxSize + 1);
      if (authorization.role() != RdapAuthorization.Role.ADMINISTRATOR) {
        query = query.filter("currentSponsorClientId in", authorization.clientIds());
      }
      resultSet = getMatchingResources(query, false, now);
    }
    return makeSearchResults(resultSet, registrars, now);
  }

  /**
   * Searches for entities by handle, returning a JSON array of entity info maps.
   *
   * <p>Searches for deleted entities are treated like wildcard searches.
   *
   * <p>We don't allow suffixes after a wildcard in entity searches. Suffixes are used in domain
   * searches to specify a TLD, and in nameserver searches to specify a locally managed domain name.
   * In both cases, the suffix can be turned into an additional query filter field. For contacts,
   * there is no equivalent string suffix that can be used as a query filter, so we disallow use.
   */
  private RdapSearchResults searchByHandle(
      final RdapSearchPattern partialStringQuery, DateTime now) {
    if (partialStringQuery.getSuffix() != null) {
      throw new UnprocessableEntityException("Suffixes not allowed in entity handle searches");
    }
    // Handle queries without a wildcard (and not including deleted) -- load by ID.
    if (!partialStringQuery.getHasWildcard() && !shouldIncludeDeleted()) {
      ContactResource contactResource = ofy().load()
          .type(ContactResource.class)
          .id(partialStringQuery.getInitialString())
          .now();
      return makeSearchResults(
          ((contactResource != null) && shouldBeVisible(contactResource, now))
              ? ImmutableList.of(contactResource)
              : ImmutableList.of(),
          IncompletenessWarningType.COMPLETE,
          getMatchingRegistrars(partialStringQuery.getInitialString()),
          now);
    // Handle queries with a wildcard (or including deleted), but no suffix. Because the handle
    // for registrars is the IANA identifier number, don't allow wildcard searches for registrars,
    // by simply not searching for registrars if a wildcard is present. Fetch an extra contact to
    // detect result set truncation.
    } else {
      ImmutableList<Registrar> registrars =
          partialStringQuery.getHasWildcard()
              ? ImmutableList.of()
              : getMatchingRegistrars(partialStringQuery.getInitialString());
      // Get the contact matches and return the results, fetching an additional contact to detect
      // truncation. If we are including deleted entries, we must fetch more entries, in case some
      // get excluded due to permissioning.
      Query<ContactResource> query =
          queryItemsByKey(
              ContactResource.class,
              partialStringQuery,
              shouldIncludeDeleted(),
              shouldIncludeDeleted()
                  ? (RESULT_SET_SIZE_SCALING_FACTOR * (rdapResultSetMaxSize + 1))
                  : (rdapResultSetMaxSize + 1));
      return makeSearchResults(
          getMatchingResources(query, shouldIncludeDeleted(), now), registrars, now);
    }
  }

  /** Looks up registrars by handle (i.e. IANA identifier). */
  private ImmutableList<Registrar> getMatchingRegistrars(final String ianaIdentifierString) {
    Long ianaIdentifier = Longs.tryParse(ianaIdentifierString);
    if (ianaIdentifier == null) {
      return ImmutableList.of();
    }
    Optional<Registrar> registrar = getRegistrarByIanaIdentifier(ianaIdentifier);
    return (registrar.isPresent() && shouldBeVisible(registrar.get()))
        ? ImmutableList.of(registrar.get())
        : ImmutableList.of();
  }

  /**
   * Builds a JSON array of entity info maps based on the specified contacts and registrars.
   *
   * <p>This is a convenience wrapper for the four-argument makeSearchResults; it unpacks the two
   * properties of the ContactsAndIncompletenessWarningType structure and passes them as separate
   * arguments.
   */
  private RdapSearchResults makeSearchResults(
      RdapResultSet<ContactResource> resultSet, List<Registrar> registrars, DateTime now) {
    return makeSearchResults(
        resultSet.resources(), resultSet.incompletenessWarningType(), registrars, now);
  }

  /**
   * Builds a JSON array of entity info maps based on the specified contacts and registrars.
   *
   * @param contacts the list of contacts which can be returned
   * @param incompletenessWarningType MIGHT_BE_INCOMPLETE if the list of contacts might be
   *        incomplete; this only matters if the total count of contacts and registrars combined is
   *        less than a full result set's worth
   * @param registrars the list of registrars which can be returned
   * @param now the current date and time
   * @return an {@link RdapSearchResults} object
   */
  private RdapSearchResults makeSearchResults(
      List<ContactResource> contacts,
      IncompletenessWarningType incompletenessWarningType,
      List<Registrar> registrars,
      DateTime now) {

    // Determine what output data type to use, depending on whether more than one entity will be
    // returned.
    OutputDataType outputDataType =
        (contacts.size() + registrars.size() > 1) ? OutputDataType.SUMMARY : OutputDataType.FULL;

    // There can be more results than our max size, partially because we have two pools to draw from
    // (contacts and registrars), and partially because we try to fetch one more than the max size,
    // so we can tell whether to display the truncation notification.
    RdapAuthorization authorization = getAuthorization();
    List<ImmutableMap<String, Object>> jsonOutputList = new ArrayList<>();
    for (ContactResource contact : contacts) {
      if (jsonOutputList.size() >= rdapResultSetMaxSize) {
        return RdapSearchResults.create(
            ImmutableList.copyOf(jsonOutputList), IncompletenessWarningType.TRUNCATED);
      }
      // As per Andy Newton on the regext mailing list, contacts by themselves have no role, since
      // they are global, and might have different roles for different domains.
      jsonOutputList.add(rdapJsonFormatter.makeRdapJsonForContact(
          contact,
          false,
          Optional.empty(),
          fullServletPath,
          rdapWhoisServer,
          now,
          outputDataType,
          authorization));
    }
    for (Registrar registrar : registrars) {
      if (jsonOutputList.size() >= rdapResultSetMaxSize) {
        return RdapSearchResults.create(
            ImmutableList.copyOf(jsonOutputList), IncompletenessWarningType.TRUNCATED);
      }
      jsonOutputList.add(rdapJsonFormatter.makeRdapJsonForRegistrar(
          registrar, false, fullServletPath, rdapWhoisServer, now, outputDataType));
    }
    return RdapSearchResults.create(
        ImmutableList.copyOf(jsonOutputList),
        (jsonOutputList.size() < rdapResultSetMaxSize)
            ? incompletenessWarningType
            : IncompletenessWarningType.COMPLETE);
  }
}
