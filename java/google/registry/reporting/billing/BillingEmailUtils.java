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

package google.registry.reporting.billing;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import google.registry.reporting.billing.BillingModule.InvoiceDirectoryPrefix;
import google.registry.util.FormattingLogger;
import google.registry.util.Retrier;
import google.registry.util.SendEmailService;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.inject.Inject;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import org.joda.time.YearMonth;

/** Utility functions for sending emails involving monthly invoices. */
class BillingEmailUtils {

  private final SendEmailService emailService;
  private final YearMonth yearMonth;
  private final String alertSenderAddress;
  private final String alertRecipientAddress;
  private final ImmutableList<String> invoiceEmailRecipients;
  private final String billingBucket;
  private final String invoiceDirectoryPrefix;
  private final GcsUtils gcsUtils;
  private final Retrier retrier;

  @Inject
  BillingEmailUtils(
      SendEmailService emailService,
      YearMonth yearMonth,
      @Config("alertSenderEmailAddress") String alertSenderAddress,
      @Config("alertRecipientEmailAddress") String alertRecipientAddress,
      @Config("invoiceEmailRecipients") ImmutableList<String> invoiceEmailRecipients,
      @Config("billingBucket") String billingBucket,
      @InvoiceDirectoryPrefix String invoiceDirectoryPrefix,
      GcsUtils gcsUtils,
      Retrier retrier) {
    this.emailService = emailService;
    this.yearMonth = yearMonth;
    this.alertSenderAddress = alertSenderAddress;
    this.alertRecipientAddress = alertRecipientAddress;
    this.invoiceEmailRecipients = invoiceEmailRecipients;
    this.billingBucket = billingBucket;
    this.invoiceDirectoryPrefix = invoiceDirectoryPrefix;
    this.gcsUtils = gcsUtils;
    this.retrier = retrier;
  }

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** Sends an e-mail to all expected recipients with an attached overall invoice from GCS. */
  void emailOverallInvoice() {
    retrier.callWithRetry(
        () -> {
          String invoiceFile =
              String.format(
                  "%s-%s.csv", BillingModule.OVERALL_INVOICE_PREFIX, yearMonth.toString());
          GcsFilename invoiceFilename =
              new GcsFilename(billingBucket, invoiceDirectoryPrefix + invoiceFile);
          try (InputStream in = gcsUtils.openInputStream(invoiceFilename)) {
            Message msg = emailService.createMessage();
            msg.setFrom(new InternetAddress(alertSenderAddress));
            for (String recipient : invoiceEmailRecipients) {
              msg.addRecipient(RecipientType.TO, new InternetAddress(recipient));
            }
            msg.setSubject(String.format("Domain Registry invoice data %s", yearMonth.toString()));
            Multipart multipart = new MimeMultipart();
            BodyPart textPart = new MimeBodyPart();
            textPart.setText(
                String.format(
                    "Attached is the %s invoice for the domain registry.", yearMonth.toString()));
            multipart.addBodyPart(textPart);
            BodyPart invoicePart = new MimeBodyPart();
            String invoiceData = CharStreams.toString(new InputStreamReader(in, UTF_8));
            invoicePart.setContent(invoiceData, "text/csv; charset=utf-8");
            invoicePart.setFileName(invoiceFile);
            multipart.addBodyPart(invoicePart);
            msg.setContent(multipart);
            msg.saveChanges();
            emailService.sendMessage(msg);
          }
        },
        new Retrier.FailureReporter() {
          @Override
          public void beforeRetry(Throwable thrown, int failures, int maxAttempts) {}

          @Override
          public void afterFinalFailure(Throwable thrown, int failures) {
            sendAlertEmail(
                String.format("Emailing invoice failed due to %s", thrown.getMessage()));
          }
        },
        IOException.class,
        MessagingException.class);
  }

  /** Sends an e-mail to the provided alert e-mail address indicating a billing failure. */
  void sendAlertEmail(String body) {
    retrier.callWithRetry(
        () -> {
          Message msg = emailService.createMessage();
          msg.setFrom(new InternetAddress(alertSenderAddress));
          msg.addRecipient(RecipientType.TO, new InternetAddress(alertRecipientAddress));
          msg.setSubject(String.format("Billing Pipeline Alert: %s", yearMonth.toString()));
          msg.setText(body);
          emailService.sendMessage(msg);
          return null;
        },
        new Retrier.FailureReporter() {
          @Override
          public void beforeRetry(Throwable thrown, int failures, int maxAttempts) {}

          @Override
          public void afterFinalFailure(Throwable thrown, int failures) {
            logger.severe(thrown, "The alert e-mail system failed.");
          }
        },
        MessagingException.class);
  }
}
