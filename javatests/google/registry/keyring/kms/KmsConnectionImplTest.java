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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.cloudkms.v1beta1.CloudKMS;
import com.google.api.services.cloudkms.v1beta1.model.CryptoKey;
import com.google.api.services.cloudkms.v1beta1.model.CryptoKeyVersion;
import com.google.api.services.cloudkms.v1beta1.model.DecryptRequest;
import com.google.api.services.cloudkms.v1beta1.model.DecryptResponse;
import com.google.api.services.cloudkms.v1beta1.model.EncryptRequest;
import com.google.api.services.cloudkms.v1beta1.model.EncryptResponse;
import com.google.api.services.cloudkms.v1beta1.model.KeyRing;
import java.io.ByteArrayInputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KmsConnectionImplTest {

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
  @Mock private CloudKMS.Projects.Locations.KeyRings.CryptoKeys.Decrypt kmsCryptoKeysDecrypt;

  @Captor private ArgumentCaptor<KeyRing> keyRing;
  @Captor private ArgumentCaptor<CryptoKey> cryptoKey;
  @Captor private ArgumentCaptor<CryptoKeyVersion> cryptoKeyVersion;
  @Captor private ArgumentCaptor<String> keyRingName;
  @Captor private ArgumentCaptor<String> cryptoKeyName;
  @Captor private ArgumentCaptor<String> cryptoKeyVersionName;
  @Captor private ArgumentCaptor<EncryptRequest> encryptRequest;
  @Captor private ArgumentCaptor<DecryptRequest> decryptRequest;

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
    when(kmsCryptoKeys.decrypt(anyString(), any(DecryptRequest.class)))
        .thenReturn(kmsCryptoKeysDecrypt);
  }

  @Test
  public void test_encrypt_createsKeyRingIfNotFound() throws Exception {
    when(kmsKeyRingsGet.execute()).thenThrow(createNotFoundException());

    new KmsConnectionImpl("foo", "bar", kms).encrypt("key", "moo".getBytes(UTF_8));

    verify(kmsKeyRings).create(keyRingName.capture(), keyRing.capture());
    assertThat(keyRingName.getValue()).isEqualTo("global");
    assertThat(keyRing.getValue())
        .isEqualTo(new KeyRing().setName("projects/foo/locations/global/keyRings/bar"));
    verify(kmsKeyRingsCreate).execute();
    verifyEncryptKmsApiCalls(
        "moo",
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/key",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/key/cryptoKeyVersions");
  }

  @Test
  public void test_encrypt_newCryptoKey() throws Exception {
    when(kmsCryptoKeysGet.execute()).thenThrow(createNotFoundException());

    new KmsConnectionImpl("foo", "bar", kms).encrypt("key", "moo".getBytes(UTF_8));

    verify(kmsCryptoKeys).create(cryptoKeyName.capture(), cryptoKey.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/key");
    assertThat(cryptoKey.getValue())
        .isEqualTo(new CryptoKey().setName("key").setPurpose("ENCRYPT_DECRYPT"));
    verify(kmsCryptoKeysCreate).execute();
    verifyEncryptKmsApiCalls(
        "moo",
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/key",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/key/cryptoKeyVersions");
  }

  @Test
  public void test_encrypt() throws Exception {
    new KmsConnectionImpl("foo", "bar", kms).encrypt("key", "moo".getBytes(UTF_8));

    verifyEncryptKmsApiCalls(
        "moo",
        "projects/foo/locations/global/keyRings/bar",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/key",
        "projects/foo/locations/global/keyRings/bar/cryptoKeys/key/cryptoKeyVersions");
  }

  @Test
  public void test_decrypt() throws Exception {
    when(kmsCryptoKeysDecrypt.execute())
        .thenReturn(new DecryptResponse().encodePlaintext("moo".getBytes(UTF_8)));

    byte[] plaintext = new KmsConnectionImpl("foo", "bar", kms).decrypt("key", "blah");

    verify(kmsCryptoKeys).decrypt(cryptoKeyName.capture(), decryptRequest.capture());
    assertThat(cryptoKeyName.getValue())
        .isEqualTo("projects/foo/locations/global/keyRings/bar/cryptoKeys/key");
    assertThat(decryptRequest.getValue()).isEqualTo(new DecryptRequest().setCiphertext("blah"));
    assertThat(plaintext).isEqualTo("moo".getBytes(UTF_8));
  }

  private void verifyEncryptKmsApiCalls(
      String goldenValue,
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
        .isEqualTo(new EncryptRequest().encodePlaintext(goldenValue.getBytes(UTF_8)));
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
}
