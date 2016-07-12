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

import static com.google.common.base.Preconditions.checkState;
import static google.registry.model.eppoutput.Result.Code.Success;
import static google.registry.model.eppoutput.Result.Code.SuccessWithNoMessages;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.isBeforeOrAt;

import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.EppException.AuthorizationErrorException;
import google.registry.flows.EppException.ObjectDoesNotExistException;
import google.registry.flows.EppException.ParameterValueSyntaxErrorException;
import google.registry.flows.EppException.RequiredParameterMissingException;
import google.registry.flows.TransactionalFlow;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.poll.MessageQueueInfo;
import google.registry.model.poll.PollMessage;
import google.registry.model.poll.PollMessageExternalKeyConverter.PollMessageExternalKeyParseException;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * An EPP flow for acknowledging poll messages.
 *
 * @error {@link PollAckFlow.InvalidMessageIdException}
 * @error {@link PollAckFlow.MessageDoesNotExistException}
 * @error {@link PollAckFlow.MissingMessageIdException}
 * @error {@link PollAckFlow.NotAuthorizedToAckMessageException}
 */
public class PollAckFlow extends PollFlow implements TransactionalFlow {

  @Inject PollAckFlow() {}

  @Override
  public final EppOutput run() throws EppException {
    if (command.getMessageId() == null) {
      throw new MissingMessageIdException();
    }

    Key<PollMessage> pollMessageKey;
    // Try parsing the messageId, and throw an exception if it's invalid.
    try {
      pollMessageKey = PollMessage.EXTERNAL_KEY_CONVERTER.reverse().convert(command.getMessageId());
    } catch (PollMessageExternalKeyParseException e) {
      throw new InvalidMessageIdException(command.getMessageId());
    }

    // Load the message to be acked. If a message is queued to be delivered in the future, we treat
    // it as if it doesn't exist yet.
    PollMessage pollMessage = ofy().load().key(pollMessageKey).now();
    if (pollMessage == null || !isBeforeOrAt(pollMessage.getEventTime(), now)) {
      throw new MessageDoesNotExistException(command.getMessageId());
    }

    // Make sure this client is authorized to ack this message. It could be that the message is
    // supposed to go to a different registrar.
    if (!getClientId().equals(pollMessage.getClientId())) {
      throw new NotAuthorizedToAckMessageException();
    }

    // This keeps track of whether we should include the current acked message in the updated
    // message count that's returned to the user. The only case where we do so is if an autorenew
    // poll message is acked, but its next event is already ready to be delivered.
    boolean includeAckedMessageInCount = false;
    if (pollMessage instanceof PollMessage.OneTime) {
      // One-time poll messages are deleted once acked.
      ofy().delete().entity(pollMessage);
    } else {
      checkState(pollMessage instanceof PollMessage.Autorenew, "Unknown poll message type");
      PollMessage.Autorenew autorenewPollMessage = (PollMessage.Autorenew) pollMessage;

      // Move the eventTime of this autorenew poll message forward by a year.
      DateTime nextEventTime = autorenewPollMessage.getEventTime().plusYears(1);

      // If the next event falls within the bounds of the end time, then just update the eventTime
      // and re-save it for future autorenew poll messages to be delivered. Otherwise, this
      // autorenew poll message has no more events to deliver and should be deleted.
      if (nextEventTime.isBefore(autorenewPollMessage.getAutorenewEndTime())) {
        ofy().save().entity(autorenewPollMessage.asBuilder().setEventTime(nextEventTime).build());
        includeAckedMessageInCount = isBeforeOrAt(nextEventTime, now);
      } else {
        ofy().delete().entity(autorenewPollMessage);
      }
    }
    // We need to return the new queue length. If this was the last message in the queue being
    // acked, then we return a special status code indicating that. Note that the query will
    // include the message being acked.
    int messageCount = getMessageQueueLength();
    if (!includeAckedMessageInCount) {
      messageCount--;
    }
    if (messageCount <= 0) {
      return createOutput(SuccessWithNoMessages);
    }

    return createOutput(
        Success,
        MessageQueueInfo.create(
            null,              // eventTime
            null,              // msg
            messageCount,
            command.getMessageId()),
        null,   // responseData
        null);  // extensions
  }

  /** Registrar is not authorized to ack this message. */
  static class NotAuthorizedToAckMessageException extends AuthorizationErrorException {
    public NotAuthorizedToAckMessageException() {
      super("Registrar is not authorized to ack this message");
    }
  }

  /** Message with this id does not exist. */
  public static class MessageDoesNotExistException extends ObjectDoesNotExistException {
    public MessageDoesNotExistException(String messageIdString) {
      super(PollMessage.class, messageIdString);
    }
  }

  /** Message id is invalid. */
  static class InvalidMessageIdException extends ParameterValueSyntaxErrorException {
    public InvalidMessageIdException(String messageIdStr) {
      super(String.format("Message id \"%s\" is invalid", messageIdStr));
    }
  }

  /** Message id is required. */
  static class MissingMessageIdException extends RequiredParameterMissingException {
    public MissingMessageIdException() {
      super("Message id is required");
    }
  }
}
