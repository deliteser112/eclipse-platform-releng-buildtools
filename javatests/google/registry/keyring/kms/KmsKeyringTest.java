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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.cloudkms.v1beta1.CloudKMS;
import com.google.api.services.cloudkms.v1beta1.model.DecryptRequest;
import com.google.api.services.cloudkms.v1beta1.model.DecryptResponse;
import com.google.common.collect.ImmutableList;
import google.registry.model.server.KmsSecret;
import google.registry.model.server.KmsSecretRevision;
import google.registry.model.server.KmsSecretRevision.Builder;
import google.registry.testing.AppEngineRule;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KmsKeyringTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Mock private CloudKMS kms;
  @Mock private CloudKMS.Projects kmsProjects;
  @Mock private CloudKMS.Projects.Locations kmsLocations;
  @Mock private CloudKMS.Projects.Locations.KeyRings kmsKeyRings;
  @Mock private CloudKMS.Projects.Locations.KeyRings.CryptoKeys kmsCryptoKeys;
  @Mock private CloudKMS.Projects.Locations.KeyRings.CryptoKeys.Decrypt kmsCryptoKeysDecrypt;

  @Captor private ArgumentCaptor<String> cryptoKeyName;
  @Captor private ArgumentCaptor<DecryptRequest> decryptRequest;

  private KmsKeyring keyring;

  @Before
  public void setUp() throws Exception {
    keyring = new KmsKeyring("foo", "bar", kms);

    when(kms.projects()).thenReturn(kmsProjects);
    when(kmsProjects.locations()).thenReturn(kmsLocations);
    when(kmsLocations.keyRings()).thenReturn(kmsKeyRings);
    when(kmsKeyRings.cryptoKeys()).thenReturn(kmsCryptoKeys);
    when(kmsCryptoKeys.decrypt(anyString(), any(DecryptRequest.class)))
        .thenReturn(kmsCryptoKeysDecrypt);
  }

  @Test
  public void test_getRdeSigningKey() throws Exception {
    persistSecret("rde-signing-private");
    persistSecret("rde-signing-public");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(
            new DecryptResponse()
                .encodePlaintext(KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded()))
        .thenReturn(
            new DecryptResponse().encodePlaintext(KmsTestHelper.getPrivateKeyring().getEncoded()));

    PGPKeyPair rdeSigningKey = keyring.getRdeSigningKey();

    verify(kmsCryptoKeys, times(2)).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getAllValues())
        .isEqualTo(
            ImmutableList.of(
                "projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-signing-public",
                "projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-signing-private"));
    assertThat(decryptRequest.getAllValues())
        .isEqualTo(
            ImmutableList.of(
                new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE),
                new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE)));
    assertThat(rdeSigningKey.getKeyID())
        .isEqualTo(KmsTestHelper.getPublicKeyring().getPublicKey().getKeyID());
  }

  @Test
  public void test_getRdeStagingEncryptionKey() throws Exception {
    persistSecret("rde-staging-public");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(
            new DecryptResponse()
                .encodePlaintext(KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded()));

    PGPPublicKey rdeStagingEncryptionKey = keyring.getRdeStagingEncryptionKey();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-staging-public");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(rdeStagingEncryptionKey.getFingerprint())
        .isEqualTo(KmsTestHelper.getPublicKeyring().getPublicKey().getFingerprint());
  }

  @Test
  public void test_getRdeStagingDecryptionKey() throws Exception {
    persistSecret("rde-staging-private");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(
            new DecryptResponse().encodePlaintext(KmsTestHelper.getPrivateKeyring().getEncoded()));

    PGPPrivateKey rdeStagingDecryptionKey = keyring.getRdeStagingDecryptionKey();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-staging-private");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(rdeStagingDecryptionKey.getKeyID())
        .isEqualTo(KmsTestHelper.getPrivateKeyring().getSecretKey().getKeyID());
  }

  @Test
  public void test_getRdeReceiverKey() throws Exception {
    persistSecret("rde-receiver-public");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(
            new DecryptResponse().encodePlaintext(KmsTestHelper.getPublicKeyring().getEncoded()));

    PGPPublicKey rdeReceiverKey = keyring.getRdeReceiverKey();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-receiver-public");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(rdeReceiverKey.getFingerprint())
        .isEqualTo(KmsTestHelper.getPublicKeyring().getPublicKey().getFingerprint());
  }

  @Test
  public void test_getBrdaSigningKey() throws Exception {
    persistSecret("brda-signing-public");
    persistSecret("brda-signing-private");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(
            new DecryptResponse()
                .encodePlaintext(KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded()))
        .thenReturn(
            new DecryptResponse().encodePlaintext(KmsTestHelper.getPrivateKeyring().getEncoded()));

    PGPKeyPair brdaSigningKey = keyring.getBrdaSigningKey();

    verify(kmsCryptoKeys, times(2)).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getAllValues())
        .isEqualTo(
            ImmutableList.of(
                "projects/foo/locations/global/keyRings/bar/cryptoKeys/brda-signing-public",
                "projects/foo/locations/global/keyRings/bar/cryptoKeys/brda-signing-private"));
    assertThat(decryptRequest.getAllValues())
        .isEqualTo(
            ImmutableList.of(
                new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE),
                new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE)));
    assertThat(brdaSigningKey.getKeyID())
        .isEqualTo(KmsTestHelper.getPrivateKeyring().getPublicKey().getKeyID());
  }

  @Test
  public void test_getBrdaReceiverKey() throws Exception {
    persistSecret("brda-receiver-public");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(
            new DecryptResponse()
                .encodePlaintext(KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded()));

    PGPPublicKey brdaReceiverKey = keyring.getBrdaReceiverKey();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/brda-receiver-public");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(brdaReceiverKey.getFingerprint())
        .isEqualTo(KmsTestHelper.getPublicKeyring().getPublicKey().getFingerprint());
  }

  @Test
  public void test_getRdeSshClientPublicKey() throws Exception {
    persistSecret("rde-ssh-client-public");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(new DecryptResponse().encodePlaintext(KmsTestHelper.DUMMY_KEY.getBytes(UTF_8)));

    String rdeSshClientPublicKey = keyring.getRdeSshClientPublicKey();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-ssh-client-public");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(rdeSshClientPublicKey).isEqualTo(KmsTestHelper.DUMMY_KEY);
  }

  @Test
  public void test_getRdeSshClientPrivateKey() throws Exception {
    persistSecret("rde-ssh-client-private");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(new DecryptResponse().encodePlaintext(KmsTestHelper.DUMMY_KEY.getBytes(UTF_8)));

    String rdeSshClientPrivateKey = keyring.getRdeSshClientPrivateKey();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-ssh-client-private");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(rdeSshClientPrivateKey).isEqualTo(KmsTestHelper.DUMMY_KEY);
  }

  @Test
  public void test_getIcannReportingPassword() throws Exception {
    persistSecret("icann-reporting-password");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(new DecryptResponse().encodePlaintext("icann123".getBytes(UTF_8)));

    String icannReportingPassword = keyring.getIcannReportingPassword();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo(
            "projects/foo/locations/global/keyRings/bar/cryptoKeys/icann-reporting-password");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(icannReportingPassword).isEqualTo("icann123");
  }

  @Test
  public void test_getMarksdbDnlLogin() throws Exception {
    persistSecret("marksdb-dnl-login");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(new DecryptResponse().encodePlaintext(KmsTestHelper.DUMMY_KEY.getBytes(UTF_8)));

    String marksdbDnlLogin = keyring.getMarksdbDnlLogin();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/marksdb-dnl-login");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(marksdbDnlLogin).isEqualTo(KmsTestHelper.DUMMY_KEY);
  }

  @Test
  public void test_getMarksdbLordnPassword() throws Exception {
    persistSecret("marksdb-lordn-password");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(new DecryptResponse().encodePlaintext(KmsTestHelper.DUMMY_KEY.getBytes(UTF_8)));

    String marksdbLordnPassword = keyring.getMarksdbLordnPassword();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/marksdb-lordn-password");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(marksdbLordnPassword).isEqualTo(KmsTestHelper.DUMMY_KEY);
  }

  @Test
  public void test_getMarksdbSmdrlLogin() throws Exception {
    persistSecret("marksdb-smdrl-login");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(new DecryptResponse().encodePlaintext(KmsTestHelper.DUMMY_KEY.getBytes(UTF_8)));

    String marksdbSmdrlLogin = keyring.getMarksdbSmdrlLogin();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/marksdb-smdrl-login");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(marksdbSmdrlLogin).isEqualTo(KmsTestHelper.DUMMY_KEY);
  }

  @Test
  public void test_getJsonCredential() throws Exception {
    persistSecret("json-credential");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(new DecryptResponse().encodePlaintext(KmsTestHelper.DUMMY_KEY.getBytes(UTF_8)));

    String jsonCredential = keyring.getJsonCredential();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/json-credential");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(jsonCredential).isEqualTo(KmsTestHelper.DUMMY_KEY);
  }

  @Test
  public void test_getBraintreePrivateKey() throws Exception {
    persistSecret("braintree-private-key");
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(new DecryptResponse().encodePlaintext(KmsTestHelper.DUMMY_KEY.getBytes(UTF_8)));

    String braintreePrivateKey = keyring.getBraintreePrivateKey();

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/braintree-private-key");
    assertThat(decryptRequest.getValue())
        .isEqualTo(new DecryptRequest().setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));
    assertThat(braintreePrivateKey).isEqualTo(KmsTestHelper.DUMMY_KEY);
  }

  private static void persistSecret(String secretName) {
    KmsSecretRevision secretRevision =
        new Builder()
            .setEncryptedValue(KmsTestHelper.DUMMY_ENCRYPTED_VALUE)
            .setKmsCryptoKeyVersionName(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION)
            .setParent(secretName)
            .build();
    KmsSecret secret = KmsSecret.create(secretName, secretRevision);
    persistResources(ImmutableList.of(secretRevision, secret));
  }
}
