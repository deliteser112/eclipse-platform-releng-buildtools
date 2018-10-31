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
import static google.registry.model.registrar.Registrar.State.ACTIVE;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT_HASH;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistPremiumList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.testing.DeterministicStringGenerator;
import google.registry.util.CidrAddressBlock;
import java.security.cert.CertificateParsingException;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link SetupOteCommand}. */
public class SetupOteCommandTest extends CommandTestCase<SetupOteCommand> {

  ImmutableList<String> passwords =
      ImmutableList.of(
          "abcdefghijklmnop",
          "qrstuvwxyzabcdef",
          "ghijklmnopqrstuv",
          "wxyzabcdefghijkl",
          "mnopqrstuvwxyzab");
  DeterministicStringGenerator passwordGenerator =
      new DeterministicStringGenerator("abcdefghijklmnopqrstuvwxyz");

  @Before
  public void init() {
    SetupOteCommand.interactive = false;
    command.validDnsWriterNames = ImmutableSet.of("FooDnsWriter", "BarDnsWriter", "VoidDnsWriter");
    command.passwordGenerator = passwordGenerator;
    persistPremiumList("default_sandbox_list", "sandbox,USD 1000");
    persistPremiumList("alternate_list", "rich,USD 3000");
  }

  /** Verify TLD creation. */
  private void verifyTldCreation(
      String tldName,
      String roidSuffix,
      TldState tldState,
      String dnsWriter,
      String premiumList,
      Duration addGracePeriodLength,
      Duration redemptionGracePeriodLength,
      Duration pendingDeleteLength,
      boolean isEarlyAccess) {
    Registry registry = Registry.get(tldName);
    assertThat(registry).isNotNull();
    assertThat(registry.getRoidSuffix()).isEqualTo(roidSuffix);
    assertThat(registry.getTldState(DateTime.now(UTC))).isEqualTo(tldState);
    assertThat(registry.getDnsWriters()).containsExactly(dnsWriter);
    assertThat(registry.getPremiumList()).isNotNull();
    assertThat(registry.getPremiumList().getName()).isEqualTo(premiumList);
    assertThat(registry.getAddGracePeriodLength()).isEqualTo(addGracePeriodLength);
    assertThat(registry.getRedemptionGracePeriodLength()).isEqualTo(redemptionGracePeriodLength);
    assertThat(registry.getPendingDeleteLength()).isEqualTo(pendingDeleteLength);
    ImmutableSortedMap<DateTime, Money> eapFeeSchedule = registry.getEapFeeScheduleAsMap();
    if (!isEarlyAccess) {
      assertThat(eapFeeSchedule)
          .isEqualTo(ImmutableSortedMap.of(new DateTime(0), Money.of(CurrencyUnit.USD, 0)));
    } else {
      assertThat(eapFeeSchedule)
          .isEqualTo(
              ImmutableSortedMap.of(
                  new DateTime(0),
                  Money.of(CurrencyUnit.USD, 0),
                  DateTime.parse("2018-03-01T00:00:00Z"),
                  Money.of(CurrencyUnit.USD, 100),
                  DateTime.parse("2022-03-01T00:00:00Z"),
                  Money.of(CurrencyUnit.USD, 0)));
    }
  }

  /** Verify TLD creation with registry default durations. */
  private void verifyTldCreation(
      String tldName, String roidSuffix, TldState tldState, String dnsWriter, String premiumList) {
    verifyTldCreation(
        tldName,
        roidSuffix,
        tldState,
        dnsWriter,
        premiumList,
        Registry.DEFAULT_ADD_GRACE_PERIOD,
        Registry.DEFAULT_REDEMPTION_GRACE_PERIOD,
        Registry.DEFAULT_PENDING_DELETE_LENGTH,
        false);
  }

