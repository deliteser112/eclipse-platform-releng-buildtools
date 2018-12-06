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

import com.google.common.collect.ImmutableList;
import google.registry.beam.spec11.ThreatMatch;
import google.registry.config.RegistryConfig.Config;
import google.registry.util.Retrier;
import google.registry.util.SendEmailService;
import java.io.IOException;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.joda.time.YearMonth;

/** Provides e-mail functionality for Spec11 tasks, such as sending Spec11 reports to registrars. */
public class Spec11EmailUtils {

  private final SendEmailService emailService;
  private final YearMonth yearMonth;
  private final String outgoingEmailAddress;
  private final String alertRecipientAddress;
  private final String spec11ReplyToAddress;
  private final String spec11EmailBodyTemplate;
  private final Spec11RegistrarThreatMatchesParser spec11RegistrarThreatMatchesParser;
  private final Retrier retrier;

  @Inject
  Spec11EmailUtils(
      SendEmailService emailService,
      YearMonth yearMonth,
      @Config("gSuiteOutgoingEmailAddress") String outgoingEmailAddress,
      @Config("alertRecipientEmailAddress") String alertRecipientAddress,
      @Config("spec11ReplyToEmailAddress") String spec11ReplyToAddress,
      @Config("spec11EmailBodyTemplate") String spec11EmailBodyTemplate,
      Spec11RegistrarThreatMatchesParser spec11RegistrarThreatMatchesParser,
      Retrier retrier) {
    this.emailService = emailService;
    this.yearMonth = yearMonth;
    this.outgoingEmailAddress = outgoingEmailAddress;
    this.alertRecipientAddress = alertRecipientAddress;
    this.spec11ReplyToAddress = spec11ReplyToAddress;
    this.spec11RegistrarThreatMatchesParser = spec11RegistrarThreatMatchesParser;
    this.spec11EmailBodyTemplate = spec11EmailBodyTemplate;
    this.retrier = retrier;
  }

  /**
   * Processes a Spec11 report on GCS for a given month and e-mails registrars based on the
   * contents.
   */
  void emailSpec11Reports() {
    try {
      retrier.callWithRetry(
          () -> {
            ImmutableList<RegistrarThreatMatches> registrarThreatMatchesList =
                spec11RegistrarThreatMatchesParser.getRegistrarThreatMatches();
            for (RegistrarThreatMatches registrarThreatMatches : registrarThreatMatchesList) {
              emailRegistrar(registrarThreatMatches);
            }
          },
          IOException.class,
          MessagingException.class);
    } catch (Throwable e) {
      // Send an alert with the root cause, unwrapping the retrier's RuntimeException
      sendAlertEmail(
          String.format("Spec11 Emailing Failure %s", yearMonth.toString()),
          String.format("Emailing spec11 reports failed due to %s", getRootCause(e).getMessage()));
      throw new RuntimeException("Emailing spec11 report failed", e);
    }
    sendAlertEmail(
        String.format("Spec11 Pipeline Success %s", yearMonth.toString()),
        "Spec11 reporting completed successfully.");
  }

  private void emailRegistrar(RegistrarThreatMatches registrarThreatMatches)
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
    msg.setSubject(
        String.format("Google Registry Monthly Threat Detector [%s]", yearMonth.toString()));
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
