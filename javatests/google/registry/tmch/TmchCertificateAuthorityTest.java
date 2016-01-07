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

import static google.registry.tmch.TmchTestData.loadString;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static google.registry.util.X509Utils.loadCertificate;

import google.registry.model.tmch.TmchCrl;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.testing.RegistryConfigRule;
import java.security.SignatureException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TmchCertificateAuthority}. */
@RunWith(JUnit4.class)
public class TmchCertificateAuthorityTest {

  public static final String GOOD_TEST_CERTIFICATE = loadString("icann-tmch-test-good.crt");
  public static final String REVOKED_TEST_CERTIFICATE = loadString("icann-tmch-test-revoked.crt");

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final RegistryConfigRule configRule = new RegistryConfigRule();

  private FakeClock clock = new FakeClock(DateTime.parse("2014-01-01T00:00:00Z"));

  @Before
  public void before() throws Exception {
    inject.setStaticField(TmchCertificateAuthority.class, "clock", clock);
  }

  @Test
  public void testFailure_prodRootExpired() throws Exception {
    configRule.useTmchProdCert();
    clock.setTo(DateTime.parse("2024-01-01T00:00:00Z"));
    thrown.expectRootCause(
        CertificateExpiredException.class, "NotAfter: Sun Jul 23 23:59:59 UTC 2023");
    TmchCertificateAuthority.getRoot();
  }

  @Test
  public void testFailure_prodRootNotYetValid() throws Exception {
    configRule.useTmchProdCert();
    clock.setTo(DateTime.parse("2000-01-01T00:00:00Z"));
    thrown.expectRootCause(CertificateNotYetValidException.class,
        "NotBefore: Wed Jul 24 00:00:00 UTC 2013");
    TmchCertificateAuthority.getRoot();
  }

  @Test
  public void testFailure_crlDoesntMatchCerts() throws Exception {
    // Use the prod cl, which won't match our test certificate.
    TmchCrl.set(readResourceUtf8(TmchCertificateAuthority.class, "icann-tmch.crl"));
    thrown.expectRootCause(SignatureException.class, "Signature does not match");
    TmchCertificateAuthority.verify(loadCertificate(GOOD_TEST_CERTIFICATE));
  }

  @Test
  public void testSuccess_verify() throws Exception {
    TmchCertificateAuthority.verify(loadCertificate(GOOD_TEST_CERTIFICATE));
  }

  @Test
  public void testFailure_verifySignatureDoesntMatch() throws Exception {
    configRule.useTmchProdCert();
    thrown.expectRootCause(SignatureException.class, "Signature does not match");
    TmchCertificateAuthority.verify(loadCertificate(GOOD_TEST_CERTIFICATE));
  }

  @Test
  public void testFailure_verifyRevoked() throws Exception {
    thrown.expect(CertificateRevokedException.class, "revoked, reason: KEY_COMPROMISE");
    TmchCertificateAuthority.verify(loadCertificate(REVOKED_TEST_CERTIFICATE));
  }
}
