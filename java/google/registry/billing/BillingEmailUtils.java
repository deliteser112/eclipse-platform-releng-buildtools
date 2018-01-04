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

package google.registry.billing;

import com.google.common.collect.ImmutableList;
import google.registry.config.RegistryConfig.Config;
import google.registry.util.FormattingLogger;
import google.registry.util.SendEmailService;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.joda.time.YearMonth;

/** Utility functions for sending emails involving monthly invoices. */
class BillingEmailUtils {

  private final SendEmailService emailService;
  private final YearMonth yearMonth;
  private final String alertSenderAddress;
  private final ImmutableList<String> invoiceEmailRecipients;
  // TODO(larryruili): Replace this bucket after verifying 2017-12 output.
  private final String beamBucketUrl;

  @Inject
  BillingEmailUtils(
      SendEmailService emailService,
      YearMonth yearMonth,
      @Config("alertSenderEmailAddress") String alertSenderAddress,
      @Config("invoiceEmailRecipients") ImmutableList<String> invoiceEmailRecipients,
      @Config("apacheBeamBucketUrl") String beamBucketUrl) {
    this.emailService = emailService;
    this.yearMonth = yearMonth;
    this.alertSenderAddress = alertSenderAddress;
    this.invoiceEmailRecipients = invoiceEmailRecipients;
    this.beamBucketUrl = beamBucketUrl;
  }

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /**
   * Sends a link to the generated overall invoice in GCS.
   *
   * <p>Note the users receiving the e-mail should have access to the object or bucket, via an
   * authorization mechanism such as IAM.
   */
  void emailInvoiceLink() {
    // TODO(larryruili): Add read permissions for appropriate buckets.
    try {
      String beamBucket = beamBucketUrl.replaceFirst("gs://", "");
      Message msg = emailService.createMessage();
      msg.setFrom(new InternetAddress(alertSenderAddress));
      for (String recipient : invoiceEmailRecipients) {
        msg.addRecipient(RecipientType.TO, new InternetAddress(recipient));
      }
      msg.setSubject(String.format("Domain Registry invoice data %s", yearMonth.toString()));
      msg.setText(
          String.format(
              "Link to invoice on GCS:\nhttps://storage.cloud.google.com/%s/%s",
              beamBucket,
              String.format(
                  "%s%s-%s.csv",
                  BillingModule.RESULTS_DIRECTORY_PREFIX,
                  BillingModule.OVERALL_INVOICE_PREFIX,
                  yearMonth.toString())));
      emailService.sendMessage(msg);
    } catch (MessagingException e) {
      // TODO(larryruili): Replace with retrier with final failure email settings.
      logger.warning(e, "E-mail service failed due to %s");
    }
  }
}
