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

package google.registry.ui.server.registrar;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.security.JsonHttpTestUtils.createJsonPayload;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT2;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT2_HASH;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT_HASH;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import google.registry.config.RegistryEnvironment;
import google.registry.model.registrar.Registrar;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for security_settings.js use of {@link RegistrarServlet}.
 *
 * <p>The default read and session validation tests are handled by the
 * superclass.
 */
@RunWith(MockitoJUnitRunner.class)
public class SecuritySettingsTest extends RegistrarServletTestCase {

  @Test
  public void testPost_updateCert_success() throws Exception {
    Registrar modified = Registrar.loadByClientId(CLIENT_ID).asBuilder()
        .setClientCertificate(SAMPLE_CERT, clock.nowUtc())
        .build();
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of(
        "op", "update",
        "args", modified.toJsonMap())));
    servlet.service(req, rsp);
    // Empty whoisServer and referralUrl fields should be set to defaults by server.
    modified = modified.asBuilder()
        .setWhoisServer(RegistryEnvironment.get().config().getRegistrarDefaultWhoisServer())
        .setReferralUrl(
            RegistryEnvironment.get().config().getRegistrarDefaultReferralUrl().toString())
        .build();
    assertThat(json.get()).containsEntry("status", "SUCCESS");
    assertThat(json.get()).containsEntry("results", asList(modified.toJsonMap()));
    assertThat(Registrar.loadByClientId(CLIENT_ID)).isEqualTo(modified);
  }

  @Test
  public void testPost_updateCert_failure() throws Exception {
    Map<String, Object> reqJson = Registrar.loadByClientId(CLIENT_ID).toJsonMap();
    reqJson.put("clientCertificate", "BLAH");
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of(
        "op", "update",
        "args", reqJson)));
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "ERROR");
    assertThat(json.get()).containsEntry("message", "Invalid X.509 PEM certificate");
  }

  @Test
  public void testChangeCertificates() throws Exception {
    Map<String, Object> jsonMap = Registrar.loadByClientId(CLIENT_ID).toJsonMap();
    jsonMap.put("clientCertificate", SAMPLE_CERT);
    jsonMap.put("failoverClientCertificate", null);
    when(req.getReader()).thenReturn(
        createJsonPayload(ImmutableMap.of("op", "update", "args", jsonMap)));
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "SUCCESS");
    Registrar registrar = Registrar.loadByClientId(CLIENT_ID);
    assertThat(registrar.getClientCertificate()).isEqualTo(SAMPLE_CERT);
    assertThat(registrar.getClientCertificateHash()).isEqualTo(SAMPLE_CERT_HASH);
    assertThat(registrar.getFailoverClientCertificate()).isNull();
    assertThat(registrar.getFailoverClientCertificateHash()).isNull();
  }

  @Test
  public void testChangeFailoverCertificate() throws Exception {
    Map<String, Object> jsonMap = Registrar.loadByClientId(CLIENT_ID).toJsonMap();
    jsonMap.put("failoverClientCertificate", SAMPLE_CERT2);
    when(req.getReader()).thenReturn(
        createJsonPayload(ImmutableMap.of("op", "update", "args", jsonMap)));
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "SUCCESS");
    Registrar registrar = Registrar.loadByClientId(CLIENT_ID);
    assertThat(registrar.getFailoverClientCertificate()).isEqualTo(SAMPLE_CERT2);
    assertThat(registrar.getFailoverClientCertificateHash()).isEqualTo(SAMPLE_CERT2_HASH);
  }

  @Test
  public void testEmptyOrNullCertificate_doesNotClearOutCurrentOne() throws Exception {
    persistResource(
        Registrar.loadByClientId(CLIENT_ID).asBuilder()
            .setClientCertificate(SAMPLE_CERT, START_OF_TIME)
            .setFailoverClientCertificate(SAMPLE_CERT2, START_OF_TIME)
            .build());
    Map<String, Object> jsonMap = Registrar.loadByClientId(CLIENT_ID).toJsonMap();
    jsonMap.put("clientCertificate", null);
    jsonMap.put("failoverClientCertificate", "");
    when(req.getReader()).thenReturn(
        createJsonPayload(ImmutableMap.of("op", "update", "args", jsonMap)));
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "SUCCESS");
    Registrar registrar = Registrar.loadByClientId(CLIENT_ID);
    assertThat(registrar.getClientCertificate()).isEqualTo(SAMPLE_CERT);
    assertThat(registrar.getClientCertificateHash()).isEqualTo(SAMPLE_CERT_HASH);
    assertThat(registrar.getFailoverClientCertificate()).isEqualTo(SAMPLE_CERT2);
    assertThat(registrar.getFailoverClientCertificateHash()).isEqualTo(SAMPLE_CERT2_HASH);
  }

  @Test
  public void testToJsonMap_containsCertificate() throws Exception {
    Map<String, Object> jsonMap = Registrar.loadByClientId(CLIENT_ID).asBuilder()
        .setClientCertificate(SAMPLE_CERT2, START_OF_TIME)
        .build()
        .toJsonMap();
    assertThat(jsonMap).containsEntry("clientCertificate", SAMPLE_CERT2);
    assertThat(jsonMap).containsEntry("clientCertificateHash", SAMPLE_CERT2_HASH);
  }

  @Test
  public void testToJsonMap_containsFailoverCertificate() throws Exception {
    Map<String, Object> jsonMap = Registrar.loadByClientId(CLIENT_ID).asBuilder()
        .setFailoverClientCertificate(SAMPLE_CERT2, START_OF_TIME)
        .build()
        .toJsonMap();
    assertThat(jsonMap).containsEntry("failoverClientCertificate", SAMPLE_CERT2);
    assertThat(jsonMap).containsEntry("failoverClientCertificateHash", SAMPLE_CERT2_HASH);
  }
}
