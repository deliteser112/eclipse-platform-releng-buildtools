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

package google.registry.proxy;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.proxy.handler.SslInitializerTestUtils.getKeyPair;
import static google.registry.proxy.handler.SslInitializerTestUtils.signKeyPair;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import dagger.BindsInstance;
import dagger.Component;
import google.registry.proxy.ProxyModule.PemBytes;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.inject.Named;
import javax.inject.Singleton;
import org.bouncycastle.openssl.PEMWriter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CertificateModule}. */
@RunWith(JUnit4.class)
public class CertificateModuleTest {

  private SelfSignedCertificate ssc;
  private PrivateKey key;
  private Certificate cert;
  private TestComponent component;

  private static byte[] getPemBytes(Object... objects) throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (PEMWriter pemWriter =
        new PEMWriter(new OutputStreamWriter(byteArrayOutputStream, UTF_8))) {
      for (Object object : objects) {
        pemWriter.writeObject(object);
      }
    }
    return byteArrayOutputStream.toByteArray();
  }

  /** Create a component with bindings to the given bytes[] as the contents from a PEM file. */
  private TestComponent createComponent(byte[] bytes) {
    return DaggerCertificateModuleTest_TestComponent.builder()
        .pemBytes(PemBytes.create(bytes))
        .build();
  }

  @Before
  public void setUp() throws Exception {
    ssc = new SelfSignedCertificate();
    KeyPair keyPair = getKeyPair();
    key = keyPair.getPrivate();
    cert = signKeyPair(ssc, keyPair, "example.tld");
  }

  @Test
  public void testSuccess() throws Exception {
    byte[] pemBytes = getPemBytes(cert, ssc.cert(), key);
    component = createComponent(pemBytes);
    assertThat(component.privateKey()).isEqualTo(key);
    assertThat(component.certificates()).asList().containsExactly(cert, ssc.cert()).inOrder();
  }

  @Test
  public void testSuccess_certificateChainNotContinuous() throws Exception {
    byte[] pemBytes = getPemBytes(cert, key, ssc.cert());
    component = createComponent(pemBytes);
    assertThat(component.privateKey()).isEqualTo(key);
    assertThat(component.certificates()).asList().containsExactly(cert, ssc.cert()).inOrder();
  }

  @Test
  public void testFailure_noPrivateKey() throws Exception {
    byte[] pemBytes = getPemBytes(cert, ssc.cert());
    component = createComponent(pemBytes);
    try {
      component.privateKey();
      fail("Expect IllegalStateException.");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("0 keys are found");
    }
  }

  @Test
  public void testFailure_twoPrivateKeys() throws Exception {
    byte[] pemBytes = getPemBytes(cert, ssc.cert(), key, ssc.key());
    component = createComponent(pemBytes);
    try {
      component.privateKey();
      fail("Expect IllegalStateException.");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("2 keys are found");
    }
  }

  @Test
  public void testFailure_certificatesOutOfOrder() throws Exception {
    byte[] pemBytes = getPemBytes(ssc.cert(), cert, key);
    component = createComponent(pemBytes);
    try {
      component.certificates();
      fail("Expect IllegalStateException.");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("is not signed by");
    }
  }

  @Test
  public void testFailure_noCertificates() throws Exception {
    byte[] pemBytes = getPemBytes(key);
    component = createComponent(pemBytes);
    try {
      component.certificates();
      fail("Expect IllegalStateException.");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("No certificates");
    }
  }

  @Singleton
  @Component(modules = {CertificateModule.class})
  interface TestComponent {

    PrivateKey privateKey();

    @Named("eppServerCertificates")
    X509Certificate[] certificates();

    @Component.Builder
    interface Builder {

      @BindsInstance
      Builder pemBytes(PemBytes pemBytes);

      TestComponent build();
    }
  }
}
