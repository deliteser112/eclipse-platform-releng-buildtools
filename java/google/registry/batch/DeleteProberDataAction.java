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

package google.registry.batch;

import static com.google.common.base.Verify.verifyNotNull;
import static google.registry.mapreduce.MapreduceRunner.PARAM_DRY_RUN;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.getTldsOfType;
import static google.registry.request.Action.Method.POST;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.EppResourceInputs;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainBase;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.index.ForeignKeyIndex.ForeignKeyDomainIndex;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldType;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import google.registry.util.PipelineUtils;
import java.util.List;
import javax.inject.Inject;

/**
 * Deletes all prober DomainResources and their subordinate history entries, poll messages, and
 * billing events, along with their ForeignKeyDomainIndex and EppResourceIndex entities.
 *
 * <p>See: https://www.youtube.com/watch?v=xuuv0syoHnM
 */
@Action(path = "/_dr/task/deleteProberData", method = POST)
public class DeleteProberDataAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @Parameter(PARAM_DRY_RUN) boolean isDryRun;
  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject DeleteProberDataAction() {}

  @Override
  public void run() {
    response.sendJavaScriptRedirect(PipelineUtils.createJobPath(mrRunner
        .setJobName("Delete prober data")
        .setModuleName("backend")
        .runMapOnly(
            new DeleteProberDataMapper(getProberRoidSuffixes(), isDryRun),
            ImmutableList.of(EppResourceInputs.createKeyInput(DomainBase.class)))));
  }

  private static ImmutableSet<String> getProberRoidSuffixes() {
    return FluentIterable.from(getTldsOfType(TldType.TEST))
        .filter(new Predicate<String>() {
          @Override
          public boolean apply(String tld) {
            // Extra sanity check to prevent us from nuking prod data if a real TLD accidentally
            // gets set to type TEST.
            return tld.endsWith(".test");
          }})
        .transform(
            new Function<String, String>() {
              @Override
              public String apply(String tld) {
                return Registry.get(tld).getRoidSuffix();
              }})
        .toSet();
  }

  /** Provides the map method that runs for each existing DomainBase entity. */
  public static class DeleteProberDataMapper extends Mapper<Key<DomainBase>, Void, Void> {

    private static final long serialVersionUID = 1737761271804180412L;

    private final ImmutableSet<String> proberRoidSuffixes;
    private final Boolean isDryRun;

    public DeleteProberDataMapper(ImmutableSet<String> proberRoidSuffixes, Boolean isDryRun) {
      this.proberRoidSuffixes = proberRoidSuffixes;
      this.isDryRun = isDryRun;
    }

    @Override
    public final void map(Key<DomainBase> key) {
      try {
        String roidSuffix = Iterables.getLast(Splitter.on('-').split(key.getName()));
        if (proberRoidSuffixes.contains(roidSuffix)) {
          deleteDomain(key);
        } else {
          getContext().incrementCounter(String.format("skipped, non-prober data"));
        }
      } catch (Throwable t) {
        logger.severefmt(t, "Error while deleting prober data for key %s", key);
        getContext().incrementCounter(String.format("error, kind %s", key.getKind()));
      }
    }

    private void deleteDomain(final Key<DomainBase> domainKey) {
      final DomainBase domain = ofy().load().key(domainKey).now();
      if (domain == null) {
        // Depending on how stale Datastore indexes are, we can get keys to resources that are
        // already deleted (e.g. by a recent previous invocation of this mapreduce). So ignore them.
        getContext().incrementCounter("already deleted");
        return;
      }
      if (domain instanceof DomainApplication) {
        // Cover the case where we somehow have a domain application with a prober ROID suffix.
        getContext().incrementCounter("skipped, domain application");
        return;
      }
      if (domain.getFullyQualifiedDomainName().equals("nic." + domain.getTld())) {
        getContext().incrementCounter("skipped, NIC domain");
        return;
      }
      int dependentsDeleted = ofy().transact(new Work<Integer>() {
        @Override
        public Integer run() {
          EppResourceIndex eppIndex = ofy().load().entity(EppResourceIndex.create(domainKey)).now();
          verifyNotNull(eppIndex, "Missing EppResourceIndex for domain %s", domain);
          ForeignKeyIndex<?> fki = ofy().load().key(ForeignKeyDomainIndex.createKey(domain)).now();
          verifyNotNull(fki, "Missing ForeignKeyDomainIndex for domain %s", domain);
          // This ancestor query selects all descendant HistoryEntries, BillingEvents, and
          // PollMessages, as well as the domain itself.
          List<Key<Object>> domainAndDependentKeys = ofy().load().ancestor(domainKey).keys().list();
          if (isDryRun) {
            logger.infofmt(
                "Would delete the following entities: %s",
                new ImmutableList.Builder<Object>()
                    .add(fki)
                    .add(eppIndex)
                    .addAll(domainAndDependentKeys)
                    .build());
          } else {
            ofy().deleteWithoutBackup().keys(domainAndDependentKeys);
            ofy().deleteWithoutBackup().entities(eppIndex, fki);
          }
          return domainAndDependentKeys.size() - 1;
        }
      });
      getContext().incrementCounter(String.format("deleted, kind %s", domainKey.getKind()));
      getContext().incrementCounter("deleted, dependent keys", dependentsDeleted);
    }
  }
}
