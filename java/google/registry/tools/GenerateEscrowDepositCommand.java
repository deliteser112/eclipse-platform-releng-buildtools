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

package google.registry.tools;

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.assertTldExists;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Result;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.EppResource;
import google.registry.model.EppResourceUtils;
import google.registry.model.ImmutableObject;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.EppResourceIndexBucket;
import google.registry.model.rde.RdeMode;
import google.registry.model.rde.RdeNamingUtils;
import google.registry.model.registrar.Registrar;
import google.registry.rde.DepositFragment;
import google.registry.rde.RdeCounter;
import google.registry.rde.RdeMarshaller;
import google.registry.rde.RdeResourceType;
import google.registry.rde.RdeUtil;
import google.registry.tldconfig.idn.IdnTableEnum;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.DateTimeParameter;
import google.registry.tools.params.PathParameter;
import google.registry.xjc.rdeheader.XjcRdeHeader;
import google.registry.xjc.rdeheader.XjcRdeHeaderElement;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** Command to generate an XML RDE escrow deposit (with relevant files) in current directory. */
@Parameters(separators = " =", commandDescription = "Generate an XML escrow deposit.")
final class GenerateEscrowDepositCommand implements RemoteApiCommand {

  @Parameter(
      names = {"-t", "--tld"},
      description = "Top level domain for which deposit should be generated.",
      required = true)
  private String tld;

  @Parameter(
      names = {"-w", "--watermark"},
      description = "Point-in-time timestamp for snapshotting the datastore.",
      validateWith = DateTimeParameter.class)
  private DateTime watermark = DateTime.now(UTC);

  @Parameter(
      names = {"-m", "--mode"},
      description = "RDE/BRDA mode of operation.")
  private RdeMode mode = RdeMode.FULL;

  @Parameter(
      names = {"-r", "--revision"},
      description = "Revision number. Use >0 for resends.")
  private int revision = 0;

  @Parameter(
      names = {"-o", "--outdir"},
      description = "Specify output directory. Default is current directory.",
      validateWith = PathParameter.OutputDirectory.class)
  private Path outdir = Paths.get(".");

  @Inject
  EscrowDepositEncryptor encryptor;

  @Inject
  RdeCounter counter;

  @Inject
  @Config("eppResourceIndexBucketCount")
  int eppResourceIndexBucketCount;

