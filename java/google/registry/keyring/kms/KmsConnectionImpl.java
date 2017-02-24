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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.services.cloudkms.v1beta1.CloudKMS;
import com.google.api.services.cloudkms.v1beta1.model.CryptoKey;
import com.google.api.services.cloudkms.v1beta1.model.CryptoKeyVersion;
import com.google.api.services.cloudkms.v1beta1.model.DecryptRequest;
import com.google.api.services.cloudkms.v1beta1.model.EncryptRequest;
import com.google.api.services.cloudkms.v1beta1.model.KeyRing;
import google.registry.config.RegistryConfig.Config;
import google.registry.keyring.api.KeyringException;
import java.io.IOException;
import javax.inject.Inject;

/** The {@link KmsConnection} which talks to Cloud KMS. */
class KmsConnectionImpl implements KmsConnection {

  private static final String KMS_KEYRING_NAME_FORMAT = "projects/%s/locations/global/keyRings/%s";
  private static final String KMS_CRYPTO_KEY_NAME_FORMAT =
      "projects/%s/locations/global/keyRings/%s/cryptoKeys/%s";
  private static final String KMS_CRYPTO_KEY_VERSION_NAME_FORMAT =
      "projects/%s/locations/global/keyRings/%s/cryptoKeys/%s/cryptoKeyVersions";

  private final CloudKMS kms;
  private final String kmsKeyRingName;
  private final String projectId;

  @Inject
  KmsConnectionImpl(
      @Config("cloudKmsProjectId") String projectId,
      @Config("cloudKmsKeyRing") String kmsKeyringName,
      CloudKMS kms) {
    this.projectId = projectId;
    this.kmsKeyRingName = kmsKeyringName;
    this.kms = kms;
  }

  @Override
  public EncryptResponse encrypt(String cryptoKeyName, byte[] value) throws IOException {
    checkArgument(
        value.length <= MAX_SECRET_SIZE_BYTES,
        "Value to encrypt was larger than %s bytes",
        MAX_SECRET_SIZE_BYTES);
    String fullKeyRingName = getKeyRingName(projectId, kmsKeyRingName);
    try {
      kms.projects().locations().keyRings().get(fullKeyRingName).execute();
    } catch (GoogleJsonResponseException jsonException) {
      if (jsonException.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        // Create the KeyRing in the "global" namespace. Encryption keys will be accessible from all
        // GCP regions.
        kms.projects()
            .locations()
            .keyRings()
            .create("global", new KeyRing().setName(fullKeyRingName))
            .execute();
      } else {
        throw jsonException;
      }
    }

    String fullKeyName = getCryptoKeyName(projectId, kmsKeyRingName, cryptoKeyName);

    try {
      kms.projects().locations().keyRings().cryptoKeys().get(fullKeyName).execute();
    } catch (GoogleJsonResponseException jsonException) {
      if (jsonException.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        kms.projects()
            .locations()
            .keyRings()
            .cryptoKeys()
            .create(
                fullKeyName, new CryptoKey().setName(cryptoKeyName).setPurpose("ENCRYPT_DECRYPT"))
            .execute();
      } else {
        throw jsonException;
      }
    }

    CryptoKeyVersion cryptoKeyVersion =
        kms.projects()
            .locations()
            .keyRings()
            .cryptoKeys()
            .cryptoKeyVersions()
            .create(
                getCryptoKeyVersionName(projectId, kmsKeyRingName, cryptoKeyName),
                new CryptoKeyVersion())
            .execute();

    return EncryptResponse.create(
        kms.projects()
            .locations()
            .keyRings()
            .cryptoKeys()
            .encrypt(cryptoKeyVersion.getName(), new EncryptRequest().encodePlaintext(value))
            .execute());
  }

  @Override
  public byte[] decrypt(String cryptoKeyName, String encodedCiphertext) {
    try {
      return kms.projects()
          .locations()
          .keyRings()
          .cryptoKeys()
          .decrypt(
              getCryptoKeyName(projectId, kmsKeyRingName, cryptoKeyName),
              new DecryptRequest().setCiphertext(encodedCiphertext))
          .execute()
          .decodePlaintext();
    } catch (IOException e) {
      throw new KeyringException(
          String.format("CloudKMS decrypt operation failed for secret %s", cryptoKeyName), e);
    }
  }

  static String getKeyRingName(String projectId, String kmsKeyRingName) {
    return String.format(KMS_KEYRING_NAME_FORMAT, projectId, kmsKeyRingName);
  }

  static String getCryptoKeyName(String projectId, String kmsKeyRingName, String cryptoKeyName) {
    return String.format(KMS_CRYPTO_KEY_NAME_FORMAT, projectId, kmsKeyRingName, cryptoKeyName);
  }

  static String getCryptoKeyVersionName(
      String projectId, String kmsKeyRingName, String cryptoKeyName) {
    return String.format(
        KMS_CRYPTO_KEY_VERSION_NAME_FORMAT, projectId, kmsKeyRingName, cryptoKeyName);
  }
}
