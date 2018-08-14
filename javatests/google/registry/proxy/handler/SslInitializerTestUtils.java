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
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
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
import java.util.concurrent.CountDownLatch;
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

  /** Sets up a server channel bound to the given local address. */
  static void setUpServer(
      EventLoopGroup eventLoopGroup,
      ChannelInitializer<LocalChannel> serverInitializer,
      LocalAddress localAddress) {
    ServerBootstrap sb =
        new ServerBootstrap()
            .group(eventLoopGroup)
            .channel(LocalServerChannel.class)
            .childHandler(serverInitializer);
    ChannelFuture unusedFuture = sb.bind(localAddress).syncUninterruptibly();
  }

  /** Sets up a client channel connecting to the give local address. */
  static Channel setUpClient(
      EventLoopGroup eventLoopGroup,
      ChannelInitializer<LocalChannel> clientInitializer,
      LocalAddress localAddress,
      BackendProtocol protocol) {
    Bootstrap b =
        new Bootstrap()
            .group(eventLoopGroup)
            .channel(LocalChannel.class)
            .handler(clientInitializer)
            .attr(PROTOCOL_KEY, protocol);
    return b.connect(localAddress).syncUninterruptibly().channel();
  }

  /**
   * A handler that echoes back its inbound message. The message is also saved in a promise for
   * inspection later.
   */
  static class EchoHandler extends ChannelInboundHandlerAdapter {

    private final CountDownLatch latch = new CountDownLatch(1);
    private String request;
    private Throwable cause;

    void waitTillReady() throws InterruptedException {
      latch.await();
    }

    String getRequest() {
      return request;
    }

    Throwable getCause() {
      return cause;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      // In the test we only send messages of type ByteBuf.
      assertThat(msg).isInstanceOf(ByteBuf.class);
      request = ((ByteBuf) msg).toString(UTF_8);
      // After the message is written back to the client, fulfill the promise.
      ChannelFuture unusedFuture = ctx.writeAndFlush(msg).addListener(f -> latch.countDown());
    }

    /** Saves any inbound error as the cause of the promise failure. */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      this.cause = cause;
      ChannelFuture unusedFuture =
          ctx.channel()
              .closeFuture()
              .addListener(
                  // Apparently the JDK SSL provider will call #exceptionCaught twice with the same
                  // exception when the handshake fails. In this case the second listener should not
                  // set the promise again.
                  f -> {
                    if (latch.getCount() == 1) {
                      latch.countDown();
                    }
                  });
    }
  }

  /** A handler that dumps its inbound message to a promise that can be inspected later. */
  static class DumpHandler extends ChannelInboundHandlerAdapter {

    private final CountDownLatch latch = new CountDownLatch(1);
    private String response;
    private Throwable cause;

    void waitTillReady() throws InterruptedException {
      latch.await();
    }

    String getResponse() {
      return response;
    }

    Throwable getCause() {
      return cause;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      // In the test we only send messages of type ByteBuf.
      assertThat(msg).isInstanceOf(ByteBuf.class);
      response = ((ByteBuf) msg).toString(UTF_8);
      // There is no more use of this message, we should release its reference count so that it
      // can be more effectively garbage collected by Netty.
      ReferenceCountUtil.release(msg);
      // Save the string in the promise and make it as complete.
      latch.countDown();
    }

    /** Saves any inbound error into the failure cause of the promise. */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      this.cause = cause;
      ctx.channel()
          .closeFuture()
          .addListener(
              f -> {
                // Apparently the JDK SSL provider will call #exceptionCaught twice with the same
                // exception when the handshake fails. In this case the second listener should not
                // set the promise again.
                if (latch.getCount() == 1) {
                  latch.countDown();
                }
              });
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
      EchoHandler echoHandler,
      DumpHandler dumpHandler)
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

    // Test that message can go through, bound inbound and outbound.
    String inputString = "Hello, world!";
    // The client writes the message to the server, which echos it back and saves the string in its
    // promise. The client receives the echo and saves it in its promise. All these activities
    // happens in the I/O thread, and this call itself returns immediately.
    ChannelFuture unusedFuture =
        channel.writeAndFlush(
            Unpooled.wrappedBuffer(inputString.getBytes(StandardCharsets.US_ASCII)));

    // Wait for both the server and the client to finish processing.
    echoHandler.waitTillReady();
    dumpHandler.waitTillReady();

    // Checks that the message is transmitted faithfully.
    String requestReceived = echoHandler.getRequest();
    String responseReceived = dumpHandler.getResponse();
    assertThat(inputString).isEqualTo(requestReceived);
    assertThat(inputString).isEqualTo(responseReceived);

    // Returns the SSL session for further assertion.
    return sslHandler.engine().getSession();
  }
}
