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
import static google.registry.proxy.handler.RelayHandler.RELAY_CHANNEL_KEY;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RelayHandler}. */
@RunWith(JUnit4.class)
public class RelayHandlerTest {

  private static final class ExpectedType {}

  private static final class OtherType {}

  private final RelayHandler<ExpectedType> relayHandler = new RelayHandler<>(ExpectedType.class);
  private final EmbeddedChannel inboundChannel = new EmbeddedChannel(relayHandler);
  private final EmbeddedChannel outboundChannel = new EmbeddedChannel();

  @Before
  public void setUp() {
    inboundChannel.attr(RELAY_CHANNEL_KEY).set(outboundChannel);
  }

  @Test
  public void testSuccess_relayInboundMessageOfExpectedType() {
    ExpectedType inboundMessage = new ExpectedType();
    // Relay handler intercepted the message, no further inbound message.
    assertThat(inboundChannel.writeInbound(inboundMessage)).isFalse();
    // Message wrote to outbound channel as-is.
    ExpectedType relayedMessage = outboundChannel.readOutbound();
    assertThat(relayedMessage).isEqualTo(inboundMessage);
  }

  @Test
  public void testSuccess_ignoreInboundMessageOfOtherType() {
    OtherType inboundMessage = new OtherType();
    // Relay handler ignores inbound message of other types, the inbound message is passed along.
    assertThat(inboundChannel.writeInbound(inboundMessage)).isTrue();
    // Nothing is written into the outbound channel.
    ExpectedType relayedMessage = outboundChannel.readOutbound();
    assertThat(relayedMessage).isNull();
  }

  @Test
  public void testSuccess_disconnectIfRelayIsUnsuccessful() {
    ExpectedType inboundMessage = new ExpectedType();
    // Outbound channel is closed.
    outboundChannel.finish();
    assertThat(inboundChannel.writeInbound(inboundMessage)).isFalse();
    ExpectedType relayedMessage = outboundChannel.readOutbound();
    assertThat(relayedMessage).isNull();
    // Inbound channel is closed as well.
    assertThat(inboundChannel.isActive()).isFalse();
  }

  @Test
  public void testSuccess_disconnectRelayChannelIfInactive() {
    ChannelFuture unusedFuture = inboundChannel.close();
    assertThat(outboundChannel.isActive()).isFalse();
  }

  @Test
  public void testSuccess_channelRead_relayNotSet() {
    ExpectedType inboundMessage = new ExpectedType();
    inboundChannel.attr(RELAY_CHANNEL_KEY).set(null);
    // Nothing to read.
    assertThat(inboundChannel.writeInbound(inboundMessage)).isFalse();
    // Inbound channel is closed.
    assertThat(inboundChannel.isActive()).isFalse();
  }
}
