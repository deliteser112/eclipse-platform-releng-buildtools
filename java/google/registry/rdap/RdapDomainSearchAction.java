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

import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.index.ForeignKeyIndex.loadAndGetKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.rdap.RdapIcannStandardInformation.TRUNCATION_NOTICES;
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
import com.googlecode.objectify.cmd.Query;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.EppResourceUtils;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.rdap.RdapJsonFormatter.BoilerplateType;
import google.registry.rdap.RdapJsonFormatter.OutputDataType;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.request.HttpException.UnprocessableEntityException;
import google.registry.request.Parameter;
import google.registry.request.auth.Auth;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.Idn;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * RDAP (new WHOIS) action for domain search requests.
 *
 * <p>All commands and responses conform to the RDAP spec as defined in RFCs 7480 through 7485.
 *
 * @see <a href="http://tools.ietf.org/html/rfc7482">RFC 7482: Registration Data Access Protocol
 *     (RDAP) Query Format</a>
 * @see <a href="http://tools.ietf.org/html/rfc7483">RFC 7483: JSON Responses for the Registration
 *     Data Access Protocol (RDAP)</a>
 */
@Action(
  path = RdapDomainSearchAction.PATH,
  method = {GET, HEAD},
  auth = Auth.AUTH_PUBLIC
)
public class RdapDomainSearchAction extends RdapActionBase {

  public static final String PATH = "/rdap/domains";

