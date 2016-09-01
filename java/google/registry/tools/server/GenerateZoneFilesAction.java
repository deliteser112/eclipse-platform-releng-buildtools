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

package google.registry.tools.server;

import static com.google.appengine.tools.cloudstorage.GcsServiceFactory.createGcsService;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Iterators.filter;
import static com.google.common.io.BaseEncoding.base16;
import static google.registry.mapreduce.inputs.EppResourceInputs.createEntityInput;
import static google.registry.model.EppResourceUtils.loadAtPointInTime;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.POST;
import static google.registry.util.PipelineUtils.createJobPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.config.ConfigModule.Config;
import google.registry.gcs.GcsUtils;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.NullInput;
import google.registry.model.EppResource;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.host.HostResource;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.JsonActionRunner;
import google.registry.util.Clock;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * MapReduce that requests generation of BIND zone files for a set of TLDs at a given time.
 *
 * <p>Zone files for each requested TLD are written to GCS. TLDs without entries produce zone files
 * with only a header. The export time must be at least two minutes in the past and no more than
 * 29 days in the past, and must be at midnight UTC.
 */
@Action(
    path = GenerateZoneFilesAction.PATH,
    method = POST,
    xsrfProtection = true,
    xsrfScope = "admin")
public class GenerateZoneFilesAction implements Runnable, JsonActionRunner.JsonAction {

  public static final String PATH = "/_dr/task/generateZoneFiles";

  /** Format for the zone file name. */
  private static final String FILENAME_FORMAT = "%s-%s.zone";

  /** Format for the GCS path to a file. */
  private static final String GCS_PATH_FORMAT = "gs://%s/%s";

  /** Format for the zone file header. */
  private static final String HEADER_FORMAT = "$ORIGIN\t%s.\n\n";

  /** Format for NS records. */
  private static final String NS_FORMAT = "%s\t%d\tIN\tNS\t%s.\n";

  /** Format for DS records. */
  private static final String DS_FORMAT = "%s\t%d\tIN\tDS\t%d %d %d %s\n";

  /** Format for A and AAAA records. */
  private static final String A_FORMAT = "%s\t%d\tIN\t%s\t%s\n";

  // TODO(b/20454352): Overhaul TTL configuration mechanism.
  /** The time to live for exported NS record, in seconds. */
  private static final int TTL_NS = 180;

  /** The time to live for exported DS record, in seconds. */
  private static final int TTL_DS = 86400;

  /** The time to live for exported A/AAAA record, in seconds. */
  private static final int TTL_A = 3600;

  @Inject MapreduceRunner mrRunner;
  @Inject JsonActionRunner jsonActionRunner;
  @Inject @Config("zoneFilesBucket") String bucket;
  @Inject @Config("gcsBufferSize") int gcsBufferSize;
  @Inject @Config("commitLogDatastoreRetention") Duration datastoreRetention;
  @Inject Clock clock;
  @Inject GenerateZoneFilesAction() {}

  @Override
  public void run() {
    jsonActionRunner.run(this);
  }

  @Override
  public Map<String, Object> handleJsonRequest(Map<String, ?> json) {
    @SuppressWarnings("unchecked")
    ImmutableSet<String> tlds = ImmutableSet.copyOf((List<String>) json.get("tlds"));
    final DateTime exportTime = DateTime.parse(json.get("exportTime").toString());
    // We disallow exporting within the past 2 minutes because there might be outstanding writes.
    // We can only reliably call loadAtPointInTime at times that are UTC midnight and >
    // datastoreRetention ago in the past.
    DateTime now = clock.nowUtc();
    if (exportTime.isAfter(now.minusMinutes(2))) {
      throw new BadRequestException("Invalid export time: must be > 2 minutes ago");
    }
    if (exportTime.isBefore(now.minus(datastoreRetention))) {
      throw new BadRequestException(String.format(
          "Invalid export time: must be < %d days ago",
          datastoreRetention.getStandardDays()));
    }
    if (!exportTime.equals(exportTime.toDateTime(UTC).withTimeAtStartOfDay())) {
      throw new BadRequestException("Invalid export time: must be midnight UTC");
    }
    String jobId = mrRunner
        .setJobName("Generate bind file stanzas")
        .setModuleName("tools")
        .setDefaultReduceShards(tlds.size())
        .runMapreduce(
            new GenerateBindFileMapper(tlds, exportTime),
            new GenerateBindFileReducer(bucket, exportTime, gcsBufferSize),
            ImmutableList.of(
                new NullInput<EppResource>(),
                createEntityInput(DomainResource.class, HostResource.class)));
    ImmutableList<String> filenames = FluentIterable.from(tlds)
        .transform(
            new Function<String, String>() {
              @Override
              public String apply(String tld) {
                return String.format(
                    GCS_PATH_FORMAT,
                    bucket,
                    String.format(FILENAME_FORMAT, tld, exportTime));
              }})
        .toList();
    return ImmutableMap.<String, Object>of(
        "jobPath", createJobPath(jobId),
        "filenames", filenames);
  }

  /** Mapper to find domains and hosts that were active at a given time. */
  static class GenerateBindFileMapper extends Mapper<EppResource, String, String> {

