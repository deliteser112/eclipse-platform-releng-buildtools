// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.loadtest;

import static com.google.appengine.api.taskqueue.QueueConstants.maxTasksPerAdd;
import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withPayload;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.partition;
import static com.google.common.collect.Lists.transform;
import static com.google.domain.registry.util.ResourceUtils.readResourceUtf8;
import static java.util.Arrays.asList;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.Parameter;
import com.google.domain.registry.util.TaskEnqueuer;

import org.joda.time.DateTime;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

/** Simple load test action that can generate configurable QPSes of various EPP actions. */
@Action(
    path = "/_dr/loadtest",
    method = Action.Method.POST,
    automaticallyPrintOk = true)
public class LoadTestAction implements Runnable {

  private static final int NUM_QUEUES = 5;
  private static final int ARBITRARY_VALID_HOST_LENGTH = 40;
  private static final int MAX_CONTACT_LENGTH = 13;
  private static final int MAX_DOMAIN_LABEL_LENGTH = 63;

  private static final String EXISTING_DOMAIN = "testdomain";
  private static final String EXISTING_CONTACT = "contact";
  private static final String EXISTING_HOST = "ns1";

  private static final Random random = new Random();

  @Inject @Parameter("loadtestClientId") String clientId;
  @Inject @Parameter("tld") String tld;
  @Inject @Parameter("delaySeconds") int delaySeconds;
  @Inject @Parameter("runSeconds") int runSeconds;
  @Inject @Parameter("successfulDomainCreates") int successfulDomainCreates;
  @Inject @Parameter("failedDomainCreates") int failedDomainCreates;
  @Inject @Parameter("domainInfos") int domainInfos;
  @Inject @Parameter("domainChecks") int domainChecks;
  @Inject @Parameter("successfulContactCreates") int successfulContactCreates;
  @Inject @Parameter("failedContactCreates") int failedContactCreates;
  @Inject @Parameter("contactInfos") int contactInfos;
  @Inject @Parameter("successfulHostCreates") int successfulHostCreates;
  @Inject @Parameter("failedHostCreates") int failedHostCreates;
  @Inject @Parameter("hostInfos") int hostInfos;
  @Inject TaskEnqueuer taskEnqueuer;
  @Inject LoadTestAction() {}

  @Override
  public void run() {
    checkArgument(
        RegistryEnvironment.get() != RegistryEnvironment.PRODUCTION,
        "DO NOT RUN LOADTESTS IN PROD!");

    DateTime initialStartSecond = DateTime.now(UTC).plusSeconds(delaySeconds);
    ImmutableList.Builder<String> preTaskXmls = new ImmutableList.Builder<>();
    ImmutableList.Builder<String> contactNamesBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<String> hostPrefixesBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < successfulDomainCreates; i++) {
      String contactName = getRandomLabel(MAX_CONTACT_LENGTH);
      String hostPrefix = getRandomLabel(ARBITRARY_VALID_HOST_LENGTH);
      contactNamesBuilder.add(contactName);
      hostPrefixesBuilder.add(hostPrefix);
      preTaskXmls.add(
          loadXml("contact_create").replace("%contact%", contactName),
          loadXml("host_create").replace("%host%", hostPrefix));
    }
    enqueue(createTasks(preTaskXmls.build(), DateTime.now(UTC)));
    ImmutableList<String> contactNames = contactNamesBuilder.build();
    ImmutableList<String> hostPrefixes = hostPrefixesBuilder.build();

