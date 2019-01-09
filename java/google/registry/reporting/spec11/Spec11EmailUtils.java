// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.reporting.spec11;

import static com.google.common.base.Throwables.getRootCause;

import google.registry.beam.spec11.ThreatMatch;
import google.registry.config.RegistryConfig.Config;
import google.registry.util.Retrier;
import google.registry.util.SendEmailService;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.joda.time.LocalDate;

/** Provides e-mail functionality for Spec11 tasks, such as sending Spec11 reports to registrars. */
public class Spec11EmailUtils {

  private final SendEmailService emailService;
  private final LocalDate date;
  private final String outgoingEmailAddress;
  private final String alertRecipientAddress;
  private final String spec11ReplyToAddress;
  private final Retrier retrier;

  @Inject
  Spec11EmailUtils(
      SendEmailService emailService,
      LocalDate date,
      @Config("gSuiteOutgoingEmailAddress") String outgoingEmailAddress,
      @Config("alertRecipientEmailAddress") String alertRecipientAddress,
      @Config("spec11ReplyToEmailAddress") String spec11ReplyToAddress,
      Retrier retrier) {
    this.emailService = emailService;
    this.date = date;
    this.outgoingEmailAddress = outgoingEmailAddress;
    this.alertRecipientAddress = alertRecipientAddress;
    this.spec11ReplyToAddress = spec11ReplyToAddress;
    this.retrier = retrier;
  }

  /**
   * Processes a list of registrar/list-of-threat pairings and sends a notification email to the
   * appropriate address.
   */
  void emailSpec11Reports(
      String spec11EmailBodyTemplate,
      String subject,
      List<RegistrarThreatMatches> registrarThreatMatchesList) {
    try {
      retrier.callWithRetry(
          () -> {
            for (RegistrarThreatMatches registrarThreatMatches : registrarThreatMatchesList) {
              emailRegistrar(spec11EmailBodyTemplate, subject, registrarThreatMatches);
            }
          },
          IOException.class,
          MessagingException.class);
    } catch (Throwable e) {
      // Send an alert with the root cause, unwrapping the retrier's RuntimeException
      sendAlertEmail(
          String.format("Spec11 Emailing Failure %s", date),
          String.format("Emailing spec11 reports failed due to %s", getRootCause(e).getMessage()));
      throw new RuntimeException("Emailing spec11 report failed", e);
    }
    sendAlertEmail(
        String.format("Spec11 Pipeline Success %s", date),
        "Spec11 reporting completed successfully.");
  }

  private void emailRegistrar(
      String spec11EmailBodyTemplate, String subject, RegistrarThreatMatches registrarThreatMatches)
      throws MessagingException {
    String registrarEmail = registrarThreatMatches.registrarEmailAddress();
    StringBuilder threatList = new StringBuilder();
    for (ThreatMatch threatMatch : registrarThreatMatches.threatMatches()) {
      threatList.append(
          String.format(
              "%s - %s\n", threatMatch.fullyQualifiedDomainName(), threatMatch.threatType()));
    }
    String body =
        spec11EmailBodyTemplate
            .replace("{REPLY_TO_EMAIL}", spec11ReplyToAddress)
            .replace("{LIST_OF_THREATS}", threatList.toString());
    Message msg = emailService.createMessage();
    msg.setSubject(subject);
    msg.setText(body);
    msg.setFrom(new InternetAddress(outgoingEmailAddress));
    msg.addRecipient(RecipientType.TO, new InternetAddress(registrarEmail));
    msg.addRecipient(RecipientType.BCC, new InternetAddress(spec11ReplyToAddress));
    emailService.sendMessage(msg);
  }

  /** Sends an e-mail indicating the state of the spec11 pipeline, with a given subject and body. */
  void sendAlertEmail(String subject, String body) {
    try {
      retrier.callWithRetry(
          () -> {
            Message msg = emailService.createMessage();
            msg.setFrom(new InternetAddress(outgoingEmailAddress));
            msg.addRecipient(RecipientType.TO, new InternetAddress(alertRecipientAddress));
            msg.setSubject(subject);
            msg.setText(body);
            emailService.sendMessage(msg);
            return null;
          },
          MessagingException.class);
    } catch (Throwable e) {
      throw new RuntimeException("The spec11 alert e-mail system failed.", e);
    }
  }
}
