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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.getRootCause;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.io.Resources.getResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.MediaType;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.SoyTofu.Renderer;
import google.registry.config.RegistryConfig.Config;
import google.registry.reporting.spec11.soy.Spec11EmailSoyInfo;
import google.registry.util.EmailMessage;
import google.registry.util.SendEmailService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.joda.time.LocalDate;

/** Provides e-mail functionality for Spec11 tasks, such as sending Spec11 reports to registrars. */
public class Spec11EmailUtils {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final SoyTofu SOY_SAUCE =
      SoyFileSet.builder()
          .add(
              getResource(
                  Spec11EmailSoyInfo.getInstance().getClass(),
                  Spec11EmailSoyInfo.getInstance().getFileName()))
          .build()
          .compileToTofu();

  private final SendEmailService emailService;
  private final InternetAddress outgoingEmailAddress;
  private final InternetAddress alertRecipientAddress;
  private final InternetAddress spec11ReplyToAddress;
  private final ImmutableList<String> spec11WebResources;
  private final String registryName;

  @Inject
  Spec11EmailUtils(
      SendEmailService emailService,
      @Config("gSuiteOutgoingEmailAddress") InternetAddress outgoingEmailAddress,
      @Config("alertRecipientEmailAddress") InternetAddress alertRecipientAddress,
      @Config("spec11ReplyToEmailAddress") InternetAddress spec11ReplyToAddress,
      @Config("spec11WebResources") ImmutableList<String> spec11WebResources,
      @Config("registryName") String registryName) {
    this.emailService = emailService;
    this.outgoingEmailAddress = outgoingEmailAddress;
    this.alertRecipientAddress = alertRecipientAddress;
    this.spec11ReplyToAddress = spec11ReplyToAddress;
    this.spec11WebResources = spec11WebResources;
    this.registryName = registryName;
  }

  /**
   * Processes a list of registrar/list-of-threat pairings and sends a notification email to the
   * appropriate address.
   */
  void emailSpec11Reports(
      LocalDate date,
      SoyTemplateInfo soyTemplateInfo,
      String subject,
      Set<RegistrarThreatMatches> registrarThreatMatchesSet) {
    // Null/empty email address shouldn't throw an exception, but we should warn if they occur.
    ImmutableSet<RegistrarThreatMatches> matchesWithoutEmails =
        registrarThreatMatchesSet.stream()
            .filter(matches -> isNullOrEmpty(matches.registrarEmailAddress()))
            .collect(toImmutableSet());
    try {
      for (RegistrarThreatMatches registrarThreatMatches :
          Sets.difference(registrarThreatMatchesSet, matchesWithoutEmails)) {
        emailRegistrar(date, soyTemplateInfo, subject, registrarThreatMatches);
      }
    } catch (Throwable e) {
      // Send an alert with the root cause, unwrapping the retrier's RuntimeException
      sendAlertEmail(
          String.format("Spec11 Emailing Failure %s", date),
          String.format("Emailing spec11 reports failed due to %s", getRootCause(e).getMessage()));
      throw new RuntimeException("Emailing spec11 report failed", e);
    }
    if (matchesWithoutEmails.isEmpty()) {
      sendAlertEmail(
          String.format("Spec11 Pipeline Success %s", date),
          "Spec11 reporting completed successfully.");
    } else {
      logger.atSevere().log(
          "Some Spec11 threat matches had no associated email addresses: %s", matchesWithoutEmails);
      // TODO(b/129401965): Use only client IDs in this message
      sendAlertEmail(
          String.format("Spec11 Pipeline Warning %s", date),
          String.format(
              "No errors occurred but the following matches had no associated email: \n"
                  + "%s\n\n"
                  + "This should not occur; please make sure we have email addresses for these"
                  + " registrar(s).",
              matchesWithoutEmails));
    }
  }

  private void emailRegistrar(
      LocalDate date,
      SoyTemplateInfo soyTemplateInfo,
      String subject,
      RegistrarThreatMatches registrarThreatMatches)
      throws MessagingException {
    emailService.sendEmail(
        EmailMessage.newBuilder()
            .setSubject(subject)
            .setBody(getContent(date, soyTemplateInfo, registrarThreatMatches))
            .setContentType(MediaType.HTML_UTF_8)
            .setFrom(outgoingEmailAddress)
            .addRecipient(new InternetAddress(registrarThreatMatches.registrarEmailAddress()))
            .setBcc(spec11ReplyToAddress)
            .build());
  }

  private String getContent(
      LocalDate date,
      SoyTemplateInfo soyTemplateInfo,
      RegistrarThreatMatches registrarThreatMatches) {
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
            "date", date.toString(),
            "registry", registryName,
            "replyToEmail", spec11ReplyToAddress.getAddress(),
            "threats", threatMatchMap,
            "resources", spec11WebResources);
    renderer.setData(data);
    return renderer.render();
  }

  /** Sends an e-mail indicating the state of the spec11 pipeline, with a given subject and body. */
  void sendAlertEmail(String subject, String body) {
    try {
      emailService.sendEmail(
          EmailMessage.newBuilder()
              .setFrom(outgoingEmailAddress)
              .addRecipient(alertRecipientAddress)
              .setBody(body)
              .setSubject(subject)
              .build());
    } catch (Throwable e) {
      throw new RuntimeException("The spec11 alert e-mail system failed.", e);
    }
  }
}
