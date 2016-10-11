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
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT_HASH;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.net.MediaType;
import google.registry.model.registrar.Registrar;
import google.registry.testing.CertificateSamples;
import google.registry.tools.ServerSideCommand.Connection;
import java.io.IOException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

/** Unit tests for {@link CreateRegistrarCommand}. */
public class CreateRegistrarCommandTest extends CommandTestCase<CreateRegistrarCommand> {

  @Mock
  private Connection connection;

  @Before
  public void init() {
    CreateRegistrarCommand.requireAddress = false;
    command.setConnection(connection);
  }

  @Test
  public void testSuccess() throws Exception {
    DateTime before = DateTime.now(UTC);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
    DateTime after = DateTime.now(UTC);

    // Clear the cache so that the CreateAutoTimestamp field gets reloaded.
    ofy().clearSessionCache();

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.testPassword("some_password")).isTrue();
    assertThat(registrar.getType()).isEqualTo(Registrar.Type.REAL);
    assertThat(registrar.getIanaIdentifier()).isEqualTo(8);
    assertThat(registrar.getState()).isEqualTo(Registrar.State.ACTIVE);
    assertThat(registrar.getAllowedTlds()).isEmpty();
    assertThat(registrar.getIpAddressWhitelist()).isEmpty();
    assertThat(registrar.getClientCertificateHash()).isNull();
    assertThat(registrar.getPhonePasscode()).isEqualTo("01234");
    assertThat(registrar.getCreationTime()).isIn(Range.closed(before, after));
    assertThat(registrar.getLastUpdateTime()).isEqualTo(registrar.getCreationTime());
    assertThat(registrar.getBlockPremiumNames()).isFalse();

