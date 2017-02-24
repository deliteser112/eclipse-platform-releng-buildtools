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

package google.registry.keyring.kms;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.googlecode.objectify.Key;
import google.registry.model.server.KmsSecret;
import google.registry.model.server.KmsSecretRevision;
import google.registry.testing.AppEngineRule;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KmsUpdaterTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  private KmsUpdater updater;

  @Before
  public void setUp() {
    updater = new KmsUpdater(new FakeKmsConnection());
  }

  @Test
  public void test_setMultipleSecrets() throws Exception {
    updater
        .setBraintreePrivateKey("value1")
        .setIcannReportingPassword("value2")
        .setJsonCredential("value3")
        .update();

    verifySecretAndSecretRevisionWritten(
        "braintree-private-key", "braintree-private-key/foo", getCiphertext("value1"));
    verifySecretAndSecretRevisionWritten(
        "icann-reporting-password", "icann-reporting-password/foo", getCiphertext("value2"));
    verifySecretAndSecretRevisionWritten(
        "json-credential", "json-credential/foo", getCiphertext("value3"));
  }

  @Test
  public void test_setBraintreePrivateKey() throws Exception {
    updater.setBraintreePrivateKey("value1").update();

    verifySecretAndSecretRevisionWritten(
        "braintree-private-key", "braintree-private-key/foo", getCiphertext("value1"));
  }

  @Test
  public void test_setBrdaReceiverKey() throws Exception {
    updater.setBrdaReceiverPublicKey(KmsTestHelper.getPublicKeyring().getPublicKey()).update();

    verifySecretAndSecretRevisionWritten(
        "brda-receiver-public",
        "brda-receiver-public/foo",
        getCiphertext(KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded()));
  }

  @Test
  public void test_setBrdaSigningKey() throws Exception {
    updater.setBrdaSigningKey(KmsTestHelper.getPrivateKeyring()).update();

    verifySecretAndSecretRevisionWritten(
        "brda-signing-private",
        "brda-signing-private/foo",
        getCiphertext(KmsTestHelper.getPrivateKeyring().getEncoded()));
    verifySecretAndSecretRevisionWritten(
        "brda-signing-public",
        "brda-signing-public/foo",
        getCiphertext(KmsTestHelper.getPrivateKeyring().getPublicKey().getEncoded()));
  }

  @Test
  public void test_setIcannReportingPassword() throws Exception {
    updater.setIcannReportingPassword("value1").update();

    verifySecretAndSecretRevisionWritten(
        "icann-reporting-password", "icann-reporting-password/foo", getCiphertext("value1"));
  }

  @Test
  public void test_setJsonCredential() throws Exception {
    updater.setJsonCredential("value1").update();

    verifySecretAndSecretRevisionWritten(
        "json-credential", "json-credential/foo", getCiphertext("value1"));
  }

  @Test
  public void test_setMarksdbDnlLogin() throws Exception {
    updater.setMarksdbDnlLogin("value1").update();

    verifySecretAndSecretRevisionWritten(
        "marksdb-dnl-login", "marksdb-dnl-login/foo", getCiphertext("value1"));
  }

  @Test
  public void test_setMarksdbLordnPassword() throws Exception {
    updater.setMarksdbLordnPassword("value1").update();

    verifySecretAndSecretRevisionWritten(
        "marksdb-lordn-password", "marksdb-lordn-password/foo", getCiphertext("value1"));
  }

  @Test
  public void test_setMarksdbSmdrlLogin() throws Exception {
    updater.setMarksdbSmdrlLogin("value1").update();

    verifySecretAndSecretRevisionWritten(
        "marksdb-smdrl-login", "marksdb-smdrl-login/foo", getCiphertext("value1"));
  }

  @Test
  public void test_setRdeReceiverKey() throws Exception {
    updater.setRdeReceiverPublicKey(KmsTestHelper.getPublicKeyring().getPublicKey()).update();

    verifySecretAndSecretRevisionWritten(
        "rde-receiver-public",
        "rde-receiver-public/foo",
        getCiphertext(KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded()));
  }

  @Test
  public void test_setRdeSigningKey() throws Exception {
    updater.setRdeSigningKey(KmsTestHelper.getPrivateKeyring()).update();

    verifySecretAndSecretRevisionWritten(
        "rde-signing-private",
        "rde-signing-private/foo",
        getCiphertext(KmsTestHelper.getPrivateKeyring().getEncoded()));
    verifySecretAndSecretRevisionWritten(
        "rde-signing-public",
        "rde-signing-public/foo",
        getCiphertext(KmsTestHelper.getPrivateKeyring().getPublicKey().getEncoded()));
  }

  @Test
  public void test_setRdeSshClientPrivateKey() throws Exception {
    updater.setRdeSshClientPrivateKey("value1").update();

    verifySecretAndSecretRevisionWritten(
        "rde-ssh-client-private", "rde-ssh-client-private/foo", getCiphertext("value1"));
  }

  @Test
  public void test_setRdeSshClientPublicKey() throws Exception {
    updater.setRdeSshClientPublicKey("value1").update();

    verifySecretAndSecretRevisionWritten(
        "rde-ssh-client-public", "rde-ssh-client-public/foo", getCiphertext("value1"));
  }

  @Test
  public void test_setRdeStagingKey() throws Exception {
    updater.setRdeStagingKey(KmsTestHelper.getPrivateKeyring()).update();

    verifySecretAndSecretRevisionWritten(
        "rde-staging-private",
        "rde-staging-private/foo",
        getCiphertext(KmsTestHelper.getPrivateKeyring().getEncoded()));
    verifySecretAndSecretRevisionWritten(
        "rde-staging-public",
        "rde-staging-public/foo",
        getCiphertext(KmsTestHelper.getPrivateKeyring().getPublicKey().getEncoded()));
  }


  private static void verifySecretAndSecretRevisionWritten(
      String secretName, String expectedCryptoKeyVersionName, String expectedEncryptedValue) {
    KmsSecret secret =
        ofy().load().key(Key.create(getCrossTldKey(), KmsSecret.class, secretName)).now();
    assertThat(secret).isNotNull();
    KmsSecretRevision secretRevision = ofy().load().key(secret.getLatestRevision()).now();
    assertThat(secretRevision.getKmsCryptoKeyVersionName()).isEqualTo(expectedCryptoKeyVersionName);
    assertThat(secretRevision.getEncryptedValue()).isEqualTo(expectedEncryptedValue);
  }

  private static String getCiphertext(byte[] plaintext) throws IOException {
    return new FakeKmsConnection().encrypt("blah", plaintext).ciphertext();
  }

  private static String getCiphertext(String plaintext) throws IOException {
    return getCiphertext(plaintext.getBytes(UTF_8));
  }
}
