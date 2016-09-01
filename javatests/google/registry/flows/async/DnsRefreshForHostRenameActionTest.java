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

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.dns.DnsQueue;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.model.host.HostResource;
import google.registry.request.HttpException.BadRequestException;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.testing.mapreduce.MapreduceTestCase;
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
    action.mrRunner = new MapreduceRunner(Optional.<Integer>of(5), Optional.<Integer>absent());
    action.response = new FakeResponse();
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void testSuccess_dnsUpdateEnqueued() throws Exception {
    createTld("tld");
    Key<HostResource> renamedHostKey = Key.create(persistActiveHost("ns1.example.tld"));
    Key<HostResource> otherHostKey = Key.create(persistActiveHost("ns2.example.tld"));
    persistResource(newDomainApplication("notadomain.tld").asBuilder()
        .setNameservers(ImmutableSet.of(renamedHostKey))
        .build());
    persistResource(newDomainResource("example.tld").asBuilder()
        .setNameservers(ImmutableSet.of(renamedHostKey))
        .build());
    persistResource(newDomainResource("otherexample.tld").asBuilder()
        .setNameservers(ImmutableSet.of(renamedHostKey))
        .build());
    persistResource(newDomainResource("untouched.tld").asBuilder()
        .setNameservers(ImmutableSet.of(otherHostKey))
        .build());
    runMapreduce(renamedHostKey.getString());
    verify(dnsQueue).addDomainRefreshTask("example.tld");
    verify(dnsQueue).addDomainRefreshTask("otherexample.tld");
    verifyNoMoreInteractions(dnsQueue);
  }

  @Test
  public void testSuccess_noDnsTasksForDeletedDomain() throws Exception {
    createTld("tld");
    Key<HostResource> renamedHostKey = Key.create(persistActiveHost("ns1.example.tld"));
    persistResource(newDomainResource("example.tld").asBuilder()
        .setNameservers(ImmutableSet.of(renamedHostKey))
        .setDeletionTime(START_OF_TIME)
        .build());
    runMapreduce(renamedHostKey.getString());
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
