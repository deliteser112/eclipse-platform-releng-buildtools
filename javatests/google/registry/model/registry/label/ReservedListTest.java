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

package google.registry.model.registry.label;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.monitoring.metrics.contrib.DistributionMetricSubject.assertThat;
import static com.google.monitoring.metrics.contrib.LongMetricSubject.assertThat;
import static google.registry.model.registry.label.DomainLabelMetrics.reservedListChecks;
import static google.registry.model.registry.label.DomainLabelMetrics.reservedListHits;
import static google.registry.model.registry.label.DomainLabelMetrics.reservedListProcessingTime;
import static google.registry.model.registry.label.ReservationType.ALLOWED_IN_SUNRISE;
import static google.registry.model.registry.label.ReservationType.FULLY_BLOCKED;
import static google.registry.model.registry.label.ReservationType.NAMESERVER_RESTRICTED;
import static google.registry.model.registry.label.ReservationType.NAME_COLLISION;
import static google.registry.model.registry.label.ReservedList.getAllowedNameservers;
import static google.registry.model.registry.label.ReservedList.getReservationTypes;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import google.registry.model.ofy.Ofy;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservedList.ReservedListEntry;
import google.registry.testing.AppEngineRule;
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
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  FakeClock clock = new FakeClock(DateTime.parse("2010-01-01T10:00:00Z"));

  @Before
  public void before() {
    inject.setStaticField(Ofy.class, "clock", clock);
    createTld("tld");
    reservedListChecks.reset();
    reservedListProcessingTime.reset();
    reservedListHits.reset();
  }

  private static void verifyUnreservedCheckCount(int unreservedCount) {
    assertThat(reservedListChecks)
        .hasValueForLabels(unreservedCount, "tld", "0", "(none)", "(none)")
        .and()
        .hasNoOtherValues();
    assertThat(reservedListProcessingTime)
        .hasAnyValueForLabels("tld", "0", "(none)", "(none)")
        .and()
        .hasNoOtherValues();
    assertThat(reservedListHits).hasNoOtherValues();
  }

  @Test
  public void testGetReservationTypes_allLabelsAreUnreserved_withNoReservedLists() {
    assertThat(Registry.get("tld").getReservedLists()).isEmpty();
    assertThat(getReservationTypes("doodle", "tld")).isEmpty();
    assertThat(getReservationTypes("access", "tld")).isEmpty();
    assertThat(getReservationTypes("rich", "tld")).isEmpty();
    verifyUnreservedCheckCount(3);
  }

  @Test
  public void testZeroReservedLists_doesNotCauseError() {
    assertThat(getReservationTypes("doodle", "tld")).isEmpty();
    verifyUnreservedCheckCount(1);
  }

  @Test
  public void testGetReservationTypes_twoLetterCodesAreAvailable() {
    for (String sld : ImmutableList.of("aa", "az", "zz", "91", "1n", "j5")) {
      assertThat(getReservationTypes(sld, "tld")).isEmpty();
    }
    verifyUnreservedCheckCount(6);
  }

  @Test
  public void testGetReservationTypes_singleCharacterDomainsAreAllowed() {
    // This isn't quite exhaustive but it's close.
    for (char c = 'a'; c <= 'z'; c++) {
      assertThat(getReservationTypes("" + c, "tld")).isEmpty();
    }
    verifyUnreservedCheckCount(26);
  }

  @Test
  public void testGetAllowedNameservers() {
    ReservedList rl1 =
        persistReservedList(
            "reserved1",
            "lol,NAMESERVER_RESTRICTED,ns1.nameserver.com",
            "lol1,NAMESERVER_RESTRICTED,ns1.nameserver.com:ns2.domain.tld:ns3.domain.tld",
            "lol2,NAMESERVER_RESTRICTED,ns.name.tld # This is a comment");
    ReservedList rl2 =
        persistReservedList(
            "reserved2",
            "lol1,NAMESERVER_RESTRICTED,ns3.nameserver.com:ns2.domain.tld:ns3.domain.tld",
            "lol2,NAMESERVER_RESTRICTED,ns3.nameserver.com:ns4.domain.tld",
            "lol3,NAMESERVER_RESTRICTED,ns3.nameserver.com");
    ReservedList rl3 =
        persistReservedList(
            "reserved3", "lol1,NAMESERVER_RESTRICTED,ns3.domain.tld", "lol4,ALLOWED_IN_SUNRISE");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl1, rl2, rl3).build());
    assertThat(getReservationTypes("lol", "tld")).containsExactly(NAMESERVER_RESTRICTED);
    assertThat(getReservationTypes("lol1", "tld")).containsExactly(NAMESERVER_RESTRICTED);
    assertThat(getReservationTypes("lol2", "tld")).containsExactly(NAMESERVER_RESTRICTED);
    assertThat(getReservationTypes("lol3", "tld")).containsExactly(NAMESERVER_RESTRICTED);
    assertThat(getAllowedNameservers(InternetDomainName.from("lol.tld")))
        .containsExactly("ns1.nameserver.com");
    assertThat(getAllowedNameservers(InternetDomainName.from("lol1.tld")))
        .containsExactly("ns3.domain.tld");
    assertThat(getAllowedNameservers(InternetDomainName.from("lol2.tld"))).isEmpty();
    assertThat(getAllowedNameservers(InternetDomainName.from("lol3.tld")))
        .containsExactly("ns3.nameserver.com");
    assertThat(getAllowedNameservers(InternetDomainName.from("lol4.tld"))).isEmpty();
  }

  @Test
  public void testGetReservationTypes_concatsMultipleListsCorrectly() {
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

    assertThat(getReservationTypes("lol", "tld")).containsExactly(FULLY_BLOCKED);
    assertThat(getReservationTypes("cat", "tld")).containsExactly(FULLY_BLOCKED);
    assertThat(getReservationTypes("roflcopter", "tld")).containsExactly(FULLY_BLOCKED);
    assertThat(getReservationTypes("snowcrash", "tld")).containsExactly(FULLY_BLOCKED);
    assertThat(getReservationTypes("doge", "tld")).isEmpty();
    assertThat(reservedListChecks)
        .hasValueForLabels(1, "tld", "0", "(none)", "(none)")
        .and()
        .hasValueForLabels(2, "tld", "1", "reserved1", FULLY_BLOCKED.toString())
        .and()
        .hasValueForLabels(2, "tld", "1", "reserved2", FULLY_BLOCKED.toString())
        .and()
        .hasNoOtherValues();
    assertThat(reservedListProcessingTime)
        .hasAnyValueForLabels("tld", "0", "(none)", "(none)")
        .and()
        .hasAnyValueForLabels("tld", "1", "reserved1", FULLY_BLOCKED.toString())
        .and()
        .hasAnyValueForLabels("tld", "1", "reserved2", FULLY_BLOCKED.toString())
        .and()
        .hasNoOtherValues();
    assertThat(reservedListHits)
        .hasValueForLabels(2, "tld", "reserved1", FULLY_BLOCKED.toString())
        .and()
        .hasValueForLabels(2, "tld", "reserved2", FULLY_BLOCKED.toString())
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void testGetReservationTypes_returnsAllReservationTypesFromMultipleListsForTheSameLabel() {
    ReservedList rl1 =
        persistReservedList("reserved1", "lol,NAME_COLLISION # yup", "cat,FULLY_BLOCKED");
    ReservedList rl2 =
        persistReservedList("reserved2", "lol,ALLOWED_IN_SUNRISE", "snowcrash,FULLY_BLOCKED");
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl1, rl2).build());

    assertThat(getReservationTypes("lol", "tld"))
        .containsExactly(NAME_COLLISION, ALLOWED_IN_SUNRISE);
    assertThat(getReservationTypes("cat", "tld")).containsExactly(FULLY_BLOCKED);
    assertThat(getReservationTypes("snowcrash", "tld")).containsExactly(FULLY_BLOCKED);
  }


  @Test
  public void testGetReservationTypes_worksAfterReservedListRemovedUsingSet() {
    ReservedList rl1 = persistReservedList(
        "reserved1", "lol,FULLY_BLOCKED", "cat,FULLY_BLOCKED");
    ReservedList rl2 = persistReservedList(
        "reserved2", "roflcopter,FULLY_BLOCKED", "snowcrash,FULLY_BLOCKED");
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl1, rl2).build());
    assertThat(getReservationTypes("roflcopter", "tld")).containsExactly(FULLY_BLOCKED);
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl1).build());
    assertWithMessage(
            "roflcopter.tld should be unreserved"
                + " after unsetting the registry's second reserved list")
        .that(getReservationTypes("roflcopter", "tld"))
        .isEmpty();
    assertThat(reservedListChecks)
        .hasValueForLabels(1, "tld", "1", "reserved2", FULLY_BLOCKED.toString())
        .and()
        .hasValueForLabels(1, "tld", "0", "(none)", "(none)")
        .and()
        .hasNoOtherValues();
    assertThat(reservedListProcessingTime)
        .hasAnyValueForLabels("tld", "1", "reserved2", FULLY_BLOCKED.toString())
        .and()
        .hasAnyValueForLabels("tld", "0", "(none)", "(none)")
        .and()
        .hasNoOtherValues();
    assertThat(reservedListHits)
        .hasValueForLabels(1, "tld", "reserved2", FULLY_BLOCKED.toString())
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void testGetReservationTypes_combinesMultipleLists() {
    ReservedList rl1 = persistReservedList(
        "reserved1", "lol,NAME_COLLISION", "roflcopter,ALLOWED_IN_SUNRISE");
    ReservedList rl2 = persistReservedList("reserved2", "lol,FULLY_BLOCKED");
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl1, rl2).build());
    assertThat(getReservationTypes("lol", "tld")).containsExactly(FULLY_BLOCKED, NAME_COLLISION);
    assertThat(getReservationTypes("roflcopter", "tld")).containsExactly(ALLOWED_IN_SUNRISE);
    assertThat(reservedListChecks)
        .hasValueForLabels(1, "tld", "1", "reserved1", ALLOWED_IN_SUNRISE.toString())
        .and()
        .hasValueForLabels(1, "tld", "2", "reserved2", FULLY_BLOCKED.toString())
        .and()
        .hasNoOtherValues();
    assertThat(reservedListProcessingTime)
        .hasAnyValueForLabels("tld", "1", "reserved1", ALLOWED_IN_SUNRISE.toString())
        .and()
        .hasAnyValueForLabels("tld", "2", "reserved2", FULLY_BLOCKED.toString())
        .and()
        .hasNoOtherValues();
    assertThat(reservedListHits)
        .hasValueForLabels(1, "tld", "reserved1", NAME_COLLISION.toString())
        .and()
        .hasValueForLabels(1, "tld", "reserved1", ALLOWED_IN_SUNRISE.toString())
        .and()
        .hasValueForLabels(1, "tld", "reserved2", FULLY_BLOCKED.toString())
        .and()
        .hasNoOtherValues();
  }

  @Test
  public void testSave() {
    ReservedList rl = persistReservedList("tld-reserved", "lol,FULLY_BLOCKED # yup");
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(rl).build());
    assertThat(getReservationTypes("lol", "tld")).containsExactly(FULLY_BLOCKED);
  }

  @Test
  public void testSave_commentsArePersistedCorrectly() {
    ReservedList reservedList = persistReservedList(
        "reserved",
        "trombone,FULLY_BLOCKED  # yup",
        "oysters,FULLY_BLOCKED #  this is a loooong comment",
        "nullcomment,ALLOWED_IN_SUNRISE  #");
    assertThat(reservedList.getReservedListEntries()).hasSize(3);

    ReservedListEntry trombone = reservedList.getReservedListEntries().get("trombone");
    assertThat(trombone.label).isEqualTo("trombone");
    assertThat(trombone.reservationType).isEqualTo(FULLY_BLOCKED);
    assertThat(trombone.comment).isEqualTo("yup");

    ReservedListEntry oysters = reservedList.getReservedListEntries().get("oysters");
    assertThat(oysters.label).isEqualTo("oysters");
    assertThat(oysters.reservationType).isEqualTo(FULLY_BLOCKED);
    assertThat(oysters.comment).isEqualTo("this is a loooong comment");

    ReservedListEntry nullComment = reservedList.getReservedListEntries().get("nullcomment");
    assertThat(nullComment.label).isEqualTo("nullcomment");
    assertThat(nullComment.reservationType).isEqualTo(ALLOWED_IN_SUNRISE);
    assertThat(nullComment.comment).isEmpty();
  }

  @Test
  public void testIsInUse_returnsTrueWhenInUse() {
    ReservedList rl = persistReservedList("reserved", "trombone,FULLY_BLOCKED");
    persistResource(Registry.get("tld").asBuilder().setReservedLists(ImmutableSet.of(rl)).build());
    assertThat(rl.isInUse()).isTrue();
  }

  @Test
  public void testIsInUse_returnsFalseWhenNotInUse() {
    ReservedList rl = persistReservedList("reserved", "trombone,FULLY_BLOCKED");
    assertThat(rl.isInUse()).isFalse();
  }

  @Test
  public void testSetFromInputLines() {
    ReservedList reservedList = persistReservedList("reserved", "trombone,FULLY_BLOCKED");
    assertThat(ReservedList.get("reserved").get().getReservedListEntries()).hasSize(1);
    reservedList = reservedList.asBuilder()
        .setReservedListMapFromLines(
            ImmutableList.of("trombone,FULLY_BLOCKED", "trombone2,FULLY_BLOCKED"))
        .build();
    assertThat(reservedList.getReservedListEntries()).hasSize(2);
  }

  @Test
  public void testAsBuilderReturnsIdenticalReservedList() {
    ReservedList original = persistReservedList("tld-reserved-cloning", "trombone,FULLY_BLOCKED");
    ReservedList clone = original.asBuilder().build();
    assertThat(clone.getName()).isEqualTo("tld-reserved-cloning");
    assertThat(clone.creationTime).isEqualTo(original.creationTime);
    assertThat(clone.lastUpdateTime).isEqualTo(original.lastUpdateTime);
    assertThat(clone.parent).isEqualTo(original.parent);
    assertThat(original.getReservedListEntries()).isEqualTo(clone.getReservedListEntries());
  }

  @Test
  public void testSave_badSyntax() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> persistReservedList("tld", "lol,FULLY_BLOCKED,e,e # yup"));
    assertThat(thrown).hasMessageThat().contains("Could not parse line in reserved list");
  }

  @Test
  public void testSave_badReservationType() {
    assertThrows(
        IllegalArgumentException.class, () -> persistReservedList("tld", "lol,FULLY_BLOCKZ # yup"));
  }

  @Test
  public void testSave_additionalRestrictionWithIncompatibleReservationType() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                persistResource(
                    Registry.get("tld")
                        .asBuilder()
                        .setReservedLists(
                            ImmutableSet.of(
                                persistReservedList("reserved1", "lol,FULLY_BLOCKED,foobar1")))
                        .build()));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Allowed nameservers must be specified for NAMESERVER_RESTRICTED reservations only");
  }

  @Test
  public void testSave_badNameservers_invalidSyntax() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                persistReservedList(
                    "reserved1",
                    "lol,NAMESERVER_RESTRICTED,ns1.domain.tld:ns2.domain.tld",
                    "lol1,NAMESERVER_RESTRICTED,ns1.domain.tld:ns@.domain.tld"));
    assertThat(thrown).hasMessageThat().contains("Not a valid domain name: 'ns@.domain.tld'");
  }

  @Test
  public void testSave_badNameservers_tooFewPartsForHostname() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                persistReservedList(
                    "reserved1",
                    "lol,NAMESERVER_RESTRICTED,ns1.domain.tld:ns2.domain.tld",
                    "lol1,NAMESERVER_RESTRICTED,ns1.domain.tld:domain.tld"));
    assertThat(thrown).hasMessageThat().contains("domain.tld is not a valid nameserver hostname");
  }

  @Test
  public void testSave_noNameserversWithNameserverRestrictedReservation() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                persistResource(
                    Registry.get("tld")
                        .asBuilder()
                        .setReservedLists(
                            ImmutableSet.of(
                                persistReservedList("reserved1", "lol,NAMESERVER_RESTRICTED")))
                        .build()));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Allowed nameservers must be specified for NAMESERVER_RESTRICTED reservations only");
  }

  @Test
  public void testParse_cannotIncludeDuplicateLabels() {
    ReservedList rl = new ReservedList.Builder().setName("blah").build();
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                rl.parse(
                    ImmutableList.of(
                        "lol,FULLY_BLOCKED",
                        "rofl,FULLY_BLOCKED",
                        "paper,FULLY_BLOCKED",
                        "wood,FULLY_BLOCKED",
                        "lol,FULLY_BLOCKED")));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "List 'blah' cannot contain duplicate labels. Dupes (with counts) were: [lol x 2]");
  }

  @Test
  public void testValidation_labelMustBeLowercase() {
    Exception e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ReservedListEntry.create("UPPER.tld", FULLY_BLOCKED, null, null));
    assertThat(e).hasMessageThat().contains("must be in puny-coded, lower-case form");
  }

  @Test
  public void testValidation_labelMustBePunyCoded() {
    Exception e =
        assertThrows(
            IllegalArgumentException.class,
            () -> ReservedListEntry.create("lower.みんな", FULLY_BLOCKED, null, null));
    assertThat(e).hasMessageThat().contains("must be in puny-coded, lower-case form");
  }
}
