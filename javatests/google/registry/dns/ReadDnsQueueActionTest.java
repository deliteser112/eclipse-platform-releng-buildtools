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

package google.registry.dns;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Lists.transform;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static google.registry.dns.DnsConstants.DNS_PUBLISH_PUSH_QUEUE_NAME;
import static google.registry.dns.DnsConstants.DNS_PULL_QUEUE_NAME;
import static google.registry.dns.DnsConstants.DNS_TARGET_CREATE_TIME_PARAM;
import static google.registry.dns.DnsConstants.DNS_TARGET_NAME_PARAM;
import static google.registry.dns.DnsConstants.DNS_TARGET_TYPE_PARAM;
import static google.registry.request.RequestParameters.PARAM_TLD;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.getQueuedParams;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.net.InternetDomainName;
import google.registry.dns.DnsConstants.TargetType;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldType;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.util.Retrier;
import google.registry.util.TaskQueueUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.IntStream;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ReadDnsQueueAction}. */
@RunWith(JUnit4.class)
public class ReadDnsQueueActionTest {

  private static final int TEST_TLD_UPDATE_BATCH_SIZE = 100;
  private DnsQueue dnsQueue;
  // Because of a bug in the queue test environment - b/73372999 - we must set the fake date of the
  // test in the future. Set to year 3000 so it'll remain in the future for a very long time.
  private FakeClock clock = new FakeClock(DateTime.parse("3000-01-01TZ"));

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue(Joiner.on('\n').join(
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
          "<queue-entries>",
          "  <queue>",
          "    <name>dns-publish</name>",
          "    <rate>1/s</rate>",
          "  </queue>",
          "  <queue>",
          "    <name>dns-pull</name>",
          "    <mode>pull</mode>",
          "  </queue>",
          "</queue-entries>"))
      .withClock(clock)
      .build();

  @Before
  public void before() throws Exception {
    // Because of b/73372999 - the FakeClock can't be in the past, or the TaskQueues stop working.
    // To make sure it's never in the past, we set the date far-far into the future
    clock.setTo(DateTime.parse("3000-01-01TZ"));
    createTlds("com", "net", "example", "multilock.uk");
    persistResource(
        Registry.get("com").asBuilder().setDnsWriters(ImmutableSet.of("comWriter")).build());
    persistResource(
        Registry.get("net").asBuilder().setDnsWriters(ImmutableSet.of("netWriter")).build());
    persistResource(
        Registry.get("example")
            .asBuilder()
            .setTldType(TldType.TEST)
            .setDnsWriters(ImmutableSet.of("exampleWriter"))
            .build());
    persistResource(
        Registry.get("multilock.uk")
            .asBuilder()
            .setNumDnsPublishLocks(1000)
            .setDnsWriters(ImmutableSet.of("multilockWriter"))
            .build());
    dnsQueue = DnsQueue.createForTesting(clock);
  }

  private void run() throws Exception {
    ReadDnsQueueAction action = new ReadDnsQueueAction();
    action.tldUpdateBatchSize = TEST_TLD_UPDATE_BATCH_SIZE;
    action.requestedMaximumDuration = Duration.standardSeconds(10);
    action.clock = clock;
    action.dnsQueue = dnsQueue;
    action.dnsPublishPushQueue = QueueFactory.getQueue(DNS_PUBLISH_PUSH_QUEUE_NAME);
    action.hashFunction = Hashing.murmur3_32();
    action.taskQueueUtils = new TaskQueueUtils(new Retrier(null, 1));
    action.jitterSeconds = Optional.empty();
    // Advance the time a little, to ensure that leaseTasks() returns all tasks.
    clock.advanceBy(Duration.standardHours(1));

    action.run();
  }

  private static TaskOptions createRefreshTask(String name, TargetType type) {
    TaskOptions options =
        TaskOptions.Builder.withMethod(Method.PULL)
            .param(DNS_TARGET_TYPE_PARAM, type.toString())
            .param(DNS_TARGET_NAME_PARAM, name)
            .param(DNS_TARGET_CREATE_TIME_PARAM, "3000-01-01TZ");
    String tld = InternetDomainName.from(name).parts().reverse().get(0);
    return options.param("tld", tld);
  }

  private static TaskMatcher createDomainRefreshTaskMatcher(String name) {
    return new TaskMatcher()
        .param(DNS_TARGET_NAME_PARAM, name)
        .param(DNS_TARGET_TYPE_PARAM, TargetType.DOMAIN.toString());
  }

