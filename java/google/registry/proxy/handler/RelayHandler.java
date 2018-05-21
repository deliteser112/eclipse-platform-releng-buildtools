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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.flogger.FluentLogger;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import javax.inject.Inject;

/**
 * Receives inbound massage of type {@code I}, and writes it to the {@code relayChannel} stored in
 * the inbound channel's attribute.
 */
public class RelayHandler<I> extends SimpleChannelInboundHandler<I> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Key used to retrieve the relay channel from a {@link Channel}'s {@link Attribute}. */
  public static final AttributeKey<Channel> RELAY_CHANNEL_KEY =
      AttributeKey.valueOf("RELAY_CHANNEL");

  public RelayHandler(Class<? extends I> clazz) {
    super(clazz, false);
  }

  /** Terminate connection when an exception is caught during inbound IO. */
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    logger.atSevere().withCause(cause).log(
        "Inbound exception caught for channel %s", ctx.channel());
    ChannelFuture unusedFuture = ctx.close();
  }

  /** Close relay channel if this channel is closed. */
  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    Channel relayChannel = ctx.channel().attr(RELAY_CHANNEL_KEY).get();
    if (relayChannel != null) {
      ChannelFuture unusedFuture = relayChannel.close();
    }
    ctx.fireChannelInactive();
  }

  /** Read message of type {@code I}, write it as-is into the relay channel. */
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, I msg) throws Exception {
    Channel relayChannel = ctx.channel().attr(RELAY_CHANNEL_KEY).get();
    checkNotNull(relayChannel, "Relay channel not specified for channel: %s", ctx.channel());
    if (relayChannel.isActive()) {
      // Relay channel is open, write to it.
      ChannelFuture channelFuture = relayChannel.writeAndFlush(msg);
      channelFuture.addListener(
          future -> {
            // Cannot write into relay channel, close this channel.
            if (!future.isSuccess()) {
              ChannelFuture unusedFuture = ctx.close();
            }
          });
    } else {
      // close this channel if the relay channel is closed.
      ChannelFuture unusedFuture = ctx.close();
    }
  }

  /** Specialized {@link RelayHandler} that takes a {@link FullHttpRequest} as inbound payload. */
  public static class FullHttpRequestRelayHandler extends RelayHandler<FullHttpRequest> {
    @Inject
    public FullHttpRequestRelayHandler() {
      super(FullHttpRequest.class);
    }
  }

  /** Specialized {@link RelayHandler} that takes a {@link FullHttpResponse} as inbound payload. */
  public static class FullHttpResponseRelayHandler extends RelayHandler<FullHttpResponse> {
    @Inject
    public FullHttpResponseRelayHandler() {
      super(FullHttpResponse.class);
    }
  }
}
