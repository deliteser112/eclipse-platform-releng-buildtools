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

import com.google.common.flogger.FluentLogger;
import google.registry.proxy.CertificateModule.EppCertificates;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Adds a server side SSL handler to the channel pipeline.
 *
 * <p>This <b>should</b> be the first handler provided for any handler provider list, if it is
 * provided. Unless you wish to first process the PROXY header with {@link ProxyProtocolHandler},
 * which should come before this handler. The type parameter {@code C} is needed so that unit tests
 * can construct this handler that works with {@link EmbeddedChannel};
 *
 * <p>The ssl handler added requires client authentication, but it uses an {@link
 * InsecureTrustManagerFactory}, which accepts any ssl certificate presented by the client, as long
 * as the client uses the corresponding private key to establish SSL handshake. The client
 * certificate hash will be passed along to GAE as an HTTP header for verification (not handled by
 * this handler).
 */
@Singleton
@Sharable
public class SslServerInitializer<C extends Channel> extends ChannelInitializer<C> {

  /**
   * Attribute key to the client certificate promise whose value is set when SSL handshake completes
   * successfully.
   */
  public static final AttributeKey<Promise<X509Certificate>> CLIENT_CERTIFICATE_PROMISE_KEY =
      AttributeKey.valueOf("CLIENT_CERTIFICATE_PROMISE_KEY");

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final SslProvider sslProvider;
  private final PrivateKey privateKey;
  private final X509Certificate[] certificates;

  @Inject
  SslServerInitializer(
      SslProvider sslProvider,
      @EppCertificates PrivateKey privateKey,
      @EppCertificates X509Certificate... certificates) {
    logger.atInfo().log("Server SSL Provider: %s", sslProvider);
    this.sslProvider = sslProvider;
    this.privateKey = privateKey;
    this.certificates = certificates;
  }

  @Override
  protected void initChannel(C channel) throws Exception {
    SslHandler sslHandler =
        SslContextBuilder.forServer(privateKey, certificates)
            .sslProvider(sslProvider)
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .clientAuth(ClientAuth.REQUIRE)
            .build()
            .newHandler(channel.alloc());
    Promise<X509Certificate> clientCertificatePromise = channel.eventLoop().newPromise();
    Future<Channel> unusedFuture =
        sslHandler
            .handshakeFuture()
            .addListener(
                future -> {
                  if (future.isSuccess()) {
                    Promise<X509Certificate> unusedPromise =
                        clientCertificatePromise.setSuccess(
                            (X509Certificate)
                                sslHandler.engine().getSession().getPeerCertificates()[0]);
                  } else {
                    Promise<X509Certificate> unusedPromise =
                        clientCertificatePromise.setFailure(future.cause());
                  }
                });
    channel.attr(CLIENT_CERTIFICATE_PROMISE_KEY).set(clientCertificatePromise);
    channel.pipeline().addLast(sslHandler);
  }
}
