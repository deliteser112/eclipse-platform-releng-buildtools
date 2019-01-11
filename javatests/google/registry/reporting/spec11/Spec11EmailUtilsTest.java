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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.reporting.spec11.Spec11RegistrarThreatMatchesParserTest.sampleThreatMatches;
import static google.registry.testing.JUnitBackports.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import google.registry.reporting.spec11.soy.Spec11EmailSoyInfo;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeSleeper;
import google.registry.util.Retrier;
import google.registry.util.SendEmailService;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nullable;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/** Unit tests for {@link Spec11EmailUtils}. */
@RunWith(JUnit4.class)
public class Spec11EmailUtilsTest {

  private static final int RETRY_COUNT = 2;
  private static final ImmutableList<String> FAKE_RESOURCES = ImmutableList.of("foo");

  private SendEmailService emailService;
  private Spec11EmailUtils emailUtils;
  private Spec11RegistrarThreatMatchesParser parser;
  private ArgumentCaptor<Message> gotMessage;

  @Before
  public void setUp() throws Exception {
    emailService = mock(SendEmailService.class);
    when(emailService.createMessage())
        .thenAnswer((args) -> new MimeMessage(Session.getInstance(new Properties(), null)));

    parser = mock(Spec11RegistrarThreatMatchesParser.class);
    LocalDate date = new LocalDate(2018, 7, 15);
    when(parser.getRegistrarThreatMatches(date)).thenReturn(sampleThreatMatches());

    gotMessage = ArgumentCaptor.forClass(Message.class);

    emailUtils =
        new Spec11EmailUtils(
            emailService,
            date,
            "my-sender@test.com",
            "my-receiver@test.com",
            "my-reply-to@test.com",
            FAKE_RESOURCES,
            "Super Cool Registry",
            new Retrier(new FakeSleeper(new FakeClock()), RETRY_COUNT));
  }

  @Test
  public void testSuccess_emailSpec11Reports() throws Exception {
    emailUtils.emailSpec11Reports(
        Spec11EmailSoyInfo.MONTHLY_SPEC_11_EMAIL,
        "Super Cool Registry Monthly Threat Detector [2018-07-15]",
        sampleThreatMatches());
    // We inspect individual parameters because Message doesn't implement equals().
    verify(emailService, times(3)).sendMessage(gotMessage.capture());
    List<Message> capturedMessages = gotMessage.getAllValues();
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
        capturedMessages.get(0),
        "my-sender@test.com",
        "a@fake.com",
        "my-reply-to@test.com",
        "Super Cool Registry Monthly Threat Detector [2018-07-15]",
        String.format(emailFormat, "<tr><td>a.com</td><td>MALWARE</td></tr>"),
        "text/html");
    validateMessage(
        capturedMessages.get(1),
        "my-sender@test.com",
        "b@fake.com",
        "my-reply-to@test.com",
        "Super Cool Registry Monthly Threat Detector [2018-07-15]",
        String.format(
            emailFormat,
            "<tr><td>b.com</td><td>MALWARE</td></tr><tr><td>c.com</td><td>MALWARE</td></tr>"),
        "text/html");
    validateMessage(
        capturedMessages.get(2),
        "my-sender@test.com",
        "my-receiver@test.com",
        null,
        "Spec11 Pipeline Success 2018-07-15",
        "Spec11 reporting completed successfully.",
        "text/plain");
  }

  @Test
  public void testFailure_tooManyRetries_emailsAlert() throws MessagingException, IOException {
    Message throwingMessage = mock(Message.class);
    doThrow(new MessagingException("expected")).when(throwingMessage).setSubject(any(String.class));
    // Only return the throwingMessage enough times to force failure. The last invocation will
    // be for the alert e-mail we're looking to verify.
    when(emailService.createMessage())
        .thenAnswer(
            new Answer<Message>() {
              private int count = 0;

              @Override
              public Message answer(InvocationOnMock invocation) {
                if (count < RETRY_COUNT) {
                  count++;
                  return throwingMessage;
                } else if (count == RETRY_COUNT) {
                  return new MimeMessage(Session.getDefaultInstance(new Properties(), null));
                } else {
                  assertWithMessage("Attempted to generate too many messages!").fail();
                  return null;
                }
              }
            });
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                emailUtils.emailSpec11Reports(
                    Spec11EmailSoyInfo.MONTHLY_SPEC_11_EMAIL, "bar", sampleThreatMatches()));
    assertThat(thrown).hasMessageThat().isEqualTo("Emailing spec11 report failed");
    assertThat(thrown)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("javax.mail.MessagingException: expected");
    // We should have created RETRY_COUNT failing messages and one final alert message
    verify(emailService, times(RETRY_COUNT + 1)).createMessage();
    // Verify we sent an e-mail alert
    verify(emailService).sendMessage(gotMessage.capture());
    validateMessage(
        gotMessage.getValue(),
        "my-sender@test.com",
        "my-receiver@test.com",
        null,
        "Spec11 Emailing Failure 2018-07-15",
        "Emailing spec11 reports failed due to expected",
        "text/plain");
  }

  @Test
  public void testSuccess_sendAlertEmail() throws MessagingException, IOException {
    emailUtils.sendAlertEmail("Spec11 Pipeline Alert: 2018-07", "Alert!");
    verify(emailService).sendMessage(gotMessage.capture());
    validateMessage(
        gotMessage.getValue(),
        "my-sender@test.com",
        "my-receiver@test.com",
        null,
        "Spec11 Pipeline Alert: 2018-07",
        "Alert!",
        "text/plain");
  }

  private void validateMessage(
      Message message,
      String from,
      String recipient,
      @Nullable String replyTo,
      String subject,
      String body,
      String contentType)
      throws MessagingException, IOException {
    assertThat(message.getFrom()).asList().containsExactly(new InternetAddress(from));
    assertThat(message.getRecipients(RecipientType.TO))
        .asList()
        .containsExactly(new InternetAddress(recipient));
    if (replyTo == null) {
      assertThat(message.getRecipients(RecipientType.BCC)).isNull();
    } else {
      assertThat(message.getRecipients(RecipientType.BCC))
          .asList()
          .containsExactly(new InternetAddress(replyTo));
    }
    assertThat(message.getRecipients(RecipientType.CC)).isNull();
    assertThat(message.getSubject()).isEqualTo(subject);
    assertThat(message.getContentType()).isEqualTo(contentType);
    assertThat(message.getContent()).isEqualTo(body);
  }
}
