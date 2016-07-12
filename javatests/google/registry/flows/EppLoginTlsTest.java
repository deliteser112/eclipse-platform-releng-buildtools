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

package google.registry.flows;

import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.base.Optional;
import google.registry.model.registrar.Registrar;
import google.registry.testing.AppEngineRule;
import google.registry.testing.CertificateSamples;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test logging in with TLS credentials. */
@RunWith(JUnit4.class)
public class EppLoginTlsTest extends EppTestCase {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();


  void setClientCertificateHash(String clientCertificateHash) {
    setTransportCredentials(new TlsCredentials(
        clientCertificateHash, Optional.of("192.168.1.100:54321"), "test.example"));
  }

  @Before
  public void initTest() throws Exception {
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH)
        .build());
    // Set a cert for the second registrar, or else any cert will be allowed for login.
    persistResource(Registrar.loadByClientId("TheRegistrar").asBuilder()
        .setClientCertificateHash(CertificateSamples.SAMPLE_CERT2_HASH)
        .build());
  }

  @Test
  public void testLoginLogout() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
  }

  @Test
  public void testLogin_wrongPasswordFails() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    // For TLS login, we also check the epp xml password.
    assertCommandAndResponse(
        "login_invalid_wrong_password.xml", "login_response_wrong_password.xml");
  }

  @Test
  public void testMultiLogin() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
    assertCommandAndResponse("logout.xml", "logout_response.xml");
    assertCommandAndResponse("login2_valid.xml", "login_response_bad_certificate.xml");
  }

  @Test
  public void testNonAuthedLogin_fails() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    assertCommandAndResponse("login2_valid.xml", "login_response_bad_certificate.xml");
  }


  @Test
  public void testBadCertificate_failsBadCertificate2200() throws Exception {
    setClientCertificateHash("laffo");
    assertCommandAndResponse("login_valid.xml", "login_response_bad_certificate.xml");
  }

  @Test
  public void testGfeDidntProvideClientCertificate_failsMissingCertificate2200() throws Exception {
    setClientCertificateHash("");
    assertCommandAndResponse("login_valid.xml", "login_response_missing_certificate.xml");
  }

  @Test
  public void testGoodPrimaryCertificate() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    DateTime now = DateTime.now(UTC);
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setClientCertificate(CertificateSamples.SAMPLE_CERT, now)
        .setFailoverClientCertificate(CertificateSamples.SAMPLE_CERT2, now)
        .build());
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
  }

  @Test
  public void testGoodFailoverCertificate() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT2_HASH);
    DateTime now = DateTime.now(UTC);
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setClientCertificate(CertificateSamples.SAMPLE_CERT, now)
        .setFailoverClientCertificate(CertificateSamples.SAMPLE_CERT2, now)
        .build());
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
  }

  @Test
  public void testMissingPrimaryCertificateButHasFailover_usesFailover() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT2_HASH);
    DateTime now = DateTime.now(UTC);
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setClientCertificate(null, now)
        .setFailoverClientCertificate(CertificateSamples.SAMPLE_CERT2, now)
        .build());
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
  }

  @Test
  public void testRegistrarHasNoCertificatesOnFile_disablesCertChecking() throws Exception {
    setClientCertificateHash("laffo");
    DateTime now = DateTime.now(UTC);
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setClientCertificate(null, now)
        .setFailoverClientCertificate(null, now)
        .build());
    assertCommandAndResponse("login_valid.xml", "login_response.xml");
  }
}