  @Override
  public void run() throws Exception {
    RdeMarshaller marshaller = new RdeMarshaller();
    assertTldExists(tld);
    String suffix = String.format("-%s-%s.tmp.xml", tld, watermark);
    Path xmlPath = outdir.resolve("deposit" + suffix);
    Path reportPath = outdir.resolve("report" + suffix);
    try {
      String id = RdeUtil.timestampToId(watermark);
      XjcRdeHeader header;
      try (OutputStream xmlOutputBytes = Files.newOutputStream(xmlPath);
          Writer xmlOutput = new OutputStreamWriter(xmlOutputBytes, UTF_8)) {
        xmlOutput.write(
            marshaller.makeHeader(id, watermark, RdeResourceType.getUris(mode), revision));
        for (ImmutableObject resource
            : Iterables.concat(Registrar.loadAll(), load(scan()))) {
          DepositFragment frag;
          if (resource instanceof Registrar) {
            frag = marshaller.marshalRegistrar((Registrar) resource);
          } else if (resource instanceof ContactResource) {
            frag = marshaller.marshalContact((ContactResource) resource);
          } else if (resource instanceof DomainResource) {
            DomainResource domain = (DomainResource) resource;
            if (!domain.getTld().equals(tld)) {
              continue;
            }
            frag = marshaller.marshalDomain(domain, mode);
          } else if (resource instanceof HostResource) {
            frag = marshaller.marshalHost((HostResource) resource);
          } else {
            continue;  // Surprise polymorphic entities, e.g. DomainApplication.
          }
          if (!frag.xml().isEmpty()) {
            xmlOutput.write(frag.xml());
            counter.increment(frag.type());
          }
          if (!frag.error().isEmpty()) {
            System.err.print(frag.error());
          }
        }
        for (IdnTableEnum idn : IdnTableEnum.values()) {
          xmlOutput.write(marshaller.marshalIdn(idn.getTable()));
          counter.increment(RdeResourceType.IDN);
        }
        header = counter.makeHeader(tld, mode);
        xmlOutput.write(marshaller.marshalStrictlyOrDie(new XjcRdeHeaderElement(header)));
        xmlOutput.write(marshaller.makeFooter());
      }
      try (OutputStream reportOutputBytes = Files.newOutputStream(reportPath)) {
        counter.makeReport(id, watermark, header, revision).marshal(reportOutputBytes, UTF_8);
      }
      String name = RdeNamingUtils.makeRydeFilename(tld, watermark, mode, 1, revision);
      encryptor.encrypt(tld, xmlPath, outdir);
      Files.move(xmlPath, outdir.resolve(name + ".xml"), REPLACE_EXISTING);
      Files.move(reportPath, outdir.resolve(name + "-report.xml"), REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(xmlPath);
      Files.deleteIfExists(reportPath);
    }
  }

  private Iterable<EppResource> scan() {
    return Iterables.concat(
        Iterables.transform(
            getEppResourceIndexBuckets(),
            new Function<Key<EppResourceIndexBucket>, Iterable<EppResource>>() {
              @Override
              public Iterable<EppResource> apply(Key<EppResourceIndexBucket> bucket) {
                System.err.printf("Scanning EppResourceIndexBucket %d of %d...\n",
                    bucket.getId(), eppResourceIndexBucketCount);
                return scanBucket(bucket);
              }}));
  }

  private Iterable<EppResource> scanBucket(final Key<EppResourceIndexBucket> bucket) {
    return ofy().load()
        .keys(FluentIterable
            .from(mode == RdeMode.FULL
                ? Arrays.asList(
                    Key.getKind(ContactResource.class),
                    Key.getKind(DomainResource.class),
                    Key.getKind(HostResource.class))
                : Arrays.asList(
                    Key.getKind(DomainResource.class)))
            .transformAndConcat(new Function<String, Iterable<EppResourceIndex>>() {
              @Override
              public Iterable<EppResourceIndex> apply(String kind) {
                return ofy().load()
                    .type(EppResourceIndex.class)
                    .ancestor(bucket)
                    .filter("kind", kind)
                    .iterable();
              }})
            .transform(new Function<EppResourceIndex, Key<EppResource>>() {
              @Override
              @SuppressWarnings("unchecked")
              public Key<EppResource> apply(EppResourceIndex index) {
                return (Key<EppResource>) index.getKey();
              }}))
        .values();
  }

  private <T extends EppResource> Iterable<T> load(final Iterable<T> resources) {
    return FluentIterable
        .from(Iterables.partition(
            Iterables.transform(resources,
                new Function<T, Result<T>>() {
                  @Override
                  public Result<T> apply(T resource) {
                    return EppResourceUtils.loadAtPointInTime(resource, watermark);
                  }}),
            1000))
        .transformAndConcat(new Function<Iterable<Result<T>>, Iterable<T>>() {
          @Override
          public Iterable<T> apply(Iterable<Result<T>> results) {
            return Iterables.transform(results,
                new Function<Result<T>, T>() {
                  @Override
                  public T apply(Result<T> result) {
                    return result.now();
                  }});
          }})
        .filter(Predicates.notNull());
  }

  private ImmutableList<Key<EppResourceIndexBucket>> getEppResourceIndexBuckets() {
    ImmutableList.Builder<Key<EppResourceIndexBucket>> builder = new ImmutableList.Builder<>();
    for (int i = 1; i <= eppResourceIndexBucketCount; i++) {
      builder.add(Key.create(EppResourceIndexBucket.class, i));
    }
    return builder.build();
  }
}