  private void assertTldsEnqueuedInPushQueue(ImmutableMultimap<String, String> tldsToDnsWriters)
      throws Exception {
    // By default, the publishDnsUpdates tasks will be enqueued one hour after the update items were
    // created in the pull queue. This is because of the clock.advanceBy in run()
    assertTasksEnqueued(
        DNS_PUBLISH_PUSH_QUEUE_NAME,
        transform(
            tldsToDnsWriters.entries().asList(),
            (Entry<String, String> tldToDnsWriter) ->
                new TaskMatcher()
                    .url(PublishDnsUpdatesAction.PATH)
                    .param("tld", tldToDnsWriter.getKey())
                    .param("dnsWriter", tldToDnsWriter.getValue())
                    .param("itemsCreated", "3000-01-01T00:00:00.000Z")
                    .param("enqueued", "3000-01-01T01:00:00.000Z")
                    // Single-lock TLDs should use lock 1 of 1 by default
                    .param("lockIndex", "1")
                    .param("numPublishLocks", "1")
                    .header("content-type", "application/x-www-form-urlencoded")));
  }

  @Test
  public void testSuccess_methodPostIsDefault() throws Exception {
    dnsQueue.addDomainRefreshTask("domain.com");
    dnsQueue.addDomainRefreshTask("domain.net");
    dnsQueue.addDomainRefreshTask("domain.example");

    run();

    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTasksEnqueued(
        DNS_PUBLISH_PUSH_QUEUE_NAME,
        new TaskMatcher().method("POST"),
        new TaskMatcher().method("POST"),
        new TaskMatcher().method("POST"));
  }

  @Test
  public void testSuccess_allSingleLockTlds() throws Exception {
    dnsQueue.addDomainRefreshTask("domain.com");
    dnsQueue.addDomainRefreshTask("domain.net");
    dnsQueue.addDomainRefreshTask("domain.example");

    run();

    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTldsEnqueuedInPushQueue(
        ImmutableMultimap.of("com", "comWriter", "net", "netWriter", "example", "exampleWriter"));
  }

  @Test
  public void testSuccess_moreUpdatesThanQueueBatchSize() throws Exception {
    // The task queue has a batch size of 1000 (that's the maximum number of items you can lease at
    // once).
    ImmutableList<String> domains =
        IntStream.range(0, 1500)
            .mapToObj(i -> String.format("domain_%04d.com", i))
            .collect(toImmutableList());
    domains.forEach(dnsQueue::addDomainRefreshTask);

    run();

    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    ImmutableList<ImmutableMultimap<String, String>> queuedParams =
        getQueuedParams(DNS_PUBLISH_PUSH_QUEUE_NAME);
    // ReadDnsQueueAction batches items per TLD in batches of size 100.
    // So for 1500 items in the DNS queue, we expect 15 items in the push queue
    assertThat(queuedParams).hasSize(15);
    // Check all the expected domains are indeed enqueued
    assertThat(
            queuedParams
                .stream()
                .flatMap(params -> params.get("domains").stream()))
        .containsExactlyElementsIn(domains);
  }

  @Test
  public void testSuccess_twoDnsWriters() throws Exception {
    persistResource(
        Registry.get("com")
            .asBuilder()
            .setDnsWriters(ImmutableSet.of("comWriter", "otherWriter"))
            .build());
    dnsQueue.addDomainRefreshTask("domain.com");

    run();

    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTldsEnqueuedInPushQueue(ImmutableMultimap.of("com", "comWriter", "com", "otherWriter"));
  }

  @Test
  public void testSuccess_differentUpdateTimes_usesMinimum() throws Exception {
    clock.setTo(DateTime.parse("3000-02-03TZ"));
    dnsQueue.addDomainRefreshTask("domain1.com");
    clock.setTo(DateTime.parse("3000-02-04TZ"));
    dnsQueue.addDomainRefreshTask("domain2.com");
    clock.setTo(DateTime.parse("3000-02-05TZ"));
    dnsQueue.addDomainRefreshTask("domain3.com");

    run();

    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertThat(getQueuedParams(DNS_PUBLISH_PUSH_QUEUE_NAME))
        .containsExactly(
            new ImmutableMultimap.Builder<String, String>()
                .put("enqueued", "3000-02-05T01:00:00.000Z")
                .put("itemsCreated", "3000-02-03T00:00:00.000Z")
                .put("tld", "com")
                .put("dnsWriter", "comWriter")
                .put("domains", "domain1.com")
                .put("domains", "domain2.com")
                .put("domains", "domain3.com")
                .put("lockIndex", "1")
                .put("numPublishLocks", "1")
                .build());
  }

  @Test
  public void testSuccess_oneTldPaused_returnedToQueue() throws Exception {
    persistResource(Registry.get("net").asBuilder().setDnsPaused(true).build());
    dnsQueue.addDomainRefreshTask("domain.com");
    dnsQueue.addDomainRefreshTask("domain.net");
    dnsQueue.addDomainRefreshTask("domain.example");

    run();

    assertTasksEnqueued(DNS_PULL_QUEUE_NAME, createDomainRefreshTaskMatcher("domain.net"));
    assertTldsEnqueuedInPushQueue(
        ImmutableMultimap.of("com", "comWriter", "example", "exampleWriter"));
  }

