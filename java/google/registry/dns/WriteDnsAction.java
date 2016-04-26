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

package google.registry.dns;

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.dns.DnsConstants.DNS_TARGET_NAME_PARAM;
import static google.registry.dns.DnsConstants.DNS_TARGET_TYPE_PARAM;
import static google.registry.model.server.Lock.executeWithLocks;
import static google.registry.request.Action.Method.POST;

import com.google.appengine.api.LifecycleManager;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.common.base.Throwables;
import com.google.common.net.InternetDomainName;

import google.registry.config.ConfigModule.Config;
import google.registry.dns.DnsConstants.TargetType;
import google.registry.dns.writer.api.DnsWriter;
import google.registry.model.registry.Registry;
import google.registry.request.Action;
import google.registry.request.HttpException;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.Parameter;
import google.registry.request.RequestParameters;
import google.registry.util.DomainNameUtils;
import google.registry.util.FormattingLogger;

import org.joda.time.Duration;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Provider;

/** Action that consumes pull-queue for zone updates to write to the DNS server. */
@Action(path = "/_dr/task/writeDns", method = POST, automaticallyPrintOk = true)
public final class WriteDnsAction implements Runnable, Callable<Void> {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject DnsQueue dnsQueue;
  @Inject Provider<DnsWriter> writerProvider;
  @Inject @Config("dnsWriteLockTimeout") Duration timeout;
  @Inject @Parameter(RequestParameters.PARAM_TLD) String tld;
  @Inject WriteDnsAction() {}

  /** Runs the task. */
  @Override
  public void run() {
    String lockName = String.format("DNS zone %s", tld);
    executeWithLocks(this, getClass(), tld, timeout, lockName);
  }

  /** Runs the task, with the lock. */
  @Override
  public Void call() {
    processBatch();
    return null;
  }

  /** Leases a batch of tasks tagged with the zone name from the pull queue and processes them. */
  private void processBatch() {
    if (LifecycleManager.getInstance().isShuttingDown()) {
      logger.infofmt("%s: lifecycle manager is shutting down", tld);
      return;
    }
    if (Registry.get(tld).getDnsPaused()) {
      logger.infofmt("%s: the dns-pull queue is paused", tld);
      return;
    }
    // Make a defensive copy to allow mutations.
    List<TaskHandle> tasks = new ArrayList<>(dnsQueue.leaseTasks(tld));
    if (tasks.isEmpty()) {
      logger.infofmt("%s: no tasks in the dns-pull queue", tld);
      return;
    }
    try (DnsWriter writer = writerProvider.get()) {
      Iterator<TaskHandle> it = tasks.iterator();
      while (it.hasNext()) {
        TaskHandle task = it.next();
        try {
          processTask(writer, task, tld);
        } catch (UnsupportedOperationException e) {
          // Handle fatal errors by deleting the task.
          logger.severefmt(e, "%s: deleting unsupported task %s", tld, task.toString());
          dnsQueue.deleteTask(task);
          it.remove();
        }
      }
    } catch (RuntimeException e) {
      Throwables.propagateIfInstanceOf(e, HttpException.class);
      // Handle transient errors by dropping the task leases.
      logger.severefmt(e, "%s: dropping leases of failed tasks", tld);
      for (TaskHandle task : tasks) {
        dnsQueue.dropTaskLease(task);
      }
      return;
    }
    for (TaskHandle task : tasks) {
      dnsQueue.deleteTask(task);
    }
    logger.infofmt("%s: batch of %s tasks processed", tld, tasks.size());
  }

  /** Stages a write to authoritative DNS for this task. */
  private static void processTask(DnsWriter writer, TaskHandle task, String tld) {
    Map<String, String> params = new HashMap<>();
    try {
      for (Map.Entry<String, String> entry : task.extractParams()) {
        params.put(entry.getKey(), entry.getValue());
      }
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    TargetType type = TargetType.valueOf(params.get(DNS_TARGET_TYPE_PARAM));
    String name = checkNotNull(params.get(DNS_TARGET_NAME_PARAM));
    switch (type) {
      case DOMAIN:
        checkRequestArgument(
            DomainNameUtils.isUnder(InternetDomainName.from(name), InternetDomainName.from(tld)),
            "domain name %s is not under tld %s", name, tld);
        writer.publishDomain(name);
        break;
      case HOST:
        checkRequestArgument(
            DomainNameUtils.isUnder(InternetDomainName.from(name), InternetDomainName.from(tld)),
            "host name %s is not under tld %s", name, tld);
        writer.publishHost(name);
        break;
      default:
        // TODO(b/11592394): Write a full zone.
        throw new UnsupportedOperationException(String.format("unexpected Type: %s", type));
    }
  }

  private static void checkRequestArgument(boolean condition, String format, Object... args) {
    if (!condition) {
      throw new BadRequestException(String.format(format, args));
    }
  }
}
