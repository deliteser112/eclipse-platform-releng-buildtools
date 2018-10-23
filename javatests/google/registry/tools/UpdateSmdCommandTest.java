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

import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.config.RegistryConfig.ConfigModule.TmchCaMode.PILOT;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainApplicationSubject.assertAboutApplications;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.tmch.TmchTestData.loadFile;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.flows.EppException.ParameterValueSyntaxErrorException;
import google.registry.flows.EppException.RequiredParameterMissingException;
import google.registry.flows.domain.DomainFlowTmchUtils;
import google.registry.model.domain.DomainApplication;
import google.registry.model.ofy.Ofy;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.smd.EncodedSignedMark;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.tmch.SmdrlCsvParser;
import google.registry.tmch.TmchCertificateAuthority;
import google.registry.tmch.TmchData;
import google.registry.tmch.TmchTestData;
import google.registry.tmch.TmchXmlSignature;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link UpdateSmdCommandTest}. */
public class UpdateSmdCommandTest extends CommandTestCase<UpdateSmdCommand> {

  DomainApplication domainApplication;

  private static final String ACTIVE_SMD =
      loadFile("active/Court-Agent-English-Active.smd");
  private static final String DIFFERENT_LABEL_SMD =
      loadFile("active/Court-Agent-Chinese-Active.smd");
  private static final String REVOKED_SMD =
      loadFile("revoked/smd/Trademark-Holder-English-Revoked.smd");
  private static final String INVALID_SMD =
      loadFile("invalid/InvalidSignature-Trademark-Agent-English-Active.smd");
  private static final String REVOKED_TMV_SMD =
      loadFile("revoked/tmv/TMVRevoked-Trademark-Agent-English-Active.smd");
  private static final CharSource REVOCATION_LIST =
      TmchTestData.loadBytes("tmch_test_smd_revocation_list.csv").asCharSource(US_ASCII);

  // Use a date that is within the valid range for the SMD test files.
  private final FakeClock clock = new FakeClock(DateTime.parse("2018-06-01TZ"));

  @Rule
  public final InjectRule inject = new InjectRule();

  @Before
  public void init() {
    inject.setStaticField(Ofy.class, "clock", clock);
    createTld("xn--q9jyb4c");
    clock.advanceOneMilli();
    domainApplication = persistResource(newDomainApplication("test-validate.xn--q9jyb4c")
        .asBuilder()
        .setPersistedCurrentSponsorClientId("TheRegistrar")
        .setEncodedSignedMarks(ImmutableList.of(EncodedSignedMark.create("base64", "garbage")))
        .build());
    clock.advanceOneMilli();
    command.tmchUtils =
        new DomainFlowTmchUtils(new TmchXmlSignature(new TmchCertificateAuthority(PILOT, clock)));
  }

  private DomainApplication reloadDomainApplication() {
    return ofy().load().entity(domainApplication).now();
  }

  @Test
  public void testSuccess() throws Exception {
    DateTime before = clock.nowUtc();
    clock.advanceOneMilli();
    String smdFile = writeToTmpFile(ACTIVE_SMD);
    runCommand("--id=2-Q9JYB4C", "--smd=" + smdFile, "--reason=testing");

    EncodedSignedMark encodedSignedMark = TmchData.readEncodedSignedMark(ACTIVE_SMD);
    assertAboutApplications().that(reloadDomainApplication())
        .hasExactlyEncodedSignedMarks(encodedSignedMark).and()
        .hasLastEppUpdateTimeAtLeast(before).and()
        .hasLastEppUpdateClientId("TheRegistrar").and()
        .hasOnlyOneHistoryEntryWhich()
            .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE).and()
            .hasClientId("TheRegistrar").and()
            .hasMetadataReason("UpdateSmdCommand: testing").and()
            .hasNoXml();
  }

  @Test
  public void testFailure_invalidSmd() throws Exception {
    String smdFile = writeToTmpFile(INVALID_SMD);
    Exception e =
        assertThrows(Exception.class, () -> runCommand("--id=2-Q9JYB4C", "--smd=" + smdFile));
    assertThat(e).hasCauseThat().isInstanceOf(ParameterValuePolicyErrorException.class);
  }

  @Test
  public void testFailure_revokedSmd() throws Exception {
    SmdrlCsvParser.parse(REVOCATION_LIST.readLines()).save();
    clock.advanceOneMilli();
    String smdFile = writeToTmpFile(REVOKED_SMD);
    Exception e =
        assertThrows(Exception.class, () -> runCommand("--id=2-Q9JYB4C", "--smd=" + smdFile));
    assertThat(e).hasCauseThat().isInstanceOf(ParameterValuePolicyErrorException.class);
  }

  @Test
  public void testFailure_revokedTmv() throws Exception {
    String smdFile = writeToTmpFile(REVOKED_TMV_SMD);
    Exception e =
        assertThrows(Exception.class, () -> runCommand("--id=2-Q9JYB4C", "--smd=" + smdFile));
    assertThat(e).hasCauseThat().isInstanceOf(ParameterValuePolicyErrorException.class);
  }

  @Test
  public void testFailure_unparseableXml() throws Exception {
    String smdFile = writeToTmpFile(base64().encode("This is not XML!".getBytes(UTF_8)));
    Exception e =
        assertThrows(Exception.class, () -> runCommand("--id=2-Q9JYB4C", "--smd=" + smdFile));
    assertThat(e).hasCauseThat().isInstanceOf(ParameterValueSyntaxErrorException.class);
  }

  @Test
  public void testFailure_badlyEncodedData() throws Exception {
    String smdFile = writeToTmpFile("Bad base64 data ~!@#$#@%%$#^$%^&^**&^)(*)(_".getBytes(UTF_8));
    Exception e =
        assertThrows(Exception.class, () -> runCommand("--id=2-Q9JYB4C", "--smd=" + smdFile));
    assertThat(e).hasCauseThat().isInstanceOf(ParameterValueSyntaxErrorException.class);
  }

  @Test
  public void testFailure_wrongLabel() throws Exception {
    String smdFile = writeToTmpFile(DIFFERENT_LABEL_SMD);
    Exception e =
        assertThrows(Exception.class, () -> runCommand("--id=2-Q9JYB4C", "--smd=" + smdFile));
    assertThat(e).hasCauseThat().isInstanceOf(RequiredParameterMissingException.class);
  }

  @Test
  public void testFailure_nonExistentApplication() throws Exception {
    String smdFile = writeToTmpFile(ACTIVE_SMD);
    assertThrows(
        IllegalArgumentException.class, () -> runCommand("--id=3-Q9JYB4C", "--smd=" + smdFile));
  }

  @Test
  public void testFailure_deletedApplication() throws Exception {
    persistResource(domainApplication.asBuilder().setDeletionTime(clock.nowUtc()).build());
    clock.advanceOneMilli();
    String smdFile = writeToTmpFile(ACTIVE_SMD);
    assertThrows(
        IllegalArgumentException.class, () -> runCommand("--id=2-Q9JYB4C", "--smd=" + smdFile));
  }
}
