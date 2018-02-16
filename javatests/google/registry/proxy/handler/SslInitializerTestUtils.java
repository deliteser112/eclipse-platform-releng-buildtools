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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import google.registry.proxy.Protocol.BackendProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;

/**
 * Utility class that provides methods used by {@link SslClientInitializerTest} and {@link
 * SslServerInitializerTest}.
 */
public class SslInitializerTestUtils {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  /**
   * Sets up a server channel bound to the given local address.
   *
   * @return the event loop group used to process incoming connections.
   */
  static EventLoopGroup setUpServer(
      ChannelInitializer<LocalChannel> serverInitializer, LocalAddress localAddress)
      throws Exception {
    // Only use one thread in the event loop group. The same event loop group will be used to
    // register client channels during setUpClient as well. This ensures that all I/O activities
    // in both channels happen in the same thread, making debugging easier (i. e. no need to jump
    // between threads when debugging, everything happens synchronously within the only I/O thread
    // effectively). Note that the main thread is still separate from the I/O thread and
    // synchronization (using the lock field) is still needed when the main thread needs to verify
    // properties calculated by the I/O thread.
    EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
    ServerBootstrap sb =
        new ServerBootstrap()
            .group(eventLoopGroup)
            .channel(LocalServerChannel.class)
            .childHandler(serverInitializer);
    ChannelFuture unusedFuture = sb.bind(localAddress).syncUninterruptibly();
    return eventLoopGroup;
  }

  /**
   * Sets up a client channel connecting to the give local address.
   *
   * @param eventLoopGroup the same {@link EventLoopGroup} that is used to bootstrap server.
   * @return the connected client channel.
   */
  static Channel setUpClient(
      EventLoopGroup eventLoopGroup,
      ChannelInitializer<LocalChannel> clientInitializer,
      LocalAddress localAddress,
      BackendProtocol protocol)
      throws Exception {
    Bootstrap b =
        new Bootstrap()
            .group(eventLoopGroup)
            .channel(LocalChannel.class)
            .handler(clientInitializer)
            .attr(PROTOCOL_KEY, protocol);
    return b.connect(localAddress).syncUninterruptibly().channel();
  }

  /** A handler that echoes back its inbound message. Used in test server. */
  static class EchoHandler extends ChannelInboundHandlerAdapter {

    /**
     * A lock that synchronizes server I/O activity with the main thread. Acquired by the server I/O
     * thread when the handler is constructed, released when the server echoes back, or when an
     * exception is caught (during SSH handshake for example).
     */
    private final Lock lock;

    /**
     * Exception that would be initialized with the exception caught during SSL handshake. This
     * field is constructed in the main thread and passed in the constructor. After a failure the
     * main thread can inspect this object to assert the cause of the failure.
     */
    private final Exception serverException;

    EchoHandler(Lock lock, Exception serverException) {
      // This handler is constructed within getClientInitializer, which is called in the I/O thread.
      // The server lock is therefore locked by the I/O thread.
      lock.lock();
      this.lock = lock;
      this.serverException = serverException;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      // Always unlock regardless of whether the write is successful.
      ctx.writeAndFlush(msg).addListener(future -> lock.unlock());
    }

