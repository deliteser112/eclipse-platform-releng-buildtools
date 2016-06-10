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


package google.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import google.registry.testing.AppEngineRule;
import google.registry.testing.ShardableTestCase;
import google.registry.testing.UserInfo;
import google.registry.util.BasicHttpSession;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/** Tests for {@link EppConsoleAction}. */
@RunWith(JUnit4.class)
public class EppConsoleActionTest extends ShardableTestCase {

  private static final byte[] INPUT_XML_BYTES = "<xml>".getBytes(UTF_8);

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withUserService(UserInfo.create("person@example.com", "12345"))
      .build();

  private void doTest(boolean superuser) {
    EppConsoleAction action = new EppConsoleAction();
    action.inputXmlBytes = INPUT_XML_BYTES;
    action.session = new BasicHttpSession();
    action.session.setAttribute("CLIENT_ID", "ClientIdentifier");
    action.session.setAttribute("SUPERUSER", superuser);
    action.eppRequestHandler = mock(EppRequestHandler.class);
    action.run();
    ArgumentCaptor<SessionMetadata> captor = ArgumentCaptor.forClass(SessionMetadata.class);
    verify(action.eppRequestHandler).executeEpp(captor.capture(), eq(INPUT_XML_BYTES));
    SessionMetadata sessionMetadata = captor.getValue();
    assertThat(((GaeUserCredentials) sessionMetadata.getTransportCredentials()).gaeUser.getEmail())
        .isEqualTo("person@example.com");
    assertThat(sessionMetadata.getClientId()).isEqualTo("ClientIdentifier");
    assertThat(sessionMetadata.isDryRun()).isFalse();  // Should always be false for console.
    assertThat(sessionMetadata.isSuperuser()).isEqualTo(superuser);
  }

  @Test
  public void testSuperuser() throws Exception {
    doTest(true);
  }

  @Test
  public void testNotSuperuser() throws Exception {
    doTest(false);
  }
}
