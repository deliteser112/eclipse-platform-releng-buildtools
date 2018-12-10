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
    when(parser.getRegistrarThreatMatches()).thenReturn(sampleThreatMatches());

    gotMessage = ArgumentCaptor.forClass(Message.class);

    emailUtils =
        new Spec11EmailUtils(
            emailService,
            new LocalDate(2018, 7, 15),
            "my-sender@test.com",
            "my-receiver@test.com",
            "my-reply-to@test.com",
            new Retrier(new FakeSleeper(new FakeClock()), RETRY_COUNT));
  }

  @Test
  public void testSuccess_emailSpec11Reports() throws Exception {
    emailUtils.emailSpec11Reports(
        "{LIST_OF_THREATS}\n{REPLY_TO_EMAIL}",
        "Google Registry Monthly Threat Detector [2018-07-15]",
        sampleThreatMatches());
    // We inspect individual parameters because Message doesn't implement equals().
    verify(emailService, times(3)).sendMessage(gotMessage.capture());
    List<Message> capturedMessages = gotMessage.getAllValues();
    validateMessage(
        capturedMessages.get(0),
        "my-sender@test.com",
        "a@fake.com",
        "my-reply-to@test.com",
        "Google Registry Monthly Threat Detector [2018-07-15]",
        "a.com - MALWARE\n\nmy-reply-to@test.com");
    validateMessage(
        capturedMessages.get(1),
        "my-sender@test.com",
        "b@fake.com",
        "my-reply-to@test.com",
        "Google Registry Monthly Threat Detector [2018-07-15]",
        "b.com - MALWARE\nc.com - MALWARE\n\nmy-reply-to@test.com");
    validateMessage(
        capturedMessages.get(2),
        "my-sender@test.com",
        "my-receiver@test.com",
        null,
        "Spec11 Pipeline Success 2018-07-15",
        "Spec11 reporting completed successfully.");
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
            () -> emailUtils.emailSpec11Reports("foo", "bar", sampleThreatMatches()));
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
        "Emailing spec11 reports failed due to expected");
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
        "Alert!");
  }

  private void validateMessage(
      Message message,
      String from,
      String recipient,
      @Nullable String replyTo,
      String subject,
      String body)
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
    assertThat(message.getContentType()).isEqualTo("text/plain");
    assertThat(message.getContent().toString()).isEqualTo(body);
  }
}
