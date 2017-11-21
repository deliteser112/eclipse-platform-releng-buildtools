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
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link SslServerInitializer}.
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
public class SslServerInitializerTest {

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
      Lock serverLock,
      Exception serverException,
      PrivateKey privateKey,
      X509Certificate... certificates)
      throws Exception {
    return new ChannelInitializer<LocalChannel>() {
      @Override
      protected void initChannel(LocalChannel ch) throws Exception {
        ch.pipeline()
            .addLast(
                new SslServerInitializer<LocalChannel>(SslProvider.JDK, privateKey, certificates),
                new EchoHandler(serverLock, serverException));
      }
    };
  }

  private ChannelInitializer<LocalChannel> getClientInitializer(
      X509Certificate trustedCertificate,
      PrivateKey privateKey,
      X509Certificate certificate,
      Lock clientLock,
      ByteBuf buffer,
      Exception clientException) {
    return new ChannelInitializer<LocalChannel>() {
      @Override
      protected void initChannel(LocalChannel ch) throws Exception {
        SslContextBuilder sslContextBuilder =
            SslContextBuilder.forClient().trustManager(trustedCertificate);
        if (privateKey != null && certificate != null) {
          sslContextBuilder.keyManager(privateKey, certificate);
        }
        SslHandler sslHandler =
            sslContextBuilder.build().newHandler(ch.alloc(), SSL_HOST, SSL_PORT);

        // Enable hostname verification.
        SSLEngine sslEngine = sslHandler.engine();
        SSLParameters sslParameters = sslEngine.getSSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        sslEngine.setSSLParameters(sslParameters);

        ch.pipeline().addLast("Client SSL Handler", sslHandler);
        ch.pipeline().addLast(new DumpHandler(clientLock, buffer, clientException));
      }
    };
  }

  @Test
  public void testSuccess_swappedInitializerWithSslHandler() throws Exception {
    SelfSignedCertificate ssc = new SelfSignedCertificate(SSL_HOST);
    SslServerInitializer<EmbeddedChannel> sslServerInitializer =
        new SslServerInitializer<>(SslProvider.JDK, ssc.key(), ssc.cert());
    EmbeddedChannel channel = new EmbeddedChannel();
    ChannelPipeline pipeline = channel.pipeline();
    pipeline.addLast(sslServerInitializer);
    ChannelHandler firstHandler = pipeline.first();
    assertThat(firstHandler.getClass()).isEqualTo(SslHandler.class);
    SslHandler sslHandler = (SslHandler) firstHandler;
    assertThat(sslHandler.engine().getNeedClientAuth()).isTrue();
    assertThat(channel.isActive()).isTrue();
  }

  @Test
  public void testSuccess_trustAnyClientCert() throws Exception {
    SelfSignedCertificate serverSsc = new SelfSignedCertificate(SSL_HOST);
    LocalAddress localAddress = new LocalAddress("TRUST_ANY_CLIENT_CERT");
    Lock clientLock = new ReentrantLock();
    Lock serverLock = new ReentrantLock();
    ByteBuf buffer = Unpooled.buffer();
    Exception clientException = new Exception();
    Exception serverException = new Exception();
    EventLoopGroup eventLoopGroup =
        setUpServer(
            getServerInitializer(serverLock, serverException, serverSsc.key(), serverSsc.cert()),
            localAddress);
    SelfSignedCertificate clientSsc = new SelfSignedCertificate();
    Channel channel =
        setUpClient(
            eventLoopGroup,
            getClientInitializer(
                serverSsc.cert(),
                clientSsc.key(),
                clientSsc.cert(),
                clientLock,
                buffer,
                clientException),
            localAddress,
            PROTOCOL);

    SSLSession sslSession =
        verifySslChannel(
            channel, ImmutableList.of(serverSsc.cert()), clientLock, serverLock, buffer, SSL_HOST);
    // Verify that the SSL session gets the client cert. Note that this SslSession is for the client
    // channel, therefore its local certificates are the remote certificates of the SslSession for
    // the server channel, and vice versa.
    assertThat(sslSession.getLocalCertificates()).asList().containsExactly(clientSsc.cert());
    assertThat(sslSession.getPeerCertificates()).asList().containsExactly(serverSsc.cert());

    Future<?> unusedFuture = eventLoopGroup.shutdownGracefully().syncUninterruptibly();
  }

  @Test
  public void testSuccess_CertSignedByOtherCA() throws Exception {
    // The self-signed cert of the CA.
    SelfSignedCertificate caSsc = new SelfSignedCertificate();
    KeyPair keyPair = getKeyPair();
    X509Certificate serverCert = signKeyPair(caSsc, keyPair, SSL_HOST);
    LocalAddress localAddress = new LocalAddress("CERT_SIGNED_BY_OTHER_CA");
    Lock clientLock = new ReentrantLock();
    Lock serverLock = new ReentrantLock();
    ByteBuf buffer = Unpooled.buffer();
    Exception clientException = new Exception();
    Exception serverException = new Exception();
    EventLoopGroup eventLoopGroup =
        setUpServer(
            getServerInitializer(
                serverLock,
                serverException,
                keyPair.getPrivate(),
                // Serving both the server cert, and the CA cert
                serverCert,
                caSsc.cert()),
            localAddress);
    SelfSignedCertificate clientSsc = new SelfSignedCertificate();
    Channel channel =
        setUpClient(
            eventLoopGroup,
            getClientInitializer(
                // Client trusts the CA cert
                caSsc.cert(),
                clientSsc.key(),
                clientSsc.cert(),
                clientLock,
                buffer,
                clientException),
            localAddress,
            PROTOCOL);

    SSLSession sslSession =
        verifySslChannel(
            channel,
            ImmutableList.of(serverCert, caSsc.cert()),
            clientLock,
            serverLock,
            buffer,
            SSL_HOST);

    assertThat(sslSession.getLocalCertificates()).asList().containsExactly(clientSsc.cert());
    assertThat(sslSession.getPeerCertificates())
        .asList()
        .containsExactly(serverCert, caSsc.cert())
        .inOrder();

    Future<?> unusedFuture = eventLoopGroup.shutdownGracefully().syncUninterruptibly();
  }

  @Test
  public void testFailure_requireClientCertificate() throws Exception {
    SelfSignedCertificate serverSsc = new SelfSignedCertificate(SSL_HOST);
    LocalAddress localAddress = new LocalAddress("REQUIRE_CLIENT_CERT");
    Lock clientLock = new ReentrantLock();
    Lock serverLock = new ReentrantLock();
    ByteBuf buffer = Unpooled.buffer();
    Exception clientException = new Exception();
    Exception serverException = new Exception();
    EventLoopGroup eventLoopGroup =
        setUpServer(
            getServerInitializer(serverLock, serverException, serverSsc.key(), serverSsc.cert()),
            localAddress);
    Channel channel =
        setUpClient(
            eventLoopGroup,
            getClientInitializer(
                serverSsc.cert(),
                // No client cert/private key used.
                null,
                null,
                clientLock,
                buffer,
                clientException),
            localAddress,
            PROTOCOL);

    serverLock.lock();

    // When the server rejects the client during handshake due to lack of client certificate, only
    // the server throws an exception.
    assertThat(serverException).hasCauseThat().isInstanceOf(DecoderException.class);
    assertThat(serverException)
        .hasCauseThat()
        .hasCauseThat()
        .isInstanceOf(SSLHandshakeException.class);
    assertThat(channel.isActive()).isFalse();

    Future<?> unusedFuture = eventLoopGroup.shutdownGracefully().syncUninterruptibly();
  }

  @Test
  public void testFailure_wrongHostnameInCertificate() throws Exception {
    SelfSignedCertificate serverSsc = new SelfSignedCertificate("wrong.com");
    LocalAddress localAddress = new LocalAddress("REQUIRE_CLIENT_CERT");
    Lock clientLock = new ReentrantLock();
    Lock serverLock = new ReentrantLock();
    ByteBuf buffer = Unpooled.buffer();
    Exception clientException = new Exception();
    Exception serverException = new Exception();
    EventLoopGroup eventLoopGroup =
        setUpServer(
            getServerInitializer(serverLock, serverException, serverSsc.key(), serverSsc.cert()),
            localAddress);
    SelfSignedCertificate clientSsc = new SelfSignedCertificate();
    Channel channel =
        setUpClient(
            eventLoopGroup,
            getClientInitializer(
                serverSsc.cert(),
                clientSsc.key(),
                clientSsc.cert(),
                clientLock,
                buffer,
                clientException),
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
    assertThat(channel.isActive()).isFalse();

    Future<?> unusedFuture = eventLoopGroup.shutdownGracefully().syncUninterruptibly();
  }
}
