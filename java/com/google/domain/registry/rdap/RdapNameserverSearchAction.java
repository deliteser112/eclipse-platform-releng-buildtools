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

import static com.google.domain.registry.model.EppResourceUtils.loadByUniqueId;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.request.Action.Method.GET;
import static com.google.domain.registry.request.Action.Method.HEAD;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Booleans;
import com.google.domain.registry.config.ConfigModule.Config;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.rdap.RdapJsonFormatter.BoilerplateType;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.HttpException;
import com.google.domain.registry.request.HttpException.BadRequestException;
import com.google.domain.registry.request.HttpException.NotFoundException;
import com.google.domain.registry.request.Parameter;
import com.google.domain.registry.util.Clock;

import com.googlecode.objectify.cmd.Query;

import org.joda.time.DateTime;

import java.net.IDN;
import java.net.InetAddress;

import javax.inject.Inject;

/**
 * RDAP (new WHOIS) action for nameserver search requests.
 *
 * <p>All commands and responses conform to the RDAP spec as defined in RFCs 7480 through 7485.
 *
 * @see <a href="http://tools.ietf.org/html/rfc7482">
 *        RFC 7482: Registration Data Access Protocol (RDAP) Query Format</a>
 * @see <a href="http://tools.ietf.org/html/rfc7483">
 *        RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
 */
@Action(path = RdapNameserverSearchAction.PATH, method = {GET, HEAD}, isPrefix = true)
public class RdapNameserverSearchAction extends RdapActionBase {

  public static final String PATH = "/rdap/nameservers";

  @Inject Clock clock;
  @Inject @Parameter("name") Optional<String> nameParam;
  @Inject @Parameter("ip") Optional<InetAddress> ipParam;
  @Inject @Config("rdapResultSetMaxSize") int rdapResultSetMaxSize;
  @Inject RdapNameserverSearchAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "nameserver search";
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  /** Parses the parameters and calls the appropriate search function. */
  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest, String linkBase) throws HttpException {
    DateTime now = clock.nowUtc();
    // RDAP syntax example: /rdap/nameservers?name=ns*.example.com.
    // The pathSearchString is not used by search commands.
    if (pathSearchString.length() > 0) {
      throw new BadRequestException("Unexpected path");
    }
    if (Booleans.countTrue(nameParam.isPresent(), ipParam.isPresent()) != 1) {
      throw new BadRequestException("You must specify either name=XXXX or ip=YYYY");
    }
    ImmutableList<ImmutableMap<String, Object>> results;
    if (nameParam.isPresent()) {
      // syntax: /rdap/nameservers?name=exam*.com
      // TODO(b/24329745): improve IDN handling
      if (!LDH_PATTERN.matcher(nameParam.get()).matches()) {
        throw new BadRequestException(
            "Name parameter must contain only letters, dots"
                + " and hyphens, and an optional single wildcard");
      }
      results = searchByName(RdapSearchPattern.create(IDN.toASCII(nameParam.get()), true), now);
    } else {
      // syntax: /rdap/nameservers?ip=1.2.3.4
      results = searchByIp(ipParam.get());
    }
    if (results.isEmpty()) {
      throw new NotFoundException("No nameservers found");
    }
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("nameserverSearchResults", results);
    RdapJsonFormatter.addTopLevelEntries(builder, BoilerplateType.OTHER, null, rdapLinkBase);
    return builder.build();
  }

  /** Searches for nameservers by name, returning a JSON array of nameserver info maps. */
  private ImmutableList<ImmutableMap<String, Object>>
      searchByName(final RdapSearchPattern partialStringQuery, final DateTime now)
          throws HttpException {
    // Handle queries without a wildcard -- just load by foreign key.
    if (!partialStringQuery.getHasWildcard()) {
      HostResource hostResource =
          loadByUniqueId(HostResource.class, partialStringQuery.getInitialString(), now);
      if (hostResource == null) {
        throw new NotFoundException("No nameservers found");
      }
      return ImmutableList.of(
          RdapJsonFormatter.makeRdapJsonForHost(
              hostResource, false, rdapLinkBase, rdapWhoisServer));
    // Handle queries with a wildcard, but no suffix. There are no pending deletes for hosts, so we
    // can call queryUndeleted.
    } else if (partialStringQuery.getSuffix() == null) {
      Query<HostResource> query = queryUndeleted(
          HostResource.class,
          "fullyQualifiedHostName",
          partialStringQuery, rdapResultSetMaxSize);
      ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
      for (HostResource hostResource : query) {
        builder.add(
            RdapJsonFormatter.makeRdapJsonForHost(
                hostResource, false, rdapLinkBase, rdapWhoisServer));
      }
      return builder.build();
    // Handle queries with a wildcard and a suffix. In this case, it is more efficient to do things
    // differently. We use the suffix to look up the domain, then loop through the subordinate hosts
    // looking for matches.
    } else {
      DomainResource domainResource =
          loadByUniqueId(DomainResource.class, partialStringQuery.getSuffix(), clock.nowUtc());
      if (domainResource == null) {
        throw new NotFoundException("No domain found for specified nameserver suffix");
      }
      ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
      for (String fqhn : ImmutableSortedSet.copyOf(domainResource.getSubordinateHosts())) {
        // We can't just check that the host name starts with the initial query string, because then
        // the query ns.exam*.example.com would match against nameserver ns.example.com.
        if (partialStringQuery.matches(fqhn)) {
          HostResource hostResource = loadByUniqueId(HostResource.class, fqhn, clock.nowUtc());
          if (hostResource != null) {
            builder.add(
                RdapJsonFormatter.makeRdapJsonForHost(
                    hostResource, false, rdapLinkBase, rdapWhoisServer));
          }
        }
      }
      return builder.build();
    }
  }

  /** Searches for nameservers by IP address, returning a JSON array of nameserver info maps. */
  private ImmutableList<ImmutableMap<String, Object>>
      searchByIp(final InetAddress inetAddress) throws HttpException {
    // In theory, we could filter on deletion time being in the future. But we can't do that in the
    // name query above (because we already have an inequality filter), and filtering on deletion
    // time differently in the two cases seems like a recipe for future confusion.
    Query<HostResource> query = ofy()
        .load()
        .type(HostResource.class)
        .filter("inetAddresses", inetAddress.getHostAddress())
        .filter("deletionTime", END_OF_TIME)
        .limit(rdapResultSetMaxSize);
    ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
    for (HostResource hostResource : query) {
      builder.add(
          RdapJsonFormatter.makeRdapJsonForHost(
              hostResource, false, rdapLinkBase, rdapWhoisServer));
    }
    return builder.build();
  }
}
