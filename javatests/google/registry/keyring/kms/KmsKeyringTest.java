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
import static google.registry.testing.DatastoreHelper.persistResources;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import google.registry.model.server.KmsSecret;
import google.registry.model.server.KmsSecretRevision;
import google.registry.model.server.KmsSecretRevision.Builder;
import google.registry.testing.AppEngineRule;
import java.io.IOException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KmsKeyringTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  private KmsKeyring keyring;

  @Before
  public void setUp() {
    keyring = new KmsKeyring(new FakeKmsConnection());
  }

  @Test
  public void test_getRdeSigningKey() throws Exception {
    saveKeyPairSecret("rde-signing-public", "rde-signing-private");

    PGPKeyPair rdeSigningKey = keyring.getRdeSigningKey();

    assertThat(rdeSigningKey.getKeyID())
        .isEqualTo(KmsTestHelper.getPublicKeyring().getPublicKey().getKeyID());
  }

  @Test
  public void test_getRdeStagingEncryptionKey() throws Exception {
    savePublicKeySecret("rde-staging-public");

    PGPPublicKey rdeStagingEncryptionKey = keyring.getRdeStagingEncryptionKey();

    assertThat(rdeStagingEncryptionKey.getFingerprint())
        .isEqualTo(KmsTestHelper.getPublicKeyring().getPublicKey().getFingerprint());
  }

  @Test
  public void test_getRdeStagingDecryptionKey() throws Exception {
    savePrivateKeySecret("rde-staging-private");

    PGPPrivateKey rdeStagingDecryptionKey = keyring.getRdeStagingDecryptionKey();

    assertThat(rdeStagingDecryptionKey.getKeyID())
        .isEqualTo(KmsTestHelper.getPrivateKeyring().getSecretKey().getKeyID());
  }

  @Test
  public void test_getRdeReceiverKey() throws Exception {
    savePublicKeySecret("rde-receiver-public");

    PGPPublicKey rdeReceiverKey = keyring.getRdeReceiverKey();

    assertThat(rdeReceiverKey.getFingerprint())
        .isEqualTo(KmsTestHelper.getPublicKeyring().getPublicKey().getFingerprint());
  }

  @Test
  public void test_getBrdaSigningKey() throws Exception {
    saveKeyPairSecret("brda-signing-public", "brda-signing-private");

    PGPKeyPair brdaSigningKey = keyring.getBrdaSigningKey();

    assertThat(brdaSigningKey.getKeyID())
        .isEqualTo(KmsTestHelper.getPrivateKeyring().getPublicKey().getKeyID());
  }

  @Test
  public void test_getBrdaReceiverKey() throws Exception {
    savePublicKeySecret("brda-receiver-public");

    PGPPublicKey brdaReceiverKey = keyring.getBrdaReceiverKey();

    assertThat(brdaReceiverKey.getFingerprint())
        .isEqualTo(KmsTestHelper.getPublicKeyring().getPublicKey().getFingerprint());
  }

  @Test
  public void test_getRdeSshClientPublicKey() throws Exception {
    saveCleartextSecret("rde-ssh-client-public");

    String rdeSshClientPublicKey = keyring.getRdeSshClientPublicKey();

    assertThat(rdeSshClientPublicKey).isEqualTo("rde-ssh-client-publicmoo");
  }

  @Test
  public void test_getRdeSshClientPrivateKey() throws Exception {
    saveCleartextSecret("rde-ssh-client-private");

    String rdeSshClientPrivateKey = keyring.getRdeSshClientPrivateKey();

    assertThat(rdeSshClientPrivateKey).isEqualTo("rde-ssh-client-privatemoo");
  }

  @Test
  public void test_getIcannReportingPassword() throws Exception {
    saveCleartextSecret("icann-reporting-password");

    String icannReportingPassword = keyring.getIcannReportingPassword();

    assertThat(icannReportingPassword).isEqualTo("icann-reporting-passwordmoo");
  }

  @Test
  public void test_getMarksdbDnlLogin() throws Exception {
    saveCleartextSecret("marksdb-dnl-login");

    String marksdbDnlLogin = keyring.getMarksdbDnlLogin();

    assertThat(marksdbDnlLogin).isEqualTo("marksdb-dnl-loginmoo");
  }

  @Test
  public void test_getMarksdbLordnPassword() throws Exception {
    saveCleartextSecret("marksdb-lordn-password");

    String marksdbLordnPassword = keyring.getMarksdbLordnPassword();

    assertThat(marksdbLordnPassword).isEqualTo("marksdb-lordn-passwordmoo");
  }

  @Test
  public void test_getMarksdbSmdrlLogin() throws Exception {
    saveCleartextSecret("marksdb-smdrl-login");

    String marksdbSmdrlLogin = keyring.getMarksdbSmdrlLogin();

    assertThat(marksdbSmdrlLogin).isEqualTo("marksdb-smdrl-loginmoo");

  }

  @Test
  public void test_getJsonCredential() throws Exception {
    saveCleartextSecret("json-credential");

    String jsonCredential = keyring.getJsonCredential();

    assertThat(jsonCredential).isEqualTo("json-credentialmoo");
  }

  @Test
  public void test_getBraintreePrivateKey() throws Exception {
    saveCleartextSecret("braintree-private-key");

    String braintreePrivateKey = keyring.getBraintreePrivateKey();

    assertThat(braintreePrivateKey).isEqualTo("braintree-private-keymoo");
  }

  private static void persistSecret(String secretName, byte[] secretValue) throws IOException {
    KmsConnection kmsConnection = new FakeKmsConnection();

    KmsSecretRevision secretRevision =
        new Builder()
            .setEncryptedValue(kmsConnection.encrypt(secretName, secretValue).ciphertext())
            .setKmsCryptoKeyVersionName(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION)
            .setParent(secretName)
            .build();
    KmsSecret secret = KmsSecret.create(secretName, secretRevision);
    persistResources(ImmutableList.of(secretRevision, secret));
  }

  private static void saveCleartextSecret(String secretName) throws Exception {
    persistSecret(secretName, (secretName + "moo").getBytes(UTF_8));
  }

  private static void savePublicKeySecret(String publicKeyName) throws Exception {
    persistSecret(publicKeyName, KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded());
  }

  private static void savePrivateKeySecret(String privateKeyName) throws Exception {
    persistSecret(privateKeyName, KmsTestHelper.getPrivateKeyring().getEncoded());
  }

  private static void saveKeyPairSecret(String publicKeyName, String privateKeyName)
      throws Exception {
    savePublicKeySecret(publicKeyName);
    savePrivateKeySecret(privateKeyName);
  }
}
