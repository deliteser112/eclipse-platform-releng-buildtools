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

package google.registry.model.registry.label;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.model.registry.label.ReservationType.ALLOWED_IN_SUNRISE;
import static google.registry.model.registry.label.ReservationType.FULLY_BLOCKED;
import static google.registry.model.registry.label.ReservationType.RESERVED_FOR_ANCHOR_TENANT;
import static google.registry.model.registry.label.ReservationType.UNRESERVED;
import static google.registry.model.registry.label.ReservedList.getReservation;
import static google.registry.model.registry.label.ReservedList.matchesAnchorTenantReservation;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.ofy.Ofy;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservedList.ReservedListEntry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ReservedList}. */
@RunWith(JUnit4.class)
public class ReservedListTest {

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  FakeClock clock = new FakeClock(DateTime.parse("2010-01-01T10:00:00Z"));

  @Before
  public void before() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    createTld("tld");
  }

  @Test
  public void testGetReservation_allLabelsAreUnreserved_withNoReservedLists() throws Exception {
    assertThat(Registry.get("tld").getReservedLists()).isEmpty();
    assertThat(getReservation("doodle", "tld")).isEqualTo(UNRESERVED);
    assertThat(getReservation("access", "tld")).isEqualTo(UNRESERVED);
    assertThat(getReservation("rich", "tld")).isEqualTo(UNRESERVED);
  }

  @Test
  public void testZeroReservedLists_doesNotCauseError() throws Exception {
    assertThat(getReservation("doodle", "tld")).isEqualTo(UNRESERVED);
  }

  @Test
  public void testGetReservation_twoLetterCodesAreAvailable() {
    for (String sld : ImmutableList.of("aa", "az", "zz", "91", "1n", "j5")) {
      assertThat(getReservation(sld, "tld")).isEqualTo(UNRESERVED);
    }
  }

  @Test
  public void testGetReservation_singleCharacterDomainsAreAllowed() {
    // This isn't quite exhaustive but it's close.
    for (char c = 'a'; c <= 'z'; c++) {
      assertThat(getReservation("" + c, "tld")).isEqualTo(UNRESERVED);
    }
  }

  @Test
  public void testMatchesAnchorTenantReservation() throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(ImmutableSet.of(
                persistReservedList(
                    "reserved1",
                    "lol,RESERVED_FOR_ANCHOR_TENANT,foobar1",
                    "lol2,RESERVED_FOR_ANCHOR_TENANT,abcdefg # This is a comment")))
            .build());
    assertThat(getReservation("lol", "tld")).isEqualTo(RESERVED_FOR_ANCHOR_TENANT);
    assertThat(getReservation("lol2", "tld")).isEqualTo(RESERVED_FOR_ANCHOR_TENANT);
    assertThat(matchesAnchorTenantReservation("lol", "tld", "foobar1")).isTrue();
    assertThat(matchesAnchorTenantReservation("lol", "tld", "foobar")).isFalse();
    assertThat(matchesAnchorTenantReservation("lol2", "tld", "abcdefg")).isTrue();
    assertThat(matchesAnchorTenantReservation("lol2", "tld", "abcdefg ")).isFalse();
    assertThat(matchesAnchorTenantReservation("random", "tld", "abcdefg ")).isFalse();
  }

  @Test
  public void testMatchesAnchorTenantReservation_falseOnOtherReservationTypes() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setReservedLists(ImmutableSet.of(
            persistReservedList(
                "reserved2",
                "lol,FULLY_BLOCKED",
                "lol2,NAME_COLLISION",
                "lol3,MISTAKEN_PREMIUM",
                "lol4,ALLOWED_IN_SUNRISE")))
        .build());
    assertThat(matchesAnchorTenantReservation("lol", "tld", "")).isFalse();
    assertThat(matchesAnchorTenantReservation("lol2", "tld", "")).isFalse();
    assertThat(matchesAnchorTenantReservation("lol3", "tld", "")).isFalse();
    assertThat(matchesAnchorTenantReservation("lol4", "tld", "")).isFalse();
    assertThat(matchesAnchorTenantReservation("lol5", "tld", "")).isFalse();
  }

  @Test
  public void testGetReservation_concatsMultipleListsCorrectly() throws Exception {
    ReservedList rl1 = persistReservedList(
        "reserved1",
        "lol,FULLY_BLOCKED # yup",
        "cat,FULLY_BLOCKED");
    ReservedList rl2 = persistReservedList(
        "reserved2",
        "roflcopter,FULLY_BLOCKED",
        "snowcrash,FULLY_BLOCKED");
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl1, rl2).build());

    assertThat(getReservation("lol", "tld")).isEqualTo(FULLY_BLOCKED);
    assertThat(getReservation("cat", "tld")).isEqualTo(FULLY_BLOCKED);
    assertThat(getReservation("roflcopter", "tld")).isEqualTo(FULLY_BLOCKED);
    assertThat(getReservation("snowcrash", "tld")).isEqualTo(FULLY_BLOCKED);
    assertThat(getReservation("doge", "tld")).isEqualTo(UNRESERVED);
  }

  @Test
  public void testGetReservation_worksAfterReservedListRemovedUsingSet() throws Exception {
    ReservedList rl1 = persistReservedList(
        "reserved1", "lol,FULLY_BLOCKED", "cat,FULLY_BLOCKED");
    ReservedList rl2 = persistReservedList(
        "reserved2", "roflcopter,FULLY_BLOCKED", "snowcrash,FULLY_BLOCKED");
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl1, rl2).build());
    assertThat(getReservation("roflcopter", "tld")).isEqualTo(FULLY_BLOCKED);
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl1).build());
    assertWithMessage(
        "roflcopter.tld should be unreserved after unsetting the registry's second reserved list")
        .that(getReservation("roflcopter", "tld"))
        .isEqualTo(UNRESERVED);
  }

  @Test
  public void testGetReservation_combinesMultipleLists_handlesSeverityCorrectly() throws Exception {
    ReservedList rl1 = persistReservedList(
        "reserved1", "lol,NAME_COLLISION", "roflcopter,ALLOWED_IN_SUNRISE");
    ReservedList rl2 = persistReservedList("reserved2", "lol,FULLY_BLOCKED");
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl1, rl2).build());
    assertThat(getReservation("lol", "tld")).isEqualTo(FULLY_BLOCKED);
    assertThat(getReservation("roflcopter", "tld")).isEqualTo(ALLOWED_IN_SUNRISE);
  }

  @Test
  public void testSave() throws Exception {
    ReservedList rl = persistReservedList("tld-reserved", "lol,FULLY_BLOCKED # yup");
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl).build());
    assertThat(getReservation("lol", "tld")).isEqualTo(FULLY_BLOCKED);
  }

  @Test
  public void testSave_commentsArePersistedCorrectly() throws Exception {
    ReservedList reservedList = persistReservedList(
        "reserved",
        "trombone,FULLY_BLOCKED  # yup",
        "oysters,MISTAKEN_PREMIUM #  this is a loooong comment",
        "nullComment,ALLOWED_IN_SUNRISE  #");
    assertThat(reservedList.getReservedListEntries()).hasSize(3);

    ReservedListEntry trombone = reservedList.getReservedListEntries().get("trombone");
    assertThat(trombone.label).isEqualTo("trombone");
    assertThat(trombone.reservationType).isEqualTo(ReservationType.FULLY_BLOCKED);
    assertThat(trombone.comment).isEqualTo("yup");

    ReservedListEntry oysters = reservedList.getReservedListEntries().get("oysters");
    assertThat(oysters.label).isEqualTo("oysters");
    assertThat(oysters.reservationType).isEqualTo(ReservationType.MISTAKEN_PREMIUM);
    assertThat(oysters.comment).isEqualTo("this is a loooong comment");

    ReservedListEntry nullComment = reservedList.getReservedListEntries().get("nullComment");
    assertThat(nullComment.label).isEqualTo("nullComment");
    assertThat(nullComment.reservationType).isEqualTo(ReservationType.ALLOWED_IN_SUNRISE);
    assertThat(nullComment.comment).isEmpty();
  }

  @Test
  public void testIsInUse_returnsTrueWhenInUse() throws Exception {
    ReservedList rl = persistReservedList("reserved", "trombone,FULLY_BLOCKED");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(ImmutableSet.of(rl)).build());
    assertThat(rl.isInUse()).isTrue();
  }

  @Test
  public void testIsInUse_returnsFalseWhenNotInUse() throws Exception {
    ReservedList rl = persistReservedList("reserved", "trombone,FULLY_BLOCKED");
    assertThat(rl.isInUse()).isFalse();
  }

  @Test
  public void testDelete() throws Exception {
    persistReservedList("reserved", "trombone,FULLY_BLOCKED");
    assertThat(ReservedList.get("reserved")).isPresent();
    ReservedList.delete("reserved");
    assertThat(ReservedList.get("reserved")).isAbsent();
  }

  @Test
  public void testSetFromInputLines() throws Exception {
    ReservedList reservedList = persistReservedList("reserved", "trombone,FULLY_BLOCKED");
    assertThat(ReservedList.get("reserved").get().getReservedListEntries()).hasSize(1);
    reservedList = reservedList.asBuilder()
        .setReservedListMapFromLines(
            ImmutableList.of("trombone,FULLY_BLOCKED", "trombone2,FULLY_BLOCKED"))
        .build();
    assertThat(reservedList.getReservedListEntries()).hasSize(2);
  }

  @Test
  public void testAsBuilderReturnsIdenticalReservedList() throws Exception {
    ReservedList original = persistReservedList("tld-reserved-cloning", "trombone,FULLY_BLOCKED");
    ReservedList clone = original.asBuilder().build();
    assertThat(clone.getName()).isEqualTo("tld-reserved-cloning");
    assertThat(clone.creationTime).isEqualTo(original.creationTime);
    assertThat(clone.description).isEqualTo(original.description);
    assertThat(clone.lastUpdateTime).isEqualTo(original.lastUpdateTime);
    assertThat(clone.parent).isEqualTo(original.parent);
    assertThat(original.getReservedListEntries()).isEqualTo(clone.getReservedListEntries());
  }

  @Test
  public void testDelete_failsWhenListDoesntExist() throws Exception {
    thrown.expect(IllegalStateException.class);
    ReservedList.delete("reserved");
  }

  @Test
  public void testSave_badSyntax() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Could not parse line in reserved list");
    persistReservedList("tld", "lol,FULLY_BLOCKED,e,e # yup");
  }

  @Test
  public void testSave_badReservationType() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    persistReservedList("tld", "lol,FULLY_BLOCKZ # yup");
  }

  @Test
  public void testSave_passwordWithNonAnchorTenantReservation() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Only anchor tenant reservations should have an auth code configured");
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                ImmutableSet.of(persistReservedList("reserved1", "lol,FULLY_BLOCKED,foobar1")))
            .build());
  }

  @Test
  public void testSave_noPasswordWithAnchorTenantReservation() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Anchor tenant reservations must have an auth code configured");
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                ImmutableSet.of(persistReservedList("reserved1", "lol,RESERVED_FOR_ANCHOR_TENANT")))
            .build());
  }

  /** Gets the name of a reserved list. */
  public static final Function<Key<ReservedList>, String> GET_NAME_FUNCTION =
      new Function<Key<ReservedList>, String>() {
        @Override
        public String apply(final Key<ReservedList> input) {
          return input.getName();
        }};
}
