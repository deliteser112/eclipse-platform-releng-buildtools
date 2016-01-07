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

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistActiveSubordinateHost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import google.registry.dns.DnsConstants.TargetType;
import google.registry.model.domain.DomainResource;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RefreshDnsAction}. */
@RunWith(JUnit4.class)
public class RefreshDnsActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final DnsQueue dnsQueue = mock(DnsQueue.class);
  private final FakeClock clock = new FakeClock();

  private void run(TargetType type, String name) {
    RefreshDnsAction action = new RefreshDnsAction();
    action.clock = clock;
    action.domainOrHostName = name;
    action.type = type;
    action.dnsQueue = dnsQueue;
    action.run();
  }

  @Before
  public void before() {
    createTld("xn--q9jyb4c");
  }

  @Test
  public void testSuccess_host() throws Exception {
    DomainResource domain = persistActiveDomain("example.xn--q9jyb4c");
    persistActiveSubordinateHost("ns1.example.xn--q9jyb4c", domain);
    run(TargetType.HOST, "ns1.example.xn--q9jyb4c");
    verify(dnsQueue).addHostRefreshTask("ns1.example.xn--q9jyb4c");
    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testSuccess_externalHostNotEnqueued() throws Exception {
    persistActiveDomain("example.xn--q9jyb4c");
    persistActiveHost("ns1.example.xn--q9jyb4c");
    thrown.expect(BadRequestException.class,
        "ns1.example.xn--q9jyb4c isn't a subordinate hostname");
    try {
      run(TargetType.HOST, "ns1.example.xn--q9jyb4c");
    } finally {
      verifyNoMoreInteractions(dnsQueue);
    }
  }

  @Test
  public void testSuccess_domain() throws Exception {
    persistActiveDomain("example.xn--q9jyb4c");
    run(TargetType.DOMAIN, "example.xn--q9jyb4c");
    verify(dnsQueue).addDomainRefreshTask("example.xn--q9jyb4c");
    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testFailure_unqualifiedName() throws Exception {
    thrown.expect(BadRequestException.class);
    run(TargetType.DOMAIN, "example");
  }

  @Test
  public void testFailure_hostDoesNotExist() throws Exception {
    thrown.expect(NotFoundException.class);
    run(TargetType.HOST, "ns1.example.xn--q9jyb4c");
  }

  @Test
  public void testFailure_domainDoesNotExist() throws Exception {
    thrown.expect(NotFoundException.class);
    run(TargetType.DOMAIN, "example.xn--q9jyb4c");
  }
}
