// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.keyring.api;

import javax.annotation.concurrent.Immutable;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;

/** {@link Keyring} that throws {@link UnsupportedOperationException} if any methods are called. */
@Immutable
public final class VoidKeyring implements Keyring {

  private static final String ERROR = "Keyring support not loaded";

  /** @throws UnsupportedOperationException always */
  @Override
  public PGPKeyPair getRdeSigningKey() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public PGPKeyPair getBrdaSigningKey() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public PGPPublicKey getRdeStagingEncryptionKey() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public PGPPrivateKey getRdeStagingDecryptionKey() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public PGPPublicKey getRdeReceiverKey() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public PGPPublicKey getBrdaReceiverKey() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public String getRdeSshClientPublicKey() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public String getRdeSshClientPrivateKey() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public String getIcannReportingPassword() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public String getMarksdbDnlLogin() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public String getMarksdbLordnPassword() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public String getMarksdbSmdrlLogin() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public String getJsonCredential() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** @throws UnsupportedOperationException always */
  @Override
  public String getBraintreePrivateKey() {
    throw new UnsupportedOperationException(ERROR);
  }

  /** Does nothing. */
  @Override
  public void close() {}
}
