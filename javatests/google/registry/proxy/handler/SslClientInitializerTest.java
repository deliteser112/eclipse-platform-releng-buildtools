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

package google.registry.proxy.handler;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.proxy.Protocol.PROTOCOL_KEY;
import static google.registry.proxy.handler.SslInitializerTestUtils.getKeyPair;
import static google.registry.proxy.handler.SslInitializerTestUtils.setUpClient;
import static google.registry.proxy.handler.SslInitializerTestUtils.setUpServer;
import static google.registry.proxy.handler.SslInitializerTestUtils.signKeyPair;
import static google.registry.proxy.handler.SslInitializerTestUtils.verifySslChannel;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import google.registry.proxy.Protocol;
import google.registry.proxy.Protocol.BackendProtocol;
import google.registry.proxy.handler.SslInitializerTestUtils.DumpHandler;
import google.registry.proxy.handler.SslInitializerTestUtils.EchoHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.Future;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLException;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import sun.security.provider.certpath.SunCertPathBuilderException;

/**
 * Unit tests for {@link SslClientInitializer}.
 *
 * <p>To validate that the handler accepts & rejects connections as expected, a test server and a
 * test client are spun up, and both connect to the {@link LocalAddress} within the JVM. This avoids
 * the overhead of routing traffic through the network layer, even if it were to go through
 * loopback. It also alleviates the need to pick a free port to use.
 *
 * <p>The local addresses used in each test method must to be different, otherwise tests run in
 * parallel may interfere with each other.
 */
@RunWith(Parameterized.class)
public class SslClientInitializerTest {

  /** Fake host to test if the SSL engine gets the correct peer host. */
  private static final String SSL_HOST = "www.example.tld";

  /** Fake port to test if the SSL engine gets the correct peer port. */
  private static final int SSL_PORT = 12345;

  @Parameter(0)
  public SslProvider sslProvider;

  // We do our best effort to test all available SSL providers.
  @Parameters(name = "{0}")
  public static SslProvider[] data() {
    return OpenSsl.isAvailable()
        ? new SslProvider[] {SslProvider.JDK, SslProvider.OPENSSL}
        : new SslProvider[] {SslProvider.JDK};
  }

  /** Saves the SNI hostname received by the server, if sent by the client. */
  private String sniHostReceived;