  private void verifyRegistrarCreation(
      String registrarName,
      String allowedTld,
      String password,
      ImmutableList<CidrAddressBlock> ipWhitelist,
      boolean hashOnly) {
    Registrar registrar = loadRegistrar(registrarName);
    assertThat(registrar).isNotNull();
    assertThat(registrar.getAllowedTlds()).containsExactlyElementsIn(ImmutableSet.of(allowedTld));
    assertThat(registrar.getRegistrarName()).isEqualTo(registrarName);
    assertThat(registrar.getState()).isEqualTo(ACTIVE);
    assertThat(registrar.testPassword(password)).isTrue();
    assertThat(registrar.getIpAddressWhitelist()).isEqualTo(ipWhitelist);
    assertThat(registrar.getClientCertificateHash()).isEqualTo(SAMPLE_CERT_HASH);
    // If certificate hash is provided, there's no certificate file stored with the registrar.
    if (!hashOnly) {
      assertThat(registrar.getClientCertificate()).isEqualTo(SAMPLE_CERT);
    }
  }

  private void verifyRegistrarCreation(
      String registrarName,
      String allowedTld,
      String password,
      ImmutableList<CidrAddressBlock> ipWhitelist) {
    verifyRegistrarCreation(registrarName, allowedTld, password, ipWhitelist, false);
  }

  private void verifyRegistrarContactCreation(String registrarName, String email) {
    ImmutableSet<RegistrarContact> registrarContacts =
        loadRegistrar(registrarName).getContacts();
    assertThat(registrarContacts).hasSize(1);
    RegistrarContact registrarContact = registrarContacts.stream().findAny().get();
    assertThat(registrarContact.getEmailAddress()).isEqualTo(email);
    assertThat(registrarContact.getName()).isEqualTo(email);
    assertThat(registrarContact.getGaeUserId()).isNotNull();
  }

  @Test
  public void testSuccess() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--email=contact@email.com",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());

    verifyTldCreation(
        "blobio-sunrise",
        "BLOBIOS0",
        TldState.START_DATE_SUNRISE,
        "VoidDnsWriter",
        "default_sandbox_list");
    verifyTldCreation(
        "blobio-landrush", "BLOBIOL1", TldState.LANDRUSH, "VoidDnsWriter", "default_sandbox_list");
    verifyTldCreation(
        "blobio-ga",
        "BLOBIOG2",
        TldState.GENERAL_AVAILABILITY,
        "VoidDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        false);
    verifyTldCreation(
        "blobio-eap",
        "BLOBIOE3",
        TldState.GENERAL_AVAILABILITY,
        "VoidDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        true);

    ImmutableList<CidrAddressBlock> ipAddress = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", passwords.get(0), ipAddress);
    verifyRegistrarCreation("blobio-2", "blobio-landrush", passwords.get(1), ipAddress);
    verifyRegistrarCreation("blobio-3", "blobio-ga", passwords.get(2), ipAddress);
    verifyRegistrarCreation("blobio-4", "blobio-ga", passwords.get(3), ipAddress);
    verifyRegistrarCreation("blobio-5", "blobio-eap", passwords.get(4), ipAddress);

    verifyRegistrarContactCreation("blobio-1", "contact@email.com");
    verifyRegistrarContactCreation("blobio-2", "contact@email.com");
    verifyRegistrarContactCreation("blobio-3", "contact@email.com");
    verifyRegistrarContactCreation("blobio-4", "contact@email.com");
    verifyRegistrarContactCreation("blobio-5", "contact@email.com");
  }

  @Test
  public void testSuccess_shortRegistrarName() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=abc",
        "--email=abc@email.com",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());

