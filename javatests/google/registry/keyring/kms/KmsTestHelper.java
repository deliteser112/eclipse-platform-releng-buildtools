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

import static com.google.common.io.Resources.getResource;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRing;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRing;

/** Stores dummy values for test use in {@link KmsUpdaterTest} and {@link KmsKeyringTest}. */
final class KmsTestHelper {

  static final String DUMMY_KEY = "the quick brown fox";
  static final String DUMMY_CRYPTO_KEY_VERSION = "cheeseburger";
  static final String DUMMY_ENCRYPTED_VALUE = "meow";

  /** The contents of a dummy PGP public key stored in a file. */
  private static final ByteSource PGP_PUBLIC_KEYRING =
      Resources.asByteSource(getResource(KmsTestHelper.class, "pgp-public-keyring.asc"));

  /** The contents of a dummy PGP private key stored in a file. */
  private static final ByteSource PGP_PRIVATE_KEYRING =
      Resources.asByteSource(getResource(KmsTestHelper.class, "pgp-private-keyring-registry.asc"));

  static BcPGPPublicKeyRing getPublicKeyring() throws Exception {
    return new BcPGPPublicKeyRing(PGPUtil.getDecoderStream(PGP_PUBLIC_KEYRING.openStream()));
  }

  static BcPGPSecretKeyRing getPrivateKeyring() throws Exception {
    return new BcPGPSecretKeyRing(PGPUtil.getDecoderStream(PGP_PRIVATE_KEYRING.openStream()));
  }

  private KmsTestHelper() {}
}
