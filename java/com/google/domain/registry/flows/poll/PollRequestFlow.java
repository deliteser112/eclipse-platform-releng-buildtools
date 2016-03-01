// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.flows.poll;

import static com.google.domain.registry.model.eppoutput.Result.Code.SuccessWithAckMessage;
import static com.google.domain.registry.model.eppoutput.Result.Code.SuccessWithNoMessages;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.util.CollectionUtils.forceEmptyToNull;

import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.EppException.ParameterValueSyntaxErrorException;
import com.google.domain.registry.model.eppoutput.EppOutput;
import com.google.domain.registry.model.poll.MessageQueueInfo;
import com.google.domain.registry.model.poll.PollMessage;

import com.googlecode.objectify.Key;

import java.util.List;

/**
 * An EPP flow for requesting poll messages.
 *
 * @error {@link PollRequestFlow.UnexpectedMessageIdException}
 */
public class PollRequestFlow extends PollFlow {

  @Override
  public final EppOutput run() throws EppException {
    if (command.getMessageId() != null) {
      throw new UnexpectedMessageIdException();
    }

    List<Key<PollMessage>> pollMessageKeys = getMessageQueueKeysInOrder();
    // Retrieve the oldest message from the queue that still exists -- since the query is eventually
    // consistent, it may return keys to some entities that no longer exist.
    for (Key<PollMessage> key : pollMessageKeys) {
      PollMessage pollMessage = ofy().load().key(key).now();
      if (pollMessage != null) {
        return createOutput(
            SuccessWithAckMessage,
            MessageQueueInfo.create(
                pollMessage.getEventTime(),
                pollMessage.getMsg(),
                pollMessageKeys.size(),
                PollMessage.EXTERNAL_KEY_CONVERTER.convert(key)),
            forceEmptyToNull(pollMessage.getResponseData()),
            forceEmptyToNull(pollMessage.getResponseExtensions()));
      }
    }
    return createOutput(SuccessWithNoMessages);
  }

  /** Unexpected message id. */
  static class UnexpectedMessageIdException extends ParameterValueSyntaxErrorException {
    public UnexpectedMessageIdException() {
      super("Unexpected message id");
    }
  }
}
