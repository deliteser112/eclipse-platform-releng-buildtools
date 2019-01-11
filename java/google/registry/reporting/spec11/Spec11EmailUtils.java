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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.io.Resources.getResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.SoyTofu.Renderer;
import google.registry.config.RegistryConfig.Config;
import google.registry.reporting.spec11.soy.Spec11EmailSoyInfo;
import google.registry.util.Retrier;
import google.registry.util.SendEmailService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.joda.time.LocalDate;

/** Provides e-mail functionality for Spec11 tasks, such as sending Spec11 reports to registrars. */
public class Spec11EmailUtils {

  private static final SoyTofu SOY_SAUCE =
      SoyFileSet.builder()
          .add(
              getResource(
                  Spec11EmailSoyInfo.getInstance().getClass(),
                  Spec11EmailSoyInfo.getInstance().getFileName()))
          .build()
          .compileToTofu();

  private final SendEmailService emailService;
  private final LocalDate date;
  private final String outgoingEmailAddress;
  private final String alertRecipientAddress;
  private final String spec11ReplyToAddress;
  private final ImmutableList<String> spec11WebResources;
  private final String registryName;
  private final Retrier retrier;

  @Inject
  Spec11EmailUtils(
      SendEmailService emailService,
      LocalDate date,
      @Config("gSuiteOutgoingEmailAddress") String outgoingEmailAddress,
      @Config("alertRecipientEmailAddress") String alertRecipientAddress,
      @Config("spec11ReplyToEmailAddress") String spec11ReplyToAddress,
      @Config("spec11WebResources") ImmutableList<String> spec11WebResources,
      @Config("registryName") String registryName,
      Retrier retrier) {
    this.emailService = emailService;
    this.date = date;
    this.outgoingEmailAddress = outgoingEmailAddress;
    this.alertRecipientAddress = alertRecipientAddress;
    this.spec11ReplyToAddress = spec11ReplyToAddress;
    this.spec11WebResources = spec11WebResources;
    this.registryName = registryName;
    this.retrier = retrier;
  }

  /**
   * Processes a list of registrar/list-of-threat pairings and sends a notification email to the
   * appropriate address.
   */
  void emailSpec11Reports(
      SoyTemplateInfo soyTemplateInfo,
      String subject,
      Set<RegistrarThreatMatches> registrarThreatMatchesSet) {
    try {
      retrier.callWithRetry(
          () -> {
            for (RegistrarThreatMatches registrarThreatMatches : registrarThreatMatchesSet) {
              emailRegistrar(soyTemplateInfo, subject, registrarThreatMatches);
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
      SoyTemplateInfo soyTemplateInfo,
      String subject,
      RegistrarThreatMatches registrarThreatMatches)
      throws MessagingException {
    Message msg = emailService.createMessage();
    msg.setSubject(subject);
    String content = getContent(soyTemplateInfo, registrarThreatMatches);
    msg.setContent(content, "text/html");
    msg.setHeader("Content-Type", "text/html");
    msg.setFrom(new InternetAddress(outgoingEmailAddress));
    msg.addRecipient(
        RecipientType.TO, new InternetAddress(registrarThreatMatches.registrarEmailAddress()));
    msg.addRecipient(RecipientType.BCC, new InternetAddress(spec11ReplyToAddress));
    emailService.sendMessage(msg);
  }

  private String getContent(
      SoyTemplateInfo soyTemplateInfo, RegistrarThreatMatches registrarThreatMatches) {
    Renderer renderer = SOY_SAUCE.newRenderer(soyTemplateInfo);
    // Soy templates require that data be in raw map/list form.
    List<Map<String, String>> threatMatchMap =
        registrarThreatMatches.threatMatches().stream()
            .map(
                threatMatch ->
                    ImmutableMap.of(
                        "fullyQualifiedDomainName", threatMatch.fullyQualifiedDomainName(),
                        "threatType", threatMatch.threatType()))
            .collect(toImmutableList());

    Map<String, Object> data =
        ImmutableMap.of(
            "registry", registryName,
            "replyToEmail", spec11ReplyToAddress,
            "threats", threatMatchMap,
            "resources", spec11WebResources);
    renderer.setData(data);
    return renderer.render();
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
