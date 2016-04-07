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

package com.google.domain.registry.dns.writer.dnsupdate;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveSubordinateHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistDeletedDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistDeletedHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.domain.secdns.DelegationSignerData;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.InjectRule;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;
import org.xbill.DNS.Update;

import java.util.ArrayList;
import java.util.Iterator;

import junit.framework.AssertionFailedError;

/** Unit tests for {@link DnsUpdateWriter}. */
@RunWith(MockitoJUnitRunner.class)
public class DnsUpdateWriterTest {

  @Rule
  public final AppEngineRule appEngine =
      AppEngineRule.builder().withDatastore().withTaskQueue().build();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Rule public final InjectRule inject = new InjectRule();

  private final FakeClock clock = new FakeClock(DateTime.parse("1971-01-01TZ"));

  @Mock private DnsMessageTransport mockResolver;
  @Captor private ArgumentCaptor<Update> updateCaptor;
  private DelegationSignerData testSignerData =
      DelegationSignerData.create(1, 3, 1, base16().decode("0123456789ABCDEF"));
  private DnsUpdateWriter writer;

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);

    createTld("tld");
    when(mockResolver.send(any(Update.class))).thenReturn(messageWithResponseCode(Rcode.NOERROR));

    writer = new DnsUpdateWriter(Duration.ZERO, mockResolver, clock);
  }

  @Test
  public void publishDomainCreatePublishesNameServers() throws Exception {
    HostResource host1 = persistActiveHost("ns1.example.tld");
    HostResource host2 = persistActiveHost("ns2.example.tld");
    DomainResource domain =
        persistActiveDomain("example.tld")
            .asBuilder()
            .setNameservers(
                ImmutableSet.of(ReferenceUnion.create(host1), ReferenceUnion.create(host2)))
            .build();
    persistResource(domain);

    writer.publishDomain("example.tld");

    verify(mockResolver).send(updateCaptor.capture());
    Update update = updateCaptor.getValue();
    assertThatUpdatedZoneIs(update, "tld.");
    assertThatUpdateDeletes(update, "example.tld.", Type.ANY);
    assertThatUpdateAdds(update, "example.tld.", Type.NS, "ns1.example.tld.", "ns2.example.tld.");
    assertThatTotalUpdateSetsIs(update, 2); // The delete and NS sets
  }

  @Test
  public void publishDomainCreatePublishesDelegationSigner() throws Exception {
    DomainResource domain =
        persistActiveDomain("example.tld")
            .asBuilder()
            .setNameservers(
                ImmutableSet.of(ReferenceUnion.create(persistActiveHost("ns1.example.tld"))))
            .setDsData(ImmutableSet.of(testSignerData))
            .build();
    persistResource(domain);

    writer.publishDomain("example.tld");

    verify(mockResolver).send(updateCaptor.capture());
    Update update = updateCaptor.getValue();
    assertThatUpdatedZoneIs(update, "tld.");
    assertThatUpdateDeletes(update, "example.tld.", Type.ANY);
    assertThatUpdateAdds(update, "example.tld.", Type.NS, "ns1.example.tld.");
    assertThatUpdateAdds(update, "example.tld.", Type.DS, "1 3 1 0123456789ABCDEF");
    assertThatTotalUpdateSetsIs(update, 3); // The delete, the NS, and DS sets
  }

  @Test
  public void publishDomainWhenNotActiveRemovesDnsRecords() throws Exception {
    DomainResource domain =
        persistActiveDomain("example.tld")
            .asBuilder()
            .addStatusValue(StatusValue.SERVER_HOLD)
            .setNameservers(
                ImmutableSet.of(ReferenceUnion.create(persistActiveHost("ns1.example.tld"))))
            .build();
    persistResource(domain);

    writer.publishDomain("example.tld");

    verify(mockResolver).send(updateCaptor.capture());
    Update update = updateCaptor.getValue();
    assertThatUpdatedZoneIs(update, "tld.");
    assertThatUpdateDeletes(update, "example.tld.", Type.ANY);
    assertThatTotalUpdateSetsIs(update, 1); // Just the delete set
  }

  @Test
  public void publishDomainDeleteRemovesDnsRecords() throws Exception {
    persistDeletedDomain("example.tld", clock.nowUtc());

    writer.publishDomain("example.tld");

    verify(mockResolver).send(updateCaptor.capture());
    Update update = updateCaptor.getValue();
    assertThatUpdatedZoneIs(update, "tld.");
    assertThatUpdateDeletes(update, "example.tld.", Type.ANY);
    assertThatTotalUpdateSetsIs(update, 1); // Just the delete set
  }

  @Test
  public void publishHostCreatePublishesAddressRecords() throws Exception {
    HostResource host =
        persistActiveSubordinateHost("ns1.example.tld", persistActiveDomain("example.tld"))
            .asBuilder()
            .setInetAddresses(
                ImmutableSet.of(
                    InetAddresses.forString("10.0.0.1"),
                    InetAddresses.forString("10.1.0.1"),
                    InetAddresses.forString("fd0e:a5c8:6dfb:6a5e:0:0:0:1")))
            .build();
    persistResource(host);

    writer.publishHost("ns1.example.tld");

    verify(mockResolver).send(updateCaptor.capture());
    Update update = updateCaptor.getValue();
    assertThatUpdatedZoneIs(update, "tld.");
    assertThatUpdateDeletes(update, "ns1.example.tld.", Type.ANY);
    assertThatUpdateAdds(update, "ns1.example.tld.", Type.A, "10.0.0.1", "10.1.0.1");
    assertThatUpdateAdds(update, "ns1.example.tld.", Type.AAAA, "fd0e:a5c8:6dfb:6a5e:0:0:0:1");
    assertThatTotalUpdateSetsIs(update, 3); // The delete, the A, and AAAA sets
  }

  @Test
  public void publishHostDeleteRemovesDnsRecords() throws Exception {
    persistDeletedHost("ns1.example.tld", clock.nowUtc());

    writer.publishHost("ns1.example.tld");

    verify(mockResolver).send(updateCaptor.capture());
    Update update = updateCaptor.getValue();
    assertThatUpdatedZoneIs(update, "tld.");
    assertThatUpdateDeletes(update, "ns1.example.tld.", Type.ANY);
    assertThatTotalUpdateSetsIs(update, 1); // Just the delete set
  }

  @Test
  public void publishDomainFailsWhenDnsUpdateReturnsError() throws Exception {
    DomainResource domain =
        persistActiveDomain("example.tld")
            .asBuilder()
            .setNameservers(
                ImmutableSet.of(ReferenceUnion.create(persistActiveHost("ns1.example.tld"))))
            .build();
    persistResource(domain);
    when(mockResolver.send(any(Message.class))).thenReturn(messageWithResponseCode(Rcode.SERVFAIL));
    thrown.expect(VerifyException.class);
    thrown.expectMessage("SERVFAIL");

    writer.publishDomain("example.tld");
  }

  @Test
  public void publishHostFailsWhenDnsUpdateReturnsError() throws Exception {
    HostResource host =
        persistActiveSubordinateHost("ns1.example.tld", persistActiveDomain("example.tld"))
            .asBuilder()
            .setInetAddresses(ImmutableSet.of(InetAddresses.forString("10.0.0.1")))
            .build();
    persistResource(host);
    when(mockResolver.send(any(Message.class))).thenReturn(messageWithResponseCode(Rcode.SERVFAIL));
    thrown.expect(VerifyException.class);
    thrown.expectMessage("SERVFAIL");

    writer.publishHost("ns1.example.tld");
  }

  private void assertThatUpdatedZoneIs(Update update, String zoneName) {
    Record[] zoneRecords = update.getSectionArray(Section.ZONE);
    assertThat(zoneRecords[0].getName().toString()).isEqualTo(zoneName);
  }

  private void assertThatTotalUpdateSetsIs(Update update, int count) {
    assertThat(update.getSectionRRsets(Section.UPDATE)).hasLength(count);
  }

  private void assertThatUpdateDeletes(Update update, String resourceName, int recordType) {
    ImmutableList<Record> deleted = findUpdateRecords(update, resourceName, recordType);
    // There's only an empty (i.e. "delete") record.
    assertThat(deleted.get(0).rdataToString()).hasLength(0);
    assertThat(deleted).hasSize(1);
  }

  private void assertThatUpdateAdds(
      Update update, String resourceName, int recordType, String... resourceData) {
    ArrayList<String> expectedData = new ArrayList<>();
    for (String resourceDatum : resourceData) {
      expectedData.add(resourceDatum.toLowerCase());
    }

    ArrayList<String> actualData = new ArrayList<>();
    for (Record record : findUpdateRecords(update, resourceName, recordType)) {
      actualData.add(record.rdataToString().toLowerCase());
    }
    assertThat(actualData).containsExactlyElementsIn(expectedData);
  }

  private ImmutableList<Record> findUpdateRecords(
      Update update, String resourceName, int recordType) {
    for (RRset set : update.getSectionRRsets(Section.UPDATE)) {
      if (set.getName().toString().equals(resourceName) && set.getType() == recordType) {
        return fixIterator(Record.class, set.rrs());
      }
    }
    throw new AssertionFailedError(
        "no record set found for resource '"
            + resourceName
            + "', type '"
            + Type.string(recordType)
            + "'");
  }

  @SuppressWarnings({"unchecked", "unused"})
  private static <T> ImmutableList<T> fixIterator(Class<T> clazz, final Iterator<?> iterator) {
    return ImmutableList.copyOf((Iterator<T>) iterator);
  }

  private Message messageWithResponseCode(int responseCode) {
    Message message = new Message();
    message.getHeader().setOpcode(Opcode.UPDATE);
    message.getHeader().setFlag(Flags.QR);
    message.getHeader().setRcode(responseCode);
    return message;
  }
}
