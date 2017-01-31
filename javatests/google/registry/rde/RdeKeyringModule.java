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

package google.registry.rde;

import static google.registry.keyring.api.PgpHelper.KeyRequirement.ENCRYPT;
import static google.registry.keyring.api.PgpHelper.KeyRequirement.SIGN;

import com.google.common.io.ByteSource;
import dagger.Module;
import dagger.Provides;
import google.registry.keyring.api.Keyring;
import google.registry.keyring.api.PgpHelper;
import google.registry.keyring.api.PgpHelper.KeyRequirement;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.concurrent.Immutable;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRingCollection;

/** Keyring factory that loads keys {@code javatests/.../rde/testdata} */
@Module
@Immutable
public final class RdeKeyringModule {

  static final String STAGING_KEY_EMAIL = "rde-unittest@registry.test";
  static final String SIGNING_KEY_EMAIL = "rde-unittest@registry.test";
  static final String RECEIVER_KEY_EMAIL = "rde-unittest@escrow.test";
  static final ByteSource PGP_PUBLIC_KEYRING = RdeTestData.get("pgp-public-keyring.asc");
  static final ByteSource PGP_PRIVATE_KEYRING = RdeTestData.get("pgp-private-keyring-registry.asc");
  static final String ICANN_REPORTING_PASSWORD = "yolo";
  static final String MARKSDB_DNL_LOGIN = "dnl:yolo";
  static final String MARKSDB_LORDN_PASSWORD = "yolo";
  static final String MARKSDB_SMDRL_LOGIN = "smdrl:yolo";

  @Provides
  public Keyring get() {
    PGPPublicKeyRingCollection publics;
    PGPSecretKeyRingCollection privates;
    try (InputStream publicInput = PGP_PUBLIC_KEYRING.openStream();
         InputStream privateInput = PGP_PRIVATE_KEYRING.openStream()) {
      publics = new BcPGPPublicKeyRingCollection(PGPUtil.getDecoderStream(publicInput));
      privates = new BcPGPSecretKeyRingCollection(PGPUtil.getDecoderStream(privateInput));
    } catch (PGPException e) {
      throw new RuntimeException("Failed to load PGP keyrings from jar", e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    final PGPKeyPair rdeStagingKey =
        PgpHelper.lookupKeyPair(publics, privates, STAGING_KEY_EMAIL, ENCRYPT);
    final PGPKeyPair rdeSigningKey =
        PgpHelper.lookupKeyPair(publics, privates, SIGNING_KEY_EMAIL, SIGN);
    final PGPPublicKey rdeReceiverKey =
        PgpHelper.lookupPublicKey(publics, RECEIVER_KEY_EMAIL, ENCRYPT);
    final PGPKeyPair brdaSigningKey = rdeSigningKey;
    final PGPPublicKey brdaReceiverKey = rdeReceiverKey;
    final String sshPublic;
    final String sshPrivate;
    try {
      sshPublic = RdeTestData.loadUtf8("registry-unittest.id_rsa.pub");
      sshPrivate = RdeTestData.loadUtf8("registry-unittest.id_rsa");
    } catch (IOException e) {
      throw new RuntimeException("Failed to load SSH keys from jar", e);
    }
    return new Keyring() {
      @Override
      public PGPPublicKey getRdeStagingEncryptionKey() {
        return rdeStagingKey.getPublicKey();
      }

      @Override
      public PGPPrivateKey getRdeStagingDecryptionKey() {
        return rdeStagingKey.getPrivateKey();
      }

      @Override
      public String getRdeSshClientPublicKey() {
        return sshPublic;
      }

      @Override
      public String getRdeSshClientPrivateKey() {
        return sshPrivate;
      }

      @Override
      public PGPKeyPair getRdeSigningKey() {
        return rdeSigningKey;
      }

      @Override
      public PGPPublicKey getRdeReceiverKey() {
        return rdeReceiverKey;
      }

      @Override
      public String getMarksdbSmdrlLogin() {
        return MARKSDB_SMDRL_LOGIN;
      }

      @Override
      public String getMarksdbLordnPassword() {
        return MARKSDB_LORDN_PASSWORD;
      }

      @Override
      public String getMarksdbDnlLogin() {
        return MARKSDB_DNL_LOGIN;
      }

      @Override
      public String getJsonCredential() {
        throw new UnsupportedOperationException();
      }

      @Override
      public String getIcannReportingPassword() {
        return ICANN_REPORTING_PASSWORD;
      }

      @Override
      public PGPKeyPair getBrdaSigningKey() {
        return brdaSigningKey;
      }

      @Override
      public PGPPublicKey getBrdaReceiverKey() {
        return brdaReceiverKey;
      }

      @Override
      public String getBraintreePrivateKey() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close() {}
    };
  }

  /** Helper method for loading a specific {@link PGPKeyPair}. */
  public PGPKeyPair get(String query, KeyRequirement want) {
    PGPPublicKeyRingCollection publics;
    PGPSecretKeyRingCollection privates;
    try (InputStream publicInput = PGP_PUBLIC_KEYRING.openStream();
         InputStream privateInput = PGP_PRIVATE_KEYRING.openStream()) {
      publics = new BcPGPPublicKeyRingCollection(PGPUtil.getDecoderStream(publicInput));
      privates = new BcPGPSecretKeyRingCollection(PGPUtil.getDecoderStream(privateInput));
    } catch (PGPException e) {
      throw new RuntimeException("Failed to load PGP keyrings from jar", e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return PgpHelper.lookupKeyPair(publics, privates, query, want);
  }
}
