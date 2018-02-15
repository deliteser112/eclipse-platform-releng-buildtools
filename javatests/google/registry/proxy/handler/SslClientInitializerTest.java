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

import com.google.common.collect.ImmutableList;
import google.registry.proxy.Protocol;
import google.registry.proxy.Protocol.BackendProtocol;
import google.registry.proxy.handler.SslInitializerTestUtils.DumpHandler;
import google.registry.proxy.handler.SslInitializerTestUtils.EchoHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.handler.codec.DecoderException;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
@RunWith(JUnit4.class)
public class SslClientInitializerTest {

  /** Fake host to test if the SSL engine gets the correct peer host. */
  private static final String SSL_HOST = "www.example.tld";

  /** Fake port to test if the SSL engine gets the correct peer port. */
  private static final int SSL_PORT = 12345;

  /** Fake protocol saved in channel attribute. */
  private static final BackendProtocol PROTOCOL =
      Protocol.backendBuilder()
          .name("ssl")
          .host(SSL_HOST)
          .port(SSL_PORT)
          .handlerProviders(ImmutableList.of())
          .build();

  private ChannelInitializer<LocalChannel> getServerInitializer(
      PrivateKey privateKey,
      X509Certificate certificate,
      Lock serverLock,
      Exception serverException)
      throws Exception {
    SslContext sslContext = SslContextBuilder.forServer(privateKey, certificate).build();
    return new ChannelInitializer<LocalChannel>() {
      @Override
      protected void initChannel(LocalChannel ch) throws Exception {
        ch.pipeline()
            .addLast(
                sslContext.newHandler(ch.alloc()), new EchoHandler(serverLock, serverException));
      }
    };
  }

  private ChannelInitializer<LocalChannel> getClientInitializer(
      SslClientInitializer<LocalChannel> sslClientInitializer,
      Lock clientLock,
      ByteBuf buffer,
      Exception clientException) {
    return new ChannelInitializer<LocalChannel>() {
      @Override
      protected void initChannel(LocalChannel ch) throws Exception {
        ch.pipeline()
            .addLast(sslClientInitializer, new DumpHandler(clientLock, buffer, clientException));
      }
    };
  }

  @Test
  public void testSuccess_swappedInitializerWithSslHandler() throws Exception {
    SslClientInitializer<EmbeddedChannel> sslClientInitializer =
        new SslClientInitializer<>(SslProvider.JDK, (X509Certificate[]) null);
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
        new SslClientInitializer<>(SslProvider.JDK, (X509Certificate[]) null);
    EmbeddedChannel channel = new EmbeddedChannel();
    ChannelPipeline pipeline = channel.pipeline();
    pipeline.addLast(sslClientInitializer);
    // Channel initializer swallows error thrown, and closes the connection.
    assertThat(channel.isActive()).isFalse();
  }

  @Test
  public void testFailure_defaultTrustManager_rejectSelfSignedCert() throws Exception {
    SelfSignedCertificate ssc = new SelfSignedCertificate(SSL_HOST);
    LocalAddress localAddress = new LocalAddress("DEFAULT_TRUST_MANAGER_REJECT_SELF_SIGNED_CERT");
    Lock clientLock = new ReentrantLock();
    Lock serverLock = new ReentrantLock();
    ByteBuf buffer = Unpooled.buffer();
    Exception clientException = new Exception();
    Exception serverException = new Exception();
    EventLoopGroup eventLoopGroup =
        setUpServer(
            getServerInitializer(ssc.key(), ssc.cert(), serverLock, serverException), localAddress);
    SslClientInitializer<LocalChannel> sslClientInitializer =
        new SslClientInitializer<>(SslProvider.JDK, (X509Certificate[]) null);
    Channel channel =
        setUpClient(
            eventLoopGroup,
            getClientInitializer(sslClientInitializer, clientLock, buffer, clientException),
            localAddress,
            PROTOCOL);
    // Wait for handshake exception to throw.
    clientLock.lock();
    serverLock.lock();
    // The connection is now terminated, both the client side and the server side should get
    // exceptions (caught in the caughtException method in EchoHandler and DumpHandler,
    // respectively).
    assertThat(clientException).hasCauseThat().isInstanceOf(DecoderException.class);
    assertThat(clientException)
        .hasCauseThat()
        .hasCauseThat()
        .isInstanceOf(SSLHandshakeException.class);
    assertThat(serverException).hasCauseThat().isInstanceOf(DecoderException.class);
    assertThat(serverException).hasCauseThat().hasCauseThat().isInstanceOf(SSLException.class);
    //assertThat(channel.isActive()).isFalse();

    Future<?> unusedFuture = eventLoopGroup.shutdownGracefully().syncUninterruptibly();
  }

