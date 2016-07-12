// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.util;

import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;

/** Wrapper around javax.mail's Transport.send that can be mocked for testing purposes. */
public class SendEmailService {

  /** Returns a new MimeMessage using default App Engine transport settings. */
  public Message createMessage() {
    return new MimeMessage(Session.getDefaultInstance(new Properties(), null));
  }

  /** Sends a message using default App Engine transport. */
  public void sendMessage(Message msg) throws MessagingException {
    Transport.send(msg);
  }
}
