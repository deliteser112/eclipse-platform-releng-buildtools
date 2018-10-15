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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import google.registry.beam.spec11.Spec11Pipeline;
import google.registry.beam.spec11.ThreatMatch;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import google.registry.reporting.spec11.Spec11Module.Spec11ReportDirectory;
import google.registry.util.Retrier;
import google.registry.util.SendEmailService;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.joda.time.YearMonth;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Provides e-mail functionality for Spec11 tasks, such as sending Spec11 reports to registrars. */
public class Spec11EmailUtils {

  private final SendEmailService emailService;
  private final YearMonth yearMonth;
  private final String alertSenderAddress;
  private final String alertRecipientAddress;
  private final String spec11ReplyToAddress;
  private final String reportingBucket;
  private final String spec11ReportDirectory;
  private final String spec11EmailBodyTemplate;
  private final GcsUtils gcsUtils;
  private final Retrier retrier;

  @Inject
  Spec11EmailUtils(
      SendEmailService emailService,
      YearMonth yearMonth,
      @Config("alertSenderEmailAddress") String alertSenderAddress,
      @Config("alertRecipientEmailAddress") String alertRecipientAddress,
      @Config("spec11ReplyToEmailAddress") String spec11ReplyToAddress,
      @Config("spec11EmailBodyTemplate") String spec11EmailBodyTemplate,
      @Config("reportingBucket") String reportingBucket,
      @Spec11ReportDirectory String spec11ReportDirectory,
      GcsUtils gcsUtils,
      Retrier retrier) {
    this.emailService = emailService;
    this.yearMonth = yearMonth;
    this.alertSenderAddress = alertSenderAddress;
    this.alertRecipientAddress = alertRecipientAddress;
    this.spec11ReplyToAddress = spec11ReplyToAddress;
    this.reportingBucket = reportingBucket;
    this.spec11ReportDirectory = spec11ReportDirectory;
    this.spec11EmailBodyTemplate = spec11EmailBodyTemplate;
    this.gcsUtils = gcsUtils;
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
            // Grab the file as an inputstream
            GcsFilename spec11ReportFilename =
                new GcsFilename(reportingBucket, spec11ReportDirectory);
            try (InputStream in = gcsUtils.openInputStream(spec11ReportFilename)) {
              ImmutableList<String> reportLines =
                  ImmutableList.copyOf(
                      CharStreams.toString(new InputStreamReader(in, UTF_8)).split("\n"));
              // Iterate from 1 to size() to skip the header at line 0.
              for (int i = 1; i < reportLines.size(); i++) {
                emailRegistrar(reportLines.get(i));
              }
            }
          },
          IOException.class,
          MessagingException.class);
    } catch (Throwable e) {
      // Send an alert with the root cause, unwrapping the retrier's RuntimeException
      sendAlertEmail(
          String.format("Spec11 Emailing Failure %s", yearMonth.toString()),
          String.format(
              "Emailing spec11 reports failed due to %s",
              getRootCause(e).getMessage()));
      throw new RuntimeException("Emailing spec11 report failed", e);
    }
    sendAlertEmail(
        String.format("Spec11 Pipeline Success %s", yearMonth.toString()),
        "Spec11 reporting completed successfully.");
  }

  private void emailRegistrar(String line) throws MessagingException, JSONException {
    // Parse the Spec11 report JSON
    JSONObject reportJSON = new JSONObject(line);
    String registrarEmail = reportJSON.getString(Spec11Pipeline.REGISTRAR_EMAIL_FIELD);
    JSONArray threatMatches = reportJSON.getJSONArray(Spec11Pipeline.THREAT_MATCHES_FIELD);
    StringBuilder threatList = new StringBuilder();
    for (int i = 0; i < threatMatches.length(); i++) {
      ThreatMatch threatMatch = ThreatMatch.fromJSON(threatMatches.getJSONObject(i));
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
    msg.setText(body.toString());
    msg.setFrom(new InternetAddress(alertSenderAddress));
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
            msg.setFrom(new InternetAddress(alertSenderAddress));
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
