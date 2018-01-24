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

package google.registry.reporting.icann;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import google.registry.util.SendEmailService;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link google.registry.reporting.icann.ReportingEmailUtils}. */
@RunWith(JUnit4.class)
public class ReportingEmailUtilsTest {
  private Message msg;
  private final SendEmailService emailService = mock(SendEmailService.class);

  @Before
  public void setUp() {
    msg = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
    when(emailService.createMessage()).thenReturn(msg);
  }

  private ReportingEmailUtils createEmailUtil() {
    ReportingEmailUtils emailUtils = new ReportingEmailUtils();
    emailUtils.sender = "test-project.appspotmail.com";
    emailUtils.recipient = "email@example.com";
    emailUtils.emailService = emailService;
    return emailUtils;
  }

  @Test
  public void testSuccess_sendsEmail() throws Exception {
    ReportingEmailUtils emailUtils = createEmailUtil();
    emailUtils.emailResults("Subject", "Body");

    assertThat(msg.getFrom()).hasLength(1);
    assertThat(msg.getFrom()[0])
      .isEqualTo(new InternetAddress("test-project.appspotmail.com"));
    assertThat(msg.getRecipients(RecipientType.TO)).hasLength(1);
    assertThat(msg.getRecipients(RecipientType.TO)[0])
        .isEqualTo(new InternetAddress("email@example.com"));
    assertThat(msg.getSubject()).isEqualTo("Subject");
    assertThat(msg.getContentType()).isEqualTo("text/plain");
    assertThat(msg.getContent().toString()).isEqualTo("Body");
  }
}