    verifyTldCreation(
        "abc-sunrise",
        "ABCSUNR0",
        TldState.START_DATE_SUNRISE,
        "VoidDnsWriter",
        "default_sandbox_list");
    verifyTldCreation(
        "abc-landrush", "ABCLAND1", TldState.LANDRUSH, "VoidDnsWriter", "default_sandbox_list");
    verifyTldCreation(
        "abc-ga",
        "ABCGA2",
        TldState.GENERAL_AVAILABILITY,
        "VoidDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        false);
    verifyTldCreation(
        "abc-eap",
        "ABCEAP3",
        TldState.GENERAL_AVAILABILITY,
        "VoidDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        true);

    ImmutableList<CidrAddressBlock> ipAddress =
        ImmutableList.of(CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("abc-1", "abc-sunrise", passwords.get(0), ipAddress);
    verifyRegistrarCreation("abc-2", "abc-landrush", passwords.get(1), ipAddress);
    verifyRegistrarCreation("abc-3", "abc-ga", passwords.get(2), ipAddress);
    verifyRegistrarCreation("abc-4", "abc-ga", passwords.get(3), ipAddress);
    verifyRegistrarCreation("abc-5", "abc-eap", passwords.get(4), ipAddress);

    verifyRegistrarContactCreation("abc-1", "abc@email.com");
    verifyRegistrarContactCreation("abc-2", "abc@email.com");
    verifyRegistrarContactCreation("abc-3", "abc@email.com");
    verifyRegistrarContactCreation("abc-4", "abc@email.com");
    verifyRegistrarContactCreation("abc-5", "abc@email.com");
  }

  @Test
  public void testSuccess_certificateHash() throws Exception {
    runCommandForced(
        "--eap_only",
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--email=contact@email.com",
        "--dns_writers=VoidDnsWriter",
        "--certhash=" + SAMPLE_CERT_HASH);

    verifyTldCreation(
        "blobio-eap",
        "BLOBIOE3",
        TldState.GENERAL_AVAILABILITY,
        "VoidDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        true);

    ImmutableList<CidrAddressBlock> ipAddress =
        ImmutableList.of(CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-5", "blobio-eap", passwords.get(0), ipAddress, true);

    verifyRegistrarContactCreation("blobio-5", "contact@email.com");
  }

  @Test
  public void testSuccess_eapOnly() throws Exception {
    runCommandForced(
        "--eap_only",
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--email=contact@email.com",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());

    verifyTldCreation(
        "blobio-eap",
        "BLOBIOE3",
        TldState.GENERAL_AVAILABILITY,
        "VoidDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        true);

    ImmutableList<CidrAddressBlock> ipAddress = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-5", "blobio-eap", passwords.get(0), ipAddress);

    verifyRegistrarContactCreation("blobio-5", "contact@email.com");
  }

  @Test
  public void testSuccess_multipleIps() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1,2.2.2.2",
        "--registrar=blobio",
        "--email=contact@email.com",
        "--dns_writers=FooDnsWriter",
        "--certfile=" + getCertFilename());

    verifyTldCreation(
        "blobio-sunrise",
        "BLOBIOS0",
        TldState.START_DATE_SUNRISE,
        "FooDnsWriter",
        "default_sandbox_list");
    verifyTldCreation(
        "blobio-landrush", "BLOBIOL1", TldState.LANDRUSH, "FooDnsWriter", "default_sandbox_list");
    verifyTldCreation(
        "blobio-ga",
        "BLOBIOG2",
        TldState.GENERAL_AVAILABILITY,
        "FooDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        false);
    verifyTldCreation(
        "blobio-eap",
        "BLOBIOE3",
        TldState.GENERAL_AVAILABILITY,
        "FooDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        true);

    ImmutableList<CidrAddressBlock> ipAddresses = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"),
        CidrAddressBlock.create("2.2.2.2"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", passwords.get(0), ipAddresses);
    verifyRegistrarCreation("blobio-2", "blobio-landrush", passwords.get(1), ipAddresses);
    verifyRegistrarCreation("blobio-3", "blobio-ga", passwords.get(2), ipAddresses);
    verifyRegistrarCreation("blobio-4", "blobio-ga", passwords.get(3), ipAddresses);
    verifyRegistrarCreation("blobio-5", "blobio-eap", passwords.get(4), ipAddresses);

    verifyRegistrarContactCreation("blobio-1", "contact@email.com");
    verifyRegistrarContactCreation("blobio-2", "contact@email.com");
    verifyRegistrarContactCreation("blobio-3", "contact@email.com");
    verifyRegistrarContactCreation("blobio-4", "contact@email.com");
    verifyRegistrarContactCreation("blobio-5", "contact@email.com");
  }

  @Test
  public void testSuccess_alternatePremiumList() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--email=contact@email.com",
        "--certfile=" + getCertFilename(),
        "--dns_writers=BarDnsWriter",
        "--premium_list=alternate_list");

    verifyTldCreation(
        "blobio-sunrise",
        "BLOBIOS0",
        TldState.START_DATE_SUNRISE,
        "BarDnsWriter",
        "alternate_list");
    verifyTldCreation(
        "blobio-landrush", "BLOBIOL1", TldState.LANDRUSH, "BarDnsWriter", "alternate_list");
    verifyTldCreation(
        "blobio-ga",
        "BLOBIOG2",
        TldState.GENERAL_AVAILABILITY,
        "BarDnsWriter",
        "alternate_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        false);
    verifyTldCreation(
        "blobio-eap",
        "BLOBIOE3",
        TldState.GENERAL_AVAILABILITY,
        "BarDnsWriter",
        "alternate_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5),
        true);

    ImmutableList<CidrAddressBlock> ipAddress = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", passwords.get(0), ipAddress);
    verifyRegistrarCreation("blobio-2", "blobio-landrush", passwords.get(1), ipAddress);
    verifyRegistrarCreation("blobio-3", "blobio-ga", passwords.get(2), ipAddress);
    verifyRegistrarCreation("blobio-4", "blobio-ga", passwords.get(3), ipAddress);
    verifyRegistrarCreation("blobio-5", "blobio-eap", passwords.get(4), ipAddress);

    verifyRegistrarContactCreation("blobio-1", "contact@email.com");
    verifyRegistrarContactCreation("blobio-2", "contact@email.com");
    verifyRegistrarContactCreation("blobio-3", "contact@email.com");
    verifyRegistrarContactCreation("blobio-4", "contact@email.com");
    verifyRegistrarContactCreation("blobio-5", "contact@email.com");
  }

  @Test
  public void testFailure_missingIpWhitelist() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class,
            () ->
                runCommandForced(
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("option is required: -w, --ip_whitelist");
  }

  @Test
  public void testFailure_missingRegistrar() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("option is required: -r, --registrar");
  }

  @Test
  public void testFailure_missingCertificateFileAndCertificateHash() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--registrar=blobio"));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Must specify exactly one of client certificate file or client certificate hash.");
  }

  @Test
  public void testFailure_suppliedCertificateFileAndCertificateHash() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--registrar=blobio",
                    "--certfile=" + getCertFilename(),
                    "--certhash=" + SAMPLE_CERT_HASH));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Must specify exactly one of client certificate file or client certificate hash.");
  }

