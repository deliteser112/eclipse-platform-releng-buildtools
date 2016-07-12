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

package google.registry.ui.server.registrar;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.security.JsonHttpTestUtils.createJsonPayload;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static java.util.Arrays.asList;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import google.registry.export.sheet.SyncRegistrarsSheetAction;
import google.registry.model.registrar.Registrar;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import javax.mail.internet.InternetAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Tests for {@link RegistrarServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class RegistrarServletTest extends RegistrarServletTestCase {

  @Test
  public void testSuccess_updateRegistrarInfo_andSendsNotificationEmail() throws Exception {
    String jsonPostData = readResourceUtf8(getClass(), "testdata/update_registrar.json");
    String expectedEmailBody = readResourceUtf8(getClass(), "testdata/update_registrar_email.txt");
    when(req.getReader()).thenReturn(createJsonPayload(jsonPostData));
    servlet.service(req, rsp);
    verify(rsp).setStatus(SC_OK);
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
    String jsonPostData =
        readResourceUtf8(getClass(), "testdata/update_registrar_duplicate_contacts.json");
    when(req.getReader()).thenReturn(createJsonPayload(jsonPostData));
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "ERROR");
    assertThat((String) json.get().get("message")).startsWith("One email address");
  }

  @Test
  public void testRead_missingXsrfToken_failure() throws Exception {
    when(req.getHeader(eq("X-CSRF-Token"))).thenReturn("");
    servlet.service(req, rsp);
    verify(rsp).sendError(403, "Invalid X-CSRF-Token");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testRead_invalidXsrfToken_failure() throws Exception {
    when(req.getHeader(eq("X-CSRF-Token"))).thenReturn("humbug");
    servlet.service(req, rsp);
    verify(rsp).sendError(403, "Invalid X-CSRF-Token");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testRead_notLoggedIn_failure() throws Exception {
    when(sessionUtils.isLoggedIn()).thenReturn(false);
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "ERROR");
    assertThat(json.get()).containsEntry("message", "Not logged in");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testRead_notAuthorized_failure() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(req)).thenReturn(false);
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "ERROR");
    assertThat((String) json.get().get("message")).startsWith("Not authorized");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testRead_emptyPayload_failure() throws Exception {
    when(req.getReader()).thenReturn(createJsonPayload(""));
    servlet.service(req, rsp);
    verify(rsp).sendError(400, "Malformed JSON");
    assertNoTasksEnqueued("sheet");
  }

  /**
   * This is the default read test for the registrar settings servlets.
   */
  @Test
  public void testRead_authorized_returnsRegistrarJson() throws Exception {
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "SUCCESS");
    assertThat(json.get()).containsEntry("results", asList(
        Registrar.loadByClientId(CLIENT_ID).toJsonMap()));
  }

  @Test
  public void testRead_authorized_nilClientId_failure() throws Exception {
    String nilClientId = "doesnotexist";
    when(sessionUtils.getRegistrarClientId(req)).thenReturn(nilClientId);
    servlet.service(req, rsp);
    verify(rsp).sendError(404, "No registrar exists with the given client id: " + nilClientId);
  }

  @Test
  public void testUpdate_emptyJsonObject_errorEmailFieldRequired() throws Exception {
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of())));
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "ERROR");
    assertThat(json.get()).containsEntry("field", "emailAddress");
    assertThat(json.get()).containsEntry("message", "This field is required.");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testUpdate_badEmail_errorEmailField() throws Exception {
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "emailAddress", "lolcat"))));
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "ERROR");
    assertThat(json.get()).containsEntry("field", "emailAddress");
    assertThat(json.get()).containsEntry("message", "Please enter a valid email address.");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testPost_nonAsciiCharacters_getsAngry() throws Exception {
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "emailAddress", "ヘ(◕。◕ヘ)@example.com"))));
    servlet.service(req, rsp);
    assertThat(json.get()).containsEntry("status", "ERROR");
    assertThat(json.get()).containsEntry("field", "emailAddress");
    assertThat(json.get()).containsEntry("message", "Please only use ASCII-US characters.");
    assertNoTasksEnqueued("sheet");
  }
}
