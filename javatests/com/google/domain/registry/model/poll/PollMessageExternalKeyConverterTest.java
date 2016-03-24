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

package com.google.domain.registry.model.poll;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveContact;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveDomain;
import static com.google.domain.registry.testing.DatastoreHelper.persistActiveHost;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.joda.time.DateTimeZone.UTC;

import com.google.domain.registry.model.domain.Period;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.model.poll.PollMessageExternalKeyConverter.PollMessageExternalKeyParseException;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.InjectRule;

import com.googlecode.objectify.Key;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PollMessageExternalKeyConverter}. */
@RunWith(JUnit4.class)
public class PollMessageExternalKeyConverterTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public InjectRule inject = new InjectRule();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  HistoryEntry historyEntry;
  FakeClock clock = new FakeClock(DateTime.now(UTC));
  PollMessageExternalKeyConverter converter = new PollMessageExternalKeyConverter();

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    createTld("foobar");
    historyEntry = persistResource(new HistoryEntry.Builder()
      .setParent(persistActiveDomain("foo.foobar"))
      .setType(HistoryEntry.Type.DOMAIN_CREATE)
      .setPeriod(Period.create(1, Period.Unit.YEARS))
      .setXmlBytes("<xml></xml>".getBytes(UTF_8))
      .setModificationTime(clock.nowUtc())
      .setClientId("foo")
      .setTrid(Trid.create("ABC-123"))
      .setBySuperuser(false)
      .setReason("reason")
      .setRequestedByRegistrar(false)
      .build());
  }

  @Test
  public void testSuccess_domain() {
    PollMessage.OneTime pollMessage = persistResource(
        new PollMessage.OneTime.Builder()
            .setClientId("TheRegistrar")
            .setEventTime(clock.nowUtc())
            .setMsg("Test poll message")
            .setParent(historyEntry)
            .build());
    Key<PollMessage> key = Key.<PollMessage>create(pollMessage);
    assertThat(converter.convert(key)).isEqualTo("1-2-FOOBAR-4-5");
    assertThat(converter.reverse().convert("1-2-FOOBAR-4-5")).isEqualTo(key);
  }

  @Test
  public void testSuccess_contact() {
    historyEntry =
        persistResource(historyEntry.asBuilder().setParent(persistActiveContact("tim")).build());
    PollMessage.OneTime pollMessage = persistResource(
        new PollMessage.OneTime.Builder()
            .setClientId("TheRegistrar")
            .setEventTime(clock.nowUtc())
            .setMsg("Test poll message")
            .setParent(historyEntry)
            .build());
    Key<PollMessage> key = Key.<PollMessage>create(pollMessage);
    assertThat(converter.convert(key)).isEqualTo("2-5-ROID-4-6");
    assertThat(converter.reverse().convert("2-5-ROID-4-6")).isEqualTo(key);
  }

  @Test
  public void testSuccess_host() {
    historyEntry =
        persistResource(historyEntry.asBuilder().setParent(persistActiveHost("time.zyx")).build());
    PollMessage.OneTime pollMessage = persistResource(
        new PollMessage.OneTime.Builder()
            .setClientId("TheRegistrar")
            .setEventTime(clock.nowUtc())
            .setMsg("Test poll message")
            .setParent(historyEntry)
            .build());
    Key<PollMessage> key = Key.<PollMessage>create(pollMessage);
    assertThat(converter.convert(key)).isEqualTo("3-5-ROID-4-6");
    assertThat(converter.reverse().convert("3-5-ROID-4-6")).isEqualTo(key);
  }

  @Test
  public void testFailure_invalidEppResourceTypeId() throws Exception {
    // Populate the testdata correctly as for 1-2-FOOBAR-4-5 so we know that the only thing that
    // is wrong here is the EppResourceTypeId.
    testSuccess_domain();
    thrown.expect(PollMessageExternalKeyParseException.class);
    converter.reverse().convert("4-2-FOOBAR-4-5");
  }

  @Test
  public void testFailure_tooFewComponentParts() throws Exception {
    thrown.expect(PollMessageExternalKeyParseException.class);
    converter.reverse().convert("1-3-EXAMPLE");
  }

  @Test
  public void testFailure_nonNumericIds() throws Exception {
    thrown.expect(PollMessageExternalKeyParseException.class);
    converter.reverse().convert("A-B-FOOBAR-D-E");
  }
}
