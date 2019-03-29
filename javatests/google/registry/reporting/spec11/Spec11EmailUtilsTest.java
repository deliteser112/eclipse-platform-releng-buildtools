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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.reporting.spec11.Spec11RegistrarThreatMatchesParserTest.sampleThreatMatches;
import static google.registry.testing.JUnitBackports.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import google.registry.reporting.spec11.soy.Spec11EmailSoyInfo;
import google.registry.util.EmailMessage;
import google.registry.util.SendEmailService;
import java.util.List;
import java.util.Optional;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link Spec11EmailUtils}. */
@RunWith(JUnit4.class)
public class Spec11EmailUtilsTest {

  private static final ImmutableList<String> FAKE_RESOURCES = ImmutableList.of("foo");

  private SendEmailService emailService;
  private Spec11EmailUtils emailUtils;
  private Spec11RegistrarThreatMatchesParser parser;
  private ArgumentCaptor<EmailMessage> contentCaptor;
  private final LocalDate date = new LocalDate(2018, 7, 15);

  @Before
  public void setUp() throws Exception {
    emailService = mock(SendEmailService.class);
    parser = mock(Spec11RegistrarThreatMatchesParser.class);
    when(parser.getRegistrarThreatMatches(date)).thenReturn(sampleThreatMatches());
    contentCaptor = ArgumentCaptor.forClass(EmailMessage.class);
    emailUtils =
        new Spec11EmailUtils(
            emailService,
            new InternetAddress("my-sender@test.com"),
            new InternetAddress("my-receiver@test.com"),
            new InternetAddress("my-reply-to@test.com"),
            FAKE_RESOURCES,
            "Super Cool Registry");
  }

  @Test
  public void testSuccess_emailMonthlySpec11Reports() throws Exception {
    emailUtils.emailSpec11Reports(
        date,
        Spec11EmailSoyInfo.MONTHLY_SPEC_11_EMAIL,
        "Super Cool Registry Monthly Threat Detector [2018-07-15]",
        sampleThreatMatches());
    // We inspect individual parameters because Message doesn't implement equals().
    verify(emailService, times(3)).sendEmail(contentCaptor.capture());
    List<EmailMessage> capturedContents = contentCaptor.getAllValues();
    String emailFormat =
        "Dear registrar partner,<p>Super Cool Registry previously notified you when the following "
            + "domains managed by your registrar were flagged for potential security concerns."
            + "</p><p>The following domains that you manage continue to be flagged by our analysis "
            + "for potential security concerns. This may be because the registrants have not "
            + "completed the requisite steps to mitigate the potential security abuse and/or have "
            + "it reviewed and delisted.</p><table><tr><th>Domain Name</th><th>Threat Type</th>"
            + "</tr>%s</table><p>Please work with the registrant to mitigate any security issues "
            + "and have the domains delisted.</p>Some helpful resources for getting off a blocked "
            + "list include:<ul><li>foo</li></ul><p>You will continue to receive a monthly summary "
            + "of all domains managed by your registrar that remain on our lists of potential "
            + "security threats. You will additionally receive a daily notice when any new domains "
            + "that are added to these lists. Once the registrant has resolved the security issues "
            + "and followed the steps to have his or her domain reviewed and delisted it will "
            + "automatically be removed from our monthly reporting.</p><p>If you have any q"
            + "uestions regarding this notice, please contact my-reply-to@test.com.</p>";

    validateMessage(
        capturedContents.get(0),
        "my-sender@test.com",
        "a@fake.com",
        Optional.of("my-reply-to@test.com"),
        "Super Cool Registry Monthly Threat Detector [2018-07-15]",
        String.format(emailFormat, "<tr><td>a.com</td><td>MALWARE</td></tr>"),
        Optional.of(MediaType.HTML_UTF_8));
    validateMessage(
        capturedContents.get(1),
        "my-sender@test.com",
        "b@fake.com",
        Optional.of("my-reply-to@test.com"),
        "Super Cool Registry Monthly Threat Detector [2018-07-15]",
        String.format(
            emailFormat,
            "<tr><td>b.com</td><td>MALWARE</td></tr><tr><td>c.com</td><td>MALWARE</td></tr>"),
        Optional.of(MediaType.HTML_UTF_8));
    validateMessage(
        capturedContents.get(2),
        "my-sender@test.com",
        "my-receiver@test.com",
        Optional.empty(),
        "Spec11 Pipeline Success 2018-07-15",
        "Spec11 reporting completed successfully.",
        Optional.empty());
  }

