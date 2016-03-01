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
import static com.google.domain.registry.testing.TaskQueueHelper.assertDnsTasksEnqueued;
import static com.google.domain.registry.testing.TaskQueueHelper.assertNoDnsTasksEnqueued;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.request.HttpException.NotFoundException;
import com.google.domain.registry.request.RequestModule;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.InjectRule;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Unit tests for Dagger injection of the DNS package. */
@RunWith(JUnit4.class)
public final class DnsInjectionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse rsp = mock(HttpServletResponse.class);
  private final StringWriter httpOutput = new StringWriter();
  private final FakeClock clock = new FakeClock(DateTime.parse("2014-01-01TZ"));
  private DnsTestComponent component;
  private DnsQueue dnsQueue;

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    when(rsp.getWriter()).thenReturn(new PrintWriter(httpOutput));
    component = DaggerDnsTestComponent.builder()
        .requestModule(new RequestModule(req, rsp))
        .build();
    dnsQueue = component.dnsQueue();
    createTld("lol");
  }

  @Test
  public void testWriteDnsTask_injectsAndWorks() throws Exception {
    persistActiveSubordinateHost("ns1.example.lol", persistActiveDomain("example.lol"));
    clock.advanceOneMilli();
    dnsQueue.addDomainRefreshTask("example.lol");
    when(req.getParameter("tld")).thenReturn("lol");
    component.writeDnsTask().run();
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testWhoisHttpServer_injectsAndWorks() throws Exception {
    persistActiveDomain("example.lol");
    when(req.getParameter("type")).thenReturn("domain");
    when(req.getParameter("name")).thenReturn("example.lol");
    component.refreshDns().run();
    assertDnsTasksEnqueued("example.lol");
  }

  @Test
  public void testWhoisHttpServer_missingDomain_throwsNotFound() throws Exception {
    when(req.getParameter("type")).thenReturn("domain");
    when(req.getParameter("name")).thenReturn("example.lol");
    thrown.expect(NotFoundException.class, "DOMAIN example.lol not found");
    component.refreshDns().run();
  }
}
