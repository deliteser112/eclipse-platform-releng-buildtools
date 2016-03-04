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

package com.google.domain.registry.rdap;

import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.request.Action.Method.GET;
import static com.google.domain.registry.request.Action.Method.HEAD;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import com.google.domain.registry.config.ConfigModule.Config;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DesignatedContact;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.rdap.RdapJsonFormatter.BoilerplateType;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.HttpException;
import com.google.domain.registry.request.HttpException.BadRequestException;
import com.google.domain.registry.request.HttpException.NotFoundException;
import com.google.domain.registry.request.HttpException.NotImplementedException;
import com.google.domain.registry.request.HttpException.UnprocessableEntityException;
import com.google.domain.registry.request.Parameter;
import com.google.domain.registry.util.Clock;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

import java.net.IDN;

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
      throw new NotImplementedException("Entity name search not implemented");
    } else {
      // syntax: /rdap/entities?handle=12345-*
      results = searchByHandle(RdapSearchPattern.create(IDN.toASCII(handleParam.get()), false));
    }
    if (results.isEmpty()) {
      throw new NotFoundException("No entities found");
    }
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("entitySearchResults", results);
    RdapJsonFormatter.addTopLevelEntries(builder, BoilerplateType.OTHER, null, rdapLinkBase);
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
