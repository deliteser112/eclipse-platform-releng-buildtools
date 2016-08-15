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

import static google.registry.model.EppResourceUtils.loadByUniqueId;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Booleans;
import com.googlecode.objectify.cmd.Query;
import google.registry.config.ConfigModule.Config;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.rdap.RdapJsonFormatter.BoilerplateType;
import google.registry.request.Action;
import google.registry.request.HttpException;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.request.Parameter;
import google.registry.util.Clock;
import google.registry.util.Idn;
import java.net.InetAddress;
import javax.inject.Inject;
import org.joda.time.DateTime;

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
      if (!LDH_PATTERN.matcher(nameParam.get()).matches()) {
        throw new BadRequestException(
            "Name parameter must contain only letters, dots"
                + " and hyphens, and an optional single wildcard");
      }
      results = searchByName(RdapSearchPattern.create(Idn.toASCII(nameParam.get()), true), now);
    } else {
      // syntax: /rdap/nameservers?ip=1.2.3.4
      results = searchByIp(ipParam.get(), now);
    }
    if (results.isEmpty()) {
      throw new NotFoundException("No nameservers found");
    }
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("nameserverSearchResults", results);
    RdapJsonFormatter.addTopLevelEntries(builder, BoilerplateType.NAMESERVER, null, rdapLinkBase);
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
              hostResource, false, rdapLinkBase, rdapWhoisServer, now));
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
                hostResource, false, rdapLinkBase, rdapWhoisServer, now));
      }
      return builder.build();
    // Handle queries with a wildcard and a suffix. In this case, it is more efficient to do things
    // differently. We use the suffix to look up the domain, then loop through the subordinate hosts
    // looking for matches.
    } else {
      DomainResource domainResource =
          loadByUniqueId(DomainResource.class, partialStringQuery.getSuffix(), now);
      if (domainResource == null) {
        throw new NotFoundException("No domain found for specified nameserver suffix");
      }
      ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
      for (String fqhn : ImmutableSortedSet.copyOf(domainResource.getSubordinateHosts())) {
        // We can't just check that the host name starts with the initial query string, because then
        // the query ns.exam*.example.com would match against nameserver ns.example.com.
        if (partialStringQuery.matches(fqhn)) {
          HostResource hostResource = loadByUniqueId(HostResource.class, fqhn, now);
          if (hostResource != null) {
            builder.add(
                RdapJsonFormatter.makeRdapJsonForHost(
                    hostResource, false, rdapLinkBase, rdapWhoisServer, now));
          }
        }
      }
      return builder.build();
    }
  }

  /** Searches for nameservers by IP address, returning a JSON array of nameserver info maps. */
  private ImmutableList<ImmutableMap<String, Object>>
      searchByIp(final InetAddress inetAddress, DateTime now) throws HttpException {
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
              hostResource, false, rdapLinkBase, rdapWhoisServer, now));
    }
    return builder.build();
  }
}
