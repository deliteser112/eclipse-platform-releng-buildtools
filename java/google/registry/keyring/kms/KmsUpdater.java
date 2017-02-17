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
import static com.google.common.base.Preconditions.checkState;
import static google.registry.keyring.kms.KmsKeyring.BRAINTREE_PRIVATE_KEY_NAME;
import static google.registry.keyring.kms.KmsKeyring.BRDA_RECEIVER_PUBLIC_NAME;
import static google.registry.keyring.kms.KmsKeyring.BRDA_SIGNING_PRIVATE_NAME;
import static google.registry.keyring.kms.KmsKeyring.BRDA_SIGNING_PUBLIC_NAME;
import static google.registry.keyring.kms.KmsKeyring.ICANN_REPORTING_PASSWORD_NAME;
import static google.registry.keyring.kms.KmsKeyring.JSON_CREDENTIAL_NAME;
import static google.registry.keyring.kms.KmsKeyring.MARKSDB_DNL_LOGIN_NAME;
import static google.registry.keyring.kms.KmsKeyring.MARKSDB_LORDN_PASSWORD_NAME;
import static google.registry.keyring.kms.KmsKeyring.MARKSDB_SMDRL_LOGIN_NAME;
import static google.registry.keyring.kms.KmsKeyring.RDE_RECEIVER_PUBLIC_NAME;
import static google.registry.keyring.kms.KmsKeyring.RDE_SIGNING_PRIVATE_NAME;
import static google.registry.keyring.kms.KmsKeyring.RDE_SIGNING_PUBLIC_NAME;
import static google.registry.keyring.kms.KmsKeyring.RDE_SSH_CLIENT_PRIVATE_NAME;
import static google.registry.keyring.kms.KmsKeyring.RDE_SSH_CLIENT_PUBLIC_NAME;
import static google.registry.keyring.kms.KmsKeyring.RDE_STAGING_PRIVATE_NAME;
import static google.registry.keyring.kms.KmsKeyring.RDE_STAGING_PUBLIC_NAME;
import static google.registry.keyring.kms.KmsKeyring.getCryptoKeyName;
import static google.registry.keyring.kms.KmsKeyring.getCryptoKeyVersionName;
import static google.registry.keyring.kms.KmsKeyring.getKeyRingName;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudkms.v1beta1.CloudKMS;
import com.google.api.services.cloudkms.v1beta1.model.CryptoKey;
import com.google.api.services.cloudkms.v1beta1.model.CryptoKeyVersion;
import com.google.api.services.cloudkms.v1beta1.model.EncryptRequest;
import com.google.api.services.cloudkms.v1beta1.model.EncryptResponse;
import com.google.api.services.cloudkms.v1beta1.model.KeyRing;
import com.google.common.collect.ImmutableMap;
import com.googlecode.objectify.VoidWork;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.server.KmsSecret;
import google.registry.model.server.KmsSecretRevision;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRing;

/**
 * The {@link KmsUpdater} accumulates updates to a {@link KmsKeyring} and persists them to KMS and
 * Datastore when closed.
 */
public final class KmsUpdater {

  private static final int RESOURCE_NOT_FOUND = 404;

  private final String projectId;
  private final String kmsKeyRingName;
  private final CloudKMS kms;

  private final HashMap<String, byte[]> secretValues;

  @Inject
  public KmsUpdater(
      @Config("cloudKmsProjectId") String projectId,
      @Config("cloudKmsKeyRing") String kmsKeyRingName,
      CloudKMS kms) {
    this.projectId = projectId;
    this.kmsKeyRingName = kmsKeyRingName;
    this.kms = kms;

    // Use LinkedHashMap to preserve insertion order on update() to simplify testing and debugging
    this.secretValues = new LinkedHashMap<>();
  }

  public KmsUpdater setRdeSigningKey(BcPGPSecretKeyRing secretKeyRing) throws IOException {
    checkArgumentNotNull(secretKeyRing);
    setSecret(RDE_SIGNING_PRIVATE_NAME, checkArgumentNotNull(secretKeyRing).getEncoded());
    setSecret(RDE_SIGNING_PUBLIC_NAME, secretKeyRing.getPublicKey().getEncoded());
    return this;
  }

  public KmsUpdater setRdeStagingKey(BcPGPSecretKeyRing secretKeyRing) throws IOException {
    checkArgumentNotNull(secretKeyRing);

    setSecret(RDE_STAGING_PRIVATE_NAME, secretKeyRing.getEncoded());
    setSecret(RDE_STAGING_PUBLIC_NAME, secretKeyRing.getPublicKey().getEncoded());
    return this;
  }

  public KmsUpdater setRdeReceiverPublicKey(PGPPublicKey rdeReceiverPublicKey) throws IOException {
    setSecret(RDE_RECEIVER_PUBLIC_NAME, checkArgumentNotNull(rdeReceiverPublicKey).getEncoded());
    return this;
  }

  public KmsUpdater setBrdaSigningKey(BcPGPSecretKeyRing secretKeyRing) throws IOException {
    checkArgumentNotNull(secretKeyRing);
    setSecret(BRDA_SIGNING_PRIVATE_NAME, secretKeyRing.getEncoded());
    setSecret(BRDA_SIGNING_PUBLIC_NAME, secretKeyRing.getPublicKey().getEncoded());
    return this;
  }

  public KmsUpdater setBrdaReceiverPublicKey(PGPPublicKey publicKey) throws IOException {
    setSecret(BRDA_RECEIVER_PUBLIC_NAME, checkArgumentNotNull(publicKey).getEncoded());
    return this;
  }