  public static final int RESULT_SET_SIZE_SCALING_FACTOR = 30;

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject Clock clock;
  @Inject @Parameter("name") Optional<String> nameParam;
  @Inject @Parameter("nsLdhName") Optional<String> nsLdhNameParam;
  @Inject @Parameter("nsIp") Optional<InetAddress> nsIpParam;
  @Inject @Config("rdapResultSetMaxSize") int rdapResultSetMaxSize;
  @Inject RdapDomainSearchAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "domain search";
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  /**
   * Parses the parameters and calls the appropriate search function.
   *
   * <p>The RDAP spec allows for domain search by domain name, nameserver name or nameserver IP.
   */
  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest, String linkBase) {
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
    RdapSearchResults results;
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
    if (results.jsonList().isEmpty()) {
      throw new NotFoundException("No domains found");
    }
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("domainSearchResults", results.jsonList());
    rdapJsonFormatter.addTopLevelEntries(
        builder,
        BoilerplateType.DOMAIN,
        results.isTruncated()
            ? TRUNCATION_NOTICES : ImmutableList.<ImmutableMap<String, Object>>of(),
        ImmutableList.<ImmutableMap<String, Object>>of(),
        rdapLinkBase);
    return builder.build();
  }

  /**
   * Searches for domains by domain name, returning a JSON array of domain info maps.
   *
   * <p>Domain query strings with wildcards are allowed to have a suffix after the wildcard, which
   * must be a TLD. If the TLD is not present, the wildcard must be preceded by at least two
   * characters (e.g. "ex*"), to avoid queries for all domains in the system. If the TLD is present,
   * the initial string is not required (e.g. "*.tld" is valid), because the search will be
   * restricted to a single TLD.
   */
  private RdapSearchResults searchByDomainName(
      final RdapSearchPattern partialStringQuery, final DateTime now) {
    // Handle queries without a wildcard -- just load by foreign key.
    if (!partialStringQuery.getHasWildcard()) {
      DomainResource domainResource =
          loadByForeignKey(DomainResource.class, partialStringQuery.getInitialString(), now);
      ImmutableList<DomainResource> results = (domainResource == null)
          ? ImmutableList.<DomainResource>of()
          : ImmutableList.of(domainResource);
      return makeSearchResults(results, false /* isTruncated */, now);
    // Handle queries with a wildcard and no initial string.
    } else if (partialStringQuery.getInitialString().isEmpty()) {
      if (partialStringQuery.getSuffix() == null) {
        throw new UnprocessableEntityException(
            "Initial search string is required for wildcard domain searches without a TLD suffix");
      }
      // Since we aren't searching on fullyQualifiedDomainName, we can perform our one allowed
      // inequality query on deletion time.
      Query<DomainResource> query = ofy().load()
          .type(DomainResource.class)
          .filter("tld", partialStringQuery.getSuffix())
          .filter("deletionTime >", now)
          .limit(rdapResultSetMaxSize + 1);
      return makeSearchResults(query.list(), false /* isTruncated */, now);
    // Handle queries with a wildcard and an initial string.
    } else {
      if ((partialStringQuery.getSuffix() == null)
          && (partialStringQuery.getInitialString().length()
              < RdapSearchPattern.MIN_INITIAL_STRING_LENGTH)) {
        throw new UnprocessableEntityException(
            String.format(
                "Initial search string must be at least %d characters for wildcard domain searches"
                    + " without a TLD suffix",
                RdapSearchPattern.MIN_INITIAL_STRING_LENGTH));
      }

      // We can't query for undeleted domains as part of the query itself; that would require an
      // inequality query on deletion time, and we are already using inequality queries on
      // fullyQualifiedDomainName. So we instead pick an arbitrary limit of
      // RESULT_SET_SIZE_SCALING_FACTOR times the result set size limit, fetch up to that many, and
      // weed out all deleted domains. If there still isn't a full result set's worth of domains, we
      // give up and return just the ones we found.
      // TODO(b/31546493): Add metrics to figure out how well this works.
      List<DomainResource> domainList = new ArrayList<>();
      Query<DomainResource> query = ofy().load()
          .type(DomainResource.class)
          .filter("fullyQualifiedDomainName <", partialStringQuery.getNextInitialString())
          .filter("fullyQualifiedDomainName >=", partialStringQuery.getInitialString());
      if (partialStringQuery.getSuffix() != null) {
        query = query.filter("tld", partialStringQuery.getSuffix());
      }
      // TODO(mountford): Investigate fetching by foreign key instead of the domain itself.
      for (DomainResource domain :
          query.limit(RESULT_SET_SIZE_SCALING_FACTOR * rdapResultSetMaxSize)) {
        if (EppResourceUtils.isActive(domain, now)) {
          if (domainList.size() >= rdapResultSetMaxSize) {
            return makeSearchResults(ImmutableList.copyOf(domainList), true /* isTruncated */, now);
          }
          domainList.add(domain);
        }
      }
      return makeSearchResults(domainList, false /* isTruncated */, now);
    }
  }

  /**
   * Searches for domains by nameserver name, returning a JSON array of domain info maps.
   *
   * <p>This is a two-step process: get a list of host references by host name, and then look up
   * domains by host reference.
   */
  private RdapSearchResults searchByNameserverLdhName(
      final RdapSearchPattern partialStringQuery, final DateTime now) {
    Iterable<Key<HostResource>> hostKeys = getNameserverRefsByLdhName(partialStringQuery, now);
    if (Iterables.isEmpty(hostKeys)) {
      throw new NotFoundException("No matching nameservers found");
    }
    return searchByNameserverRefs(hostKeys, now);
  }

  /**
   * Assembles a list of {@link HostResource} keys by name.
   *
   * <p>Nameserver query strings with wildcards are allowed to have a suffix after the wildcard,
   * which must be a domain. If the domain is not specified, or is not an existing domain in one of
   * our TLDs, the wildcard must be preceded by at least two characters (e.g. "ns*"), to avoid
   * queries for all nameservers in the system. If the suffix specifies an existing domain, the
   * initial string is not required (e.g. "*.example.tld" is valid), because we can look up the
   * domain and just list all of its subordinate hosts.
   */
  private Iterable<Key<HostResource>> getNameserverRefsByLdhName(
      final RdapSearchPattern partialStringQuery, final DateTime now) {
    // Handle queries without a wildcard; just load the host by foreign key in the usual way.
    if (!partialStringQuery.getHasWildcard()) {
      Key<HostResource> hostKey = loadAndGetKey(
          HostResource.class, partialStringQuery.getInitialString(), now);
      if (hostKey == null) {
        return ImmutableList.of();
      } else {
        return ImmutableList.of(hostKey);
      }
    // Handle queries with a wildcard.
    } else {
      // If there is a suffix, it must be a domain. If it happens to be a domain that we manage,
      // we can look up the domain and look through the subordinate hosts. This is more efficient,
      // and lets us permit wildcard searches with no initial string.
      if (partialStringQuery.getSuffix() != null) {
        DomainResource domainResource = loadByForeignKey(
            DomainResource.class, partialStringQuery.getSuffix(), now);
        if (domainResource != null) {
          ImmutableList.Builder<Key<HostResource>> builder = new ImmutableList.Builder<>();
          for (String fqhn : ImmutableSortedSet.copyOf(domainResource.getSubordinateHosts())) {
            // We can't just check that the host name starts with the initial query string, because
            // then the query ns.exam*.example.com would match against nameserver ns.example.com.
            if (partialStringQuery.matches(fqhn)) {
              Key<HostResource> hostKey = loadAndGetKey(HostResource.class, fqhn, now);
              if (hostKey != null) {
                builder.add(hostKey);
              } else {
                logger.warningfmt("Host key unexpectedly null");
              }
            }
          }
          return builder.build();
        }
      }
      // If there's no suffix, or it isn't a domain we manage, query the host resources. Query the
      // resources themselves, rather than the foreign key indexes, because then we have an index on
      // fully qualified host name and deletion time, so we can check the deletion status in the
      // query itself. There are no pending deletes for hosts, so we can call queryUndeleted. In
      // this case, the initial string must be present, to avoid querying every host in the system.
      // This restriction is enforced by queryUndeleted().
      // TODO (b/24463238): figure out how to limit the size of these queries effectively
      Iterable<Key<HostResource>> keys =
          queryUndeleted(HostResource.class, "fullyQualifiedHostName", partialStringQuery, 1000)
              .keys();
      // queryUndeleted() ignores suffixes, so if one was specified, we must filter on the partial
      // string query.
      if (partialStringQuery.getSuffix() == null) {
        return keys;
      } else {
        ImmutableList.Builder<Key<HostResource>> filteredKeys = new ImmutableList.Builder<>();
        for (Key<HostResource> key : keys) {
          if (partialStringQuery.matches(key.getName())) {
            filteredKeys.add(key);
          }
        }
        return filteredKeys.build();
      }
    }
  }

  /**
   * Searches for domains by nameserver address, returning a JSON array of domain info maps.
   *
   * <p>This is a two-step process: get a list of host references by IP address, and then look up
   * domains by host reference.
   */
  private RdapSearchResults searchByNameserverIp(
      final InetAddress inetAddress, final DateTime now) {
    // In theory, we could filter on the deletion time being in the future. But we can't do that in
    // the query on nameserver name (because we're already using an inequality query), and it seems
    // dangerous and confusing to filter on deletion time differently between the two queries.
    // Find all domains that link to any of these hosts, and return information about them.
    // TODO (b/24463238): figure out how to limit the size of these queries effectively
    return searchByNameserverRefs(
        ofy()
            .load()
            .type(HostResource.class)
            .filter("inetAddresses", inetAddress.getHostAddress())
            .filter("deletionTime", END_OF_TIME)
            .limit(1000)
            .keys(),
        now);
  }

  /**
   * Locates all domains which are linked to a set of host keys.
   *
   * <p>This method is called by {@link #searchByNameserverLdhName} and
   * {@link #searchByNameserverIp} after they assemble the relevant host keys.
   */
  private RdapSearchResults searchByNameserverRefs(
      final Iterable<Key<HostResource>> hostKeys, final DateTime now) {
    // We must break the query up into chunks, because the in operator is limited to 30 subqueries.
    // Since it is possible for the same domain to show up more than once in our result list (if
    // we do a wildcard nameserver search that returns multiple nameservers used by the same
    // domain), we must create a set of resulting {@link DomainResource} objects. But we use a
    // LinkedHashSet to preserve the order in which we found the domains.
    LinkedHashSet<DomainResource> domains = new LinkedHashSet<>();
    for (List<Key<HostResource>> chunk : Iterables.partition(hostKeys, 30)) {
      for (DomainResource domain : ofy().load()
          .type(DomainResource.class)
          .filter("nsHosts in", chunk)
          .filter("deletionTime >", now)
          .limit(rdapResultSetMaxSize + 1)) {
        if (!domains.contains(domain)) {
          if (domains.size() >= rdapResultSetMaxSize) {
            return makeSearchResults(ImmutableList.copyOf(domains), true /* isTruncated */, now);
          }
          domains.add(domain);
        }
      }
    }
    return makeSearchResults(ImmutableList.copyOf(domains), false /* isTruncated */, now);
  }

  /**
   * Output JSON for a list of domains.
   *
   * <p>The isTruncated parameter should be true if the search found more results than are in the
   * list, meaning that the truncation notice should be added.
   */
  private RdapSearchResults makeSearchResults(
      List<DomainResource> domains, boolean isTruncated, DateTime now) {
    OutputDataType outputDataType =
        (domains.size() > 1) ? OutputDataType.SUMMARY : OutputDataType.FULL;
    RdapAuthorization authorization = getAuthorization();
    ImmutableList.Builder<ImmutableMap<String, Object>> jsonBuilder = new ImmutableList.Builder<>();
    for (DomainResource domain : domains) {
      jsonBuilder.add(
          rdapJsonFormatter.makeRdapJsonForDomain(
              domain, false, rdapLinkBase, rdapWhoisServer, now, outputDataType, authorization));
    }
    return RdapSearchResults.create(jsonBuilder.build(), isTruncated);
  }
}
