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

package google.registry.flows.poll;

import static google.registry.flows.poll.PollFlowUtils.getPollMessagesQuery;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_WITH_ACK_MESSAGE;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_WITH_NO_MESSAGES;
import static google.registry.util.CollectionUtils.forceEmptyToNull;

import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.ParameterValueSyntaxErrorException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.PollMessageId;
import google.registry.flows.LoggedInFlow;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.poll.MessageQueueInfo;
import google.registry.model.poll.PollMessage;
import javax.inject.Inject;

/**
 * An EPP flow for requesting poll messages.
 *
 * @error {@link PollRequestFlow.UnexpectedMessageIdException}
 */
public class PollRequestFlow extends LoggedInFlow {

  @Inject @ClientId String clientId;
  @Inject @PollMessageId String messageId;
  @Inject PollRequestFlow() {}

  @Override
  public final EppOutput run() throws EppException {
    if (!messageId.isEmpty()) {
      throw new UnexpectedMessageIdException();
    }
    // Return the oldest message from the queue.
    PollMessage pollMessage = getPollMessagesQuery(clientId, now).first().now();
    if (pollMessage == null) {
      return createOutput(SUCCESS_WITH_NO_MESSAGES);
    }
    return createOutput(
        SUCCESS_WITH_ACK_MESSAGE,
        forceEmptyToNull(pollMessage.getResponseData()),
        forceEmptyToNull(pollMessage.getResponseExtensions()),
        MessageQueueInfo.create(
            pollMessage.getEventTime(),
            pollMessage.getMsg(),
            getPollMessagesQuery(clientId, now).count(),
            PollMessage.EXTERNAL_KEY_CONVERTER.convert(Key.create(pollMessage))));
  }

  /** Unexpected message id. */
  static class UnexpectedMessageIdException extends ParameterValueSyntaxErrorException {
    public UnexpectedMessageIdException() {
      super("Unexpected message id");
    }
  }
}
