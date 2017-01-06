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

import static google.registry.config.RegistryConfig.getSingletonCachePersistDuration;
import static google.registry.config.RegistryConfig.getSingletonCacheRefreshDuration;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.tmch.TmchCrl;
import google.registry.util.Clock;
import google.registry.util.NonFinalForTesting;
import google.registry.util.SystemClock;
import google.registry.util.X509Utils;
import java.security.GeneralSecurityException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutionException;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

/**
 * Helper methods for accessing ICANN's TMCH root certificate and revocation list.
 *
 * <p>There are two CRLs, a real one for the production environment and a testing one for
 * non-production environments. The Datastore singleton {@link TmchCrl} entity is used to cache this
 * CRL once loaded and will always contain the proper one corresponding to the environment.
 */
@Immutable
@ThreadSafe
public final class TmchCertificateAuthority {

  private static final String ROOT_CRT_FILE = "icann-tmch.crt";
  private static final String TEST_ROOT_CRT_FILE = "icann-tmch-test.crt";
  private static final String CRL_FILE = "icann-tmch.crl";
  private static final String TEST_CRL_FILE = "icann-tmch-test.crl";

  private boolean tmchCaTestingMode;

  public @Inject TmchCertificateAuthority(@Config("tmchCaTestingMode") boolean tmchCaTestingMode) {
    this.tmchCaTestingMode = tmchCaTestingMode;
  }

  /**
   * A cached supplier that loads the CRL from Datastore or chooses a default value.
   *
   * <p>We keep the cache here rather than caching TmchCrl in the model, because loading the CRL
   * string into an X509CRL instance is expensive and should itself be cached.
   *
   * <p>Note that the stored CRL won't exist for tests, and on deployed environments will always
   * correspond to the correct CRL for the given testing mode because {@link TmchCrlAction} can
   * only persist the correct one for this given environment.
   */
  private static final LoadingCache<Boolean, X509CRL> CRL_CACHE =
      CacheBuilder.newBuilder()
          .expireAfterWrite(getSingletonCacheRefreshDuration().getMillis(), MILLISECONDS)
          .build(
              new CacheLoader<Boolean, X509CRL>() {
                @Override
                public X509CRL load(final Boolean tmchCaTestingMode)
                    throws GeneralSecurityException {
                  TmchCrl storedCrl = TmchCrl.get();
                  try {
                    String crlContents;
                    if (storedCrl == null) {
                      String file = tmchCaTestingMode.booleanValue() ? TEST_CRL_FILE : CRL_FILE;
                      crlContents = readResourceUtf8(TmchCertificateAuthority.class, file);
                    } else {
                      crlContents = storedCrl.getCrl();
                    }
                    X509CRL crl = X509Utils.loadCrl(crlContents);
                    crl.verify(ROOT_CACHE.get(tmchCaTestingMode).getPublicKey());
                    return crl;
                  } catch (ExecutionException e) {
                    if (e.getCause() instanceof GeneralSecurityException) {
                      throw (GeneralSecurityException) e.getCause();
                    } else {
                      throw new RuntimeException("Unexpected exception while loading CRL", e);
                    }
                  }
                }});

  /** A cached function that loads the CRT from a jar resource. */
  private static final LoadingCache<Boolean, X509Certificate> ROOT_CACHE =
      CacheBuilder.newBuilder()
          .expireAfterWrite(getSingletonCachePersistDuration().getMillis(), MILLISECONDS)
          .build(
              new CacheLoader<Boolean, X509Certificate>() {
                @Override
                public X509Certificate load(final Boolean tmchCaTestingMode)
                    throws GeneralSecurityException {
                  String file =
                      tmchCaTestingMode.booleanValue() ? TEST_ROOT_CRT_FILE : ROOT_CRT_FILE;
                  X509Certificate root =
                      X509Utils.loadCertificate(
                          readResourceUtf8(TmchCertificateAuthority.class, file));
                  root.checkValidity(clock.nowUtc().toDate());
                  return root;
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
  public void verify(X509Certificate cert) throws GeneralSecurityException {
    synchronized (TmchCertificateAuthority.class) {
      X509Utils.verifyCertificate(getRoot(), getCrl(), cert, clock.nowUtc().toDate());
    }
  }

  /**
   * Update to the latest TMCH X.509 certificate revocation list and save it to Datastore.
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
  public void updateCrl(String asciiCrl, String url) throws GeneralSecurityException {
    X509CRL crl = X509Utils.loadCrl(asciiCrl);
    X509Utils.verifyCrl(getRoot(), getCrl(), crl, clock.nowUtc().toDate());
    TmchCrl.set(asciiCrl, url);
  }

  public X509Certificate getRoot() throws GeneralSecurityException {
    try {
      return ROOT_CACHE.get(tmchCaTestingMode);
    } catch (Exception e) {
      if (e.getCause() instanceof GeneralSecurityException) {
        throw (GeneralSecurityException) e.getCause();
      } else if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      throw new RuntimeException(e);
    }
  }

  public X509CRL getCrl() throws GeneralSecurityException {
    try {
      return CRL_CACHE.get(tmchCaTestingMode);
    } catch (Exception e) {
      if (e.getCause() instanceof GeneralSecurityException) {
        throw (GeneralSecurityException) e.getCause();
      } else if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      throw new RuntimeException(e);
    }
  }
}
