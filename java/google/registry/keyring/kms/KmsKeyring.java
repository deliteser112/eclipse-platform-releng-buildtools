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

import static com.google.common.base.Preconditions.checkState;
import static google.registry.keyring.api.PgpHelper.KeyRequirement.ENCRYPT;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.googlecode.objectify.Key;
import google.registry.keyring.api.Keyring;
import google.registry.keyring.api.KeyringException;
import google.registry.keyring.api.PgpHelper;
import google.registry.model.server.KmsSecret;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Inject;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRing;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRing;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;

/**
 * A {@link Keyring} implementation which stores encrypted secrets in Datastore and decrypts them
 * using encryption keys stored in Cloud KMS.
 *
 * @see <a href="https://cloud.google.com/kms/docs/">Google Cloud Key Management Service
 *     Documentation</a>
 */
public class KmsKeyring implements Keyring {

  static final String BRAINTREE_PRIVATE_KEY_NAME = "braintree-private-key";
  static final String BRDA_RECEIVER_PUBLIC_NAME = "brda-receiver-public";
  static final String BRDA_SIGNING_PRIVATE_NAME = "brda-signing-private";
  static final String BRDA_SIGNING_PUBLIC_NAME = "brda-signing-public";
  static final String ICANN_REPORTING_PASSWORD_NAME = "icann-reporting-password";
  static final String JSON_CREDENTIAL_NAME = "json-credential";
  static final String MARKSDB_DNL_LOGIN_NAME = "marksdb-dnl-login";
  static final String MARKSDB_LORDN_PASSWORD_NAME = "marksdb-lordn-password";
  static final String MARKSDB_SMDRL_LOGIN_NAME = "marksdb-smdrl-login";
  static final String RDE_RECEIVER_PUBLIC_NAME = "rde-receiver-public";
  static final String RDE_SIGNING_PRIVATE_NAME = "rde-signing-private";
  static final String RDE_SIGNING_PUBLIC_NAME = "rde-signing-public";
  static final String RDE_SSH_CLIENT_PRIVATE_NAME = "rde-ssh-client-private";
  static final String RDE_SSH_CLIENT_PUBLIC_NAME = "rde-ssh-client-public";
  static final String RDE_STAGING_PRIVATE_NAME = "rde-staging-private";
  static final String RDE_STAGING_PUBLIC_NAME = "rde-staging-public";

  private final KmsConnection kmsConnection;

  @Inject
  KmsKeyring(KmsConnection kmsConnection) {
    this.kmsConnection = kmsConnection;
  }

  @Override
  public PGPKeyPair getRdeSigningKey() {
    return getKeyPair(RDE_SIGNING_PUBLIC_NAME, RDE_SIGNING_PRIVATE_NAME);
  }

  @Override
  public PGPPublicKey getRdeStagingEncryptionKey() {
    return getPublicKeyForEncrypting(RDE_STAGING_PUBLIC_NAME);
  }

  @Override
  public PGPPrivateKey getRdeStagingDecryptionKey() {
    return getPrivateKey(RDE_STAGING_PRIVATE_NAME);
  }

  @Override
  public PGPPublicKey getRdeReceiverKey() {
    return getPublicKeyForEncrypting(RDE_RECEIVER_PUBLIC_NAME);
  }

  @Override
  public PGPKeyPair getBrdaSigningKey() {
    return getKeyPair(BRDA_SIGNING_PUBLIC_NAME, BRDA_SIGNING_PRIVATE_NAME);
  }

  @Override
  public PGPPublicKey getBrdaReceiverKey() {
    return getPublicKeyForEncrypting(BRDA_RECEIVER_PUBLIC_NAME);
  }

  @Override
  public String getRdeSshClientPublicKey() {
    return new String(getDecryptedData((RDE_SSH_CLIENT_PUBLIC_NAME)), UTF_8);
  }

  @Override
  public String getRdeSshClientPrivateKey() {
    return new String(getDecryptedData(RDE_SSH_CLIENT_PRIVATE_NAME), UTF_8);
  }

  @Override
  public String getIcannReportingPassword() {
    return new String(getDecryptedData(ICANN_REPORTING_PASSWORD_NAME), UTF_8);
  }

  @Override
  public String getMarksdbDnlLogin() {
    return new String(getDecryptedData(MARKSDB_DNL_LOGIN_NAME), UTF_8);
  }

  @Override
  public String getMarksdbLordnPassword() {
    return new String(getDecryptedData(MARKSDB_LORDN_PASSWORD_NAME), UTF_8);
  }

  @Override
  public String getMarksdbSmdrlLogin() {
    return new String(getDecryptedData(MARKSDB_SMDRL_LOGIN_NAME), UTF_8);
  }

  @Override
  public String getJsonCredential() {
    return new String(getDecryptedData(JSON_CREDENTIAL_NAME), UTF_8);
  }

  @Override
  public String getBraintreePrivateKey() {
    return new String(getDecryptedData(BRAINTREE_PRIVATE_KEY_NAME), UTF_8);
  }

  /** No persistent resources are maintained for this Keyring implementation. */
  @Override
  public void close() {}

  private PGPKeyPair getKeyPair(String publicKeyName, String privateKeyName) {
    try {
      PGPPublicKey publicKey =
          new BcPGPPublicKeyRing(getPgpInputStream(publicKeyName)).getPublicKey();
      return new PGPKeyPair(publicKey, getPrivateKey(privateKeyName));
    } catch (IOException e) {
      throw new KeyringException(
          String.format(
              "Could not parse public key %s and private key %s", publicKeyName, privateKeyName),
          e);
    }
  }

  private PGPPublicKey getPublicKeyForEncrypting(String publicKeyName) {
    try {
      return PgpHelper.lookupPublicSubkey(
              new BcPGPPublicKeyRing(getPgpInputStream(publicKeyName)), ENCRYPT)
          .get();
    } catch (IOException e) {
      throw new KeyringException(String.format("Could not parse public key %s", publicKeyName), e);
    }
  }

  private PGPPrivateKey getPrivateKey(String privateKeyName) {
    try {
      PGPSecretKeyRing privateKeyRing = new BcPGPSecretKeyRing(getPgpInputStream(privateKeyName));
      // There shouldn't be a passphrase on the key
      return privateKeyRing
          .getSecretKey()
          .extractPrivateKey(
              new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider())
                  .build(new char[0]));
    } catch (IOException | PGPException e) {
      throw new KeyringException(
          String.format("Could not parse private key %s", privateKeyName), e);
    }
  }

  private InputStream getPgpInputStream(String privateKeyName) throws IOException {
    return PGPUtil.getDecoderStream(new ByteArrayInputStream(getDecryptedData(privateKeyName)));
  }

  private byte[] getDecryptedData(String keyName) {
    KmsSecret secret =
        ofy().load().key(Key.create(getCrossTldKey(), KmsSecret.class, keyName)).now();
    checkState(secret != null, "Requested secret '%s' does not exist.", keyName);
    String encryptedData = ofy().load().key(secret.getLatestRevision()).now().getEncryptedValue();

    try {
      return kmsConnection.decrypt(secret.getName(), encryptedData);
    } catch (IOException e) {
      throw new KeyringException(
          String.format("CloudKMS decrypt operation failed for secret %s", keyName), e);
    }
  }
}
