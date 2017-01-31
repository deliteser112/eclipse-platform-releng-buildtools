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

package google.registry.ui.server.registrar;

import static com.google.common.collect.Iterables.toArray;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import google.registry.config.RegistryConfig.Config;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import google.registry.util.SendEmailService;
import java.util.List;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

/**
 * Utility class for sending emails from the app.
 */
public class SendEmailUtils {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private final String gSuiteOutgoingEmailAddress;
  private final String gSuiteOutoingEmailDisplayName;

  @Inject
  public SendEmailUtils(
      @Config("gSuiteOutgoingEmailAddress") String gSuiteOutgoingEmailAddress,
      @Config("gSuiteOutoingEmailDisplayName") String gSuiteOutoingEmailDisplayName) {
    this.gSuiteOutgoingEmailAddress = gSuiteOutgoingEmailAddress;
    this.gSuiteOutoingEmailDisplayName = gSuiteOutoingEmailDisplayName;
  }

  @NonFinalForTesting
  private static SendEmailService emailService = new SendEmailService();

  /**
   * Sends an email from Nomulus to the specified recipient(s). Returns true iff sending was
   * successful.
   */
  public boolean sendEmail(Iterable<String> addresses, final String subject, String body) {
    try {
      Message msg = emailService.createMessage();
      msg.setFrom(
          new InternetAddress(gSuiteOutgoingEmailAddress, gSuiteOutoingEmailDisplayName));
      List<InternetAddress> emails = FluentIterable
          .from(addresses)
          .transform(new Function<String, InternetAddress>() {
            @Override
            public InternetAddress apply(String emailAddress) {
              try {
                return new InternetAddress(emailAddress, true);
              } catch (AddressException e) {
                logger.severefmt(
                    e,
                    "Could not send email to %s with subject '%s'.",
                    emailAddress,
                    subject);
                // Returning null excludes this address from the list of recipients on the email.
                return null;
              }
            }})
          .filter(Predicates.notNull())
          .toList();
      if (emails.isEmpty()) {
        return false;
      }
      msg.addRecipients(Message.RecipientType.TO, toArray(emails, InternetAddress.class));
      msg.setSubject(subject);
      msg.setText(body);
      emailService.sendMessage(msg);
    } catch (Throwable t) {
      logger.severefmt(
          t,
          "Could not email to addresses %s with subject '%s'.",
          Joiner.on(", ").join(addresses),
          subject);
      return false;
    }
    return true;
  }
}
