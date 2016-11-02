// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tmch;

import static com.google.common.base.Throwables.propagateIfInstanceOf;
import static google.registry.util.CacheUtils.memoizeWithLongExpiration;
import static google.registry.util.CacheUtils.memoizeWithShortExpiration;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static google.registry.util.X509Utils.loadCrl;

import com.google.common.base.Supplier;
import google.registry.config.RegistryEnvironment;
import google.registry.model.tmch.TmchCrl;
import google.registry.util.Clock;
import google.registry.util.NonFinalForTesting;
import google.registry.util.SystemClock;
import google.registry.util.X509Utils;
import java.security.GeneralSecurityException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/** Datastore singleton for ICANN's TMCH root certificate and revocation list. */
@Immutable
@ThreadSafe
public final class TmchCertificateAuthority {

  private static final RegistryEnvironment ENVIRONMENT = RegistryEnvironment.get();

  private static final String ROOT_CRT_FILE = "icann-tmch.crt";
  private static final String TEST_ROOT_CRT_FILE = "icann-tmch-test.crt";
  private static final String CRL_FILE = "icann-tmch.crl";
  private static final String TEST_CRL_FILE = "icann-tmch-test.crl";

  /**
   * A cached supplier that loads the crl from datastore or chooses a default value.
   *
   * <p>We keep the cache here rather than caching TmchCrl in the model, because loading the crl
   * string into an X509CRL instance is expensive and should itself be cached.
   */
  private static final Supplier<X509CRL> CRL_CACHE =
      memoizeWithShortExpiration(new Supplier<X509CRL>() {
        @Override
        public X509CRL get() {
          TmchCrl storedCrl = TmchCrl.get();
          try {
            X509CRL crl = loadCrl((storedCrl == null)
                ? readResourceUtf8(
                    TmchCertificateAuthority.class,
                    ENVIRONMENT.config().getTmchCaTestingMode() ? TEST_CRL_FILE : CRL_FILE)
                : storedCrl.getCrl());
            crl.verify(getRoot().getPublicKey());
            return crl;
          } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
          }
        }});

  /** A cached function that loads the crt from a jar resource. */
  private static final Supplier<X509Certificate> ROOT_CACHE =
      memoizeWithLongExpiration(new Supplier<X509Certificate>() {
        @Override
        public X509Certificate get() {
          try {
            X509Certificate root = X509Utils.loadCertificate(readResourceUtf8(
                TmchCertificateAuthority.class,
                ENVIRONMENT.config().getTmchCaTestingMode() ? TEST_ROOT_CRT_FILE : ROOT_CRT_FILE));
            root.checkValidity(clock.nowUtc().toDate());
            return root;
          } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
          }
        }});

  @NonFinalForTesting
  private static Clock clock = new SystemClock();

  /**
   * Check that {@code cert} is signed by the ICANN TMCH CA root and not revoked.
   *
   * <p>Support for certificate chains has not been implemented.
   *
   * @throws GeneralSecurityException for unsupported protocols, certs not signed by the TMCH,
   *         incorrect keys, and for invalid, old, not-yet-valid or revoked certificates.
   * @see X509Utils#verifyCertificate
   */
  public static void verify(X509Certificate cert) throws GeneralSecurityException {
    synchronized (TmchCertificateAuthority.class) {
      X509Utils.verifyCertificate(getRoot(), getCrl(), cert, clock.nowUtc().toDate());
    }
  }

  /**
   * Update to the latest TMCH X.509 certificate revocation list and save to the datastore.
   *
   * <p>Your ASCII-armored CRL must be signed by the current ICANN root certificate.
   *
   * <p>This will not take effect (either on this instance or on others) until the CRL_CACHE next
   * refreshes itself.
   *
   * @throws GeneralSecurityException for unsupported protocols, certs not signed by the TMCH,
   *         incorrect keys, and for invalid, old, not-yet-valid or revoked certificates.
   * @see X509Utils#verifyCrl
   */
  public static void updateCrl(String asciiCrl) throws GeneralSecurityException {
    X509CRL crl = X509Utils.loadCrl(asciiCrl);
    X509Utils.verifyCrl(getRoot(), getCrl(), crl, clock.nowUtc().toDate());
    TmchCrl.set(asciiCrl);
  }

  public static X509Certificate getRoot() throws GeneralSecurityException {
    try {
      return ROOT_CACHE.get();
    } catch (RuntimeException e) {
      propagateIfInstanceOf(e.getCause(), GeneralSecurityException.class);
      throw e;
    }
  }

  public static X509CRL getCrl() throws GeneralSecurityException {
    try {
      return CRL_CACHE.get();
    } catch (RuntimeException e) {
      propagateIfInstanceOf(e.getCause(), GeneralSecurityException.class);
      throw e;
    }
  }
}
