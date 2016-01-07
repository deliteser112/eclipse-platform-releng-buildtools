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

package google.registry.tools;

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainApplicationSubject.assertAboutApplications;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableList;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.domain.launch.LaunchNotice.InvalidChecksumException;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.smd.EncodedSignedMark;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link UpdateClaimsNoticeCommandTest}. */
public class UpdateClaimsNoticeCommandTest extends CommandTestCase<UpdateClaimsNoticeCommand> {

  DomainApplication domainApplication;

  @Before
  public void init() {
    createTld("xn--q9jyb4c");
    domainApplication = persistResource(newDomainApplication("example-one.xn--q9jyb4c")
        .asBuilder()
        .setCurrentSponsorClientId("TheRegistrar")
        .build());
  }

  private DomainApplication reloadDomainApplication() {
    return ofy().load().entity(domainApplication).now();
  }

  @Test
  public void testSuccess_noLaunchNotice() throws Exception {
    DateTime before = new DateTime(UTC);
    assertAboutApplications().that(domainApplication).hasLaunchNotice(null);

    runCommand(
        "--id=2-Q9JYB4C",
        "--tcn_id=370d0b7c9223372036854775807",
        "--expiration_time=2010-08-16T09:00:00.0Z",
        "--accepted_time=2009-08-16T09:00:00.0Z");

    assertAboutApplications().that(reloadDomainApplication())
        .hasLaunchNotice(LaunchNotice.create(
            "370d0b7c9223372036854775807",
            "tmch",
            DateTime.parse("2010-08-16T09:00:00.0Z"),
            DateTime.parse("2009-08-16T09:00:00.0Z"))).and()
        .hasLastEppUpdateTimeAtLeast(before).and()
        .hasLastEppUpdateClientId("TheRegistrar").and()
        .hasOnlyOneHistoryEntryWhich()
            .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE).and()
            .hasClientId("TheRegistrar");
  }

  @Test
  public void testSuccess_paddedChecksum() throws Exception {
    DateTime before = new DateTime(UTC);
    domainApplication = persistResource(newDomainApplication("imdb.xn--q9jyb4c"));

    runCommand(
        "--id=4-Q9JYB4C",
        "--tcn_id=0a07ec6e0000000000010995975",
        "--expiration_time=2014-02-28T12:00:00.0Z",
        "--accepted_time=2014-02-26T12:00:00.0Z");

    assertAboutApplications().that(reloadDomainApplication())
        .hasLaunchNotice(LaunchNotice.create(
            "0a07ec6e0000000000010995975",
            "tmch",
            DateTime.parse("2014-02-28T12:00:00.0Z"),
            DateTime.parse("2014-02-26T12:00:00.0Z"))).and()
        .hasLastEppUpdateTimeAtLeast(before).and()
        .hasLastEppUpdateClientId("TheRegistrar").and()
        .hasOnlyOneHistoryEntryWhich()
            .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE).and()
            .hasClientId("TheRegistrar");
  }

  @Test
  public void testSuccess_replaceExistingLaunchNotice() throws Exception {
    DateTime before = new DateTime(UTC);

    // Set a launch notice which should get overwritten.
    domainApplication = persistResource(domainApplication.asBuilder()
        .setLaunchNotice(LaunchNotice.create("foobar", "tmch", END_OF_TIME, START_OF_TIME))
        .build());

    runCommand(
        "--id=2-Q9JYB4C",
        "--tcn_id=370d0b7c9223372036854775807",
        "--expiration_time=2010-08-16T09:00:00.0Z",
        "--accepted_time=2009-08-16T09:00:00.0Z");

    assertAboutApplications().that(reloadDomainApplication())
        .hasLaunchNotice(LaunchNotice.create(
            "370d0b7c9223372036854775807",
            "tmch",
            DateTime.parse("2010-08-16T09:00:00.0Z"),
            DateTime.parse("2009-08-16T09:00:00.0Z"))).and()
        .hasLastEppUpdateTimeAtLeast(before).and()
        .hasLastEppUpdateClientId("TheRegistrar").and()
        .hasOnlyOneHistoryEntryWhich()
            .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE).and()
            .hasClientId("TheRegistrar");
  }

  @Test
  public void testFailure_badClaimsNotice() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--id=1-Q9JYB4C",
        "--tcn_id=foobarbaz",
        "--expiration_time=2010-08-16T09:00:00.0Z",
        "--accepted_time=2009-08-16T09:00:00.0Z");
  }

  @Test
  public void testFailure_claimsNoticeForWrongLabel() throws Exception {
    domainApplication = persistResource(newDomainApplication("bad-label.xn--q9jyb4c"));
    thrown.expectRootCause(InvalidChecksumException.class);
    runCommand(
        "--id=4-Q9JYB4C",
        "--tcn_id=370d0b7c9223372036854775807",
        "--expiration_time=2010-08-16T09:00:00.0Z",
        "--accepted_time=2009-08-16T09:00:00.0Z");
  }

  @Test
  public void testFailure_sunriseApplication() throws Exception {
    // Add an encoded signed mark to the application to make it a sunrise application.
    domainApplication = persistResource(domainApplication.asBuilder()
        .setEncodedSignedMarks(ImmutableList.of(EncodedSignedMark.create("base64", "AAAAA")))
        .build());
    thrown.expect(IllegalArgumentException.class);

    runCommand(
        "--id=1-Q9JYB4C",
        "--tcn_id=370d0b7c9223372036854775807",
        "--expiration_time=2010-08-16T09:00:00.0Z",
        "--accepted_time=2009-08-16T09:00:00.0Z");
  }
}
