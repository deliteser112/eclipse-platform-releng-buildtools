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
import static google.registry.model.index.ForeignKeyIndex.loadAndGetReference;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;
import static google.registry.util.DateTimeUtils.END_OF_TIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Booleans;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import com.googlecode.objectify.cmd.Query;
import com.googlecode.objectify.cmd.QueryKeys;
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
import java.util.List;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * RDAP (new WHOIS) action for domain search requests.
 *
 * <p>All commands and responses conform to the RDAP spec as defined in RFCs 7480 through 7485.
 *
 * @see <a href="http://tools.ietf.org/html/rfc7482">
 *        RFC 7482: Registration Data Access Protocol (RDAP) Query Format</a>
 * @see <a href="http://tools.ietf.org/html/rfc7483">
 *        RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
 */
@Action(path = RdapDomainSearchAction.PATH, method = {GET, HEAD}, isPrefix = true)
public class RdapDomainSearchAction extends RdapActionBase {

  public static final String PATH = "/rdap/domains";

  @Inject Clock clock;
  @Inject @Parameter("name") Optional<String> nameParam;
  @Inject @Parameter("nsLdhName") Optional<String> nsLdhNameParam;
  @Inject @Parameter("nsIp") Optional<InetAddress> nsIpParam;
  @Inject RdapDomainSearchAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "domain search";
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
    // RDAP syntax example: /rdap/domains?name=exam*.com.
    // The pathSearchString is not used by search commands.
    if (pathSearchString.length() > 0) {
      throw new BadRequestException("Unexpected path");
    }
    if (Booleans.countTrue(nameParam.isPresent(), nsLdhNameParam.isPresent(), nsIpParam.isPresent())
        != 1) {
      throw new BadRequestException(
          "You must specify either name=XXXX, nsLdhName=YYYY or nsIp=ZZZZ");
    }
    ImmutableList<ImmutableMap<String, Object>> results;
    if (nameParam.isPresent()) {
      // syntax: /rdap/domains?name=exam*.com
      String asciiName;
      try {
        asciiName = Idn.toASCII(nameParam.get());
      } catch (Exception e) {
        throw new BadRequestException("Invalid value of nsLdhName parameter");
      }
      results = searchByDomainName(RdapSearchPattern.create(asciiName, true), now);
    } else if (nsLdhNameParam.isPresent()) {
      // syntax: /rdap/domains?nsLdhName=ns1.exam*.com
      // RFC 7482 appears to say that Unicode domains must be specified using punycode when
      // passed to nsLdhName, so IDN.toASCII is not called here.
      if (!LDH_PATTERN.matcher(nsLdhNameParam.get()).matches()) {
        throw new BadRequestException("Invalid value of nsLdhName parameter");
      }
      results = searchByNameserverLdhName(
          RdapSearchPattern.create(nsLdhNameParam.get(), true), now);
    } else {
      // syntax: /rdap/domains?nsIp=1.2.3.4
      results = searchByNameserverIp(nsIpParam.get(), now);
    }
    if (results.isEmpty()) {
      throw new NotFoundException("No domains found");
    }
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("domainSearchResults", results);
    RdapJsonFormatter.addTopLevelEntries(builder, BoilerplateType.DOMAIN, null, rdapLinkBase);
    return builder.build();
  }

  /** Searches for domains by domain name, returning a JSON array of domain info maps. */
  private ImmutableList<ImmutableMap<String, Object>>
      searchByDomainName(final RdapSearchPattern partialStringQuery, final DateTime now) {
    // Handle queries without a wildcard -- just load by foreign key.
    if (!partialStringQuery.getHasWildcard()) {
      DomainResource domainResource =
          loadByUniqueId(DomainResource.class, partialStringQuery.getInitialString(), now);
      if (domainResource == null) {
        return ImmutableList.of();
      }
      return ImmutableList.of(
          RdapJsonFormatter.makeRdapJsonForDomain(
              domainResource, false, rdapLinkBase, rdapWhoisServer));
    // Handle queries with a wildcard.
    } else {
      Query<DomainResource> query = ofy().load()
          .type(DomainResource.class)
          // TODO(b/24463238): figure out how to limit the size of these queries effectively
          .filter("fullyQualifiedDomainName >=", partialStringQuery.getInitialString())
          .filter("fullyQualifiedDomainName <", partialStringQuery.getNextInitialString())
          .limit(1000);
      if (partialStringQuery.getSuffix() != null) {
        query = query.filter("tld", partialStringQuery.getSuffix());
      }
      ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
      for (DomainResource domainResource : query) {
        if (domainResource.getDeletionTime().isAfter(now)) {
          builder.add(
              RdapJsonFormatter.makeRdapJsonForDomain(
                  domainResource, false, rdapLinkBase, rdapWhoisServer));
        }
      }
      return builder.build();
    }
  }

  /** Searches for domains by nameserver name, returning a JSON array of domain info maps. */
  private ImmutableList<ImmutableMap<String, Object>>
      searchByNameserverLdhName(final RdapSearchPattern partialStringQuery, final DateTime now)
          throws HttpException {
    ImmutableList<Ref<HostResource>> hostRefs;
    // Handle queries without a wildcard; just load the host by foreign key in the usual way.
    if (!partialStringQuery.getHasWildcard()) {
      Ref<HostResource> hostRef = loadAndGetReference(
          HostResource.class, partialStringQuery.getInitialString(), clock.nowUtc());
      if (hostRef == null) {
        return ImmutableList.of();
      }
      hostRefs = ImmutableList.of(hostRef);
    // Handle queries with a wildcard, but no suffix. Query the host resources themselves, rather
    // than the foreign key index, because then we have an index on fully qualified host name and
    // deletion time, so we can check the deletion status in the query itself. There are no pending
    // deletes for hosts, so we can call queryUndeleted.
    } else if (partialStringQuery.getSuffix() == null) {
      // TODO (b/24463238): figure out how to limit the size of these queries effectively
      Query<HostResource> query = queryUndeleted(
          HostResource.class,
          "fullyQualifiedHostName",
          partialStringQuery, 1000);
      ImmutableList.Builder<Ref<HostResource>> builder = new ImmutableList.Builder<>();
      for (Key<HostResource> hostResourceKey : query.keys()) {
        builder.add(Ref.create(hostResourceKey));
      }
      hostRefs = builder.build();
      if (hostRefs.isEmpty()) {
        throw new NotFoundException("No matching nameservers found");
      }
    // Handle queries with a wildcard and a suffix. In this case, it is more efficient to do things
    // differently. We use the suffix to look up the domain, then loop through the subordinate hosts
    // looking for matches.
    } else {
      DomainResource domainResource = loadByUniqueId(
          DomainResource.class, partialStringQuery.getSuffix(), clock.nowUtc());
      if (domainResource == null) {
        throw new NotFoundException("No domain found for specified nameserver suffix");
      }
      ImmutableList.Builder<Ref<HostResource>> builder = new ImmutableList.Builder<>();
      for (String fqhn : ImmutableSortedSet.copyOf(domainResource.getSubordinateHosts())) {
        // We can't just check that the host name starts with the initial query string, because then
        // the query ns.exam*.example.com would match against nameserver ns.example.com.
        if (partialStringQuery.matches(fqhn)) {
          Ref<HostResource> hostRef = loadAndGetReference(HostResource.class, fqhn, clock.nowUtc());
          if (hostRef != null) {
            builder.add(hostRef);
          }
        }
      }
      hostRefs = builder.build();
      if (hostRefs.isEmpty()) {
        throw new NotFoundException("No matching nameservers found");
      }
    }
    // Find all domains that link to any of these hosts, and return information about them.
    return searchByNameserverRefs(hostRefs, now);
  }

  /** Searches for domains by nameserver address, returning a JSON array of domain info maps. */
  private ImmutableList<ImmutableMap<String, Object>>
      searchByNameserverIp(final InetAddress inetAddress, final DateTime now) {
    // In theory, we could filter on the deletion time being in the future. But we can't do that in
    // the query on nameserver name (because we're already using an inequality query), and it seems
    // dangerous and confusing to filter on deletion time differently between the two queries.
    QueryKeys<HostResource> query = ofy()
        .load()
        .type(HostResource.class)
        .filter("inetAddresses", inetAddress.getHostAddress())
        .filter("deletionTime", END_OF_TIME)
        .limit(1000)
        .keys();
    ImmutableList.Builder<Ref<HostResource>> builder = new ImmutableList.Builder<>();
    for (Key<HostResource> key : query) {
      builder.add(Ref.create(key));
    }
    ImmutableList<Ref<HostResource>> hostRefs = builder.build();
    if (hostRefs.isEmpty()) {
      return ImmutableList.of();
    }
    // Find all domains that link to any of these hosts, and return information about them.
    return searchByNameserverRefs(hostRefs, now);
  }

  /**
   * Locates all domains which are linked to a set of host refs. This method is called by
   * {@link #searchByNameserverLdhName} and {@link #searchByNameserverIp} after they assemble the
   * relevant host refs.
   */
  private ImmutableList<ImmutableMap<String, Object>>
      searchByNameserverRefs(final Iterable<Ref<HostResource>> hostRefs, final DateTime now) {
    // We must break the query up into chunks, because the in operator is limited to 30 subqueries.
    ImmutableList.Builder<ImmutableMap<String, Object>> builder = new ImmutableList.Builder<>();
    for (List<Ref<HostResource>> chunk : Iterables.partition(hostRefs, 30)) {
      Query<DomainResource> query = ofy().load()
          .type(DomainResource.class)
          .filter("nameservers.linked in", chunk)
          .filter("deletionTime >", now)
          .limit(1000);
      for (DomainResource domainResource : query) {
        builder.add(
            RdapJsonFormatter.makeRdapJsonForDomain(
                domainResource, false, rdapLinkBase, rdapWhoisServer));
      }
    }
    return builder.build();
  }
}
