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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.testing.JUnitBackports.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.collect.ImmutableList;
import google.registry.gcs.GcsUtils;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeSleeper;
import google.registry.util.Retrier;
import google.registry.util.SendEmailService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.joda.time.YearMonth;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/** Unit tests for {@link google.registry.reporting.billing.BillingEmailUtils}. */
@RunWith(JUnit4.class)
public class BillingEmailUtilsTest {

  private static final int RETRY_COUNT = 2;

  private SendEmailService emailService;
  private BillingEmailUtils emailUtils;
  private GcsUtils gcsUtils;
  private ArgumentCaptor<Message> msgCaptor;

  @Before
  public void setUp() {
    emailService = mock(SendEmailService.class);
    when(emailService.createMessage())
        .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties(), null)));
    gcsUtils = mock(GcsUtils.class);
    when(gcsUtils.openInputStream(new GcsFilename("test-bucket", "results/REG-INV-2017-10.csv")))
        .thenReturn(
            new ByteArrayInputStream("test,data\nhello,world".getBytes(StandardCharsets.UTF_8)));
    msgCaptor = ArgumentCaptor.forClass(Message.class);

    emailUtils =
        new BillingEmailUtils(
            emailService,
            new YearMonth(2017, 10),
            "my-sender@test.com",
            "my-receiver@test.com",
            ImmutableList.of("hello@world.com", "hola@mundo.com"),
            "test-bucket",
            "REG-INV",
            "results/",
            gcsUtils,
            new Retrier(new FakeSleeper(new FakeClock()), RETRY_COUNT));
  }

  @Test
  public void testSuccess_emailOverallInvoice() throws MessagingException, IOException {
    emailUtils.emailOverallInvoice();
    // We inspect individual parameters because Message doesn't implement equals().
    verify(emailService).sendMessage(msgCaptor.capture());
    Message expectedMsg = msgCaptor.getValue();
    assertThat(expectedMsg.getFrom())
        .asList()
        .containsExactly(new InternetAddress("my-sender@test.com"));
    assertThat(expectedMsg.getAllRecipients())
        .asList()
        .containsExactly(
            new InternetAddress("hello@world.com"), new InternetAddress("hola@mundo.com"));
    assertThat(expectedMsg.getSubject()).isEqualTo("Domain Registry invoice data 2017-10");
    assertThat(expectedMsg.getContent()).isInstanceOf(Multipart.class);
    Multipart contents = (Multipart) expectedMsg.getContent();
    assertThat(contents.getCount()).isEqualTo(2);
    assertThat(contents.getBodyPart(0)).isInstanceOf(BodyPart.class);
    BodyPart textPart = contents.getBodyPart(0);
    assertThat(textPart.getContentType()).isEqualTo("text/plain; charset=us-ascii");
    assertThat(textPart.getContent().toString())
        .isEqualTo("Attached is the 2017-10 invoice for the domain registry.");
    assertThat(contents.getBodyPart(1)).isInstanceOf(BodyPart.class);
    BodyPart attachmentPart = contents.getBodyPart(1);
    assertThat(attachmentPart.getContentType()).endsWith("name=REG-INV-2017-10.csv");
    assertThat(attachmentPart.getContent().toString()).isEqualTo("test,data\nhello,world");
  }

  @Test
  public void testFailure_tooManyRetries_emailsAlert() throws MessagingException, IOException {
    // This message throws whenever it tries to set content, to force the overall invoice to fail.
    Message throwingMessage = mock(Message.class);
    doThrow(new MessagingException("expected"))
        .when(throwingMessage)
        .setContent(any(Multipart.class));
    when(emailService.createMessage()).thenAnswer(
        new Answer<Message>() {
          private int count = 0;

          @Override
          public Message answer(InvocationOnMock invocation) {
            // Once we've failed the retry limit for the original invoice, return a normal message
            // so we can properly check its contents.
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
        }
    );
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> emailUtils.emailOverallInvoice());
    assertThat(thrown).hasMessageThat().isEqualTo("Emailing invoice failed");
    assertThat(thrown)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("javax.mail.MessagingException: expected");
    // Verify we sent an e-mail alert
    verify(emailService).sendMessage(msgCaptor.capture());
    validateAlertMessage(msgCaptor.getValue(), "Emailing invoice failed due to expected");
  }

  @Test
  public void testSuccess_sendAlertEmail() throws MessagingException, IOException {
    emailUtils.sendAlertEmail("Alert!");
    verify(emailService).sendMessage(msgCaptor.capture());
    validateAlertMessage(msgCaptor.getValue(), "Alert!");
  }

  private void validateAlertMessage(Message msg, String body)
      throws MessagingException, IOException {
    assertThat(msg.getFrom()).hasLength(1);
    assertThat(msg.getFrom()[0]).isEqualTo(new InternetAddress("my-sender@test.com"));
    assertThat(msg.getAllRecipients())
        .asList()
        .containsExactly(new InternetAddress("my-receiver@test.com"));
    assertThat(msg.getSubject()).isEqualTo("Billing Pipeline Alert: 2017-10");
    assertThat(msg.getContentType()).isEqualTo("text/plain");
    assertThat(msg.getContent().toString()).isEqualTo(body);
  }
}