  // All I/O operations are done inside the single thread within this event loop group, which is
  // different from the main test thread. Therefore synchronizations are required to make sure that
  // certain I/O activities are finished when assertions are performed.
  private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);

  // Handler attached to server's channel to record the request received.
  private final EchoHandler echoHandler = new EchoHandler();

  // Handler attached to client's channel to record the response received.
  private final DumpHandler dumpHandler = new DumpHandler();

  /** Fake protocol saved in channel attribute. */
  private static final BackendProtocol PROTOCOL =
      Protocol.backendBuilder()
          .name("ssl")
          .host(SSL_HOST)
          .port(SSL_PORT)
          .handlerProviders(ImmutableList.of())
          .build();

  @After
  public void shutDown() {
    Future<?> unusedFuture = eventLoopGroup.shutdownGracefully();
  }

  private ChannelInitializer<LocalChannel> getServerInitializer(
      PrivateKey privateKey, X509Certificate certificate) throws Exception {
    SslContext sslContext = SslContextBuilder.forServer(privateKey, certificate).build();
    return new ChannelInitializer<LocalChannel>() {
      @Override
      protected void initChannel(LocalChannel ch) {
        ch.pipeline()
            .addLast(
                new SniHandler(
                    hostname -> {
                      sniHostReceived = hostname;
                      return sslContext;
                    }),
                echoHandler);
      }
    };
  }

  private ChannelInitializer<LocalChannel> getClientInitializer(
      SslClientInitializer<LocalChannel> sslClientInitializer) {
    return new ChannelInitializer<LocalChannel>() {
      @Override
      protected void initChannel(LocalChannel ch) {
        ch.pipeline().addLast(sslClientInitializer, dumpHandler);
      }
    };
  }

  @Test
  public void testSuccess_swappedInitializerWithSslHandler() throws Exception {
    SslClientInitializer<EmbeddedChannel> sslClientInitializer =
        new SslClientInitializer<>(sslProvider);
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.attr(PROTOCOL_KEY).set(PROTOCOL);
    ChannelPipeline pipeline = channel.pipeline();
    pipeline.addLast(sslClientInitializer);
    ChannelHandler firstHandler = pipeline.first();
    assertThat(firstHandler.getClass()).isEqualTo(SslHandler.class);
    SslHandler sslHandler = (SslHandler) firstHandler;
    assertThat(sslHandler.engine().getPeerHost()).isEqualTo(SSL_HOST);
    assertThat(sslHandler.engine().getPeerPort()).isEqualTo(SSL_PORT);
    assertThat(channel.isActive()).isTrue();
  }

  @Test
  public void testSuccess_protocolAttributeNotSet() {
    SslClientInitializer<EmbeddedChannel> sslClientInitializer =
        new SslClientInitializer<>(sslProvider);
    EmbeddedChannel channel = new EmbeddedChannel();
    ChannelPipeline pipeline = channel.pipeline();
    pipeline.addLast(sslClientInitializer);
    // Channel initializer swallows error thrown, and closes the connection.
    assertThat(channel.isActive()).isFalse();
  }

  @Test
  public void testFailure_defaultTrustManager_rejectSelfSignedCert() throws Exception {
    SelfSignedCertificate ssc = new SelfSignedCertificate(SSL_HOST);
    LocalAddress localAddress =
        new LocalAddress("DEFAULT_TRUST_MANAGER_REJECT_SELF_SIGNED_CERT_" + sslProvider);
    setUpServer(eventLoopGroup, getServerInitializer(ssc.key(), ssc.cert()), localAddress);
    SslClientInitializer<LocalChannel> sslClientInitializer =
        new SslClientInitializer<>(sslProvider);
    Channel channel =
        setUpClient(
            eventLoopGroup, getClientInitializer(sslClientInitializer), localAddress, PROTOCOL);
    // Wait for handshake exception to throw.
    echoHandler.waitTillReady();
    dumpHandler.waitTillReady();
    // The connection is now terminated, both the client side and the server side should get
    // exceptions.
    assertThat(Throwables.getRootCause(dumpHandler.getCause()))
        .isInstanceOf(SunCertPathBuilderException.class);
    assertThat(Throwables.getRootCause(echoHandler.getCause())).isInstanceOf(SSLException.class);
    assertThat(channel.isActive()).isFalse();
  }

  @Test
  public void testSuccess_customTrustManager_acceptCertSignedByTrustedCa() throws Exception {
    LocalAddress localAddress =
        new LocalAddress("CUSTOM_TRUST_MANAGER_ACCEPT_CERT_SIGNED_BY_TRUSTED_CA_" + sslProvider);

    // Generate a new key pair.
    KeyPair keyPair = getKeyPair();

    // Generate a self signed certificate, and use it to sign the key pair.
    SelfSignedCertificate ssc = new SelfSignedCertificate();
    X509Certificate cert = signKeyPair(ssc, keyPair, SSL_HOST);

    // Set up the server to use the signed cert and private key to perform handshake;
    PrivateKey privateKey = keyPair.getPrivate();
    setUpServer(eventLoopGroup, getServerInitializer(privateKey, cert), localAddress);

    // Set up the client to trust the self signed cert used to sign the cert that server provides.
    SslClientInitializer<LocalChannel> sslClientInitializer =
        new SslClientInitializer<>(sslProvider, new X509Certificate[] {ssc.cert()});
    Channel channel =
        setUpClient(
            eventLoopGroup, getClientInitializer(sslClientInitializer), localAddress, PROTOCOL);

    verifySslChannel(channel, ImmutableList.of(cert), echoHandler, dumpHandler);

    // Verify that the SNI extension is sent during handshake.
    assertThat(sniHostReceived).isEqualTo(SSL_HOST);
  }

  @Test
  public void testFailure_customTrustManager_wrongHostnameInCertificate() throws Exception {
    LocalAddress localAddress =
        new LocalAddress("CUSTOM_TRUST_MANAGER_WRONG_HOSTNAME_" + sslProvider);

    // Generate a new key pair.
    KeyPair keyPair = getKeyPair();

    // Generate a self signed certificate, and use it to sign the key pair.
    SelfSignedCertificate ssc = new SelfSignedCertificate();
    X509Certificate cert = signKeyPair(ssc, keyPair, "wrong.com");

    // Set up the server to use the signed cert and private key to perform handshake;
    PrivateKey privateKey = keyPair.getPrivate();
    setUpServer(eventLoopGroup, getServerInitializer(privateKey, cert), localAddress);

    // Set up the client to trust the self signed cert used to sign the cert that server provides.
    SslClientInitializer<LocalChannel> sslClientInitializer =
        new SslClientInitializer<>(sslProvider, new X509Certificate[] {ssc.cert()});
    Channel channel =
        setUpClient(
            eventLoopGroup, getClientInitializer(sslClientInitializer), localAddress, PROTOCOL);

    echoHandler.waitTillReady();
    dumpHandler.waitTillReady();

    // When the client rejects the server cert due to wrong hostname, both the client and server
    // should throw exceptions.
    Throwable rootCause = Throwables.getRootCause(dumpHandler.getCause());
    assertThat(rootCause).isInstanceOf(CertificateException.class);
    assertThat(rootCause).hasMessageThat().contains(SSL_HOST);
    assertThat(Throwables.getRootCause(echoHandler.getCause())).isInstanceOf(SSLException.class);
    assertThat(channel.isActive()).isFalse();
  }
}