    verify(connection).send(
        eq("/_dr/admin/createGroups"),
        eq(ImmutableMap.of("clientId", "clientz")),
        eq(MediaType.PLAIN_TEXT_UTF_8),
        eq(new byte[0]));
  }

  @Test
  public void testSuccess_quotedPassword() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=\"some_password\"",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.testPassword("some_password")).isTrue();
  }

  @Test
  public void testSuccess_registrarTypeFlag() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=TEST",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getType()).isEqualTo(Registrar.Type.TEST);
  }

  @Test
  public void testSuccess_registrarStateFlag() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--registrar_state=SUSPENDED",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getState()).isEqualTo(Registrar.State.SUSPENDED);
  }

  @Test
  public void testSuccess_allowedTlds() throws Exception {
    createTlds("xn--q9jyb4c", "foobar");

    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--allowed_tlds=xn--q9jyb4c,foobar",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getAllowedTlds()).containsExactly("xn--q9jyb4c", "foobar");
  }

  @Test
  public void testSuccess_groupCreationCanBeDisabled() throws Exception {
    runCommandForced(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=TEST",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--create_groups=false",
        "clientz");

    verifyZeroInteractions(connection);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testFailure_groupCreationFails() throws Exception {
    when(
            connection.send(
                Mockito.anyString(),
                Mockito.anyMapOf(String.class, String.class),
                Mockito.any(MediaType.class),
                Mockito.any(byte[].class)))
        .thenThrow(new IOException("BAD ROBOT NO COOKIE"));
    runCommandForced(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=TEST",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertInStdout("Registrar created, but groups creation failed with error");
    assertInStdout("BAD ROBOT NO COOKIE");
  }

  @Test
  public void testSuccess_groupCreationDoesntOccurOnAlphaEnv() throws Exception {
    runCommandInEnvironment(
        RegistryToolEnvironment.ALPHA,
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=TEST",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    verifyZeroInteractions(connection);
  }

  @Test
  public void testSuccess_ipWhitelistFlag() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--ip_whitelist=192.168.1.1,192.168.0.2/16",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getIpAddressWhitelist())
        .containsExactlyElementsIn(registrar.getIpAddressWhitelist()).inOrder();
  }

  @Test
  public void testSuccess_ipWhitelistFlagNull() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--ip_whitelist=null",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getIpAddressWhitelist()).isEmpty();
  }

  @Test
  public void testSuccess_clientCertFileFlag() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--cert_file=" + getCertFilename(),
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getClientCertificateHash())
        .isEqualTo(CertificateSamples.SAMPLE_CERT_HASH);
  }

  @Test
  public void testSuccess_clientCertHashFlag() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--cert_hash=" + SAMPLE_CERT_HASH,
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getClientCertificate()).isNull();
    assertThat(registrar.getClientCertificateHash()).isEqualTo(SAMPLE_CERT_HASH);
  }

  @Test
  public void testSuccess_failoverClientCertFileFlag() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--failover_cert_file=" + getCertFilename(),
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getClientCertificate()).isNull();
    assertThat(registrar.getClientCertificateHash()).isNull();
    assertThat(registrar.getFailoverClientCertificate()).isEqualTo(SAMPLE_CERT);
    assertThat(registrar.getFailoverClientCertificateHash()).isEqualTo(SAMPLE_CERT_HASH);
  }

  @Test
  public void testSuccess_ianaId() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=12345",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getIanaIdentifier()).isEqualTo(12345);
  }

  @Test
  public void testSuccess_billingId() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--billing_id=12345",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getBillingIdentifier()).isEqualTo(12345);
  }

  @Test
  public void testSuccess_streetAddress() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--street=\"1234 Main St\"",
        "--street \"4th Floor\"",
        "--street \"Suite 1\"",
        "--city Brooklyn",
        "--state NY",
        "--zip 11223",
        "--cc US",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getLocalizedAddress()).isNotNull();
    assertThat(registrar.getLocalizedAddress().getStreet()).hasSize(3);
    assertThat(registrar.getLocalizedAddress().getStreet().get(0)).isEqualTo("1234 Main St");
    assertThat(registrar.getLocalizedAddress().getStreet().get(1)).isEqualTo("4th Floor");
    assertThat(registrar.getLocalizedAddress().getStreet().get(2)).isEqualTo("Suite 1");
    assertThat(registrar.getLocalizedAddress().getCity()).isEqualTo("Brooklyn");
    assertThat(registrar.getLocalizedAddress().getState()).isEqualTo("NY");
    assertThat(registrar.getLocalizedAddress().getZip()).isEqualTo("11223");
    assertThat(registrar.getLocalizedAddress().getCountryCode()).isEqualTo("US");
  }

  @Test
  public void testSuccess_email() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--email=foo@foo.foo",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getEmailAddress()).isEqualTo("foo@foo.foo");
  }

  @Test
  public void testSuccess_url() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--url=http://foo.foo",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getUrl()).isEqualTo("http://foo.foo");
  }

  @Test
  public void testSuccess_phone() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--phone=+1.2125556342",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getPhoneNumber()).isEqualTo("+1.2125556342");
  }

  @Test
  public void testSuccess_optionalParamsAsNull() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=TEST",
        "--icann_referral_email=foo@bar.test",
        "--iana_id=null",
        "--billing_id=null",
        "--phone=null",
        "--fax=null",
        "--email=null",
        "--url=null",
        "--drive_id=null",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getIanaIdentifier()).isNull();
    assertThat(registrar.getBillingIdentifier()).isNull();
    assertThat(registrar.getPhoneNumber()).isNull();
    assertThat(registrar.getFaxNumber()).isNull();
    assertThat(registrar.getEmailAddress()).isNull();
    assertThat(registrar.getUrl()).isNull();
    assertThat(registrar.getDriveFolderId()).isNull();

  }

  @Test
  public void testSuccess_optionalParamsAsEmptyString() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=TEST",
        "--iana_id=",
        "--billing_id=",
        "--phone=",
        "--fax=",
        "--email=",
        "--url=",
        "--drive_id=",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getIanaIdentifier()).isNull();
    assertThat(registrar.getBillingIdentifier()).isNull();
    assertThat(registrar.getPhoneNumber()).isNull();
    assertThat(registrar.getFaxNumber()).isNull();
    assertThat(registrar.getEmailAddress()).isNull();
    assertThat(registrar.getUrl()).isNull();
    assertThat(registrar.getDriveFolderId()).isNull();
  }

  @Test
  public void testSuccess_blockPremiumNames() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--block_premium=true",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getBlockPremiumNames()).isTrue();
  }

  @Test
  public void testSuccess_noBlockPremiumNames() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--block_premium=false",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getBlockPremiumNames()).isFalse();
  }

  @Test
  public void testFailure_badPhoneNumber() throws Exception {
    thrown.expect(ParameterException.class, "phone");
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--phone=+1.112.555.6342",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_badPhoneNumber2() throws Exception {
    thrown.expect(ParameterException.class, "phone");
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--phone=+1.5555555555e",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testSuccess_fax() throws Exception {
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--fax=+1.2125556342",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");

    Registrar registrar = Registrar.loadByClientId("clientz");
    assertThat(registrar).isNotNull();
    assertThat(registrar.getFaxNumber()).isEqualTo("+1.2125556342");
  }

  @Test
  public void testFailure_missingRegistrarType() throws Exception {
    thrown.expect(NullPointerException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--iana_id=8",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_invalidRegistrarType() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=INVALID_TYPE",
        "--iana_id=8",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_invalidRegistrarState() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--registrar_state=INVALID_STATE",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_allowedTldDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--allowed_tlds=foobar",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_invalidIpWhitelistFlag() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--ip_whitelist=foobarbaz",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testSuccess_ipWhitelistFlagWithNull() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--ip_whitelist=192.168.1.1,192.168.0.2/16,null",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_invalidCertFileContents() throws Exception {
    thrown.expect(Exception.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--cert_file=" + writeToTmpFile("ABCDEF"),
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_invalidFailoverCertFileContents() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--failover_cert_file=" + writeToTmpFile("ABCDEF"),
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_certHashAndCertFile() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--cert_file=" + getCertFilename(),
        "--cert_hash=ABCDEF",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_certHashNotBase64() throws Exception {
    thrown.expect(IllegalArgumentException.class, "base64");
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--cert_hash=!",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_certHashNotA256BitValue() throws Exception {
    thrown.expect(IllegalArgumentException.class, "256");
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--cert_hash=abc",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_missingName() throws Exception {
    thrown.expect(NullPointerException.class);
    runCommand(
        "--password=blobio",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_missingPassword() throws Exception {
    thrown.expect(NullPointerException.class);
    runCommand(
        "--name=blobio",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_emptyPassword() throws Exception {
    thrown.expect(NullPointerException.class);
    runCommand(
        "--name=blobio",
        "--password=\"\"",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_clientIdTooShort() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "ab");
  }

  @Test
  public void testFailure_clientIdTooLong() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientabcdefghijk");
  }

  @Test
  public void testFailure_missingClientId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force");
  }

  @Test
  public void testFailure_missingStreetLines() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--city Brooklyn",
        "--state NY",
        "--zip 11223",
        "--cc US",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_missingCity() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--street=\"1234 Main St\"",
        "--street \"4th Floor\"",
        "--street \"Suite 1\"",
        "--state NY",
        "--zip 11223",
        "--cc US",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }


  @Test
  public void testFailure_missingState() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--street=\"1234 Main St\"",
        "--street \"4th Floor\"",
        "--street \"Suite 1\"",
        "--city Brooklyn",
        "--zip 11223",
        "--cc US",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_missingZip() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--street=\"1234 Main St\"",
        "--street \"4th Floor\"",
        "--street \"Suite 1\"",
        "--city Brooklyn",
        "--state NY",
        "--cc US",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_missingCc() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--street=\"1234 Main St\"",
        "--street \"4th Floor\"",
        "--street \"Suite 1\"",
        "--city Brooklyn",
        "--state NY",
        "--zip 11223",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_invalidCc() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--street=\"1234 Main St\"",
        "--street \"4th Floor\"",
        "--street \"Suite 1\"",
        "--city Brooklyn",
        "--state NY",
        "--zip 11223",
        "--cc USA",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_tooManyStreetLines() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--street=\"Attn:Hey You Guys\"",
        "--street=\"1234 Main St\"",
        "--street \"4th Floor\"",
        "--street \"Suite 1\"",
        "--city Brooklyn",
        "--state NY",
        "--zip 11223",
        "--cc US",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_tooFewStreetLines() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--street",
        "--city Brooklyn",
        "--state NY",
        "--zip 11223",
        "--cc US",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_missingIanaIdForRealRegistrar() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_negativeIanaId() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=-1",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_nonIntegerIanaId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=ABC12345",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_negativeBillingId() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--billing_id=-1",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_nonIntegerBillingId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--billing_id=ABC12345",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_missingPhonePasscode() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_missingIcannReferralEmail() throws Exception {
    thrown.expect(NullPointerException.class, "--icann_referral_email");
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_passcodeTooShort() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=0123",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_passcodeTooLong() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=0123",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_invalidPasscode() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=code1",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_twoClientsSpecified() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "ClientY",
        "clientz");
  }

  @Test
  public void testFailure_unknownFlag() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--unrecognized_flag=foo",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_alreadyExists() throws Exception {
    persistResource(new Registrar.Builder()
        .setClientId("existing")
        .setIanaIdentifier(1L)
        .setType(Registrar.Type.REAL)
        .build());
    thrown.expect(IllegalStateException.class, "Registrar existing already exists");
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "existing");
  }

  @Test
  public void testFailure_registrarNameSimilarToExisting() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "The registrar name tHeRe GiStRaR normalizes "
        + "identically to existing registrar name The Registrar");
    // Normalizes identically to "The Registrar" which is created by AppEngineRule.
    runCommand(
        "--name=tHeRe GiStRaR",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_clientIdNormalizesToExisting() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "The registrar client identifier theregistrar "
        + "normalizes identically to existing registrar TheRegistrar");
    runCommand(
        "--name=blahhh",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "theregistrar");
  }

  @Test
  public void testFailure_clientIdIsInvalidFormat() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Client identifier (.L33T) can only contain lowercase letters, numbers, and hyphens");
    runCommand(
        "--name=blahhh",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        ".L33T");
  }

  @Test
  public void testFailure_phone() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--phone=3",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }

  @Test
  public void testFailure_fax() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--name=blobio",
        "--password=some_password",
        "--registrar_type=REAL",
        "--iana_id=8",
        "--fax=3",
        "--passcode=01234",
        "--icann_referral_email=foo@bar.test",
        "--force",
        "clientz");
  }
}
