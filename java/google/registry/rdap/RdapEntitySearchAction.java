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

package google.registry.rdap;

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;
import google.registry.config.ConfigModule.Config;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.registrar.Registrar;
import google.registry.rdap.RdapJsonFormatter.BoilerplateType;
import google.registry.request.Action;
import google.registry.request.HttpException;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.request.HttpException.NotImplementedException;
import google.registry.request.HttpException.UnprocessableEntityException;
import google.registry.request.Parameter;
import google.registry.util.Clock;
import javax.inject.Inject;

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
      String pathSearchString, boolean isHeadRequest, String linkBase) throws HttpException {
    // RDAP syntax example: /rdap/entities?fn=Bobby%20Joe*.
    // The pathSearchString is not used by search commands.
    if (pathSearchString.length() > 0) {
      throw new BadRequestException("Unexpected path");
    }
    if (Booleans.countTrue(fnParam.isPresent(), handleParam.isPresent()) != 1) {
      throw new BadRequestException("You must specify either fn=XXXX or handle=YYYY");
    }
    ImmutableList<ImmutableMap<String, Object>> results;
    if (fnParam.isPresent()) {
      // syntax: /rdap/entities?fn=Bobby%20Joe*
      // TODO(b/25973399): implement entity name search, and move the comment below to that routine
      // As per Gustavo Lozano of ICANN, registrar name search should be by registrar name only, not
      // by registrar contact name:
      //
      //   The search is by registrar name only. The profile is supporting the functionality defined
      //   in the Base Registry Agreement (see 1.6 of Section 4 of the Base Registry Agreement,
      //   https://newgtlds.icann.org/sites/default/files/agreements/
      //   agreement-approved-09jan14-en.htm).
      throw new NotImplementedException("Entity name search not implemented");
    } else {
      // syntax: /rdap/entities?handle=12345-*
      // The handle is either the contact roid or the registrar clientId.
      results = searchByHandle(RdapSearchPattern.create(handleParam.get(), false));
    }
    if (results.isEmpty()) {
      throw new NotFoundException("No entities found");
    }
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("entitySearchResults", results);
    RdapJsonFormatter.addTopLevelEntries(builder, BoilerplateType.ENTITY, null, rdapLinkBase);
    return builder.build();
  }

  /** Searches for entities by handle, returning a JSON array of entity info maps. */
  private ImmutableList<ImmutableMap<String, Object>>
      searchByHandle(final RdapSearchPattern partialStringQuery) throws HttpException {
    // Handle queries without a wildcard -- load by ID.
    if (!partialStringQuery.getHasWildcard()) {
      ContactResource contactResource = ofy().load()
          .type(ContactResource.class)
          .id(partialStringQuery.getInitialString())
          .now();
      Registrar registrar = Registrar.loadByClientId(partialStringQuery.getInitialString());
      ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
      if ((contactResource != null) && contactResource.getDeletionTime().isEqual(END_OF_TIME)) {
        // As per Andy Newton on the regext mailing list, contacts by themselves have no role, since
        // they are global, and might have different roles for different domains.
        builder.add(RdapJsonFormatter.makeRdapJsonForContact(
            contactResource,
            false,
            Optional.<DesignatedContact.Type>absent(),
            rdapLinkBase,
            rdapWhoisServer));
      }
      if ((registrar != null) && registrar.isActiveAndPubliclyVisible()) {
        builder.add(RdapJsonFormatter.makeRdapJsonForRegistrar(
            registrar, false, rdapLinkBase, rdapWhoisServer));
      }
      return builder.build();
    // Handle queries with a wildcard, but no suffix. For contact resources, the deletion time will
    // always be END_OF_TIME for non-deleted records; unlike domain resources, we don't need to
    // worry about deletion times in the future. That allows us to use an equality query for the
    // deletion time.
    } else if (partialStringQuery.getSuffix() == null) {
      ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
      Query<ContactResource> query = ofy().load()
          .type(ContactResource.class)
          .filterKey(">=", Key.create(ContactResource.class, partialStringQuery.getInitialString()))
          .filterKey(
              "<", Key.create(ContactResource.class, partialStringQuery.getNextInitialString()))
          .filter("deletionTime", END_OF_TIME)
          .limit(rdapResultSetMaxSize);
      for (ContactResource contactResource : query) {
        builder.add(RdapJsonFormatter.makeRdapJsonForContact(
            contactResource,
            false,
            Optional.<DesignatedContact.Type>absent(),
            rdapLinkBase,
            rdapWhoisServer));
      }
      for (Registrar registrar
          : Registrar.loadByClientIdRange(
              partialStringQuery.getInitialString(),
              partialStringQuery.getNextInitialString(),
              rdapResultSetMaxSize)) {
        if (registrar.isActiveAndPubliclyVisible()) {
          builder.add(RdapJsonFormatter.makeRdapJsonForRegistrar(
              registrar, false, rdapLinkBase, rdapWhoisServer));
        }
      }
      // In theory, there could be more results than our max size, so limit the size.
      ImmutableList<ImmutableMap<String, Object>> resultSet = builder.build();
      return (resultSet.size() <= rdapResultSetMaxSize)
          ? resultSet
          : resultSet.subList(0, rdapResultSetMaxSize);
    // Don't allow suffixes in entity handle search queries.
    } else {
      throw new UnprocessableEntityException("Suffixes not allowed in entity handle searches");
    }
  }
}
