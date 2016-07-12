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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.registrar.Registrar.State.ACTIVE;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT_HASH;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistPremiumList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.util.CidrAddressBlock;
import java.security.cert.CertificateParsingException;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link SetupOteCommand}. */
public class SetupOteCommandTest extends CommandTestCase<SetupOteCommand> {

  ImmutableList<String> passwords = ImmutableList.of(
      "abcdefghijklmnop", "qrstuvwxyzabcdef", "ghijklmnopqrstuv", "wxyzabcdefghijkl");
  FakePasswordGenerator passwordGenerator = new FakePasswordGenerator("abcdefghijklmnopqrstuvwxyz");

  @Before
  public void init() {
    command.passwordGenerator = passwordGenerator;
    persistPremiumList("default_sandbox_list", "sandbox,USD 1000");
    persistPremiumList("alternate_list", "rich,USD 3000");
  }

  /** Verify TLD creation. */
  private void verifyTldCreation(
      String tldName,
      String roidSuffix,
      TldState tldState,
      String premiumList,
      Duration addGracePeriodLength,
      Duration redemptionGracePeriodLength,
      Duration pendingDeleteLength) {
    Registry registry = Registry.get(tldName);
    assertThat(registry).isNotNull();
    assertThat(registry.getRoidSuffix()).isEqualTo(roidSuffix);
    assertThat(registry.getTldState(DateTime.now(UTC))).isEqualTo(tldState);

    assertThat(registry.getPremiumList()).isNotNull();
    assertThat(registry.getPremiumList().getName()).isEqualTo(premiumList);

    assertThat(registry.getAddGracePeriodLength()).isEqualTo(addGracePeriodLength);
    assertThat(registry.getRedemptionGracePeriodLength()).isEqualTo(redemptionGracePeriodLength);
    assertThat(registry.getPendingDeleteLength()).isEqualTo(pendingDeleteLength);
  }

  /** Verify TLD creation with registry default durations. */
  private void verifyTldCreation(
      String tldName, String roidSuffix, TldState tldState, String premiumList) {
    verifyTldCreation(
        tldName,
        roidSuffix,
        tldState,
        premiumList,
        Registry.DEFAULT_ADD_GRACE_PERIOD,
        Registry.DEFAULT_REDEMPTION_GRACE_PERIOD,
        Registry.DEFAULT_PENDING_DELETE_LENGTH);
  }

  private void verifyRegistrarCreation(
      String registrarName,
      String allowedTld,
      String password,
      ImmutableList<CidrAddressBlock> ipWhitelist) {
    Registrar registrar = Registrar.loadByClientId(registrarName);
    assertThat(registrar).isNotNull();
    assertThat(registrar.getAllowedTlds()).containsExactlyElementsIn(ImmutableSet.of(allowedTld));
    assertThat(registrar.getRegistrarName()).isEqualTo(registrarName);
    assertThat(registrar.getState()).isEqualTo(ACTIVE);
    assertThat(registrar.testPassword(password)).isTrue();
    assertThat(registrar.getIpAddressWhitelist()).isEqualTo(ipWhitelist);
    assertThat(registrar.getClientCertificate()).isEqualTo(SAMPLE_CERT);
    assertThat(registrar.getClientCertificateHash()).isEqualTo(SAMPLE_CERT_HASH);
  }

  @Test
  public void testSuccess() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--certfile=" + getCertFilename());

    verifyTldCreation("blobio-sunrise", "BLOBIOS0", TldState.SUNRISE, "default_sandbox_list");
    verifyTldCreation("blobio-landrush", "BLOBIOL1", TldState.LANDRUSH, "default_sandbox_list");
    verifyTldCreation(
        "blobio-ga",
        "BLOBIOG2",
        TldState.GENERAL_AVAILABILITY,
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5));

    ImmutableList<CidrAddressBlock> ipAddress = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", passwords.get(0), ipAddress);
    verifyRegistrarCreation("blobio-2", "blobio-landrush", passwords.get(1), ipAddress);
    verifyRegistrarCreation("blobio-3", "blobio-ga", passwords.get(2), ipAddress);
    verifyRegistrarCreation("blobio-4", "blobio-ga", passwords.get(3), ipAddress);
  }

  @Test
  public void testSuccess_multipleIps() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1,2.2.2.2",
        "--registrar=blobio",
        "--certfile=" + getCertFilename());

    verifyTldCreation("blobio-sunrise", "BLOBIOS0", TldState.SUNRISE, "default_sandbox_list");
    verifyTldCreation("blobio-landrush", "BLOBIOL1", TldState.LANDRUSH, "default_sandbox_list");
    verifyTldCreation(
        "blobio-ga",
        "BLOBIOG2",
        TldState.GENERAL_AVAILABILITY,
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5));

    ImmutableList<CidrAddressBlock> ipAddresses = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"),
        CidrAddressBlock.create("2.2.2.2"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", passwords.get(0), ipAddresses);
    verifyRegistrarCreation("blobio-2", "blobio-landrush", passwords.get(1), ipAddresses);
    verifyRegistrarCreation("blobio-3", "blobio-ga", passwords.get(2), ipAddresses);
    verifyRegistrarCreation("blobio-4", "blobio-ga", passwords.get(3), ipAddresses);
  }

  public void testSuccess_alternatePremiumList() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--certfile=" + getCertFilename(),
        "--premium_list=alternate_list");

    verifyTldCreation("blobio-sunrise", "BLOBIOS0", TldState.SUNRISE, "alternate_list");
    verifyTldCreation("blobio-landrush", "BLOBIOL1", TldState.LANDRUSH, "alternate_list");
    verifyTldCreation(
        "blobio-ga",
        "BLOBIOG2",
        TldState.GENERAL_AVAILABILITY,
        "alternate_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5));

    ImmutableList<CidrAddressBlock> ipAddress = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", passwords.get(0), ipAddress);
    verifyRegistrarCreation("blobio-2", "blobio-landrush", passwords.get(1), ipAddress);
    verifyRegistrarCreation("blobio-3", "blobio-ga", passwords.get(2), ipAddress);
    verifyRegistrarCreation("blobio-4", "blobio-ga", passwords.get(3), ipAddress);
  }

  @Test
  public void testFailure_missingIpWhitelist() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced(
        "--registrar=blobio",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_missingRegistrar() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_missingCertificateFile() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio");
  }

  @Test
  public void testFailure_invalidCert() throws Exception {
    thrown.expect(CertificateParsingException.class);
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--certfile=/dev/null");
  }

  @Test
  public void testFailure_invalidRegistrar() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=3blobio",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_registrarTooShort() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=bl",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_registrarTooLong() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobiotoooolong",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_registrarInvalidCharacter() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blo#bio",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_invalidPremiumList() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--certfile=" + getCertFilename(),
        "--premium_list=foo");
  }

  @Test
  public void testFailure_tldExists() throws Exception {
    thrown.expect(IllegalStateException.class);
    createTld("blobio-sunrise");

    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_registrarExists() throws Exception {
    thrown.expect(IllegalStateException.class);

    Registrar registrar = Registrar.loadByClientId("TheRegistrar").asBuilder()
        .setClientIdentifier("blobio-1")
        .setRegistrarName("blobio-1")
        .build();
    persistResource(registrar);

    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--certfile=" + getCertFilename());
  }
}
