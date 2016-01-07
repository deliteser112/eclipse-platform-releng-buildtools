// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.model.poll;

import static com.google.common.base.Preconditions.checkNotNull;

import google.registry.model.ImmutableObject;
import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import org.joda.time.DateTime;

/** Information about the message queue for the currently logged in registrar. */
public class MessageQueueInfo extends ImmutableObject {

  /** The date and time that the current message was enqueued. */
  @XmlElement(name = "qDate")
  DateTime queueDate;

  /** A human-readable message. */
  String msg;

  /** The number of messages currently in the queue. */
  @XmlAttribute(name = "count")
  Integer queueLength;

  /** The id of the message currently at the head of the queue. */
  @XmlAttribute(name = "id")
  String messageId;

  public DateTime getQueueDate() {
    return queueDate;
  }

  public String getMsg() {
    return msg;
  }

  public Integer getQueueLength() {
    return queueLength;
  }

  public String getMessageId() {
    return messageId;
  }

  public static MessageQueueInfo create(
      @Nullable DateTime queueDate,
      @Nullable String msg,
      Integer queueLength,
      String messageId) {
    MessageQueueInfo instance = new MessageQueueInfo();
    instance.queueDate = queueDate;
    instance.msg = msg;
    instance.queueLength = checkNotNull(queueLength);
    instance.messageId = checkNotNull(messageId);
    return instance;
  }
}