  @Test
  public void testSuccess_emailDailySpec11Reports() throws Exception {
    emailUtils.emailSpec11Reports(
        date,
        Spec11EmailSoyInfo.DAILY_SPEC_11_EMAIL,
        "Super Cool Registry Daily Threat Detector [2018-07-15]",
        sampleThreatMatches());
    // We inspect individual parameters because Message doesn't implement equals().
    verify(emailService, times(3)).sendEmail(contentCaptor.capture());
    List<EmailMessage> capturedMessages = contentCaptor.getAllValues();
    String emailFormat =
        "Dear registrar partner,<p>"
            + "Super Cool Registry conducts a daily analysis of all domains registered in its TLDs "
            + "to identify potential security concerns. On 2018-07-15, the following domains that "
            + "your registrar manages were flagged for potential security concerns:</p><table><tr>"
            + "<th>Domain Name</th><th>Threat Type</th></tr>%s</table><p><b>Please communicate "
            + "these findings to the registrant and work with the registrant to mitigate any "
            + "security issues and have the domains delisted.</b></p>Some helpful resources for "
            + "getting off a blocked list include:<ul><li>foo</li></ul><p>You will continue to "
            + "receive daily notices when new domains managed by your registrar are flagged for "
            + "abuse, as well as a monthly summary of all of your domains under management that "
            + "remain flagged for abuse. Once the registrant has resolved the security issues and "
            + "followed the steps to have his or her domain reviewed and delisted it will "
            + "automatically be removed from our reporting.</p><p>If you would like to change "
            + "the email to which these notices are sent please update your abuse contact using "
            + "your registrar portal account.</p><p>If you have any questions regarding this "
            + "notice, please contact my-reply-to@test.com.</p>";

    validateMessage(
        capturedMessages.get(0),
        "my-sender@test.com",
        "a@fake.com",
        Optional.of("my-reply-to@test.com"),
        "Super Cool Registry Daily Threat Detector [2018-07-15]",
        String.format(emailFormat, "<tr><td>a.com</td><td>MALWARE</td></tr>"),
        Optional.of(MediaType.HTML_UTF_8));
    validateMessage(
        capturedMessages.get(1),
        "my-sender@test.com",
        "b@fake.com",
        Optional.of("my-reply-to@test.com"),
        "Super Cool Registry Daily Threat Detector [2018-07-15]",
        String.format(
            emailFormat,
            "<tr><td>b.com</td><td>MALWARE</td></tr><tr><td>c.com</td><td>MALWARE</td></tr>"),
        Optional.of(MediaType.HTML_UTF_8));
    validateMessage(
        capturedMessages.get(2),
        "my-sender@test.com",
        "my-receiver@test.com",
        Optional.empty(),
        "Spec11 Pipeline Success 2018-07-15",
        "Spec11 reporting completed successfully.",
        Optional.empty());
  }

  @Test
  public void testFailure_emailsAlert() throws MessagingException {
    doThrow(new RuntimeException(new MessagingException("expected")))
        .doNothing()
        .when(emailService)
        .sendEmail(contentCaptor.capture());
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                emailUtils.emailSpec11Reports(
                    date, Spec11EmailSoyInfo.MONTHLY_SPEC_11_EMAIL, "bar", sampleThreatMatches()));
    assertThat(thrown).hasMessageThat().isEqualTo("Emailing spec11 report failed");
    assertThat(thrown)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("javax.mail.MessagingException: expected");
    // Verify we sent an e-mail alert
    verify(emailService, times(2)).sendEmail(contentCaptor.capture());
    validateMessage(
        contentCaptor.getValue(),
        "my-sender@test.com",
        "my-receiver@test.com",
        Optional.empty(),
        "Spec11 Emailing Failure 2018-07-15",
        "Emailing spec11 reports failed due to expected",
        Optional.empty());
  }

  @Test
  public void testSuccess_sendAlertEmail() throws MessagingException {
    emailUtils.sendAlertEmail("Spec11 Pipeline Alert: 2018-07", "Alert!");
    verify(emailService).sendEmail(contentCaptor.capture());
    validateMessage(
        contentCaptor.getValue(),
        "my-sender@test.com",
        "my-receiver@test.com",
        Optional.empty(),
        "Spec11 Pipeline Alert: 2018-07",
        "Alert!",
        Optional.empty());
  }

  @Test
  public void testWarning_emptyEmailAddressForRegistrars() throws Exception {
    ImmutableSet<RegistrarThreatMatches> matchesWithoutEmails =
        sampleThreatMatches().stream()
            .map(matches -> RegistrarThreatMatches.create("", matches.threatMatches()))
            .collect(toImmutableSet());
    emailUtils.emailSpec11Reports(
        date,
        Spec11EmailSoyInfo.DAILY_SPEC_11_EMAIL,
        "Super Cool Registry Daily Threat Detector [2018-07-15]",
        matchesWithoutEmails);
    verify(emailService).sendEmail(contentCaptor.capture());
    validateMessage(
        contentCaptor.getValue(),
        "my-sender@test.com",
        "my-receiver@test.com",
        Optional.empty(),
        "Spec11 Pipeline Warning 2018-07-15",
        "No errors occurred but the following matches had no associated email: \n"
            + "[RegistrarThreatMatches{registrarEmailAddress=, threatMatches=[ThreatMatch"
            + "{threatType=MALWARE, platformType=ANY_PLATFORM, metadata=NONE,"
            + " fullyQualifiedDomainName=a.com}]}, RegistrarThreatMatches{registrarEmailAddress=,"
            + " threatMatches=[ThreatMatch{threatType=MALWARE, platformType=ANY_PLATFORM,"
            + " metadata=NONE, fullyQualifiedDomainName=b.com}, ThreatMatch{threatType=MALWARE,"
            + " platformType=ANY_PLATFORM, metadata=NONE, fullyQualifiedDomainName=c.com}]}]\n\n"
            + "This should not occur; please make sure we have email addresses for these"
            + " registrar(s).",
        Optional.empty());
  }

  private void validateMessage(
      EmailMessage message,
      String from,
      String recipient,
      Optional<String> replyTo,
      String subject,
      String body,
      Optional<MediaType> contentType)
      throws MessagingException {
    EmailMessage.Builder expectedContentBuilder =
        EmailMessage.newBuilder()
            .setFrom(new InternetAddress(from))
            .addRecipient(new InternetAddress(recipient))
            .setSubject(subject)
            .setBody(body);

    if (replyTo.isPresent()) {
      expectedContentBuilder.setBcc(new InternetAddress(replyTo.get()));
    }
    contentType.ifPresent(expectedContentBuilder::setContentType);
    assertThat(message).isEqualTo(expectedContentBuilder.build());
  }
}