  @Test
  public void testSuccess_customTrustManager_acceptCertSignedByTrustedCa() throws Exception {
    LocalAddress localAddress =
        new LocalAddress("CUSTOM_TRUST_MANAGER_ACCEPT_CERT_SIGNED_BY_TRUSTED_CA");
    Lock clientLock = new ReentrantLock();
    Lock serverLock = new ReentrantLock();
    ByteBuf buffer = Unpooled.buffer();
    Exception clientException = new Exception();
    Exception serverException = new Exception();

    // Generate a new key pair.
    KeyPair keyPair = getKeyPair();

    // Generate a self signed certificate, and use it to sign the key pair.
    SelfSignedCertificate ssc = new SelfSignedCertificate();
    X509Certificate cert = signKeyPair(ssc, keyPair, SSL_HOST);

    // Set up the server to use the signed cert and private key to perform handshake;
    PrivateKey privateKey = keyPair.getPrivate();
    EventLoopGroup eventLoopGroup =
        setUpServer(
            getServerInitializer(privateKey, cert, serverLock, serverException), localAddress);

    // Set up the client to trust the self signed cert used to sign the cert that server provides.
    SslClientInitializer<LocalChannel> sslClientInitializer =
        new SslClientInitializer<>(SslProvider.JDK, ssc.cert());
    Channel channel =
        setUpClient(
            eventLoopGroup,
            getClientInitializer(sslClientInitializer, clientLock, buffer, clientException),
            localAddress,
            PROTOCOL);

    verifySslChannel(channel, ImmutableList.of(cert), clientLock, serverLock, buffer, SSL_HOST);

    Future<?> unusedFuture = eventLoopGroup.shutdownGracefully().syncUninterruptibly();
  }

  @Test
  public void testFailure_customTrustManager_wrongHostnameInCertificate() throws Exception {
    LocalAddress localAddress = new LocalAddress("CUSTOM_TRUST_MANAGER_WRONG_HOSTNAME");
    Lock clientLock = new ReentrantLock();
    Lock serverLock = new ReentrantLock();
    ByteBuf buffer = Unpooled.buffer();
    Exception clientException = new Exception();
    Exception serverException = new Exception();

    // Generate a new key pair.
    KeyPair keyPair = getKeyPair();

    // Generate a self signed certificate, and use it to sign the key pair.
    SelfSignedCertificate ssc = new SelfSignedCertificate();
    X509Certificate cert = signKeyPair(ssc, keyPair, "wrong.com");

    // Set up the server to use the signed cert and private key to perform handshake;
    PrivateKey privateKey = keyPair.getPrivate();
    EventLoopGroup eventLoopGroup =
        setUpServer(
            getServerInitializer(privateKey, cert, serverLock, serverException), localAddress);

    // Set up the client to trust the self signed cert used to sign the cert that server provides.
    SslClientInitializer<LocalChannel> sslClientInitializer =
        new SslClientInitializer<>(SslProvider.JDK, ssc.cert());
    Channel channel =
        setUpClient(
            eventLoopGroup,
            getClientInitializer(sslClientInitializer, clientLock, buffer, clientException),
            localAddress,
            PROTOCOL);

    serverLock.lock();
    clientLock.lock();

    // When the client rejects the server cert due to wrong hostname, the client error is wrapped
    // several layers in the exception. The server also throws an exception.
    assertThat(clientException).hasCauseThat().isInstanceOf(DecoderException.class);
    assertThat(clientException)
        .hasCauseThat()
        .hasCauseThat()
        .isInstanceOf(SSLHandshakeException.class);
    assertThat(clientException)
        .hasCauseThat()
        .hasCauseThat()
        .hasCauseThat()
        .isInstanceOf(SSLHandshakeException.class);
    assertThat(clientException)
        .hasCauseThat()
        .hasCauseThat()
        .hasCauseThat()
        .hasCauseThat()
        .isInstanceOf(CertificateException.class);
    assertThat(clientException)
        .hasCauseThat()
        .hasCauseThat()
        .hasCauseThat()
        .hasCauseThat()
        .hasMessageThat()
        .contains(SSL_HOST);
    assertThat(serverException).hasCauseThat().isInstanceOf(DecoderException.class);
    assertThat(serverException).hasCauseThat().hasCauseThat().isInstanceOf(SSLException.class);
    //assertThat(channel.isActive()).isFalse();

    Future<?> unusedFuture = eventLoopGroup.shutdownGracefully().syncUninterruptibly();
  }
}
