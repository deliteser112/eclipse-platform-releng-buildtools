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

package google.registry.batch;

import static com.google.common.base.Preconditions.checkState;
import static google.registry.flows.ResourceFlowUtils.updateForeignKeyIndexDeletionTime;
import static google.registry.mapreduce.MapreduceRunner.PARAM_DRY_RUN;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.getTldsOfType;
import static google.registry.model.reporting.HistoryEntry.Type.DOMAIN_DELETE;
import static google.registry.request.Action.Method.POST;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.tools.mapreduce.Mapper;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import google.registry.config.RegistryConfig.Config;
import google.registry.dns.DnsQueue;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.EppResourceInputs;
import google.registry.model.EppResourceUtils;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.DomainResource;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldType;
import google.registry.model.reporting.HistoryEntry;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import google.registry.util.PipelineUtils;
import java.util.List;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * Deletes all prober DomainResources and their subordinate history entries, poll messages, and
 * billing events, along with their ForeignKeyDomainIndex and EppResourceIndex entities.
 *
 * <p>See: https://www.youtube.com/watch?v=xuuv0syoHnM
 */
@Action(
  path = "/_dr/task/deleteProberData",
  method = POST,
  auth = Auth.AUTH_INTERNAL_ONLY
)
public class DeleteProberDataAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @Parameter(PARAM_DRY_RUN) boolean isDryRun;
  @Inject @Config("registryAdminClientId") String registryAdminClientId;
  @Inject MapreduceRunner mrRunner;
  @Inject Response response;
  @Inject DeleteProberDataAction() {}

  @Override
  public void run() {
    checkState(
        !Strings.isNullOrEmpty(registryAdminClientId),
        "Registry admin client ID must be configured for prober data deletion to work");
    response.sendJavaScriptRedirect(PipelineUtils.createJobPath(mrRunner
        .setJobName("Delete prober data")
        .setModuleName("backend")
        .runMapOnly(
            new DeleteProberDataMapper(getProberRoidSuffixes(), isDryRun, registryAdminClientId),
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

    private static final DnsQueue dnsQueue = DnsQueue.create();
    private static final long serialVersionUID = -7724537393697576369L;

    private final ImmutableSet<String> proberRoidSuffixes;
    private final Boolean isDryRun;
    private final String registryAdminClientId;

    public DeleteProberDataMapper(
        ImmutableSet<String> proberRoidSuffixes, Boolean isDryRun, String registryAdminClientId) {
      this.proberRoidSuffixes = proberRoidSuffixes;
      this.isDryRun = isDryRun;
      this.registryAdminClientId = registryAdminClientId;
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
      final DomainBase domainBase = ofy().load().key(domainKey).now();
      if (domainBase == null) {
        // Depending on how stale Datastore indexes are, we can get keys to resources that are
        // already deleted (e.g. by a recent previous invocation of this mapreduce). So ignore them.
        getContext().incrementCounter("already deleted");
        return;
      }
      if (domainBase instanceof DomainApplication) {
        // Cover the case where we somehow have a domain application with a prober ROID suffix.
        getContext().incrementCounter("skipped, domain application");
        return;
      }

      DomainResource domain = (DomainResource) domainBase;
      if (domain.getFullyQualifiedDomainName().equals("nic." + domain.getTld())) {
        getContext().incrementCounter("skipped, NIC domain");
        return;
      }
      if (domain.getCreationTime().isAfter(DateTime.now(UTC).minusHours(1))) {
        getContext().incrementCounter("skipped, domain too new");
        return;
      }
      if (!domain.getSubordinateHosts().isEmpty()) {
        logger.warningfmt("Cannot delete domain %s because it has subordinate hosts.", domainKey);
        getContext().incrementCounter("skipped, had subordinate host(s)");
        return;
      }

      // If the domain is still active, that means that the prober encountered a failure and did not
      // successfully soft-delete the domain (thus leaving its DNS entry published). We soft-delete
      // it now so that the DNS entry can be handled. The domain will then be hard-deleted the next
      // time the mapreduce is run.
      if (EppResourceUtils.isActive(domain, DateTime.now(UTC))) {
        if (isDryRun) {
          logger.infofmt("Would soft-delete the active domain: %s", domainKey);
        } else {
          softDeleteDomain(domain);
        }
        getContext().incrementCounter("domains soft-deleted");
        return;
      }

      final Key<EppResourceIndex> eppIndex = Key.create(EppResourceIndex.create(domainKey));
      final Key<? extends ForeignKeyIndex<?>> fki = ForeignKeyIndex.createKey(domain);

      int entitiesDeleted = ofy().transact(new Work<Integer>() {
        @Override
        public Integer run() {
          // This ancestor query selects all descendant HistoryEntries, BillingEvents, PollMessages,
          // and TLD-specific entities, as well as the domain itself.
          List<Key<Object>> domainAndDependentKeys = ofy().load().ancestor(domainKey).keys().list();
          ImmutableSet<Key<?>> allKeys = new ImmutableSet.Builder<Key<?>>()
              .add(fki)
              .add(eppIndex)
              .addAll(domainAndDependentKeys)
              .build();
          if (isDryRun) {
            logger.infofmt("Would hard-delete the following entities: %s", allKeys);
          } else {
            ofy().deleteWithoutBackup().keys(allKeys);
          }
          return allKeys.size();
        }
      });
      getContext().incrementCounter("domains hard-deleted");
      getContext().incrementCounter("total entities hard-deleted", entitiesDeleted);
    }

    private void softDeleteDomain(final DomainResource domain) {
      ofy().transactNew(new VoidWork() {
        @Override
        public void vrun() {
          DomainResource deletedDomain = domain
              .asBuilder()
              .setDeletionTime(ofy().getTransactionTime())
              .setStatusValues(null)
              .build();
          HistoryEntry historyEntry = new HistoryEntry.Builder()
              .setParent(domain)
              .setType(DOMAIN_DELETE)
              .setModificationTime(ofy().getTransactionTime())
              .setBySuperuser(true)
              .setReason("Deletion of prober data")
              .setClientId(registryAdminClientId)
              .build();
          // Note that we don't bother handling grace periods, billing events, pending transfers,
          // poll messages, or auto-renews because these will all be hard-deleted the next time the
          // mapreduce runs anyway.
          ofy().save().entities(deletedDomain, historyEntry);
          updateForeignKeyIndexDeletionTime(deletedDomain);
          dnsQueue.addDomainRefreshTask(deletedDomain.getFullyQualifiedDomainName());
        }
      });
    }
  }
}
