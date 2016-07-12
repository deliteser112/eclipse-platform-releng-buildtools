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

package google.registry.model.eppcommon;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.BaseEncoding;
import com.googlecode.objectify.annotation.Embed;
import google.registry.model.ImmutableObject;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * "A {@code TRID} (transaction identifier) element containing the transaction identifier assigned
 * by the server to the command for which the response is being returned. The transaction identifier
 * is formed using the {@code clTRID} associated with the command if supplied by the client and a
 * {@code svTRID} (server transaction identifier) that is assigned by and unique to the server."
 */
@Embed
@XmlType(propOrder = {"clientTransactionId", "serverTransactionId"})
public class Trid extends ImmutableObject {

  private static final String SERVER_ID = getServerId();
  private static final AtomicLong COUNTER = new AtomicLong();

  /** Creates a unique id for this server instance, as a base64 encoded UUID. */
  private static String getServerId() {
    UUID uuid = UUID.randomUUID();
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.asLongBuffer()
        .put(0, uuid.getMostSignificantBits())
        .put(1, uuid.getLeastSignificantBits());
    return BaseEncoding.base64().encode(buffer.array());
  }

  /** The server transaction id. */
  @XmlElement(name = "svTRID", namespace = "urn:ietf:params:xml:ns:epp-1.0")
  String serverTransactionId;

  /** The client transaction id, if provided by the client, otherwise null. */
  @XmlElement(name = "clTRID", namespace = "urn:ietf:params:xml:ns:epp-1.0")
  String clientTransactionId;

  public String getServerTransactionId() {
    return serverTransactionId;
  }

  public String getClientTransactionId() {
    return clientTransactionId;
  }

  public static Trid create(String clientTransactionId) {
    Trid instance = new Trid();
    instance.clientTransactionId = clientTransactionId;
    // The server id can be at most 64 characters. The SERVER_ID is at most 22 characters (128 bits
    // in base64), plus the dash. That leaves 41 characters, so we just append the counter in hex.
    instance.serverTransactionId = String.format("%s-%x", SERVER_ID, COUNTER.incrementAndGet());
    return instance;
  }

  @VisibleForTesting
  public static Trid create(String clientTransactionId, String serverTransactionId) {
    Trid instance = new Trid();
    instance.clientTransactionId = clientTransactionId;
    instance.serverTransactionId = serverTransactionId;
    return instance;
  }
}
