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

package google.registry.dns;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.google.common.collect.Sets.difference;
import static google.registry.dns.DnsConstants.DNS_PUBLISH_PUSH_QUEUE_NAME;
import static google.registry.dns.DnsConstants.DNS_TARGET_NAME_PARAM;
import static google.registry.dns.DnsConstants.DNS_TARGET_TYPE_PARAM;
import static google.registry.model.registry.Registries.getTlds;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.TreeMultimap;
import google.registry.config.ConfigModule.Config;
import google.registry.dns.DnsConstants.TargetType;
import google.registry.model.registry.Registry;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.RequestParameters;
import google.registry.util.FormattingLogger;
import google.registry.util.TaskEnqueuer;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import org.joda.time.Duration;

/**
 * Action for fanning out DNS refresh tasks by TLD, using data taken from the DNS pull queue.
 *
 * <h3>Parameters Reference</h3>
 *
 * <ul>
 * <li>{@code jitterSeconds} Randomly delay each task by up to this many seconds.
 * <li>{@code keepTasks} Do not delete any tasks from the pull queue, whether they are processed or
 *      not.
 * </ul>
 */
@Action(path = "/_dr/cron/readDnsQueue", automaticallyPrintOk = true)
public final class ReadDnsQueueAction implements Runnable {

  public static final String KEEP_TASKS_PARAM = "keepTasks";

  private static final String JITTER_SECONDS_PARAM = "jitterSeconds";
  private static final Random random = new Random();
  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @Config("dnsTldUpdateBatchSize") int tldUpdateBatchSize;
  @Inject @Config("dnsWriteLockTimeout") Duration writeLockTimeout;
  @Inject @Named(DNS_PUBLISH_PUSH_QUEUE_NAME) Queue dnsPublishPushQueue;
  @Inject @Parameter(JITTER_SECONDS_PARAM) Optional<Integer> jitterSeconds;
  @Inject @Parameter(KEEP_TASKS_PARAM) boolean keepTasks;
  @Inject DnsQueue dnsQueue;
  @Inject TaskEnqueuer taskEnqueuer;
  @Inject ReadDnsQueueAction() {}

  /** Container for items we pull out of the DNS pull queue and process for fanout. */
  @AutoValue
  abstract static class RefreshItem implements Comparable<RefreshItem> {
    static RefreshItem create(TargetType type, String name) {
      return new AutoValue_ReadDnsQueueAction_RefreshItem(type, name);
    }

    abstract TargetType type();

    abstract String name();

    @Override
    public int compareTo(RefreshItem other) {
      return ComparisonChain.start()
          .compare(this.type(), other.type())
          .compare(this.name(), other.name())
          .result();
    }
  }

  /** Leases all tasks from the pull queue and creates per-tld update actions for them. */
  @Override
  public void run() {
    Set<String> tldsOfInterest = getTlds();

    List<TaskHandle> tasks = dnsQueue.leaseTasks(writeLockTimeout);
    if (tasks.isEmpty()) {
      return;
    }
    logger.infofmt("leased %d tasks", tasks.size());
    // Normally, all tasks will be deleted from the pull queue. But some might have to remain if
    // we are not interested in the associated TLD, or if the TLD is paused. Remember which these
    // are.
    Set<TaskHandle> tasksToKeep = new HashSet<>();
    // The paused TLDs for which we found at least one refresh request.
    Set<String> pausedTlds = new HashSet<>();
    // Create a sorted multimap into which we will insert the refresh items, so that the items for
    // each TLD will be grouped together, and domains and hosts will be grouped within a TLD. The
    // grouping and ordering of domains and hosts is not technically necessary, but a predictable
    // ordering makes it possible to write detailed tests.
    TreeMultimap<String, RefreshItem> refreshItemMultimap = TreeMultimap.create();
    // Read all tasks on the DNS pull queue and load them into the refresh item multimap.
    for (TaskHandle task : tasks) {
      try {
        Map<String, String> params = ImmutableMap.copyOf(task.extractParams());
        String tld = params.get(RequestParameters.PARAM_TLD);
        if (tld == null) {
          logger.severe("discarding invalid DNS refresh request; no TLD specified");
        } else if (!tldsOfInterest.contains(tld)) {
          tasksToKeep.add(task);
        } else if (Registry.get(tld).getDnsPaused()) {
          tasksToKeep.add(task);
          pausedTlds.add(tld);
        } else {
          String typeString = params.get(DNS_TARGET_TYPE_PARAM);
          String name = params.get(DNS_TARGET_NAME_PARAM);
          TargetType type = TargetType.valueOf(typeString);
          switch (type) {
            case DOMAIN:
            case HOST:
              refreshItemMultimap.put(tld, RefreshItem.create(type, name));
              break;
            default:
              logger.severefmt("discarding DNS refresh request of type %s", typeString);
              break;
          }
        }
      } catch (RuntimeException | UnsupportedEncodingException e) {
        logger.severefmt(e, "discarding invalid DNS refresh request (task %s)", task);
      }
    }
    if (!pausedTlds.isEmpty()) {
      logger.infofmt("the dns-pull queue is paused for tlds: %s", pausedTlds);
    }
    // Loop through the multimap by TLD and generate refresh tasks for the hosts and domains.
    for (Map.Entry<String, Collection<RefreshItem>> tldRefreshItemsEntry
        : refreshItemMultimap.asMap().entrySet()) {
      for (List<RefreshItem> chunk : Iterables.partition(
          tldRefreshItemsEntry.getValue(), tldUpdateBatchSize)) {
        TaskOptions options = withUrl(PublishDnsUpdatesAction.PATH)
            .countdownMillis(jitterSeconds.isPresent()
                ? random.nextInt((int) SECONDS.toMillis(jitterSeconds.get()))
                : 0)
            .param(RequestParameters.PARAM_TLD, tldRefreshItemsEntry.getKey());
        for (RefreshItem refreshItem : chunk) {
          options.param(
              (refreshItem.type() == TargetType.HOST)
                  ? PublishDnsUpdatesAction.HOSTS_PARAM
                  : PublishDnsUpdatesAction.DOMAINS_PARAM,
              refreshItem.name());
        }
        taskEnqueuer.enqueue(dnsPublishPushQueue, options);
      }
    }
    Set<TaskHandle> tasksToDelete = difference(ImmutableSet.copyOf(tasks), tasksToKeep);
    // In keepTasks mode, never delete any tasks.
    if (keepTasks) {
      logger.infofmt("would have deleted %d tasks", tasksToDelete.size());
      for (TaskHandle task : tasks) {
        dnsQueue.dropTaskLease(task);
      }
    // Otherwise, either delete or drop the lease of each task.
    } else {
      logger.infofmt("deleting %d tasks", tasksToDelete.size());
      dnsQueue.deleteTasks(ImmutableList.copyOf(tasksToDelete));
      logger.infofmt("dropping %d tasks", tasksToKeep.size());
      for (TaskHandle task : tasksToKeep) {
        dnsQueue.dropTaskLease(task);
      }
      logger.infofmt("done");
    }
  }
}
