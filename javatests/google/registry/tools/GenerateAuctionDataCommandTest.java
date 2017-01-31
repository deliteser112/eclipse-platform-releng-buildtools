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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newContactResourceWithRoid;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newSunriseApplication;
import static google.registry.testing.DatastoreHelper.persistResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import google.registry.model.contact.ContactAddress;
import google.registry.model.contact.ContactPhoneNumber;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.PostalInfo;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.ofy.Ofy;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit tests for {@link GenerateAuctionDataCommand}. */
public class GenerateAuctionDataCommandTest extends CommandTestCase<GenerateAuctionDataCommand> {

  @Rule
  public final TemporaryFolder folder = new TemporaryFolder();

  @Rule
  public final InjectRule inject = new InjectRule();

  FakeClock clock = new FakeClock(DateTime.parse("2000-01-01TZ"));

  Path output;
  ContactResource contact1;
  ContactResource contact2;

  String getOutput() throws IOException {
    return new String(Files.readAllBytes(output), UTF_8).trim();
  }

  @Before
  public void init() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);
    output = Paths.get(folder.newFile().toString());

    createTld("xn--q9jyb4c");
    contact1 = persistResource(
        newContactResourceWithRoid("contact1", "C1-ROID").asBuilder()
            .setLocalizedPostalInfo(new PostalInfo.Builder()
                .setType(PostalInfo.Type.LOCALIZED)
                .setName("Joe Registrant")
                .setOrg("Google")
                .setAddress(new ContactAddress.Builder()
                    .setStreet(ImmutableList.of("111 8th Ave", "4th Floor", "Suite 100"))
                    .setCity("New York")
                    .setState("NY")
                    .setZip("10011")
                    .setCountryCode("US")
                    .build())
                .build())
            .setVoiceNumber(new ContactPhoneNumber.Builder()
                .setPhoneNumber("867-5000")
                .setExtension("0000")
                .build())
            .setFaxNumber(new ContactPhoneNumber.Builder()
                .setPhoneNumber("867-5000")
                .setExtension("0001")
                .build())
            .setEmailAddress("joe.registrant@example.com")
            .build());
    contact2 = persistResource(
        newContactResourceWithRoid("contact1", "C2-ROID").asBuilder()
            .setLocalizedPostalInfo(new PostalInfo.Builder()
                .setType(PostalInfo.Type.LOCALIZED)
                .setName("Jáne Registrant")
                .setOrg("Charleston Road Registry")
                .setAddress(new ContactAddress.Builder()
                    .setStreet(ImmutableList.of("1600 Charleston Road"))
                    .setCity("Mountain View")
                    .setState("CA")
                    .setZip("94043")
                    .setCountryCode("US")
                    .build())
                .build())
            .setInternationalizedPostalInfo(new PostalInfo.Builder()
                .setType(PostalInfo.Type.INTERNATIONALIZED)
                .setName("Jane Registrant")
                .setOrg("Charleston Road Registry")
                .setAddress(new ContactAddress.Builder()
                    .setStreet(ImmutableList.of("1600 Charleston Road"))
                    .setCity("Mountain View")
                    .setState("CA")
                    .setZip("94043")
                    .setCountryCode("US")
                    .build())
               .build())
            .setVoiceNumber(new ContactPhoneNumber.Builder()
                .setPhoneNumber("555-1234")
                .setExtension("0000")
                .build())
            .setFaxNumber(new ContactPhoneNumber.Builder()
                .setPhoneNumber("555-1235")
                .setExtension("0001")
                .build())
            .setEmailAddress("jane.registrant@example.com")
            .build());
  }

  @Test
  public void testSuccess_emptyOutput() throws Exception {
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEmpty();
  }

  @Test
  public void testSuccess_nullApplicationFields() throws Exception {
    // Make two contacts with different email addresses.
    ContactResource contact = persistResource(
        newContactResourceWithRoid("contact1234", "C1234-ROID")
            .asBuilder()
            .setEmailAddress("foo@bar.baz")
            .build());
    ContactResource otherContact = persistResource(
        newContactResourceWithRoid("contact5678", "C5678-ROID")
            .asBuilder()
            .setEmailAddress("bar@baz.foo")
            .build());
    persistResource(newDomainApplication("label.xn--q9jyb4c", contact));
    persistResource(newDomainApplication("label.xn--q9jyb4c", otherContact));
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEqualTo(Joiner.on('\n').join(ImmutableList.of(
        "label.xn--q9jyb4c|2-Q9JYB4C|2000-01-01 00:00:00||TheRegistrar|||||||||foo@bar.baz"
            + "|||Landrush",
        "label.xn--q9jyb4c|3-Q9JYB4C|2000-01-01 00:00:00||TheRegistrar|||||||||bar@baz.foo"
            + "|||Landrush",
        "TheRegistrar|John Doe|The Registrar|123 Example Bőulevard||Williamsburg|NY|"
            + "11211|US|new.registrar@example.com|+1.2223334444")));
  }

  @Test
  public void testSuccess_multipleRegistrars() throws Exception {
    persistResource(newDomainApplication("label.xn--q9jyb4c", contact1));
    persistResource(newDomainApplication("label.xn--q9jyb4c", contact2)
        .asBuilder()
        .setCurrentSponsorClientId("NewRegistrar")
        .build());
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEqualTo(Joiner.on('\n').join(ImmutableList.of(
        "label.xn--q9jyb4c|2-Q9JYB4C|2000-01-01 00:00:00||TheRegistrar|Joe Registrant|Google|"
            + "111 8th Ave|4th Floor Suite 100|New York|NY|10011|US|joe.registrant@example.com|"
            + "867-5000 x0000||Landrush",
        "label.xn--q9jyb4c|3-Q9JYB4C|2000-01-01 00:00:00||NewRegistrar|Jane Registrant|"
            + "Charleston Road Registry|1600 Charleston Road||Mountain View|CA|94043|US|"
            + "jane.registrant@example.com|555-1234 x0000||Landrush",
        "NewRegistrar|Jane Doe|New Registrar|123 Example Bőulevard||Williamsburg|NY|"
            + "11211|US|new.registrar@example.com|+1.3334445555",
        "TheRegistrar|John Doe|The Registrar|123 Example Bőulevard||Williamsburg|NY|"
            + "11211|US|new.registrar@example.com|+1.2223334444")));
  }

  @Test
  public void testSuccess_allFieldsPopulated() throws Exception {
    persistResource(newDomainApplication("label.xn--q9jyb4c", contact1)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-06T00:30:00Z"))
        .build());
    persistResource(newDomainApplication("label.xn--q9jyb4c", contact2)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-07T00:30:00Z"))
        .build());
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEqualTo(Joiner.on('\n').join(ImmutableList.of(
        "label.xn--q9jyb4c|2-Q9JYB4C|2000-01-01 00:00:00|2006-06-06 00:30:00|TheRegistrar|"
            + "Joe Registrant|Google|111 8th Ave|4th Floor Suite 100|New York|NY|10011|US|"
            + "joe.registrant@example.com|867-5000 x0000||Landrush",
        "label.xn--q9jyb4c|3-Q9JYB4C|2000-01-01 00:00:00|2006-06-07 00:30:00|TheRegistrar|"
            + "Jane Registrant|Charleston Road Registry|1600 Charleston Road||Mountain View|CA|"
            + "94043|US|jane.registrant@example.com|555-1234 x0000||Landrush",
        "TheRegistrar|John Doe|The Registrar|123 Example Bőulevard||Williamsburg|NY|"
            + "11211|US|new.registrar@example.com|+1.2223334444")));
  }

  @Test
  public void testSuccess_multipleSunriseMultipleLandrushApplications() throws Exception {
    persistResource(newDomainApplication("label.xn--q9jyb4c", contact1)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-07T00:30:00Z"))
        .build());
    persistResource(newDomainApplication("label.xn--q9jyb4c", contact2)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-08T00:30:00Z"))
        .build());
    persistResource(newSunriseApplication("label.xn--q9jyb4c", contact1)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-09T00:30:00Z"))
        .build());
    persistResource(newSunriseApplication("label.xn--q9jyb4c", contact2)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-10T00:30:00Z"))
        .build());
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEqualTo(Joiner.on('\n').join(ImmutableList.of(
        "label.xn--q9jyb4c|4-Q9JYB4C|2000-01-01 00:00:00|2006-06-09 00:30:00|TheRegistrar|"
            + "Joe Registrant|Google|111 8th Ave|4th Floor Suite 100|New York|NY|10011|US|"
            + "joe.registrant@example.com|867-5000 x0000||Sunrise",
        "label.xn--q9jyb4c|5-Q9JYB4C|2000-01-01 00:00:00|2006-06-10 00:30:00|TheRegistrar|"
            + "Jane Registrant|Charleston Road Registry|1600 Charleston Road||Mountain View|CA|"
            + "94043|US|jane.registrant@example.com|555-1234 x0000||Sunrise",
        "TheRegistrar|John Doe|The Registrar|123 Example Bőulevard||Williamsburg|NY|"
            + "11211|US|new.registrar@example.com|+1.2223334444")));
  }

  @Test
  public void testSuccess_multipleLabels() throws Exception {
    persistResource(newDomainApplication("label.xn--q9jyb4c", contact1)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-06T00:30:00Z"))
        .build());
    persistResource(newDomainApplication("label.xn--q9jyb4c", contact2)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-07T00:30:00Z"))
        .build());
    persistResource(newSunriseApplication("label2.xn--q9jyb4c", contact1)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-09T00:30:00Z"))
        .build());
    persistResource(newSunriseApplication("label2.xn--q9jyb4c", contact2)
        .asBuilder()
        .setLastEppUpdateTime(DateTime.parse("2006-06-10T00:30:00Z"))
        .build());
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEqualTo(Joiner.on('\n').join(ImmutableList.of(
        "label.xn--q9jyb4c|2-Q9JYB4C|2000-01-01 00:00:00|2006-06-06 00:30:00|TheRegistrar|"
            + "Joe Registrant|Google|111 8th Ave|4th Floor Suite 100|New York|NY|10011|US|"
            + "joe.registrant@example.com|867-5000 x0000||Landrush",
        "label.xn--q9jyb4c|3-Q9JYB4C|2000-01-01 00:00:00|2006-06-07 00:30:00|TheRegistrar|"
            + "Jane Registrant|Charleston Road Registry|1600 Charleston Road||Mountain View|CA|"
            + "94043|US|jane.registrant@example.com|555-1234 x0000||Landrush",
        "label2.xn--q9jyb4c|4-Q9JYB4C|2000-01-01 00:00:00|2006-06-09 00:30:00|TheRegistrar|"
            + "Joe Registrant|Google|111 8th Ave|4th Floor Suite 100|New York|NY|10011|US|"
            + "joe.registrant@example.com|867-5000 x0000||Sunrise",
        "label2.xn--q9jyb4c|5-Q9JYB4C|2000-01-01 00:00:00|2006-06-10 00:30:00|TheRegistrar|"
            + "Jane Registrant|Charleston Road Registry|1600 Charleston Road||Mountain View|CA|"
            + "94043|US|jane.registrant@example.com|555-1234 x0000||Sunrise",
        "TheRegistrar|John Doe|The Registrar|123 Example Bőulevard||Williamsburg|NY|"
            + "11211|US|new.registrar@example.com|+1.2223334444")));
  }

  @Test
  public void testSuccess_oneSunriseApplication() throws Exception {
    persistResource(newSunriseApplication("label.xn--q9jyb4c"));
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEmpty();
  }

  @Test
  public void testSuccess_twoSunriseApplicationsOneRejected() throws Exception {
    persistResource(newSunriseApplication("label.xn--q9jyb4c"));
    persistResource(newSunriseApplication("label.xn--q9jyb4c")
        .asBuilder()
        .setApplicationStatus(ApplicationStatus.REJECTED)
        .build());
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEmpty();
  }

  @Test
  public void testSuccess_twoSunriseApplicationsOneDeleted() throws Exception {
    persistResource(newSunriseApplication("label.xn--q9jyb4c"));
    DateTime deletionTime = DateTime.now(UTC);
    persistResource(newSunriseApplication("label.xn--q9jyb4c")
        .asBuilder()
        .setDeletionTime(deletionTime)
        .build());
    clock.setTo(deletionTime);
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEmpty();
  }

  @Test
  public void testSuccess_oneLandrushApplication() throws Exception {
    persistResource(newDomainApplication("label.xn--q9jyb4c"));
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEmpty();
  }

  @Test
  public void testSuccess_oneSunriseApplicationMultipleLandrushApplications() throws Exception {
    persistResource(newSunriseApplication("label.xn--q9jyb4c"));
    persistResource(newDomainApplication("label.xn--q9jyb4c"));
    persistResource(newDomainApplication("label.xn--q9jyb4c"));
    runCommand("--output=" + output, "xn--q9jyb4c");
    assertThat(getOutput()).isEmpty();
  }

  @Test
  public void testFailure_missingTldName() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand();
  }

  @Test
  public void testFailure_tooManyParameters() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("xn--q9jyb4c", "foobar");
  }

  @Test
  public void testFailure_nonexistentTld() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("foobarbaz");
  }
}
