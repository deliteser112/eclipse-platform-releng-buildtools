// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.ui.server.registrar;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import google.registry.export.sheet.SyncRegistrarsSheetAction;
import google.registry.model.registrar.Registrar;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import java.util.Map;
import javax.mail.internet.InternetAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Tests for {@link RegistrarSettingsAction}. */
@RunWith(MockitoJUnitRunner.class)
public class RegistrarSettingsActionTest extends RegistrarSettingsActionTestCase {

  @Test
  public void testSuccess_updateRegistrarInfo_andSendsNotificationEmail() throws Exception {
    String expectedEmailBody = readResourceUtf8(getClass(), "testdata/update_registrar_email.txt");
    action.handleJsonRequest(readJsonFromFile("testdata/update_registrar.json"));
    verify(rsp, never()).setStatus(anyInt());
    verify(emailService).createMessage();
    verify(emailService).sendMessage(message);
    assertThat(message.getAllRecipients()).asList().containsExactly(
        new InternetAddress("notification@test.example"),
        new InternetAddress("notification2@test.example"));
    assertThat(message.getContent()).isEqualTo(expectedEmailBody);
    assertTasksEnqueued("sheet", new TaskMatcher()
        .url(SyncRegistrarsSheetAction.PATH)
        .method("GET")
        .header("Host", "backend.hostname"));
  }

  @Test
  public void testFailure_updateRegistrarInfo_duplicateContacts() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(
        readJsonFromFile("testdata/update_registrar_duplicate_contacts.json"));
    assertThat(response).containsEntry("status", "ERROR");
    assertThat((String) response.get("message")).startsWith("One email address");
  }

  @Test
  public void testRead_notAuthorized_failure() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(req)).thenReturn(false);
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.<String, Object>of());
    assertThat(response).containsEntry("status", "ERROR");
    assertThat((String) response.get("message")).startsWith("Not authorized");
    assertNoTasksEnqueued("sheet");
  }

  /**
   * This is the default read test for the registrar settings actions.
   */
  @Test
  public void testRead_authorized_returnsRegistrarJson() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.<String, Object>of());
    assertThat(response).containsEntry("status", "SUCCESS");
    assertThat(response).containsEntry("results", asList(
        Registrar.loadByClientId(CLIENT_ID).toJsonMap()));
  }

  @Test
  public void testUpdate_emptyJsonObject_errorEmailFieldRequired() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of()));
    assertThat(response).containsEntry("status", "ERROR");
    assertThat(response).containsEntry("field", "emailAddress");
    assertThat(response).containsEntry("message", "This field is required.");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testUpdate_badEmail_errorEmailField() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "emailAddress", "lolcat")));
    assertThat(response).containsEntry("status", "ERROR");
    assertThat(response).containsEntry("field", "emailAddress");
    assertThat(response).containsEntry("message", "Please enter a valid email address.");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testPost_nonAsciiCharacters_getsAngry() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "emailAddress", "ヘ(◕。◕ヘ)@example.com")));
    assertThat(response).containsEntry("status", "ERROR");
    assertThat(response).containsEntry("field", "emailAddress");
    assertThat(response).containsEntry("message", "Please only use ASCII-US characters.");
    assertNoTasksEnqueued("sheet");
  }
}