    private static final long serialVersionUID = 4647941823789859913L;

    private final ImmutableSet<String> tlds;
    private final DateTime exportTime;

    GenerateBindFileMapper(ImmutableSet<String> tlds, DateTime exportTime) {
      this.tlds = tlds;
      this.exportTime = exportTime;
    }

    @Override
    public void map(EppResource resource) {
      if (resource == null) {  // Force the reducer to always generate a bind header for each tld.
        for (String tld : tlds) {
          emit(tld, null);
        }
      } else if (resource instanceof DomainResource) {
        mapDomain((DomainResource) resource);
      } else {
        mapHost((HostResource) resource);
      }
    }

    private void mapDomain(DomainResource domain) {
      // Domains never change their tld, so we can check if it's from the wrong tld right away.
      if (tlds.contains(domain.getTld())) {
        domain = loadAtPointInTime(domain, exportTime).now();
        if (domain != null) {  // A null means the domain was deleted (or not created) at this time.
          String stanza = domainStanza(domain, exportTime);
          if (!stanza.isEmpty()) {
            emit(domain.getTld(), stanza);
            getContext().incrementCounter(domain.getTld() + " domains");
          }
        }
      }
    }

    private void mapHost(HostResource host) {
      host = loadAtPointInTime(host, exportTime).now();
      if (host != null) {  // A null means the host was deleted (or not created) at this time.
        // Find a matching tld. Hosts might change their tld, so check after the point-in-time load.
        String fullyQualifiedHostName = host.getFullyQualifiedHostName();
        for (String tld : tlds) {
          if (fullyQualifiedHostName.endsWith("." + tld)) {
            String stanza = hostStanza(host);
            if (!stanza.isEmpty()) {
              emit(tld, stanza);
              getContext().incrementCounter(tld + " hosts");
            }
            return;
          }
        }
      }
    }
  }

  /** Reducer to write zone files to GCS. */
  static class GenerateBindFileReducer extends Reducer<String, String, Void> {

    private static final long serialVersionUID = -8489050680083119352L;

    private final String bucket;
    private final DateTime exportTime;
    private final int gcsBufferSize;

    GenerateBindFileReducer(String bucket, DateTime exportTime, int gcsBufferSize) {
      this.bucket = bucket;
      this.exportTime = exportTime;
      this.gcsBufferSize = gcsBufferSize;
    }

    @Override
    public void reduce(String tld, ReducerInput<String> stanzas) {
      String stanzaCounter = tld + " stanzas";
      GcsFilename filename =
          new GcsFilename(bucket, String.format(FILENAME_FORMAT, tld, exportTime));
      GcsUtils cloudStorage =
          new GcsUtils(createGcsService(RetryParams.getDefaultInstance()), gcsBufferSize);
      try (OutputStream gcsOutput = cloudStorage.openOutputStream(filename);
          Writer osWriter = new OutputStreamWriter(gcsOutput, UTF_8);
          PrintWriter writer = new PrintWriter(osWriter)) {
        writer.printf(HEADER_FORMAT, tld);
        for (Iterator<String> stanzaIter = filter(stanzas, notNull()); stanzaIter.hasNext(); ) {
          writer.println(stanzaIter.next());
          getContext().incrementCounter(stanzaCounter);
        }
        writer.flush();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Generates DNS records for a domain (NS and DS).
   *
   * These look like this:
   * {@code
   *   foo.tld 180 IN NS ns.example.com.
   *   foo.tld 86400 IN DS 1 2 3 000102
   * }
   */
  private static String domainStanza(DomainResource domain, DateTime exportTime) {
    StringBuilder result = new StringBuilder();
    for (HostResource nameserver : ofy().load().keys(domain.getNameservers()).values()) {
      result.append(String.format(
          NS_FORMAT,
          domain.getFullyQualifiedDomainName(),
          TTL_NS,
          // Load the nameservers at the export time in case they've been renamed or deleted.
          loadAtPointInTime(nameserver, exportTime).now().getFullyQualifiedHostName()));
    }
    for (DelegationSignerData dsData : domain.getDsData()) {
      result.append(String.format(
          DS_FORMAT,
          domain.getFullyQualifiedDomainName(),
          TTL_DS,
          dsData.getKeyTag(),
          dsData.getAlgorithm(),
          dsData.getDigestType(),
          base16().encode((dsData.getDigest()))));
    }
    return result.toString();
  }

  /**
   * Generates DNS records for a domain (A and AAAA).
   *
   * <p>These look like this:
   * {@code
   *   ns.foo.tld 3600 IN A 127.0.0.1
   *   ns.foo.tld 3600 IN AAAA 0:0:0:0:0:0:0:1
   * }
   */
  private static String hostStanza(HostResource host) {
    StringBuilder result = new StringBuilder();
    for (InetAddress addr : host.getInetAddresses()) {
      // must be either IPv4 or IPv6
      String rrSetClass = (addr instanceof Inet4Address) ? "A" : "AAAA";
      result.append(String.format(
          A_FORMAT,
          host.getFullyQualifiedHostName(),
          TTL_A,
          rrSetClass,
          addr.getHostAddress()));
    }
    return result.toString();
  }
}
