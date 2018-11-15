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

import static com.google.common.base.Throwables.getRootCause;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import google.registry.reporting.billing.BillingModule.InvoiceDirectoryPrefix;
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
  private final String outgoingEmailAddress;
  private final String alertRecipientAddress;
  private final ImmutableList<String> invoiceEmailRecipients;
  private final String billingBucket;
  private final String invoiceFilePrefix;
  private final String invoiceDirectoryPrefix;
  private final GcsUtils gcsUtils;
  private final Retrier retrier;

  @Inject
  BillingEmailUtils(
      SendEmailService emailService,
      YearMonth yearMonth,
      @Config("gSuiteOutgoingEmailAddress") String outgoingEmailAddress,
      @Config("alertRecipientEmailAddress") String alertRecipientAddress,
      @Config("invoiceEmailRecipients") ImmutableList<String> invoiceEmailRecipients,
      @Config("billingBucket") String billingBucket,
      @Config("invoiceFilePrefix") String invoiceFilePrefix,
      @InvoiceDirectoryPrefix String invoiceDirectoryPrefix,
      GcsUtils gcsUtils,
      Retrier retrier) {
    this.emailService = emailService;
    this.yearMonth = yearMonth;
    this.outgoingEmailAddress = outgoingEmailAddress;
    this.alertRecipientAddress = alertRecipientAddress;
    this.invoiceEmailRecipients = invoiceEmailRecipients;
    this.billingBucket = billingBucket;
    this.invoiceFilePrefix = invoiceFilePrefix;
    this.invoiceDirectoryPrefix = invoiceDirectoryPrefix;
    this.gcsUtils = gcsUtils;
    this.retrier = retrier;
  }

  /** Sends an e-mail to all expected recipients with an attached overall invoice from GCS. */
  void emailOverallInvoice() {
    try {
      retrier.callWithRetry(
          () -> {
            String invoiceFile =
                String.format("%s-%s.csv", invoiceFilePrefix, yearMonth);
            GcsFilename invoiceFilename =
                new GcsFilename(billingBucket, invoiceDirectoryPrefix + invoiceFile);
            try (InputStream in = gcsUtils.openInputStream(invoiceFilename)) {
              Message msg = emailService.createMessage();
              msg.setFrom(new InternetAddress(outgoingEmailAddress));
              for (String recipient : invoiceEmailRecipients) {
                msg.addRecipient(RecipientType.TO, new InternetAddress(recipient));
              }
              msg.setSubject(
                  String.format("Domain Registry invoice data %s", yearMonth));
              Multipart multipart = new MimeMultipart();
              BodyPart textPart = new MimeBodyPart();
              textPart.setText(
                  String.format(
                      "Attached is the %s invoice for the domain registry.", yearMonth));
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
          IOException.class,
          MessagingException.class);
    } catch (Throwable e) {
      // Strip one layer, because callWithRetry wraps in a RuntimeException
      sendAlertEmail(
          String.format(
              "Emailing invoice failed due to %s",
              getRootCause(e).getMessage()));
      throw new RuntimeException("Emailing invoice failed", e);
    }
  }

  /** Sends an e-mail to the provided alert e-mail address indicating a billing failure. */
  void sendAlertEmail(String body) {
    try {
      retrier.callWithRetry(
          () -> {
            Message msg = emailService.createMessage();
            msg.setFrom(new InternetAddress(outgoingEmailAddress));
            msg.addRecipient(RecipientType.TO, new InternetAddress(alertRecipientAddress));
            msg.setSubject(String.format("Billing Pipeline Alert: %s", yearMonth));
            msg.setText(body);
            emailService.sendMessage(msg);
            return null;
          },
          MessagingException.class);
    } catch (Throwable e) {
      throw new RuntimeException("The alert e-mail system failed.", e);
    }
  }
}
