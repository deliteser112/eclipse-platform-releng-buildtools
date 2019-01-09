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

package google.registry.ui.server;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.toArray;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.util.SendEmailService;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

/**
 * Utility class for sending emails from the app.
 */
public class SendEmailUtils {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String gSuiteOutgoingEmailAddress;
  private final String gSuiteOutgoingEmailDisplayName;
  private final SendEmailService emailService;
  private final ImmutableList<String> registrarChangesNotificationEmailAddresses;

  @Inject
  public SendEmailUtils(
      @Config("gSuiteOutgoingEmailAddress") String gSuiteOutgoingEmailAddress,
      @Config("gSuiteOutgoingEmailDisplayName") String gSuiteOutgoingEmailDisplayName,
      @Config("registrarChangesNotificationEmailAddresses")
          ImmutableList<String> registrarChangesNotificationEmailAddresses,
      SendEmailService emailService) {
    this.gSuiteOutgoingEmailAddress = gSuiteOutgoingEmailAddress;
    this.gSuiteOutgoingEmailDisplayName = gSuiteOutgoingEmailDisplayName;
    this.emailService = emailService;
    this.registrarChangesNotificationEmailAddresses = registrarChangesNotificationEmailAddresses;
  }

  /**
   * Sends an email from Nomulus to the registrarChangesNotificationEmailAddresses. Returns true iff
   * sending to at least 1 address was successful.
   *
   * <p>This means that if there are no recepients ({@link #hasRecepients} returns false), this will
   * return false even thought no error happened.
   *
   * <p>This also means that if there are multiple recepients, it will return true even if some (but
   * not all) of the recepients had an error.
   */
  public boolean sendEmail(final String subject, String body) {
    try {
      Message msg = emailService.createMessage();
      msg.setFrom(
          new InternetAddress(gSuiteOutgoingEmailAddress, gSuiteOutgoingEmailDisplayName));
      List<InternetAddress> emails =
          registrarChangesNotificationEmailAddresses.stream()
              .map(
                  emailAddress -> {
                    try {
                      return new InternetAddress(emailAddress, true);
                    } catch (AddressException e) {
                      logger.atSevere().withCause(e).log(
                          "Could not send email to %s with subject '%s'.", emailAddress, subject);
                      // Returning null excludes this address from the list of recipients on the
                      // email.
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .collect(toImmutableList());
      if (emails.isEmpty()) {
        return false;
      }
      msg.addRecipients(Message.RecipientType.TO, toArray(emails, InternetAddress.class));
      msg.setSubject(subject);
      msg.setText(body);
      emailService.sendMessage(msg);
    } catch (Throwable t) {
      logger.atSevere().withCause(t).log(
          "Could not email to addresses %s with subject '%s'.",
          Joiner.on(", ").join(registrarChangesNotificationEmailAddresses), subject);
      return false;
    }
    return true;
  }

  /**
   * Returns whether there are any recepients set up. {@link #sendEmail} will always return false if
   * there are no recepients.
   */
  public boolean hasRecepients() {
    return !registrarChangesNotificationEmailAddresses.isEmpty();
  }
}