  @Test
  public void testSuccess_oneTldUnknown_returnedToQueue() throws Exception {
    dnsQueue.addDomainRefreshTask("domain.com");
    dnsQueue.addDomainRefreshTask("domain.example");
    QueueFactory.getQueue(DNS_PULL_QUEUE_NAME)
        .add(
            TaskOptions.Builder.withDefaults()
                .method(Method.PULL)
                .param(DNS_TARGET_TYPE_PARAM, TargetType.DOMAIN.toString())
                .param(DNS_TARGET_NAME_PARAM, "domain.unknown")
                .param(DNS_TARGET_CREATE_TIME_PARAM, "3000-01-01TZ")
                .param(PARAM_TLD, "unknown"));

    run();

    assertTasksEnqueued(DNS_PULL_QUEUE_NAME, createDomainRefreshTaskMatcher("domain.unknown"));
    assertTldsEnqueuedInPushQueue(
        ImmutableMultimap.of("com", "comWriter", "example", "exampleWriter"));
  }

  @Test
  public void testSuccess_corruptTaskTldMismatch_published() throws Exception {
    // TODO(mcilwain): what's the correct action to take in this case?
    dnsQueue.addDomainRefreshTask("domain.com");
    dnsQueue.addDomainRefreshTask("domain.example");
    QueueFactory.getQueue(DNS_PULL_QUEUE_NAME)
        .add(
            TaskOptions.Builder.withDefaults()
                .method(Method.PULL)
                .param(DNS_TARGET_TYPE_PARAM, TargetType.DOMAIN.toString())
                .param(DNS_TARGET_NAME_PARAM, "domain.wrongtld")
                .param(DNS_TARGET_CREATE_TIME_PARAM, "3000-01-01TZ")
                .param(PARAM_TLD, "net"));

    run();

    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTldsEnqueuedInPushQueue(
        ImmutableMultimap.of("com", "comWriter", "example", "exampleWriter", "net", "netWriter"));
  }

  @Test
  public void testSuccess_corruptTaskNoTld_discarded() throws Exception {
    dnsQueue.addDomainRefreshTask("domain.com");
    dnsQueue.addDomainRefreshTask("domain.example");
    QueueFactory.getQueue(DNS_PULL_QUEUE_NAME)
        .add(
            TaskOptions.Builder.withDefaults()
                .method(Method.PULL)
                .param(DNS_TARGET_TYPE_PARAM, TargetType.DOMAIN.toString())
                .param(DNS_TARGET_NAME_PARAM, "domain.net"));

    run();

    // The corrupt task isn't in the pull queue, but also isn't in the push queue
    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTldsEnqueuedInPushQueue(
        ImmutableMultimap.of("com", "comWriter", "example", "exampleWriter"));
  }

  @Test
  public void testSuccess_corruptTaskNoName_discarded() throws Exception {
    dnsQueue.addDomainRefreshTask("domain.com");
    dnsQueue.addDomainRefreshTask("domain.example");
    QueueFactory.getQueue(DNS_PULL_QUEUE_NAME)
        .add(
            TaskOptions.Builder.withDefaults()
                .method(Method.PULL)
                .param(DNS_TARGET_TYPE_PARAM, TargetType.DOMAIN.toString())
                .param(PARAM_TLD, "net"));

    run();

    // The corrupt task isn't in the pull queue, but also isn't in the push queue
    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTldsEnqueuedInPushQueue(
        ImmutableMultimap.of("com", "comWriter", "example", "exampleWriter"));
  }

  @Test
  public void testSuccess_corruptTaskNoType_discarded() throws Exception {
    dnsQueue.addDomainRefreshTask("domain.com");
    dnsQueue.addDomainRefreshTask("domain.example");
    QueueFactory.getQueue(DNS_PULL_QUEUE_NAME)
        .add(
            TaskOptions.Builder.withDefaults()
                .method(Method.PULL)
                .param(DNS_TARGET_NAME_PARAM, "domain.net")
                .param(PARAM_TLD, "net"));

    run();

    // The corrupt task isn't in the pull queue, but also isn't in the push queue
    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTldsEnqueuedInPushQueue(
        ImmutableMultimap.of("com", "comWriter", "example", "exampleWriter"));
  }

