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

package google.registry.export;

import static com.google.appengine.tools.cloudstorage.GcsServiceFactory.createGcsService;
import static google.registry.mapreduce.inputs.EppResourceInputs.createEntityInput;
import static google.registry.model.EppResourceUtils.isActive;
import static google.registry.model.registry.Registries.getTldsOfType;
import static google.registry.request.Action.Method.POST;
import static google.registry.util.PipelineUtils.createJobPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.domain.DomainResource;
import google.registry.model.registry.Registry.TldType;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * A mapreduce that exports the list of active domains on all real TLDs to Google Cloud Storage.
 *
 * Each TLD's active domain names are exported as a newline-delimited flat text file with the name
 * TLD.txt into the domain-lists bucket.  Note that this overwrites the files in place.
 */
@Action(path = "/_dr/task/exportDomainLists", method = POST)
public class ExportDomainListsAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();
  private static final int MAX_NUM_REDUCE_SHARDS = 100;

  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject @Config("domainListsGcsBucket") String gcsBucket;
  @Inject @Config("gcsBufferSize") int gcsBufferSize;
  @Inject ExportDomainListsAction() {}

  @Override
  public void run() {
    ImmutableSet<String> realTlds = getTldsOfType(TldType.REAL);
    logger.infofmt("Exporting domain lists for tlds %s", realTlds);
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Export domain lists")
        .setModuleName("backend")
        .setDefaultReduceShards(Math.min(realTlds.size(), MAX_NUM_REDUCE_SHARDS))
        .runMapreduce(
            new ExportDomainListsMapper(DateTime.now(UTC), realTlds),
            new ExportDomainListsReducer(gcsBucket, gcsBufferSize),
            ImmutableList.of(createEntityInput(DomainResource.class)))));
  }

  static class ExportDomainListsMapper extends Mapper<DomainResource, String, String> {

    private static final long serialVersionUID = -7312206212434039854L;

    private final DateTime exportTime;
    private final ImmutableSet<String> realTlds;

    ExportDomainListsMapper(DateTime exportTime, ImmutableSet<String> realTlds) {
      this.exportTime = exportTime;
      this.realTlds = realTlds;
    }

    @Override
    public void map(DomainResource domain) {
      if (realTlds.contains(domain.getTld()) && isActive(domain, exportTime)) {
        emit(domain.getTld(), domain.getFullyQualifiedDomainName());
        getContext().incrementCounter(String.format("domains in tld %s", domain.getTld()));
      }
    }
  }

  static class ExportDomainListsReducer extends Reducer<String, String, Void> {

    private static final long serialVersionUID = 7035260977259119087L;

    private final String gcsBucket;
    private final int gcsBufferSize;

    public ExportDomainListsReducer(String gcsBucket, int gcsBufferSize) {
      this.gcsBucket = gcsBucket;
      this.gcsBufferSize = gcsBufferSize;
    }

    @Override
    public void reduce(String tld, ReducerInput<String> fqdns) {
      GcsFilename filename = new GcsFilename(gcsBucket, tld + ".txt");
      GcsUtils cloudStorage =
          new GcsUtils(createGcsService(RetryParams.getDefaultInstance()), gcsBufferSize);
      try (OutputStream gcsOutput = cloudStorage.openOutputStream(filename);
          Writer osWriter = new OutputStreamWriter(gcsOutput, UTF_8);
          PrintWriter writer = new PrintWriter(osWriter)) {
        long count;
        for (count = 0; fqdns.hasNext(); count++) {
          writer.println(fqdns.next());
        }
        writer.flush();
        getContext().incrementCounter("tld domain lists written out");
        logger.infofmt("Wrote out %d domains for tld %s.", count, tld);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
