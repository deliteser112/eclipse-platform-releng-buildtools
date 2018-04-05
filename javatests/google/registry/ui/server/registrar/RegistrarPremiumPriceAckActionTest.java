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

package google.registry.ui.server.registrar;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.config.RegistryConfig.getGSuiteOutgoingEmailAddress;
import static google.registry.config.RegistryConfig.getGSuiteOutgoingEmailDisplayName;
import static google.registry.security.JsonHttpTestUtils.createJsonPayload;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.testing.TestDataHelper.loadFile;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.export.sheet.SyncRegistrarsSheetAction;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.request.JsonActionRunner;
import google.registry.request.JsonResponse;
import google.registry.request.ResponseImpl;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeHttpSession;
import google.registry.testing.InjectRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.util.SendEmailService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RegistrarPremiumPriceAckActionTest {

  private static final String CLIENT_ID = "NewRegistrar";

  private final RegistrarPremiumPriceAckAction action = new RegistrarPremiumPriceAckAction();

  public RegistrarPremiumPriceAckActionTest() {}

  @Rule
  public final AppEngineRule appEngine =
      AppEngineRule.builder().withDatastore().withTaskQueue().build();

  @Rule public final InjectRule inject = new InjectRule();

  final HttpServletRequest req = mock(HttpServletRequest.class);
  final HttpServletResponse rsp = mock(HttpServletResponse.class);
  final SendEmailService emailService = mock(SendEmailService.class);
  final ModulesService modulesService = mock(ModulesService.class);
  final User user =
      new User("janedoe", "theregistrar.com", AppEngineRule.NEW_REGISTRAR_GAE_USER_ID);

  Message message;

  final StringWriter writer = new StringWriter();

  @Before
  public void setUp() throws Exception {
    action.request = req;
    action.sessionUtils = new SessionUtils();
    action.sessionUtils.registryAdminClientId = CLIENT_ID;
    action.authResult = AuthResult.create(AuthLevel.USER, UserAuthInfo.create(user, false));
    action.jsonActionRunner =
        new JsonActionRunner(ImmutableMap.of(), new JsonResponse(new ResponseImpl(rsp)));
    action.registrarChangesNotificationEmailAddresses =
        ImmutableList.of("notification@test.example", "notification2@test.example");
    action.sendEmailUtils =
        new SendEmailUtils(getGSuiteOutgoingEmailAddress(), getGSuiteOutgoingEmailDisplayName());

    message = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
    when(emailService.createMessage()).thenReturn(message);
    when(req.getMethod()).thenReturn("POST");
    when(rsp.getWriter()).thenReturn(new PrintWriter(writer));
    when(req.getContentType()).thenReturn("application/json");
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of("op", "read")));
    when(modulesService.getVersionHostname("backend", null)).thenReturn("backend.hostname");
    FakeHttpSession session = new FakeHttpSession();
    when(req.getSession()).thenReturn(session);
    session.setAttribute(SessionUtils.CLIENT_ID_ATTRIBUTE, CLIENT_ID);

    inject.setStaticField(SendEmailUtils.class, "emailService", emailService);
    inject.setStaticField(SyncRegistrarsSheetAction.class, "modulesService", modulesService);

    Registrar registrar = AppEngineRule.makeRegistrar1();
    RegistrarContact contact = AppEngineRule.makeRegistrarContact1();
    persistResource(registrar);
    persistResource(contact);
  }

  @Test
  public void testPost_updatePremiumPriceAckRequired() throws Exception {
    Registrar registrar = loadRegistrar(CLIENT_ID);

    Registrar modified = registrar.asBuilder().setPremiumPriceAckRequired(true).build();
    Map<String, Object> reqJson = modified.toJsonMap();
    Map<String, Object> response =
        action.handleJsonRequest(ImmutableMap.of("op", "update", "args", reqJson));
    assertThat(response).containsEntry("status", "SUCCESS");
    assertThat(loadRegistrar(CLIENT_ID).getPremiumPriceAckRequired()).isTrue();

    // Verify that we sent notification emails.
    String expectedEmailBody = loadFile(getClass(), "update_ppa_email.txt");
    verify(rsp, never()).setStatus(anyInt());
    verify(emailService).createMessage();
    verify(emailService).sendMessage(message);
    assertThat(message.getAllRecipients())
        .asList()
        .containsExactly(
            new InternetAddress("notification@test.example"),
            new InternetAddress("notification2@test.example"));
    assertThat(message.getContent()).isEqualTo(expectedEmailBody);
    assertTasksEnqueued(
        "sheet",
        new TaskMatcher()
            .url(SyncRegistrarsSheetAction.PATH)
            .method("GET")
            .header("Host", "backend.hostname"));

    // Verify that switching back also works.
    modified = modified.asBuilder().setPremiumPriceAckRequired(false).build();
    reqJson = modified.toJsonMap();
    response = action.handleJsonRequest(ImmutableMap.of("op", "update", "args", reqJson));
    assertThat(response).containsEntry("status", "SUCCESS");
    assertThat(loadRegistrar(CLIENT_ID).getPremiumPriceAckRequired()).isFalse();
  }
}
