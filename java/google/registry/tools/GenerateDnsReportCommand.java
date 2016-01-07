// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools;

import static com.google.common.io.BaseEncoding.base16;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.util.DateTimeUtils.isBeforeOrAt;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.host.HostResource;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import google.registry.util.Clock;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;

/** Command to generate a report of all DNS data. */
@Parameters(separators = " =", commandDescription = "Generate report of all DNS data in a TLD.")
final class GenerateDnsReportCommand implements RemoteApiCommand {

  @Parameter(
      names = {"-t", "--tld"},
      description = "Target TLD.",
      required = true)
  private String tld;

  @Parameter(
      names = {"-o", "--output"},
      description = "Output file.",
      validateWith = PathParameter.OutputFile.class)
  private Path output = Paths.get("/dev/stdout");

  @Inject
  Clock clock;

  @Override
  public void run() throws Exception {
    assertTldExists(tld);
    Files.write(output, new Generator().generate().getBytes(US_ASCII));
  }

  private class Generator {
    private final DateTime now = clock.nowUtc();
    private final StringBuilder result = new StringBuilder();
    private boolean first = true;

    String generate() {
      result.append("[\n");

      Iterable<DomainResource> domains = ofy().load().type(DomainResource.class).filter("tld", tld);
      for (DomainResource domain : domains) {
        // Skip deleted domains and domains that don't get published to DNS.
        if (isBeforeOrAt(domain.getDeletionTime(), now) || !domain.shouldPublishToDns()) {
          continue;
        }
        write(domain);
      }

      Iterable<HostResource> nameservers = ofy().load().type(HostResource.class);
      for (HostResource nameserver : nameservers) {
        // Skip deleted hosts and external hosts.
        if (isBeforeOrAt(nameserver.getDeletionTime(), now)
            || nameserver.getInetAddresses().isEmpty()) {
          continue;
        }
        write(nameserver);
      }

      return result.append("\n]\n").toString();
    }

    private void write(DomainResource domain) {
      ImmutableList<String> nameservers = FluentIterable
          .from(domain.loadNameserverFullyQualifiedHostNames())
          .toSortedList(Ordering.natural());
      ImmutableList<Map<String, ?>> dsData = FluentIterable.from(domain.getDsData())
          .transform(new Function<DelegationSignerData, Map<String, ?>>() {
              @Override
              public Map<String, ?> apply(DelegationSignerData dsData) {
                return ImmutableMap.of(
                    "keyTag", dsData.getKeyTag(),
                    "algorithm", dsData.getAlgorithm(),
                    "digestType", dsData.getDigestType(),
                    "digest", base16().encode(dsData.getDigest()));
              }})
          .toList();
      ImmutableMap.Builder<String, Object> mapBuilder = new ImmutableMap.Builder<>();
      mapBuilder.put("domain", domain.getFullyQualifiedDomainName());
      if (!nameservers.isEmpty()) {
        mapBuilder.put("nameservers", nameservers);
      }
      if (!dsData.isEmpty()) {
        mapBuilder.put("dsData", dsData);
      }
      writeJson(mapBuilder.build());
    }

    private void write(HostResource nameserver) {
      ImmutableList<String> ipAddresses = FluentIterable.from(nameserver.getInetAddresses())
          .transform(new Function<InetAddress, String>() {
              @Override
              public String apply(InetAddress inetAddress) {
                return inetAddress.getHostAddress();
              }})
          .toSortedList(Ordering.natural());
      ImmutableMap<String, ?> map  = ImmutableMap.of(
          "host", nameserver.getFullyQualifiedHostName(),
          "ips", ipAddresses);
      writeJson(map);
    }

    private void writeJson(Map<String, ?> map) {
      if (first) {
        first = false;
      } else {
        result.append(",\n");
      }
      result.append(JSONValue.toJSONString(map));
    }
  }
}