  @Test
  public void testSuccess_corruptTaskWrongType_discarded() throws Exception {
    dnsQueue.addDomainRefreshTask("domain.com");
    dnsQueue.addDomainRefreshTask("domain.example");
    QueueFactory.getQueue(DNS_PULL_QUEUE_NAME)
        .add(
            TaskOptions.Builder.withDefaults()
                .method(Method.PULL)
                .param(DNS_TARGET_TYPE_PARAM, "Wrong type")
                .param(DNS_TARGET_NAME_PARAM, "domain.net")
                .param(PARAM_TLD, "net"));

    run();

    // The corrupt task isn't in the pull queue, but also isn't in the push queue
    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTldsEnqueuedInPushQueue(
        ImmutableMultimap.of("com", "comWriter", "example", "exampleWriter"));
  }

  @Test
  public void testSuccess_zone_getsIgnored() throws Exception {
    dnsQueue.addHostRefreshTask("ns1.domain.com");
    dnsQueue.addDomainRefreshTask("domain.net");
    dnsQueue.addZoneRefreshTask("example");

    run();

    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTasksEnqueued(
        DNS_PUBLISH_PUSH_QUEUE_NAME,
        new TaskMatcher().url(PublishDnsUpdatesAction.PATH).param("domains", "domain.net"),
        new TaskMatcher().url(PublishDnsUpdatesAction.PATH).param("hosts", "ns1.domain.com"));
  }

  @Test
  public void testSuccess_manyDomainsAndHosts() throws Exception {
    List<TaskMatcher> expectedTasks = new ArrayList<>();
    for (String tld : ImmutableList.of("com", "net")) {
      int refreshItemsInTask = 0;
      TaskMatcher task = null;
      // 0: domain; 1: host 1; 2: host 2
      for (int thingType = 0; thingType < 3; thingType++) {
        for (int i = 0; i < 150; i++) {
          String domainName = String.format("domain%04d.%s", i, tld);
          // If we don't have an existing task into which to dump new refreshes, create one.
          if (task == null) {
            task = new TaskMatcher().url(PublishDnsUpdatesAction.PATH);
            expectedTasks.add(task);
            refreshItemsInTask = 0;
          }
          switch (thingType) {
            case 1:
              getQueue(DNS_PULL_QUEUE_NAME)
                  .add(createRefreshTask("ns1." + domainName, TargetType.HOST));
              task.param("hosts", "ns1." + domainName);
              break;
            case 2:
              getQueue(DNS_PULL_QUEUE_NAME)
                  .add(createRefreshTask("ns2." + domainName, TargetType.HOST));
              task.param("hosts", "ns2." + domainName);
              break;
            default:
              dnsQueue.addDomainRefreshTask(domainName);
              task.param("domains", domainName);
              break;
          }
          // If this task is now full up, wash our hands of it, so that we'll start a new one the
          // next time through the loop.
          refreshItemsInTask++;
          if (refreshItemsInTask >= TEST_TLD_UPDATE_BATCH_SIZE) {
            task = null;
          }
        }
      }
    }

    run();

    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    assertTasksEnqueued(DNS_PUBLISH_PUSH_QUEUE_NAME, expectedTasks);
  }

  @Test
  public void testSuccess_lockGroupsHostBySuperordinateDomain() throws Exception {
    dnsQueue.addDomainRefreshTask("hello.multilock.uk");
    dnsQueue.addHostRefreshTask("ns1.abc.hello.multilock.uk");
    dnsQueue.addHostRefreshTask("ns2.hello.multilock.uk");
    dnsQueue.addDomainRefreshTask("another.multilock.uk");
    dnsQueue.addHostRefreshTask("ns3.def.another.multilock.uk");
    dnsQueue.addHostRefreshTask("ns4.another.multilock.uk");

    run();

    assertNoTasksEnqueued(DNS_PULL_QUEUE_NAME);
    // Expect two different groups; in-balliwick hosts are locked with their superordinate domains.
    assertTasksEnqueued(
        DNS_PUBLISH_PUSH_QUEUE_NAME,
        new TaskMatcher()
            .url(PublishDnsUpdatesAction.PATH)
            .param("tld", "multilock.uk")
            .param("dnsWriter", "multilockWriter")
            .param("itemsCreated", "3000-01-01T00:00:00.000Z")
            .param("enqueued", "3000-01-01T01:00:00.000Z")
            .param("domains", "hello.multilock.uk")
            .param("hosts", "ns1.abc.hello.multilock.uk")
            .param("hosts", "ns2.hello.multilock.uk")
            .header("content-type", "application/x-www-form-urlencoded"),
        new TaskMatcher()
            .url(PublishDnsUpdatesAction.PATH)
            .param("tld", "multilock.uk")
            .param("dnsWriter", "multilockWriter")
            .param("itemsCreated", "3000-01-01T00:00:00.000Z")
            .param("enqueued", "3000-01-01T01:00:00.000Z")
            .param("domains", "another.multilock.uk")
            .param("hosts", "ns3.def.another.multilock.uk")
            .param("hosts", "ns4.another.multilock.uk")
            .header("content-type", "application/x-www-form-urlencoded"));
  }
}