    /** Saves any inbound error into the server exception field. */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      serverException.initCause(cause);
      // If an exception is caught, we should also release the lock after the channel is closed
      // so that the main thread knows there is an exception to inspect now.
      ctx.channel().closeFuture().addListener(f -> lock.unlock());
    }
  }

  /** A handler that dumps its inbound message in to {@link ByteBuf}. */
  static class DumpHandler extends ChannelInboundHandlerAdapter {

    /**
     * A lock that synchronizes client I/O activity with the main thread. Acquired by the client I/O
     * thread when the handler is constructed, released when the client receives an response, or
     * when an exception is caught (during SSH handshake for example).
     */
    private final Lock lock;

    /**
     * A Buffer that is used to store incoming message. Constructed in the main thread and passed in
     * the constructor. The main thread can inspect this object to assert that the incoming message
     * is as expected.
     */
    private final ByteBuf buffer;

    /**
     * Exception that would be initialized with the exception caught during SSL handshake. This
     * field is constructed in the main thread and passed in the constructor. After a failure the
     * main thread can inspect this object to assert the cause of the failure.
     */
    private final Exception clientException;

    DumpHandler(Lock lock, ByteBuf buffer, Exception clientException) {
      super();
      // This handler is constructed within getClientInitializer, which is called in the I/O thread.
      // The client lock is therefore locked by the I/O thread.
      lock.lock();
      this.lock = lock;
      this.buffer = buffer;
      this.clientException = clientException;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      buffer.writeBytes((ByteBuf) msg);
      // If a message is received here, the main thread must be waiting to acquire the lock from
      // the I/O thread in order to verify it. Releasing the lock to notify the main thread it can
      // continue now that the message has been written.
      lock.unlock();
    }

    /** Saves any inbound error into clientException. */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      clientException.initCause(cause);
      // If an exception is caught here, the main thread must be waiting to acquire the lock from
      // the I/O thread in order to verify it. Releasing the lock after the channel is closed to
      // notify the main thread it can continue now that the exception has been written.
      ctx.channel().closeFuture().addListener(f -> lock.unlock());
    }
  }

  public static KeyPair getKeyPair() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
    keyPairGenerator.initialize(2048, new SecureRandom());
    return keyPairGenerator.generateKeyPair();
  }

  /**
   * Signs the given key pair with the given self signed certificate.
   *
   * @return signed public key (of the key pair) certificate
   */
  public static X509Certificate signKeyPair(
      SelfSignedCertificate ssc, KeyPair keyPair, String hostname) throws Exception {
    X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
    X500Principal dnName = new X500Principal("CN=" + hostname);
    certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
    certGen.setSubjectDN(dnName);
    certGen.setIssuerDN(ssc.cert().getSubjectX500Principal());
    certGen.setNotBefore(Date.from(Instant.now().minus(Duration.ofDays(1))));
    certGen.setNotAfter(Date.from(Instant.now().plus(Duration.ofDays(1))));
    certGen.setPublicKey(keyPair.getPublic());
    certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");
    return certGen.generate(ssc.key(), "BC");
  }

  /**
   * Verifies tha the SSL channel is established as expected, and also sends a message to the server
   * and verifies if it is echoed back correctly.
   *
   * @param certs The certificate that the server should provide.
   * @return The SSL session in current channel, can be used for further validation.
   */
  static SSLSession verifySslChannel(
      Channel channel,
      ImmutableList<X509Certificate> certs,
      Lock clientLock,
      Lock serverLock,
      ByteBuf buffer,
      String sniHostname)
      throws Exception {
    SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
    // Wait till the handshake is complete.
    sslHandler.handshakeFuture().get();

    assertThat(channel.isActive()).isTrue();
    assertThat(sslHandler.handshakeFuture().isSuccess()).isTrue();
    assertThat(sslHandler.engine().getSession().isValid()).isTrue();
    assertThat(sslHandler.engine().getSession().getPeerCertificates())
        .asList()
        .containsExactly(certs.toArray());
    // Verify that the client sent expected SNI name during handshake.
    assertThat(sslHandler.engine().getSSLParameters().getServerNames()).hasSize(1);
    assertThat(sslHandler.engine().getSSLParameters().getServerNames().get(0).getEncoded())
        .isEqualTo(sniHostname.getBytes(UTF_8));

    // Test that message can go through, bound inbound and outbound.
    String inputString = "Hello, world!";
    // The client writes the message to the server, which echos it back. The client receives the
    // echo and writes to BUFFER. All these activities happens in the I/O thread, and this call
    // returns immediately.
    ChannelFuture unusedFuture =
        channel.writeAndFlush(
            Unpooled.wrappedBuffer(inputString.getBytes(StandardCharsets.US_ASCII)));
    // The lock is acquired by the I/O thread when the client's DumpHandler is constructed.
    // Attempting to acquire it here blocks the main thread, until the I/O thread releases the lock
    // after the DumpHandler writes the echo back to the buffer.
    clientLock.lock();
    serverLock.lock();
    assertThat(buffer.toString(StandardCharsets.US_ASCII)).isEqualTo(inputString);
    return sslHandler.engine().getSession();
  }
}
