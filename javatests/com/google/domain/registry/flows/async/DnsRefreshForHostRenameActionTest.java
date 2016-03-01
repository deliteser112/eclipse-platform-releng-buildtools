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

package com.google.domain.registry.flows.async;

import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainApplication;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainResource;
import static com.google.domain.registry.testing.DatastoreHelper.newHostResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.dns.DnsQueue;
import com.google.domain.registry.mapreduce.MapreduceRunner;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.request.HttpException.BadRequestException;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.InjectRule;
import com.google.domain.registry.testing.mapreduce.MapreduceTestCase;

import com.googlecode.objectify.Key;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DnsRefreshForHostRenameAction}. */
@RunWith(JUnit4.class)
public class DnsRefreshForHostRenameActionTest
    extends MapreduceTestCase<DnsRefreshForHostRenameAction> {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public InjectRule inject = new InjectRule();

  private final DnsQueue dnsQueue = mock(DnsQueue.class);

  @Before
  public void setup() throws Exception {
    inject.setStaticField(DnsRefreshForHostRenameAction.class, "dnsQueue", dnsQueue);
  }

  private void runMapreduce(String hostKeyString) throws Exception {
    action = new DnsRefreshForHostRenameAction();
    action.hostKeyString = hostKeyString;
    action.mrRunner = new MapreduceRunner(Optional.<Integer>absent(), Optional.<Integer>absent());
    action.response = new FakeResponse();
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void testSuccess_dnsUpdateEnqueued() throws Exception {
    createTld("tld");
    HostResource renamedHost = persistActiveHost("ns1.example.tld");
    HostResource otherHost = persistActiveHost("ns2.example.tld");
    persistResource(newDomainApplication("notadomain.tld").asBuilder()
        .setNameservers(ImmutableSet.of(ReferenceUnion.create(renamedHost)))
        .build());
    persistResource(newDomainResource("example.tld").asBuilder()
        .setNameservers(ImmutableSet.of(ReferenceUnion.create(renamedHost)))
        .build());
    persistResource(newDomainResource("otherexample.tld").asBuilder()
        .setNameservers(ImmutableSet.of(ReferenceUnion.create(renamedHost)))
        .build());
    persistResource(newDomainResource("untouched.tld").asBuilder()
        .setNameservers(ImmutableSet.of(ReferenceUnion.create(otherHost)))
        .build());
    runMapreduce(Key.create(renamedHost).getString());
    verify(dnsQueue).addDomainRefreshTask("example.tld");
    verify(dnsQueue).addDomainRefreshTask("otherexample.tld");
    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testSuccess_noDnsTasksForDeletedDomain() throws Exception {
    createTld("tld");
    HostResource renamedHost = persistActiveHost("ns1.example.tld");
    persistResource(newDomainResource("example.tld").asBuilder()
        .setNameservers(ImmutableSet.of(ReferenceUnion.create(renamedHost)))
        .setDeletionTime(START_OF_TIME)
        .build());
    runMapreduce(Key.create(renamedHost).getString());
    verifyZeroInteractions(dnsQueue);
  }

  @Test
  public void testFailure_badKeyPassed() throws Exception {
    createTld("tld");
    thrown.expect(BadRequestException.class, "Could not parse key string: a bad key");
    runMapreduce("a bad key");
  }

  @Test
  public void testFailure_hostDoesntExist() throws Exception {
    createTld("tld");
    HostResource notPersistedHost = newHostResource("ns1.example.tld");
    thrown.expect(
        BadRequestException.class,
        "Could not load resource for key: Key<?>(HostResource(\"2-ROID\"))");
    runMapreduce(Key.create(notPersistedHost).getString());
  }
}
