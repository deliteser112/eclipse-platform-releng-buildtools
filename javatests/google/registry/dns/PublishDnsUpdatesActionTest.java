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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveSubordinateHost;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.expectThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.dns.DnsMetrics.ActionStatus;
import google.registry.dns.DnsMetrics.CommitStatus;
import google.registry.dns.DnsMetrics.PublishStatus;
import google.registry.dns.writer.DnsWriter;
import google.registry.model.domain.DomainResource;
import google.registry.model.ofy.Ofy;
import google.registry.model.registry.Registry;
import google.registry.request.HttpException.ServiceUnavailableException;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeLockHandler;
import google.registry.testing.InjectRule;
import java.util.Optional;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PublishDnsUpdatesAction}. */
@RunWith(JUnit4.class)
public class PublishDnsUpdatesActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();
  private final FakeClock clock = new FakeClock(DateTime.parse("1971-01-01TZ"));
  private final FakeLockHandler lockHandler = new FakeLockHandler(true);
  private final DnsWriter dnsWriter = mock(DnsWriter.class);
  private final DnsMetrics dnsMetrics = mock(DnsMetrics.class);
  private final DnsQueue dnsQueue = mock(DnsQueue.class);
  private PublishDnsUpdatesAction action;

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    createTld("xn--q9jyb4c");
    persistResource(
        Registry.get("xn--q9jyb4c")
            .asBuilder()
            .setDnsWriters(ImmutableSet.of("correctWriter"))
            .build());
    DomainResource domain1 = persistActiveDomain("example.xn--q9jyb4c");
    persistActiveSubordinateHost("ns1.example.xn--q9jyb4c", domain1);
    persistActiveSubordinateHost("ns2.example.xn--q9jyb4c", domain1);
    DomainResource domain2 = persistActiveDomain("example2.xn--q9jyb4c");
    persistActiveSubordinateHost("ns1.example.xn--q9jyb4c", domain2);
    clock.advanceOneMilli();
  }

  private PublishDnsUpdatesAction createAction(String tld) throws Exception {
    PublishDnsUpdatesAction action = new PublishDnsUpdatesAction();
    action.timeout = Duration.standardSeconds(10);
    action.tld = tld;
    action.hosts = ImmutableSet.of();
    action.domains = ImmutableSet.of();
    action.itemsCreateTime = Optional.of(clock.nowUtc().minusHours(2));
    action.enqueuedTime = Optional.of(clock.nowUtc().minusHours(1));
    action.dnsWriter = "correctWriter";
    action.dnsWriterProxy = new DnsWriterProxy(ImmutableMap.of("correctWriter", dnsWriter));
    action.dnsMetrics = dnsMetrics;
    action.dnsQueue = dnsQueue;
    action.lockHandler = lockHandler;
    action.clock = clock;
    return action;
  }

  @Test
  public void testHost_published() throws Exception {
    action = createAction("xn--q9jyb4c");
    action.hosts = ImmutableSet.of("ns1.example.xn--q9jyb4c");
    action.run();

    verify(dnsWriter).publishHost("ns1.example.xn--q9jyb4c");
    verify(dnsWriter).commit();
    verifyNoMoreInteractions(dnsWriter);

    verify(dnsMetrics).incrementPublishDomainRequests(0, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishDomainRequests(0, PublishStatus.REJECTED);
    verify(dnsMetrics).incrementPublishHostRequests(1, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishHostRequests(0, PublishStatus.REJECTED);
    verify(dnsMetrics).recordCommit("correctWriter", CommitStatus.SUCCESS, Duration.ZERO, 0, 1);
    verify(dnsMetrics)
        .recordActionResult(
            "correctWriter",
            ActionStatus.SUCCESS,
            1,
            Duration.standardHours(2),
            Duration.standardHours(1));
    verifyNoMoreInteractions(dnsMetrics);

    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testDomain_published() throws Exception {
    action = createAction("xn--q9jyb4c");
    action.domains = ImmutableSet.of("example.xn--q9jyb4c");
    action.run();

    verify(dnsWriter).publishDomain("example.xn--q9jyb4c");
    verify(dnsWriter).commit();
    verifyNoMoreInteractions(dnsWriter);

    verify(dnsMetrics).incrementPublishDomainRequests(1, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishDomainRequests(0, PublishStatus.REJECTED);
    verify(dnsMetrics).incrementPublishHostRequests(0, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishHostRequests(0, PublishStatus.REJECTED);
    verify(dnsMetrics).recordCommit("correctWriter", CommitStatus.SUCCESS, Duration.ZERO, 1, 0);
    verify(dnsMetrics)
        .recordActionResult(
            "correctWriter",
            ActionStatus.SUCCESS,
            1,
            Duration.standardHours(2),
            Duration.standardHours(1));
    verifyNoMoreInteractions(dnsMetrics);

    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testPublish_commitFails() throws Exception {
    action = createAction("xn--q9jyb4c");
    action.domains = ImmutableSet.of("example.xn--q9jyb4c", "example2.xn--q9jyb4c");
    action.hosts =
        ImmutableSet.of(
            "ns1.example.xn--q9jyb4c", "ns2.example.xn--q9jyb4c", "ns1.example2.xn--q9jyb4c");
    doThrow(new RuntimeException()).when(dnsWriter).commit();
    expectThrows(RuntimeException.class, action::run);

    verify(dnsMetrics).incrementPublishDomainRequests(2, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishDomainRequests(0, PublishStatus.REJECTED);
    verify(dnsMetrics).incrementPublishHostRequests(3, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishHostRequests(0, PublishStatus.REJECTED);
    verify(dnsMetrics).recordCommit("correctWriter", CommitStatus.FAILURE, Duration.ZERO, 2, 3);
    verify(dnsMetrics)
        .recordActionResult(
            "correctWriter",
            ActionStatus.COMMIT_FAILURE,
            5,
            Duration.standardHours(2),
            Duration.standardHours(1));
    verifyNoMoreInteractions(dnsMetrics);

    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testHostAndDomain_published() throws Exception {
    action = createAction("xn--q9jyb4c");
    action.domains = ImmutableSet.of("example.xn--q9jyb4c", "example2.xn--q9jyb4c");
    action.hosts = ImmutableSet.of(
        "ns1.example.xn--q9jyb4c", "ns2.example.xn--q9jyb4c", "ns1.example2.xn--q9jyb4c");
    action.run();

    verify(dnsWriter).publishDomain("example.xn--q9jyb4c");
    verify(dnsWriter).publishDomain("example2.xn--q9jyb4c");
    verify(dnsWriter).publishHost("ns1.example.xn--q9jyb4c");
    verify(dnsWriter).publishHost("ns2.example.xn--q9jyb4c");
    verify(dnsWriter).publishHost("ns1.example2.xn--q9jyb4c");
    verify(dnsWriter).commit();
    verifyNoMoreInteractions(dnsWriter);

    verify(dnsMetrics).incrementPublishDomainRequests(2, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishDomainRequests(0, PublishStatus.REJECTED);
    verify(dnsMetrics).incrementPublishHostRequests(3, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishHostRequests(0, PublishStatus.REJECTED);
    verify(dnsMetrics).recordCommit("correctWriter", CommitStatus.SUCCESS, Duration.ZERO, 2, 3);
    verify(dnsMetrics)
        .recordActionResult(
            "correctWriter",
            ActionStatus.SUCCESS,
            5,
            Duration.standardHours(2),
            Duration.standardHours(1));
    verifyNoMoreInteractions(dnsMetrics);

    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testWrongTld_notPublished() throws Exception {
    action = createAction("xn--q9jyb4c");
    action.domains = ImmutableSet.of("example.com", "example2.com");
    action.hosts = ImmutableSet.of("ns1.example.com", "ns2.example.com", "ns1.example2.com");
    action.run();

    verify(dnsWriter).commit();
    verifyNoMoreInteractions(dnsWriter);

    verify(dnsMetrics).incrementPublishDomainRequests(0, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishDomainRequests(2, PublishStatus.REJECTED);
    verify(dnsMetrics).incrementPublishHostRequests(0, PublishStatus.ACCEPTED);
    verify(dnsMetrics).incrementPublishHostRequests(3, PublishStatus.REJECTED);
    verify(dnsMetrics).recordCommit("correctWriter", CommitStatus.SUCCESS, Duration.ZERO, 0, 0);
    verify(dnsMetrics)
        .recordActionResult(
            "correctWriter",
            ActionStatus.SUCCESS,
            5,
            Duration.standardHours(2),
            Duration.standardHours(1));
    verifyNoMoreInteractions(dnsMetrics);

    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testLockIsntAvailable() throws Exception {
    action = createAction("xn--q9jyb4c");
    action.domains = ImmutableSet.of("example.com", "example2.com");
    action.hosts = ImmutableSet.of("ns1.example.com", "ns2.example.com", "ns1.example2.com");
    action.lockHandler = new FakeLockHandler(false);
    ServiceUnavailableException thrown =
        expectThrows(ServiceUnavailableException.class, action::run);
    assertThat(thrown).hasMessageThat().contains("Lock failure");

    verifyNoMoreInteractions(dnsWriter);

    verify(dnsMetrics)
        .recordActionResult(
            "correctWriter",
            ActionStatus.LOCK_FAILURE,
            5,
            Duration.standardHours(2),
            Duration.standardHours(1));
    verifyNoMoreInteractions(dnsMetrics);

    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testWrongDnsWriter() throws Exception {
    action = createAction("xn--q9jyb4c");
    action.domains = ImmutableSet.of("example.com", "example2.com");
    action.hosts = ImmutableSet.of("ns1.example.com", "ns2.example.com", "ns1.example2.com");
    action.dnsWriter = "wrongWriter";
    action.run();

    verifyNoMoreInteractions(dnsWriter);

    verify(dnsMetrics)
        .recordActionResult(
            "wrongWriter",
            ActionStatus.BAD_WRITER,
            5,
            Duration.standardHours(2),
            Duration.standardHours(1));
    verifyNoMoreInteractions(dnsMetrics);

    verify(dnsQueue).addDomainRefreshTask("example.com");
    verify(dnsQueue).addDomainRefreshTask("example2.com");
    verify(dnsQueue).addHostRefreshTask("ns1.example.com");
    verify(dnsQueue).addHostRefreshTask("ns2.example.com");
    verify(dnsQueue).addHostRefreshTask("ns1.example2.com");
    verifyNoMoreInteractions(dnsQueue);
  }
}
