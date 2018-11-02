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

package google.registry.reporting.icann;

import com.google.common.flogger.FluentLogger;
import google.registry.config.RegistryConfig.Config;
import google.registry.util.SendEmailService;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;

/** Static utils for emailing reporting results. */
public class ReportingEmailUtils {

  @Inject @Config("gSuiteOutgoingEmailAddress") String sender;
  @Inject @Config("alertRecipientEmailAddress") String recipient;
  @Inject SendEmailService emailService;
  @Inject ReportingEmailUtils() {}

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  void emailResults(String subject, String body) {
    try {
      Message msg = emailService.createMessage();
      logger.atInfo().log("Emailing %s", recipient);
      msg.setFrom(new InternetAddress(sender));
      msg.addRecipient(RecipientType.TO, new InternetAddress(recipient));
      msg.setSubject(subject);
      msg.setText(body);
      emailService.sendMessage(msg);
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("E-mail service failed.");
    }
  }
}
