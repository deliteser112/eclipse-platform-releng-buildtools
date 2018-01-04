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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import google.registry.util.SendEmailService;
import java.io.IOException;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.joda.time.YearMonth;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BillingEmailUtils}. */
@RunWith(JUnit4.class)
public class BillingEmailUtilsTest {

  private SendEmailService emailService;
  private Message msg;
  private BillingEmailUtils emailUtils;

  @Before
  public void setUp() {
    msg = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
    emailService = mock(SendEmailService.class);
    when(emailService.createMessage()).thenReturn(msg);

    emailUtils =
        new BillingEmailUtils(
            emailService,
            new YearMonth(2017, 10),
            "my-sender@test.com",
            ImmutableList.of("hello@world.com", "hola@mundo.com"),
            "gs://test-bucket");
  }

  @Test
  public void testSuccess_sendsEmail() throws MessagingException, IOException {
    emailUtils.emailInvoiceLink();
    assertThat(msg.getFrom()).hasLength(1);
    assertThat(msg.getFrom()[0])
        .isEqualTo(new InternetAddress("my-sender@test.com"));
    assertThat(msg.getAllRecipients())
        .asList()
        .containsExactly(
            new InternetAddress("hello@world.com"), new InternetAddress("hola@mundo.com"));
    assertThat(msg.getSubject()).isEqualTo("Domain Registry invoice data 2017-10");
    assertThat(msg.getContentType()).isEqualTo("text/plain");
    assertThat(msg.getContent().toString())
        .isEqualTo(
            "Link to invoice on GCS:\n"
                + "https://storage.cloud.google.com/test-bucket/results/CRR-INV-2017-10.csv");
  }
}
