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

package com.google.domain.registry.dns;

import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveSubordinateHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.TaskQueueHelper.assertDnsTasksEnqueued;
import static com.google.domain.registry.testing.TaskQueueHelper.assertNoDnsTasksEnqueued;
import static com.google.domain.registry.testing.TaskQueueHelper.clearTaskQueue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.domain.registry.dns.writer.api.DnsWriter;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.InjectRule;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import javax.inject.Provider;

/** Unit tests for {@link WriteDnsTask}. */
@RunWith(MockitoJUnitRunner.class)
public class WriteDnsTaskTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private final FakeClock clock = new FakeClock(DateTime.parse("1971-01-01TZ"));

  private final DnsWriter dnsWriter = mock(DnsWriter.class);
  private final DnsQueue dnsQueue = new DnsQueue();

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    createTld("xn--q9jyb4c");
    dnsQueue.queue = QueueFactory.getQueue(DnsConstants.DNS_PULL_QUEUE_NAME);
    dnsQueue.writeBatchSize = 7;
    dnsQueue.writeLockTimeout = Duration.standardSeconds(10);
  }

  private void run(String tld) {
    WriteDnsTask task = new WriteDnsTask();
    task.dnsQueue = dnsQueue;
    task.timeout = Duration.standardSeconds(10);
    task.tld = tld;
    task.writerProvider = new Provider<DnsWriter>() {
      @Override
      public DnsWriter get() {
        return dnsWriter;
      }};
    task.run();
  }

  @After
  public void cleanUp() throws Exception {
    clearTaskQueue("dns-pull");
  }

  @Test
  public void testSuccess_host() throws Exception {
    persistActiveSubordinateHost(
        "ns1.example.xn--q9jyb4c", persistActiveDomain("example.xn--q9jyb4c"));
    clock.advanceOneMilli();
    dnsQueue.addHostRefreshTask("ns1.example.xn--q9jyb4c");
    assertDnsTasksEnqueued("ns1.example.xn--q9jyb4c");
    run("xn--q9jyb4c");
    verify(dnsWriter).publishHost("ns1.example.xn--q9jyb4c");
    verify(dnsWriter).close();
    verifyNoMoreInteractions(dnsWriter);
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testSuccess_domain() throws Exception {
    persistActiveSubordinateHost(
        "ns1.example.xn--q9jyb4c", persistActiveDomain("example.xn--q9jyb4c"));
    clock.advanceOneMilli();
    dnsQueue.addDomainRefreshTask("example.xn--q9jyb4c");
    assertDnsTasksEnqueued("example.xn--q9jyb4c");
    run("xn--q9jyb4c");
    verify(dnsWriter).publishDomain("example.xn--q9jyb4c");
    verify(dnsWriter).close();
    verifyNoMoreInteractions(dnsWriter);
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testSuccess_zone() throws Exception {
    dnsQueue.addZoneRefreshTask("xn--q9jyb4c");
    assertDnsTasksEnqueued("xn--q9jyb4c");
    run("xn--q9jyb4c");
    verify(dnsWriter).close();
    verifyNoMoreInteractions(dnsWriter);
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testSuccess_dnsPaused() throws Exception {
    persistActiveSubordinateHost(
        "ns1.example.xn--q9jyb4c", persistActiveDomain("example.xn--q9jyb4c"));
    clock.advanceOneMilli();
    dnsQueue.addDomainRefreshTask("example.xn--q9jyb4c");
    assertDnsTasksEnqueued("example.xn--q9jyb4c");
    persistResource(Registry.get("xn--q9jyb4c").asBuilder().setDnsPaused(true).build());
    clock.advanceOneMilli();
    run("xn--q9jyb4c");
    verifyZeroInteractions(dnsWriter);
    assertDnsTasksEnqueued("example.xn--q9jyb4c");
  }

  @Test
  public void testSuccess_twoTasksFromTheSameTld() throws Exception {
    persistActiveSubordinateHost(
        "ns1.example.xn--q9jyb4c", persistActiveDomain("example.xn--q9jyb4c"));
    clock.advanceOneMilli();
    dnsQueue.addDomainRefreshTask("example.xn--q9jyb4c");

    persistActiveSubordinateHost(
        "ns1.example2.xn--q9jyb4c", persistActiveDomain("example2.xn--q9jyb4c"));
    clock.advanceOneMilli();
    dnsQueue.addDomainRefreshTask("example2.xn--q9jyb4c");
    assertDnsTasksEnqueued("example.xn--q9jyb4c", "example2.xn--q9jyb4c");

    run("xn--q9jyb4c");
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testSuccess_twoTasksInDifferentTlds() throws Exception {
    // refresh example.xn--q9jyb4c
    persistActiveSubordinateHost(
        "ns1.example.xn--q9jyb4c", persistActiveDomain("example.xn--q9jyb4c"));
    clock.advanceOneMilli();
    dnsQueue.addDomainRefreshTask("example.xn--q9jyb4c");
    // refresh example.example
    createTld("example");
    persistActiveSubordinateHost(
        "ns1.example.example", persistActiveDomain("example.example"));
    clock.advanceOneMilli();
    dnsQueue.addDomainRefreshTask("example.example");
    // there should now be two tasks enqueued
    assertDnsTasksEnqueued("example.example", "example.xn--q9jyb4c");
    // process one, leaving one
    run("example");
    assertDnsTasksEnqueued("example.xn--q9jyb4c");
    // process the other, leaving none
    run("xn--q9jyb4c");
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testSuccess_domainDeleted() throws Exception {
    dnsQueue.addDomainRefreshTask("example.xn--q9jyb4c");
    assertDnsTasksEnqueued("example.xn--q9jyb4c");

    run("xn--q9jyb4c");
    assertNoDnsTasksEnqueued();
  }
}
