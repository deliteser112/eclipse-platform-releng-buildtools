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
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/** Tests for {@link EppToolAction}. */
@RunWith(JUnit4.class)
public class EppToolActionTest {

  private void doTest(boolean isDryRun, boolean isSuperuser) {
    EppToolAction action = new EppToolAction();
    action.clientIdentifier = "ClientIdentifier";
    action.isDryRun = isDryRun;
    action.isSuperuser = isSuperuser;
    action.eppRequestHandler = mock(EppRequestHandler.class);
    action.xml = "<xml>";
    action.run();
    ArgumentCaptor<SessionMetadata> captor = ArgumentCaptor.forClass(SessionMetadata.class);
    verify(action.eppRequestHandler).executeEpp(
        captor.capture(),
        isA(PasswordOnlyTransportCredentials.class),
        eq(EppRequestSource.TOOL),
        eq(isDryRun),
        eq(isSuperuser),
        eq(action.xml.getBytes(UTF_8)));
    assertThat(captor.getValue().getClientId()).isEqualTo("ClientIdentifier");
  }

  @Test
  public void testDryRunAndSuperuser() throws Exception {
    doTest(true, true);
  }

  @Test
  public void testDryRun() throws Exception {
    doTest(true, false);
  }

  @Test
  public void testSuperuser() throws Exception {
    doTest(false, true);
  }

  @Test
  public void testNeitherDryRunNorSuperuser() throws Exception {
    doTest(false, false);
  }
}
