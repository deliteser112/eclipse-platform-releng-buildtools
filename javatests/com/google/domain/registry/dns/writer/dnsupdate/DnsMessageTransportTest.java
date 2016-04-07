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

package com.google.domain.registry.dns.writer.dnsupdate;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.VerifyException;

import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;
import org.xbill.DNS.Update;
import org.xbill.DNS.utils.base16;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.net.SocketFactory;

/** Unit tests for {@link DnsMessageTransport}. */
@RunWith(MockitoJUnitRunner.class)
public class DnsMessageTransportTest {

  private static final String UPDATE_HOST = "127.0.0.1";

  @Mock private SocketFactory mockFactory;
  @Mock private Socket mockSocket;
  private Message simpleQuery;
  private Message expectedResponse;
  private DnsMessageTransport resolver;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void before() throws Exception {
    simpleQuery =
        Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN));
    expectedResponse = responseMessageWithCode(simpleQuery, Rcode.NOERROR);
    when(mockFactory.createSocket(InetAddress.getByName(UPDATE_HOST), DnsMessageTransport.DNS_PORT))
        .thenReturn(mockSocket);
    resolver = new DnsMessageTransport(mockFactory, UPDATE_HOST, Duration.ZERO);
  }

  @Test
  public void sentMessageHasCorrectLengthAndContent() throws Exception {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(messageToBytesWithLength(expectedResponse));
    when(mockSocket.getInputStream()).thenReturn(inputStream);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    when(mockSocket.getOutputStream()).thenReturn(outputStream);

    resolver.send(simpleQuery);

    ByteBuffer sentMessage = ByteBuffer.wrap(outputStream.toByteArray());
    int messageLength = sentMessage.getShort();
    byte[] messageData = new byte[messageLength];
    sentMessage.get(messageData);
    assertThat(messageLength).isEqualTo(simpleQuery.toWire().length);
    assertThat(base16.toString(messageData)).isEqualTo(base16.toString(simpleQuery.toWire()));
  }

  @Test
  public void receivedMessageWithLengthHasCorrectContent() throws Exception {
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(messageToBytesWithLength(expectedResponse));
    when(mockSocket.getInputStream()).thenReturn(inputStream);
    when(mockSocket.getOutputStream()).thenReturn(new ByteArrayOutputStream());

    Message actualResponse = resolver.send(simpleQuery);

    assertThat(base16.toString(actualResponse.toWire()))
        .isEqualTo(base16.toString(expectedResponse.toWire()));
  }

  @Test
  public void eofReceivingResponse() throws Exception {
    byte[] messageBytes = messageToBytesWithLength(expectedResponse);
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(Arrays.copyOf(messageBytes, messageBytes.length - 1));
    when(mockSocket.getInputStream()).thenReturn(inputStream);
    when(mockSocket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    thrown.expect(EOFException.class);

    Message expectedQuery = new Message();
    resolver.send(expectedQuery);
  }

  @Test
  public void timeoutReceivingResponse() throws Exception {
    InputStream mockInputStream = mock(InputStream.class);
    when(mockInputStream.read()).thenThrow(new SocketTimeoutException("testing"));
    when(mockSocket.getInputStream()).thenReturn(mockInputStream);
    when(mockSocket.getOutputStream()).thenReturn(new ByteArrayOutputStream());

    Duration testTimeout = Duration.standardSeconds(1);
    DnsMessageTransport resolver = new DnsMessageTransport(mockFactory, UPDATE_HOST, testTimeout);
    Message expectedQuery = new Message();
    try {
      resolver.send(expectedQuery);
      fail("exception expected");
    } catch (SocketTimeoutException e) {
      verify(mockSocket).setSoTimeout((int) testTimeout.getMillis());
    }
  }

  @Test
  public void sentMessageTooLongThrowsException() throws Exception {
    Update oversize = new Update(Name.fromString("tld", Name.root));
    for (int i = 0; i < 2000; i++) {
      oversize.add(
          ARecord.newRecord(
              Name.fromString("test-extremely-long-name-" + i + ".tld", Name.root),
              Type.A,
              DClass.IN));
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    when(mockSocket.getOutputStream()).thenReturn(outputStream);
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("message larger than maximum");

    resolver.send(oversize);
  }

  @Test
  public void responseIdMismatchThrowsExeption() throws Exception {
    expectedResponse.getHeader().setID(1 + simpleQuery.getHeader().getID());
    when(mockSocket.getInputStream())
        .thenReturn(new ByteArrayInputStream(messageToBytesWithLength(expectedResponse)));
    when(mockSocket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    thrown.expect(VerifyException.class);
    thrown.expectMessage(
        "response ID "
            + expectedResponse.getHeader().getID()
            + " does not match query ID "
            + simpleQuery.getHeader().getID());

    resolver.send(simpleQuery);
  }

  @Test
  public void responseOpcodeMismatchThrowsException() throws Exception {
    simpleQuery.getHeader().setOpcode(Opcode.QUERY);
    expectedResponse.getHeader().setOpcode(Opcode.STATUS);
    when(mockSocket.getInputStream())
        .thenReturn(new ByteArrayInputStream(messageToBytesWithLength(expectedResponse)));
    when(mockSocket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    thrown.expect(VerifyException.class);
    thrown.expectMessage("response opcode 'STATUS' does not match query opcode 'QUERY'");

    resolver.send(simpleQuery);
  }

  private Message responseMessageWithCode(Message query, int responseCode) {
    Message message = new Message(query.getHeader().getID());
    message.getHeader().setOpcode(query.getHeader().getOpcode());
    message.getHeader().setFlag(Flags.QR);
    message.getHeader().setRcode(responseCode);
    return message;
  }

  private byte[] messageToBytesWithLength(Message message) throws IOException {
    byte[] bytes = message.toWire();
    ByteBuffer buffer =
        ByteBuffer.allocate(bytes.length + DnsMessageTransport.MESSAGE_LENGTH_FIELD_BYTES);
    buffer.putShort((short) bytes.length);
    buffer.put(bytes);
    return buffer.array();
  }
}
