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

import static google.registry.config.RegistryConfig.LocalTestConfig.GOOGLE_APPS_ADMIN_EMAIL_DISPLAY_NAME;
import static google.registry.config.RegistryConfig.LocalTestConfig.GOOGLE_APPS_SEND_FROM_EMAIL_ADDRESS;
import static google.registry.security.JsonHttpTestUtils.createJsonPayload;
import static google.registry.security.JsonHttpTestUtils.createJsonResponseSupplier;
import static google.registry.security.XsrfTokenManager.generateToken;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import com.google.appengine.api.modules.ModulesService;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.export.sheet.SyncRegistrarsSheetAction;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.request.JsonActionRunner;
import google.registry.request.JsonResponse;
import google.registry.request.ResponseImpl;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.util.SendEmailService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Base class for tests using {@link RegistrarSettingsAction}. */
@RunWith(MockitoJUnitRunner.class)
public class RegistrarSettingsActionTestCase {

  static final String CLIENT_ID = "TheRegistrar";

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Mock
  HttpServletRequest req;

  @Mock
  HttpServletResponse rsp;

  @Mock
  SendEmailService emailService;

  @Mock
  ModulesService modulesService;

  @Mock
  SessionUtils sessionUtils;

  Message message;

  final RegistrarSettingsAction action = new RegistrarSettingsAction();
  final StringWriter writer = new StringWriter();
  final Supplier<Map<String, Object>> json = createJsonResponseSupplier(writer);
  final FakeClock clock = new FakeClock(DateTime.parse("2014-01-01T00:00:00Z"));

  @Before
  public void setUp() throws Exception {
    action.request = req;
    action.sessionUtils = sessionUtils;
    action.initialRegistrar = Registrar.loadByClientId(CLIENT_ID);
    action.jsonActionRunner = new JsonActionRunner(
        ImmutableMap.<String, Object>of(), new JsonResponse(new ResponseImpl(rsp)));
    action.registrarChangesNotificationEmailAddresses = ImmutableList.of(
        "notification@test.example", "notification2@test.example");
    action.sendEmailUtils =
        new SendEmailUtils(
            GOOGLE_APPS_SEND_FROM_EMAIL_ADDRESS, GOOGLE_APPS_ADMIN_EMAIL_DISPLAY_NAME);
    inject.setStaticField(Ofy.class, "clock", clock);
    inject.setStaticField(SendEmailUtils.class, "emailService", emailService);
    inject.setStaticField(SyncRegistrarsSheetAction.class, "modulesService", modulesService);
    message = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
    when(emailService.createMessage()).thenReturn(message);
    when(req.getMethod()).thenReturn("POST");
    when(rsp.getWriter()).thenReturn(new PrintWriter(writer));
    when(req.getContentType()).thenReturn("application/json");
    when(req.getHeader(eq("X-CSRF-Token"))).thenReturn(generateToken("console"));
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of("op", "read")));
    when(sessionUtils.isLoggedIn()).thenReturn(true);
    when(sessionUtils.checkRegistrarConsoleLogin(req)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(req)).thenReturn(CLIENT_ID);
    when(modulesService.getVersionHostname("backend", null)).thenReturn("backend.hostname");
  }

  protected Map<String, Object> readJsonFromFile(String filename) {
    String contents = readResourceUtf8(getClass(), filename);
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> json = (Map<String, Object>) JSONValue.parseWithException(contents);
      return json;
    } catch (ParseException ex) {
      throw new RuntimeException(ex);
    }
  }
}