  public KmsUpdater setRdeSshClientPublicKey(String asciiPublicKey) {
    setSecret(RDE_SSH_CLIENT_PUBLIC_NAME, checkArgumentNotNull(asciiPublicKey).getBytes(UTF_8));
    return this;
  }

  public KmsUpdater setRdeSshClientPrivateKey(String asciiPrivateKey) {
    setSecret(RDE_SSH_CLIENT_PRIVATE_NAME, checkArgumentNotNull(asciiPrivateKey).getBytes(UTF_8));
    return this;
  }

  public KmsUpdater setIcannReportingPassword(String password) {
    setSecret(ICANN_REPORTING_PASSWORD_NAME, checkArgumentNotNull(password).getBytes(UTF_8));
    return this;
  }

  public KmsUpdater setMarksdbDnlLogin(String login) {
    setSecret(MARKSDB_DNL_LOGIN_NAME, checkArgumentNotNull(login).getBytes(UTF_8));
    return this;
  }

  public KmsUpdater setMarksdbLordnPassword(String password) {
    setSecret(MARKSDB_LORDN_PASSWORD_NAME, checkArgumentNotNull(password).getBytes(UTF_8));
    return this;
  }

  public KmsUpdater setMarksdbSmdrlLogin(String login) {
    setSecret(MARKSDB_SMDRL_LOGIN_NAME, checkArgumentNotNull(login).getBytes(UTF_8));
    return this;
  }

  public KmsUpdater setJsonCredential(String credential) {
    setSecret(JSON_CREDENTIAL_NAME, checkArgumentNotNull(credential).getBytes(UTF_8));
    return this;
  }

  public KmsUpdater setBraintreePrivateKey(String braintreePrivateKey) {
    setSecret(
        BRAINTREE_PRIVATE_KEY_NAME, checkArgumentNotNull(braintreePrivateKey).getBytes(UTF_8));
    return this;
  }

  /**
   * Generates new encryption keys in KMS, encrypts the updated secrets with them, and persists the
   * encrypted secrets to Datastore.
   *
   * <p>The operations in this method are organized so that existing {@link KmsSecretRevision}
   * entities remain primary and decryptable if a failure occurs.
   */
  public void update() throws IOException {
    checkState(!secretValues.isEmpty(), "At least one Keyring value must be persisted");

    persistEncryptedValues(encryptValues(secretValues));
  }

  /**
   * Encrypts updated secrets using KMS. If the configured {@code KeyRing} or {@code CryptoKey}
   * associated with a secret doesn't exist, they will first be created.
   *
   * @see google.registry.config.RegistryConfigSettings#kms
   */
  private ImmutableMap<String, EncryptResponse> encryptValues(Map<String, byte[]> keyValues)
      throws IOException {
    String fullKeyRingName = getKeyRingName(projectId, kmsKeyRingName);
    try {
      kms.projects().locations().keyRings().get(fullKeyRingName).execute();
    } catch (GoogleJsonResponseException jsonException) {
      if (jsonException.getStatusCode() == RESOURCE_NOT_FOUND) {
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

    ImmutableMap.Builder<String, EncryptResponse> encryptedValues = new ImmutableMap.Builder<>();
    for (Map.Entry<String, byte[]> entry : keyValues.entrySet()) {
      String keyName = entry.getKey();
      String fullKeyName = getCryptoKeyName(projectId, kmsKeyRingName, keyName);

      try {
        kms.projects().locations().keyRings().cryptoKeys().get(fullKeyName).execute();
      } catch (GoogleJsonResponseException jsonException) {
        if (jsonException.getStatusCode() == RESOURCE_NOT_FOUND) {
          kms.projects()
              .locations()
              .keyRings()
              .cryptoKeys()
              .create(fullKeyName, new CryptoKey().setName(keyName).setPurpose("ENCRYPT_DECRYPT"))
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
                  getCryptoKeyVersionName(projectId, kmsKeyRingName, keyName),
                  new CryptoKeyVersion())
              .execute();

      encryptedValues.put(
          keyName,
          kms.projects()
              .locations()
              .keyRings()
              .cryptoKeys()
              .encrypt(
                  cryptoKeyVersion.getName(),
                  new EncryptRequest().encodePlaintext(entry.getValue()))
              .execute());
    }
    return encryptedValues.build();
  }

  /**
   * Persists encrypted secrets to Datastore as {@link KmsSecretRevision} entities and makes them
   * primary. {@link KmsSecret} entities point to the latest {@link KmsSecretRevision}.
   *
   * <p>The changes are committed transactionally; if an error occurs, all existing {@link
   * KmsSecretRevision} entities will remain primary.
   */
  private static void persistEncryptedValues(
      final ImmutableMap<String, EncryptResponse> encryptedValues) {
    ofy()
        .transact(
            new VoidWork() {
              @Override
              public void vrun() {
                for (Map.Entry<String, EncryptResponse> entry : encryptedValues.entrySet()) {
                  String secretName = entry.getKey();
                  EncryptResponse revisionData = entry.getValue();

                  KmsSecretRevision secretRevision =
                      new KmsSecretRevision.Builder()
                          .setEncryptedValue(revisionData.getCiphertext())
                          .setKmsCryptoKeyVersionName(revisionData.getName())
                          .setParent(secretName)
                          .build();
                  ofy()
                      .save()
                      .entities(secretRevision, KmsSecret.create(secretName, secretRevision));
                }
              }
            });
  }

  private void setSecret(String secretName, byte[] value) {
    checkArgument(!secretValues.containsKey(secretName), "Attempted to set %s twice", secretName);
    secretValues.put(secretName, value);
  }
}
