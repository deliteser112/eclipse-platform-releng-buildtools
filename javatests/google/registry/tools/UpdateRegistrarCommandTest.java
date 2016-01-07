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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.registrar.Registrar.loadByClientId;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT_HASH;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.model.billing.RegistrarBillingEntry;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.Registrar.BillingMethod;
import google.registry.model.registrar.Registrar.State;
import google.registry.model.registrar.Registrar.Type;
import google.registry.util.CidrAddressBlock;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link UpdateRegistrarCommand}. */
public class UpdateRegistrarCommandTest extends CommandTestCase<UpdateRegistrarCommand> {

  @Test
  public void testSuccess_password() throws Exception {
    assertThat(loadByClientId("NewRegistrar").testPassword("some_password")).isFalse();
    runCommand("--password=some_password", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").testPassword("some_password")).isTrue();
  }

  @Test
  public void testSuccess_registrarType() throws Exception {
    persistResource(
        loadByClientId("NewRegistrar")
            .asBuilder()
            .setType(Registrar.Type.OTE)
            .setIanaIdentifier(null)
            .build());
    assertThat(loadByClientId("NewRegistrar").getType()).isEqualTo(Type.OTE);
    runCommand("--registrar_type=TEST", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getType()).isEqualTo(Type.TEST);
  }

  @Test
  public void testFailure_noPasscodeOnChangeToReal() throws Exception {
    persistResource(
        loadByClientId("NewRegistrar")
            .asBuilder()
            .setType(Registrar.Type.OTE)
            .setIanaIdentifier(null)
            .setPhonePasscode(null)
            .build());
    thrown.expect(IllegalArgumentException.class, "--passcode is required for REAL registrars.");
    runCommand("--registrar_type=REAL", "--iana_id=1000", "--force", "NewRegistrar");
  }

  @Test
  public void testSuccess_registrarState() throws Exception {
    assertThat(loadByClientId("NewRegistrar").getState()).isEqualTo(State.ACTIVE);
    runCommand("--registrar_state=SUSPENDED", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getState()).isEqualTo(State.SUSPENDED);
  }

  @Test
  public void testSuccess_allowedTlds() throws Exception {
    createTlds("xn--q9jyb4c", "foobar");
    persistResource(
        loadByClientId("NewRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.of("xn--q9jyb4c"))
            .build());
    runCommand("--allowed_tlds=xn--q9jyb4c,foobar", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getAllowedTlds())
        .containsExactly("xn--q9jyb4c", "foobar");
  }

  @Test
  public void testSuccess_addAllowedTlds() throws Exception {
    createTlds("xn--q9jyb4c", "foo", "bar");
    persistResource(
        loadByClientId("NewRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.of("xn--q9jyb4c"))
            .build());
    runCommand("--add_allowed_tlds=foo,bar", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getAllowedTlds())
        .containsExactly("xn--q9jyb4c", "foo", "bar");
  }

  @Test
  public void testSuccess_addAllowedTldsWithDupes() throws Exception {
    createTlds("xn--q9jyb4c", "foo", "bar");
    persistResource(
        loadByClientId("NewRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.of("xn--q9jyb4c"))
            .build());
    runCommand("--add_allowed_tlds=xn--q9jyb4c,foo,bar", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getAllowedTlds())
        .isEqualTo(ImmutableSet.of("xn--q9jyb4c", "foo", "bar"));
  }

  @Test
  public void testSuccess_ipWhitelist() throws Exception {
    assertThat(loadByClientId("NewRegistrar").getIpAddressWhitelist()).isEmpty();
    runCommand("--ip_whitelist=192.168.1.1,192.168.0.2/16", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getIpAddressWhitelist())
        .containsExactly(
            CidrAddressBlock.create("192.168.1.1"), CidrAddressBlock.create("192.168.0.2/16"))
        .inOrder();
  }

  @Test
  public void testSuccess_clearIpWhitelist() throws Exception {
    persistResource(
        loadByClientId("NewRegistrar")
            .asBuilder()
            .setIpAddressWhitelist(
                ImmutableList.of(
                    CidrAddressBlock.create("192.168.1.1"),
                    CidrAddressBlock.create("192.168.0.2/16")))
            .build());
    assertThat(loadByClientId("NewRegistrar").getIpAddressWhitelist()).isNotEmpty();
    runCommand("--ip_whitelist=null", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getIpAddressWhitelist()).isEmpty();
  }

  @Test
  public void testSuccess_certFile() throws Exception {
    Registrar registrar = checkNotNull(loadByClientId("NewRegistrar"));
    assertThat(registrar.getClientCertificate()).isNull();
    assertThat(registrar.getClientCertificateHash()).isNull();
    runCommand("--cert_file=" + getCertFilename(), "--force", "NewRegistrar");
    registrar = checkNotNull(loadByClientId("NewRegistrar"));
    // NB: Hash was computed manually using 'openssl x509 -fingerprint -sha256 -in ...' and then
    // converting the result from a hex string to non-padded base64 encoded string.
    assertThat(registrar.getClientCertificate()).isEqualTo(SAMPLE_CERT);
    assertThat(registrar.getClientCertificateHash()).isEqualTo(SAMPLE_CERT_HASH);
  }

  @Test
  public void testSuccess_certHash() throws Exception {
    assertThat(loadByClientId("NewRegistrar").getClientCertificateHash()).isNull();
    runCommand("--cert_hash=" + SAMPLE_CERT_HASH, "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getClientCertificateHash())
        .isEqualTo(SAMPLE_CERT_HASH);
  }

  @Test
  public void testSuccess_clearCert() throws Exception {
    persistResource(
        loadByClientId("NewRegistrar")
            .asBuilder()
            .setClientCertificate(SAMPLE_CERT, DateTime.now(UTC))
            .build());
    assertThat(isNullOrEmpty(loadByClientId("NewRegistrar").getClientCertificate())).isFalse();
    runCommand("--cert_file=/dev/null", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getClientCertificate()).isNull();
  }

  @Test
  public void testSuccess_clearCertHash() throws Exception {
    persistResource(
        loadByClientId("NewRegistrar")
            .asBuilder()
            .setClientCertificateHash(SAMPLE_CERT_HASH)
            .build());
    assertThat(isNullOrEmpty(loadByClientId("NewRegistrar").getClientCertificateHash())).isFalse();
    runCommand("--cert_hash=null", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getClientCertificateHash()).isNull();
  }

  @Test
  public void testSuccess_ianaId() throws Exception {
    assertThat(loadByClientId("NewRegistrar").getIanaIdentifier()).isEqualTo(8);
    runCommand("--iana_id=12345", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getIanaIdentifier()).isEqualTo(12345);
  }

  @Test
  public void testSuccess_billingId() throws Exception {
    assertThat(loadByClientId("NewRegistrar").getBillingIdentifier()).isNull();
    runCommand("--billing_id=12345", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getBillingIdentifier()).isEqualTo(12345);
  }

  @Test
  public void testSuccess_changeBillingMethodToBraintreeWhenBalanceIsZero() throws Exception {
    createTlds("xn--q9jyb4c");
    assertThat(loadByClientId("NewRegistrar").getBillingMethod()).isEqualTo(BillingMethod.EXTERNAL);
    runCommand("--billing_method=braintree", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getBillingMethod())
        .isEqualTo(BillingMethod.BRAINTREE);
  }

  @Test
  public void testFailure_changeBillingMethodWhenBalanceIsNonZero() throws Exception {
    createTlds("xn--q9jyb4c");
    Registrar registrar = checkNotNull(loadByClientId("NewRegistrar"));
    persistResource(
        new RegistrarBillingEntry.Builder()
            .setPrevious(null)
            .setParent(registrar)
            .setCreated(DateTime.parse("1984-12-18TZ"))
            .setDescription("USD Invoice for December")
            .setAmount(Money.parse("USD 10.00"))
            .build());
    assertThat(registrar.getBillingMethod()).isEqualTo(BillingMethod.EXTERNAL);
    thrown.expect(IllegalStateException.class,
        "Refusing to change billing method on Registrar 'NewRegistrar' from EXTERNAL to BRAINTREE"
            + " because current balance is non-zero: {USD=USD 10.00}");
    runCommand("--billing_method=braintree", "--force", "NewRegistrar");
  }

  @Test
  public void testSuccess_streetAddress() throws Exception {
    runCommand("--street=\"1234 Main St\"", "--street \"4th Floor\"", "--street \"Suite 1\"",
        "--city Brooklyn", "--state NY", "--zip 11223", "--cc US", "--force", "NewRegistrar");

    Registrar registrar = checkNotNull(loadByClientId("NewRegistrar"));
    assertThat(registrar.getLocalizedAddress() != null).isTrue();
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
  public void testSuccess_blockPremiumNames() throws Exception {
    assertThat(loadByClientId("NewRegistrar").getBlockPremiumNames()).isFalse();
    runCommand("--block_premium=true", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getBlockPremiumNames()).isTrue();
  }

  @Test
  public void testSuccess_resetBlockPremiumNames() throws Exception {
    persistResource(loadByClientId("NewRegistrar").asBuilder().setBlockPremiumNames(true).build());
    runCommand("--block_premium=false", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getBlockPremiumNames()).isFalse();
  }

  @Test
  public void testSuccess_blockPremiumNamesUnspecified() throws Exception {
    persistResource(loadByClientId("NewRegistrar").asBuilder().setBlockPremiumNames(true).build());
    // Make some unrelated change where we don't specify "--block_premium".
    runCommand("--billing_id=12345", "--force", "NewRegistrar");
    // Make sure the field didn't get reset back to false.
    assertThat(loadByClientId("NewRegistrar").getBlockPremiumNames()).isTrue();
  }

  @Test
  public void testSuccess_updateMultiple() throws Exception {
    assertThat(loadByClientId("TheRegistrar").getState()).isEqualTo(State.ACTIVE);
    assertThat(loadByClientId("NewRegistrar").getState()).isEqualTo(State.ACTIVE);
    runCommand("--registrar_state=SUSPENDED", "--force", "TheRegistrar", "NewRegistrar");
    assertThat(loadByClientId("TheRegistrar").getState()).isEqualTo(State.SUSPENDED);
    assertThat(loadByClientId("NewRegistrar").getState()).isEqualTo(State.SUSPENDED);
  }

  @Test
  public void testSuccess_resetOptionalParamsNullString() throws Exception {
    Registrar registrar = checkNotNull(loadByClientId("NewRegistrar"));
    registrar = persistResource(registrar.asBuilder()
        .setType(Type.PDT) // for non-null IANA ID
        .setIanaIdentifier(9995L)
        .setBillingIdentifier(1L)
        .setPhoneNumber("+1.2125555555")
        .setFaxNumber("+1.2125555556")
        .setEmailAddress("registrar@example.tld")
        .setUrl("http://www.example.tld")
        .setDriveFolderId("id")
        .build());

    assertThat(registrar.getIanaIdentifier()).isNotNull();
    assertThat(registrar.getBillingIdentifier()).isNotNull();
    assertThat(registrar.getPhoneNumber()).isNotNull();
    assertThat(registrar.getFaxNumber()).isNotNull();
    assertThat(registrar.getEmailAddress()).isNotNull();
    assertThat(registrar.getUrl()).isNotNull();
    assertThat(registrar.getDriveFolderId()).isNotNull();

    runCommand(
        "--registrar_type=TEST", // necessary for null IANA ID
        "--iana_id=null",
        "--billing_id=null",
        "--phone=null",
        "--fax=null",
        "--email=null",
        "--url=null",
        "--drive_id=null",
        "--force",
        "NewRegistrar");

    registrar = checkNotNull(loadByClientId("NewRegistrar"));
    assertThat(registrar.getIanaIdentifier()).isNull();
    assertThat(registrar.getBillingIdentifier()).isNull();
    assertThat(registrar.getPhoneNumber()).isNull();
    assertThat(registrar.getFaxNumber()).isNull();
    assertThat(registrar.getEmailAddress()).isNull();
    assertThat(registrar.getUrl()).isNull();
    assertThat(registrar.getDriveFolderId()).isNull();
  }

  @Test
  public void testSuccess_resetOptionalParamsEmptyString() throws Exception {
    Registrar registrar = checkNotNull(loadByClientId("NewRegistrar"));
    registrar = persistResource(registrar.asBuilder()
        .setType(Type.PDT) // for non-null IANA ID
        .setIanaIdentifier(9995L)
        .setBillingIdentifier(1L)
        .setPhoneNumber("+1.2125555555")
        .setFaxNumber("+1.2125555556")
        .setEmailAddress("registrar@example.tld")
        .setUrl("http://www.example.tld")
        .setDriveFolderId("id")
        .build());

    assertThat(registrar.getIanaIdentifier()).isNotNull();
    assertThat(registrar.getBillingIdentifier()).isNotNull();
    assertThat(registrar.getPhoneNumber()).isNotNull();
    assertThat(registrar.getFaxNumber()).isNotNull();
    assertThat(registrar.getEmailAddress()).isNotNull();
    assertThat(registrar.getUrl()).isNotNull();
    assertThat(registrar.getDriveFolderId()).isNotNull();

    runCommand(
        "--registrar_type=TEST", // necessary for null IANA ID
        "--iana_id=",
        "--billing_id=",
        "--phone=",
        "--fax=",
        "--email=",
        "--url=",
        "--drive_id=",
        "--force",
        "NewRegistrar");

    registrar = checkNotNull(loadByClientId("NewRegistrar"));
    assertThat(registrar.getIanaIdentifier()).isNull();
    assertThat(registrar.getBillingIdentifier()).isNull();
    assertThat(registrar.getPhoneNumber()).isNull();
    assertThat(registrar.getFaxNumber()).isNull();
    assertThat(registrar.getEmailAddress()).isNull();
    assertThat(registrar.getUrl()).isNull();
    assertThat(registrar.getDriveFolderId()).isNull();
  }

  @Test
  public void testSuccess_setWhoisServer_works() throws Exception {
    runCommand("--whois=whois.goth.black", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getWhoisServer()).isEqualTo("whois.goth.black");
  }

  @Test
  public void testSuccess_triggerGroupSyncing_works() throws Exception {
    persistResource(
        loadByClientId("NewRegistrar").asBuilder().setContactsRequireSyncing(false).build());
    assertThat(loadByClientId("NewRegistrar").getContactsRequireSyncing()).isFalse();
    runCommand("--sync_groups=true", "--force", "NewRegistrar");
    assertThat(loadByClientId("NewRegistrar").getContactsRequireSyncing()).isTrue();
  }

  @Test
  public void testFailure_invalidRegistrarType() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand("--registrar_type=INVALID_TYPE", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_invalidRegistrarState() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand("--registrar_state=INVALID_STATE", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_negativeIanaId() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--iana_id=-1", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_nonIntegerIanaId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand("--iana_id=ABC123", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_negativeBillingId() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--billing_id=-1", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_nonIntegerBillingId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand("--billing_id=ABC123", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_passcodeTooShort() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--passcode=0123", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_passcodeTooLong() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--passcode=012345", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_invalidPasscode() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--passcode=code1", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_allowedTldDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--allowed_tlds=foobar", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_addAllowedTldDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--add_allowed_tlds=foobar", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_allowedTldsAndAddAllowedTlds() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--allowed_tlds=bar", "--add_allowed_tlds=foo", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_invalidIpWhitelist() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--ip_whitelist=foobarbaz", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_invalidCertFileContents() throws Exception {
    thrown.expect(Exception.class);
    runCommand("--cert_file=" + writeToTmpFile("ABCDEF"), "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_certHashAndCertFile() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--cert_file=" + getCertFilename(), "--cert_hash=ABCDEF", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_missingClientId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand("--force");
  }

  @Test
  public void testFailure_missingStreetLines() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--city Brooklyn", "--state NY", "--zip 11223", "--cc US", "--force",
        "NewRegistrar");
  }

  @Test
  public void testFailure_missingCity() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--street=\"1234 Main St\"", "--street \"4th Floor\"", "--street \"Suite 1\"",
        "--state NY", "--zip 11223", "--cc US", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_missingState() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--street=\"1234 Main St\"", "--street \"4th Floor\"", "--street \"Suite 1\"",
        "--city Brooklyn", "--zip 11223", "--cc US", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_missingZip() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--street=\"1234 Main St\"", "--street \"4th Floor\"", "--street \"Suite 1\"",
        "--city Brooklyn", "--state NY", "--cc US", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_missingCc() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--street=\"1234 Main St\"", "--street \"4th Floor\"", "--street \"Suite 1\"",
        "--city Brooklyn", "--state NY", "--zip 11223", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_missingInvalidCc() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--street=\"1234 Main St\"", "--street \"4th Floor\"", "--street \"Suite 1\"",
        "--city Brooklyn", "--state NY", "--zip 11223", "--cc USA", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_tooManyStreetLines() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--street \"Attn:Hey You Guys\"", "--street \"1234 Main St\"",
        "--street \"4th Floor\"", "--street \"Suite 1\"", "--city Brooklyn", "--state NY",
        "--zip 11223", "--cc USA", "--force", "NewRegistrar");
  }

  @Test
  public void testFailure_tooFewStreetLines() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("--street", "--city Brooklyn", "--state NY", "--zip 11223", "--cc USA", "--force",
        "NewRegistrar");
  }

  @Test
  public void testFailure_unknownFlag() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand("--force", "--unrecognized_flag=foo", "NewRegistrar");
  }

  @Test
  public void testFailure_doesNotExist() throws Exception {
    thrown.expect(NullPointerException.class);
    runCommand("--force", "ClientZ");
  }

  @Test
  public void testFailure_registrarNameSimilarToExisting() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    // Normalizes identically to "The Registrar" which is created by AppEngineRule.
    runCommand("--name tHeRe GiStRaR", "--force", "NewRegistrar");
  }
}