  @Test
  public void testFailure_missingDnsWriter() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--email=contact@email.com",
                    "--certfile=" + getCertFilename(),
                    "--registrar=blobio"));
    assertThat(thrown).hasMessageThat().contains("option is required: --dns_writers");
  }

  @Test
  public void testFailure_missingEmail() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename(),
                    "--registrar=blobio"));
    assertThat(thrown).hasMessageThat().contains("option is required: --email");
  }

  @Test
  public void testFailure_invalidCert() {
    CertificateParsingException thrown =
        assertThrows(
            CertificateParsingException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=/dev/null"));
    assertThat(thrown).hasMessageThat().contains("No X509Certificate found");
  }

  @Test
  public void testFailure_invalidRegistrar() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--registrar=3blobio",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Registrar name is invalid");
  }

  @Test
  public void testFailure_invalidDnsWriter() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--dns_writers=InvalidDnsWriter",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Invalid DNS writer name(s) specified: [InvalidDnsWriter]");
  }

  @Test
  public void testFailure_registrarTooShort() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--registrar=bl",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Registrar name is invalid");
  }

  @Test
  public void testFailure_registrarTooLong() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--registrar=blobiotoooolong",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Registrar name is invalid");
  }

  @Test
  public void testFailure_registrarInvalidCharacter() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--registrar=blo#bio",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Registrar name is invalid");
  }

  @Test
  public void testFailure_invalidPremiumList() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename(),
                    "--premium_list=foo"));
    assertThat(thrown).hasMessageThat().contains("The premium list 'foo' doesn't exist");
  }

  @Test
  public void testFailure_tldExists() {
    createTld("blobio-sunrise");
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("TLD 'blobio-sunrise' already exists");
  }

  @Test
  public void testFailure_registrarExists() {
    Registrar registrar = loadRegistrar("TheRegistrar").asBuilder()
        .setClientId("blobio-1")
        .setRegistrarName("blobio-1")
        .build();
    persistResource(registrar);
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                runCommandForced(
                    "--ip_whitelist=1.1.1.1",
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--dns_writers=VoidDnsWriter",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Registrar blobio-1 already exists");
  }
}
