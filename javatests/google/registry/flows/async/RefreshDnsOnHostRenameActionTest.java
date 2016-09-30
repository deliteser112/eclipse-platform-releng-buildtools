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

package google.registry.flows.async;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static google.registry.flows.async.RefreshDnsOnHostRenameAction.QUEUE_ASYNC_HOST_RENAME;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistDeletedHost;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.TaskQueueHelper.assertDnsTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertNoDnsTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.time.Duration.millis;
import static org.joda.time.Duration.standardHours;
import static org.joda.time.Duration.standardSeconds;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.host.HostResource;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.FakeSleeper;
import google.registry.testing.InjectRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.testing.mapreduce.MapreduceTestCase;
import google.registry.util.Retrier;
import google.registry.util.Sleeper;
import google.registry.util.SystemSleeper;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RefreshDnsOnHostRenameAction}. */
@RunWith(JUnit4.class)
public class RefreshDnsOnHostRenameActionTest
    extends MapreduceTestCase<RefreshDnsOnHostRenameAction> {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public InjectRule inject = new InjectRule();

  private AsyncFlowEnqueuer enqueuer;
  private final FakeClock clock = new FakeClock(DateTime.parse("2015-01-15T11:22:33Z"));

  @Before
  public void setup() throws Exception {
    createTld("tld");

    enqueuer = new AsyncFlowEnqueuer();
    enqueuer.asyncDnsRefreshPullQueue = getQueue(QUEUE_ASYNC_HOST_RENAME);
    enqueuer.retrier = new Retrier(new FakeSleeper(clock), 1);

    action = new RefreshDnsOnHostRenameAction();
    action.clock = clock;
    action.mrRunner = new MapreduceRunner(Optional.<Integer>of(5), Optional.<Integer>absent());
    action.pullQueue = getQueue(QUEUE_ASYNC_HOST_RENAME);
    action.response = new FakeResponse();
    action.retrier = new Retrier(new FakeSleeper(clock), 1);
  }

  private void runMapreduce() throws Exception {
    clock.advanceOneMilli();
    // Use hard sleeps to ensure that the tasks are enqueued properly and will be leased.
    Sleeper sleeper = new SystemSleeper();
    sleeper.sleep(millis(50));
    action.run();
    sleeper.sleep(millis(50));
    executeTasksUntilEmpty("mapreduce", clock);
    sleeper.sleep(millis(50));
    clock.advanceBy(standardSeconds(5));
    ofy().clearSessionCache();
  }

  @Test
  public void testSuccess_dnsUpdateEnqueued() throws Exception {
    HostResource host = persistActiveHost("ns1.example.tld");
    persistResource(
        newDomainApplication("notadomain.tld")
            .asBuilder()
            .setNameservers(ImmutableSet.of(Key.create(host)))
            .build());
    persistResource(newDomainResource("example.tld", host));
    persistResource(newDomainResource("otherexample.tld", host));
    persistResource(newDomainResource("untouched.tld", persistActiveHost("ns2.example.tld")));

    enqueuer.enqueueAsyncDnsRefresh(host);
    runMapreduce();
    assertDnsTasksEnqueued("example.tld", "otherexample.tld");
    assertNoTasksEnqueued(QUEUE_ASYNC_HOST_RENAME);
  }

  @Test
  public void testSuccess_multipleHostsProcessedInBatch() throws Exception {
    HostResource host1 = persistActiveHost("ns1.example.tld");
    HostResource host2 = persistActiveHost("ns2.example.tld");
    HostResource host3 = persistActiveHost("ns3.example.tld");
    persistResource(newDomainResource("example1.tld", host1));
    persistResource(newDomainResource("example2.tld", host2));
    persistResource(newDomainResource("example3.tld", host3));

    enqueuer.enqueueAsyncDnsRefresh(host1);
    enqueuer.enqueueAsyncDnsRefresh(host2);
    enqueuer.enqueueAsyncDnsRefresh(host3);
    runMapreduce();
    assertDnsTasksEnqueued("example1.tld", "example2.tld", "example3.tld");
    assertNoTasksEnqueued(QUEUE_ASYNC_HOST_RENAME);
  }

  @Test
  public void testSuccess_deletedHost_doesntTriggerDnsRefresh() throws Exception {
    HostResource host = persistDeletedHost("ns11.fakesss.tld", clock.nowUtc().minusDays(4));
    persistResource(newDomainResource("example1.tld", host));
    enqueuer.enqueueAsyncDnsRefresh(host);
    runMapreduce();
    assertNoDnsTasksEnqueued();
    assertNoTasksEnqueued(QUEUE_ASYNC_HOST_RENAME);
  }

  @Test
  public void testSuccess_noDnsTasksForDeletedDomain() throws Exception {
    HostResource renamedHost = persistActiveHost("ns1.example.tld");
    persistResource(
        newDomainResource("example.tld", renamedHost)
            .asBuilder()
            .setDeletionTime(START_OF_TIME)
            .build());
    enqueuer.enqueueAsyncDnsRefresh(renamedHost);
    runMapreduce();
    assertNoDnsTasksEnqueued();
    assertNoTasksEnqueued(QUEUE_ASYNC_HOST_RENAME);
  }

  @Test
  public void testRun_hostDoesntExist_delaysTask() throws Exception {
    HostResource host = newHostResource("ns1.example.tld");
    enqueuer.enqueueAsyncDnsRefresh(host);
    runMapreduce();
    assertNoDnsTasksEnqueued();
    assertTasksEnqueued(
        QUEUE_ASYNC_HOST_RENAME,
        new TaskMatcher()
            .payload("hostKey=" + Key.create(host).getString())
            .etaDelta(standardHours(23), standardHours(25)));
  }
}