    ImmutableList.Builder<TaskOptions> tasks = new ImmutableList.Builder<>();
    for (int offsetSeconds = 0; offsetSeconds < runSeconds; offsetSeconds++) {
      DateTime startSecond = initialStartSecond.plusSeconds(offsetSeconds);
      // The first "failed" creates might actually succeed if the object doesn't already exist, but
      // that shouldn't affect the load numbers.
      tasks.addAll(createTasks(
          createNumCopies(
              loadXml("contact_create").replace("%contact%", EXISTING_CONTACT),
              failedContactCreates),
          startSecond));
      tasks.addAll(createTasks(
          createNumCopies(
            loadXml("host_create").replace("%host%", EXISTING_HOST),
            failedHostCreates),
          startSecond));
      tasks.addAll(createTasks(
          createNumCopies(
            loadXml("domain_create")
                .replace("%tld%", tld)
                .replace("%domain%", EXISTING_DOMAIN)
                .replace("%contact%", EXISTING_CONTACT)
                .replace("%host%", EXISTING_HOST),
            failedDomainCreates),
          startSecond));
      // We can do infos on the known existing objects.
      tasks.addAll(createTasks(
          createNumCopies(
            loadXml("contact_info").replace("%contact%", EXISTING_CONTACT),
            contactInfos),
          startSecond));
      tasks.addAll(createTasks(
          createNumCopies(
            loadXml("host_info").replace("%host%", EXISTING_HOST),
            hostInfos),
          startSecond));
      tasks.addAll(createTasks(
          createNumCopies(
            loadXml("domain_info")
                .replace("%tld%", tld)
                .replace("%domain%", EXISTING_DOMAIN),
            domainInfos),
          startSecond));
      // The domain check template uses "example.TLD" which won't exist, and one existing domain.
      tasks.addAll(createTasks(
          createNumCopies(
            loadXml("domain_check")
                .replace("%tld%", tld)
                .replace("%domain%", EXISTING_DOMAIN),
            domainChecks),
          startSecond));
      // Do successful creates on random names
      tasks.addAll(createTasks(
          transform(
              createNumCopies(loadXml("contact_create"), successfulContactCreates),
              randomNameReplacer("%contact%", MAX_CONTACT_LENGTH)),
          startSecond));
      tasks.addAll(createTasks(
          transform(
              createNumCopies(loadXml("host_create"), successfulHostCreates),
              randomNameReplacer("%host%", ARBITRARY_VALID_HOST_LENGTH)),
          startSecond));
      tasks.addAll(createTasks(
          FluentIterable
              .from(createNumCopies(
                  loadXml("domain_create").replace("%tld%", tld),
                  successfulDomainCreates))
              .transform(randomNameReplacer("%domain%", MAX_DOMAIN_LABEL_LENGTH))
              .transform(listNameReplacer("%contact%", contactNames))
              .transform(listNameReplacer("%host%", hostPrefixes))
              .toList(),
          startSecond));
    }
    enqueue(tasks.build());
  }

  private String loadXml(String name) {
    return readResourceUtf8(LoadTestAction.class, String.format("templates/%s.xml", name));
  }

  private List<String> createNumCopies(String xml, int numCopies) {
    String[] xmls = new String[numCopies];
    Arrays.fill(xmls, xml);
    return asList(xmls);
  }

  private Function<String, String> listNameReplacer(final String toReplace, List<String> choices) {
    final Iterator<String> iterator = Iterators.cycle(choices);
    return new Function<String, String>() {
      @Override
      public String apply(String xml) {
        return xml.replace(toReplace, iterator.next());
      }};
  }

  private Function<String, String> randomNameReplacer(final String toReplace, final int numChars) {
    return new Function<String, String>() {
      @Override
      public String apply(String xml) {
        return xml.replace(toReplace, getRandomLabel(numChars));
      }};
  }

  private String getRandomLabel(int numChars) {
    StringBuilder name = new StringBuilder();
    for (int j = 0; j < numChars; j++) {
      name.append(Character.forDigit(random.nextInt(Character.MAX_RADIX), Character.MAX_RADIX));
    }
    return name.toString();
  }

  private List<TaskOptions> createTasks(List<String> xmls, DateTime start) {
    ImmutableList.Builder<TaskOptions> tasks = new ImmutableList.Builder<>();
    for (int i = 0; i < xmls.size(); i++) {
      // Space tasks evenly within across a second.
      int offsetMillis = (int) (1000.0 / xmls.size() * i);
      tasks.add(withPayload(new LoadTask(clientId, xmls.get(i)))
          .etaMillis(start.plusMillis(offsetMillis).getMillis()));
    }
    return tasks.build();
  }

  private void enqueue(List<TaskOptions> tasks) {
    List<List<TaskOptions>> chunks = partition(tasks, maxTasksPerAdd());
    // Farm out tasks to multiple queues to work around queue qps quotas.
    for (int i = 0; i < chunks.size(); i++) {
      taskEnqueuer.enqueue(getQueue("load" + (i % NUM_QUEUES)), chunks.get(i));
    }
  }
}
