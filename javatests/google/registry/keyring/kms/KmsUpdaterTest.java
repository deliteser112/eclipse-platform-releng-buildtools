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
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.cloudkms.v1beta1.CloudKMS;
import com.google.api.services.cloudkms.v1beta1.model.CryptoKey;
import com.google.api.services.cloudkms.v1beta1.model.CryptoKeyVersion;
import com.google.api.services.cloudkms.v1beta1.model.EncryptRequest;
import com.google.api.services.cloudkms.v1beta1.model.EncryptResponse;
import com.google.api.services.cloudkms.v1beta1.model.KeyRing;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import google.registry.model.server.KmsSecret;
import google.registry.model.server.KmsSecretRevision;
import google.registry.testing.AppEngineRule;
import java.io.ByteArrayInputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KmsUpdaterTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Mock private CloudKMS kms;
  @Mock private CloudKMS.Projects kmsProjects;
  @Mock private CloudKMS.Projects.Locations kmsLocations;
  @Mock private CloudKMS.Projects.Locations.KeyRings kmsKeyRings;
  @Mock private CloudKMS.Projects.Locations.KeyRings.Get kmsKeyRingsGet;
  @Mock private CloudKMS.Projects.Locations.KeyRings.Create kmsKeyRingsCreate;
  @Mock private CloudKMS.Projects.Locations.KeyRings.CryptoKeys kmsCryptoKeys;
  @Mock private CloudKMS.Projects.Locations.KeyRings.CryptoKeys.Get kmsCryptoKeysGet;
  @Mock private CloudKMS.Projects.Locations.KeyRings.CryptoKeys.Create kmsCryptoKeysCreate;

  @Mock
  private CloudKMS.Projects.Locations.KeyRings.CryptoKeys.CryptoKeyVersions kmsCryptoKeyVersions;

  @Mock
  private CloudKMS.Projects.Locations.KeyRings.CryptoKeys.CryptoKeyVersions.Create
      kmsCryptoKeyVersionsCreate;

  @Mock private CloudKMS.Projects.Locations.KeyRings.CryptoKeys.Encrypt kmsCryptoKeysEncrypt;

  @Captor private ArgumentCaptor<KeyRing> keyRing;
  @Captor private ArgumentCaptor<CryptoKey> cryptoKey;
  @Captor private ArgumentCaptor<CryptoKeyVersion> cryptoKeyVersion;
  @Captor private ArgumentCaptor<String> keyRingName;
  @Captor private ArgumentCaptor<String> cryptoKeyName;
  @Captor private ArgumentCaptor<String> cryptoKeyVersionName;
  @Captor private ArgumentCaptor<EncryptRequest> encryptRequest;

  private KmsUpdater updater;

  @Before
  public void setUp() throws Exception {
    when(kms.projects()).thenReturn(kmsProjects);
    when(kmsProjects.locations()).thenReturn(kmsLocations);
    when(kmsLocations.keyRings()).thenReturn(kmsKeyRings);
    when(kmsKeyRings.get(anyString())).thenReturn(kmsKeyRingsGet);
    when(kmsKeyRings.create(anyString(), any(KeyRing.class))).thenReturn(kmsKeyRingsCreate);
    when(kmsKeyRings.cryptoKeys()).thenReturn(kmsCryptoKeys);
    when(kmsCryptoKeys.get(anyString())).thenReturn(kmsCryptoKeysGet);
    when(kmsCryptoKeys.create(anyString(), any(CryptoKey.class))).thenReturn(kmsCryptoKeysCreate);
    when(kmsCryptoKeys.cryptoKeyVersions()).thenReturn(kmsCryptoKeyVersions);
    when(kmsCryptoKeyVersions.create(anyString(), any(CryptoKeyVersion.class)))
        .thenReturn(kmsCryptoKeyVersionsCreate);
    when(kmsCryptoKeyVersionsCreate.execute())
        .thenReturn(new CryptoKeyVersion().setName(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION));
    when(kmsCryptoKeys.encrypt(anyString(), any(EncryptRequest.class)))
        .thenReturn(kmsCryptoKeysEncrypt);
    when(kmsCryptoKeysEncrypt.execute())
        .thenReturn(
            new EncryptResponse()
                .setName(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION)
                .setCiphertext(KmsTestHelper.DUMMY_ENCRYPTED_VALUE));

    updater = new KmsUpdater("foo", "bar", kms);
  }

  @Test
  public void test_close_createsNewKeyRing_ifNotFound() throws Exception {
    when(kmsKeyRingsGet.execute()).thenThrow(createNotFoundException());

    updater.setBraintreePrivateKey(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verify(kmsKeyRings).create(keyRingName.capture(), keyRing.capture());
    assertThat(keyRingName.getValue()).isEqualTo("global");
    assertThat(keyRing.getValue())
        .isEqualTo(new KeyRing().setName("projects/foo/locations/global/keyRings/bar"));
    verify(kmsKeyRingsCreate).execute();
  }

  @Test
  public void test_close_createsNewCryptoKey_ifNotFound() throws Exception {
    when(kmsCryptoKeysGet.execute()).thenThrow(createNotFoundException());

    updater.setBraintreePrivateKey(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verify(kmsCryptoKeys).create(cryptoKeyName.capture(), cryptoKey.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/braintree-private-key");
    assertThat(cryptoKey.getValue())
        .isEqualTo(new CryptoKey().setName("braintree-private-key").setPurpose("ENCRYPT_DECRYPT"));
    verify(kmsCryptoKeysCreate).execute();
  }

  @Test
  public void test_setMultipleSecrets() throws Exception {
    updater
        .setBraintreePrivateKey("value1")
        .setIcannReportingPassword("value2")
        .setJsonCredential("value3");
    updater.update();

    verify(kmsCryptoKeys, times(3)).get(cryptoKeyName.capture());
    assertThat(cryptoKeyName.getAllValues())
        .isEqualTo(
            ImmutableList.of(
                "projects/foo/locations/" + "global/keyRings/bar/cryptoKeys/braintree-private-key",
                "projects/foo/locations/"
                    + "global/keyRings/bar/cryptoKeys/icann-reporting-password",
                "projects/foo/locations/" + "global/keyRings/bar/cryptoKeys/json-credential"));

    verify(kmsCryptoKeyVersions, times(3))
        .create(cryptoKeyVersionName.capture(), cryptoKeyVersion.capture());
    assertThat(cryptoKeyVersionName.getAllValues())
        .isEqualTo(
            ImmutableList.of(
                "projects/foo/locations/"
                    + "global/keyRings/bar/cryptoKeys/braintree-private-key/cryptoKeyVersions",
                "projects/foo/locations/"
                    + "global/keyRings/bar/cryptoKeys/icann-reporting-password/cryptoKeyVersions",
                "projects/foo/locations/"
                    + "global/keyRings/bar/cryptoKeys/json-credential/cryptoKeyVersions"));

    verify(kmsCryptoKeys, times(3)).encrypt(cryptoKeyName.capture(), encryptRequest.capture());
    assertThat(cryptoKeyName.getValue()).isEqualTo(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION);
    assertThat(encryptRequest.getAllValues())
        .isEqualTo(
            ImmutableList.of(
                new EncryptRequest().encodePlaintext("value1".getBytes(UTF_8)),
                new EncryptRequest().encodePlaintext("value2".getBytes(UTF_8)),
                new EncryptRequest().encodePlaintext("value3".getBytes(UTF_8))));

    KmsSecret firstSecret = loadSecret("braintree-private-key");
    assertThat(firstSecret).isNotNull();
    assertThat(firstSecret.getLatestRevision()).isNotNull();

    KmsSecret secondSecret = loadSecret("icann-reporting-password");
    assertThat(secondSecret).isNotNull();
    assertThat(secondSecret.getLatestRevision()).isNotNull();

    KmsSecret thirdSecret = loadSecret("icann-reporting-password");
    assertThat(thirdSecret).isNotNull();
    assertThat(thirdSecret.getLatestRevision()).isNotNull();
  }

  @Test
  public void test_setBraintreePrivateKey() throws Exception {
    updater.setBraintreePrivateKey(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "braintree-private-key",
        KmsTestHelper.DUMMY_KEY.getBytes(UTF_8),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/braintree-private-key",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/braintree-private-key/cryptoKeyVersions");
  }

  @Test
  public void test_setBrdaReceiverKey() throws Exception {
    updater.setBrdaReceiverPublicKey(KmsTestHelper.getPublicKeyring().getPublicKey());
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "brda-receiver-public",
        KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded(),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/brda-receiver-public",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/brda-receiver-public/cryptoKeyVersions");
  }

  @Test
  public void test_setBrdaSigningKey() throws Exception {
    updater.setBrdaSigningKey(KmsTestHelper.getPrivateKeyring());
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "brda-signing-private",
        KmsTestHelper.getPrivateKeyring().getEncoded(),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/brda-signing-private",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/brda-signing-private/cryptoKeyVersions",
        "brda-signing-public",
        KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded(),
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/brda-signing-public",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/brda-signing-public/cryptoKeyVersions");
  }

  @Test
  public void test_setIcannReportingPassword() throws Exception {
    updater.setIcannReportingPassword(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "icann-reporting-password",
        KmsTestHelper.DUMMY_KEY.getBytes(UTF_8),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/icann-reporting-password",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/icann-reporting-password/cryptoKeyVersions");
  }

  @Test
  public void test_setJsonCredential() throws Exception {
    updater.setJsonCredential(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "json-credential",
        KmsTestHelper.DUMMY_KEY.getBytes(UTF_8),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/json-credential",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/json-credential/cryptoKeyVersions");
  }

  @Test
  public void test_setMarksdbDnlLogin() throws Exception {
    updater.setMarksdbDnlLogin(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "marksdb-dnl-login",
        KmsTestHelper.DUMMY_KEY.getBytes(UTF_8),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/marksdb-dnl-login",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/marksdb-dnl-login/cryptoKeyVersions");
  }

  @Test
  public void test_setMarksdbLordnPassword() throws Exception {
    updater.setMarksdbLordnPassword(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "marksdb-lordn-password",
        KmsTestHelper.DUMMY_KEY.getBytes(UTF_8),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/marksdb-lordn-password",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/marksdb-lordn-password/cryptoKeyVersions");
  }

  @Test
  public void test_setMarksdbSmdrlLogin() throws Exception {
    updater.setMarksdbSmdrlLogin(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "marksdb-smdrl-login",
        KmsTestHelper.DUMMY_KEY.getBytes(UTF_8),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/marksdb-smdrl-login",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/marksdb-smdrl-login/cryptoKeyVersions");
  }

  @Test
  public void test_setRdeReceiverKey() throws Exception {
    updater.setRdeReceiverPublicKey(KmsTestHelper.getPublicKeyring().getPublicKey());
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "rde-receiver-public",
        KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded(),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-receiver-public",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/rde-receiver-public/cryptoKeyVersions");
  }

  @Test
  public void test_setRdeSigningKey() throws Exception {
    updater.setRdeSigningKey(KmsTestHelper.getPrivateKeyring());
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "rde-signing-private",
        KmsTestHelper.getPrivateKeyring().getEncoded(),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-signing-private",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/rde-signing-private/cryptoKeyVersions",
        "rde-signing-public",
        KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded(),
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-signing-public",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/rde-signing-public/cryptoKeyVersions");
  }

  @Test
  public void test_setRdeSshClientPrivateKey() throws Exception {
    updater.setRdeSshClientPrivateKey(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "rde-ssh-client-private",
        KmsTestHelper.DUMMY_KEY.getBytes(UTF_8),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-ssh-client-private",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/rde-ssh-client-private/cryptoKeyVersions");
  }

  @Test
  public void test_setRdeSshClientPublicKey() throws Exception {
    updater.setRdeSshClientPublicKey(KmsTestHelper.DUMMY_KEY);
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "rde-ssh-client-public",
        KmsTestHelper.DUMMY_KEY.getBytes(UTF_8),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-ssh-client-public",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/rde-ssh-client-public/cryptoKeyVersions");
  }

  @Test
  public void test_setRdeStagingKey() throws Exception {
    updater.setRdeStagingKey(KmsTestHelper.getPrivateKeyring());
    updater.update();

    verifyKmsApiCallsAndDatastoreWrites(
        "rde-staging-private",
        KmsTestHelper.getPrivateKeyring().getEncoded(),
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-staging-private",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/rde-staging-private/cryptoKeyVersions",
        "rde-staging-public",
        KmsTestHelper.getPublicKeyring().getPublicKey().getEncoded(),
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/rde-staging-public",
        "projects/foo/locations/"
            + "global/keyRings/bar/cryptoKeys/rde-staging-public/cryptoKeyVersions");
  }

  private void verifyKmsApiCallsAndDatastoreWrites(
      String secretName,
      byte[] goldenValue,
      String goldenCryptoKeyRingName,
      String goldenCryptoKeyName,
      String goldenCryptoKeyVersionName)
      throws Exception {
    verify(kmsKeyRings).get(keyRingName.capture());
    assertThat(keyRingName.getValue()).isEqualTo(goldenCryptoKeyRingName);

    verify(kmsCryptoKeys).get(cryptoKeyName.capture());
    assertThat(cryptoKeyName.getValue()).isEqualTo(goldenCryptoKeyName);

    verify(kmsCryptoKeyVersions).create(cryptoKeyVersionName.capture(), cryptoKeyVersion.capture());
    assertThat(cryptoKeyVersionName.getValue()).isEqualTo(goldenCryptoKeyVersionName);

    verify(kmsCryptoKeys).encrypt(cryptoKeyName.capture(), encryptRequest.capture());
    assertThat(cryptoKeyName.getValue()).isEqualTo(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION);
    assertThat(encryptRequest.getValue())
        .isEqualTo(new EncryptRequest().encodePlaintext(goldenValue));

    KmsSecret secret = loadSecret(secretName);
    KmsSecretRevision secretRevision = ofy().load().key(secret.getLatestRevision()).now();
    assertThat(secretRevision.getKmsCryptoKeyVersionName())
        .isEqualTo(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION);
    assertThat(secretRevision.getEncryptedValue()).isEqualTo(KmsTestHelper.DUMMY_ENCRYPTED_VALUE);
  }

  /** Variant of {@code verifyKmsApiCallsAndDatastoreWrites} for key pairs. */
  private void verifyKmsApiCallsAndDatastoreWrites(
      String firstSecretName,
      byte[] firstGoldenValue,
      String goldenCryptoKeyRingName,
      String firstGoldenCryptoKeyName,
      String firstGoldenCryptoKeyVersionName,
      String secondSecretName,
      byte[] secondGoldenValue,
      String secondGoldenCryptoKeyName,
      String secondGoldenCryptoKeyVersionName)
      throws Exception {
    verify(kmsKeyRings, times(1)).get(keyRingName.capture());
    assertThat(keyRingName.getValue()).isEqualTo(goldenCryptoKeyRingName);

    verify(kmsCryptoKeys, times(2)).get(cryptoKeyName.capture());
    assertThat(cryptoKeyName.getAllValues())
        .isEqualTo(ImmutableList.of(firstGoldenCryptoKeyName, secondGoldenCryptoKeyName));

    verify(kmsCryptoKeyVersions, times(2))
        .create(cryptoKeyVersionName.capture(), cryptoKeyVersion.capture());
    assertThat(cryptoKeyVersionName.getAllValues())
        .isEqualTo(
            ImmutableList.of(firstGoldenCryptoKeyVersionName, secondGoldenCryptoKeyVersionName));

    verify(kmsCryptoKeys, times(2)).encrypt(cryptoKeyName.capture(), encryptRequest.capture());
    assertThat(cryptoKeyName.getValue()).isEqualTo(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION);
    assertThat(encryptRequest.getAllValues())
        .isEqualTo(
            ImmutableList.of(
                new EncryptRequest().encodePlaintext(firstGoldenValue),
                new EncryptRequest().encodePlaintext(secondGoldenValue)));

    KmsSecret secret = loadSecret(firstSecretName);
    KmsSecretRevision secretRevision = ofy().load().key(secret.getLatestRevision()).now();
    assertThat(secretRevision.getKmsCryptoKeyVersionName())
        .isEqualTo(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION);
    assertThat(secretRevision.getEncryptedValue()).isEqualTo(KmsTestHelper.DUMMY_ENCRYPTED_VALUE);

    KmsSecret secondSecret = loadSecret(secondSecretName);
    KmsSecretRevision secondSecretRevision =
        ofy().load().key(secondSecret.getLatestRevision()).now();
    assertThat(secondSecretRevision.getKmsCryptoKeyVersionName())
        .isEqualTo(KmsTestHelper.DUMMY_CRYPTO_KEY_VERSION);
    assertThat(secondSecretRevision.getEncryptedValue())
        .isEqualTo(KmsTestHelper.DUMMY_ENCRYPTED_VALUE);
  }

  private static GoogleJsonResponseException createNotFoundException() throws Exception {
    ByteArrayInputStream inputStream = new ByteArrayInputStream("".getBytes(UTF_8));
    HttpResponse response = GoogleJsonResponseExceptionHelper.createHttpResponse(404, inputStream);
    HttpResponseException.Builder httpResponseExceptionBuilder =
        new HttpResponseException.Builder(response);
    httpResponseExceptionBuilder.setStatusCode(404);
    httpResponseExceptionBuilder.setStatusMessage("NOT_FOUND");
    return new GoogleJsonResponseException(httpResponseExceptionBuilder, null);
  }

  private static KmsSecret loadSecret(String secret) {
    return ofy().load().key(Key.create(getCrossTldKey(), KmsSecret.class, secret)).now();
  }
}
