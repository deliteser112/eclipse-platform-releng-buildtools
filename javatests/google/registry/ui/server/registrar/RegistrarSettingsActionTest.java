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
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.testing.TestDataHelper.loadFile;
import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.export.sheet.SyncRegistrarsSheetAction;
import google.registry.model.registrar.Registrar;
import google.registry.request.HttpException.ForbiddenException;
import google.registry.request.auth.AuthResult;
import google.registry.testing.CertificateSamples;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.util.CidrAddressBlock;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RegistrarSettingsAction}. */
@RunWith(JUnit4.class)
public class RegistrarSettingsActionTest extends RegistrarSettingsActionTestCase {

  @Test
  public void testSuccess_updateRegistrarInfo_andSendsNotificationEmail() throws Exception {
    String expectedEmailBody = loadFile(getClass(), "update_registrar_email.txt");
    action.handleJsonRequest(readJsonFromFile("update_registrar.json", getLastUpdateTime()));
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
        readJsonFromFile("update_registrar_duplicate_contacts.json", getLastUpdateTime()));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "results", ImmutableList.of(),
        "message",
        "One email address (etphonehome@example.com) cannot be used for multiple contacts");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testRead_notAuthorized_failure() throws Exception {
    when(sessionUtils.getRegistrarForAuthResult(
            any(HttpServletRequest.class), any(AuthResult.class)))
        .thenThrow(new ForbiddenException("Not authorized to access Registrar Console"));
    assertThrows(ForbiddenException.class, () -> action.handleJsonRequest(ImmutableMap.of()));
    assertNoTasksEnqueued("sheet");
  }

  /**
   * This is the default read test for the registrar settings actions.
   */
  @Test
  public void testRead_authorized_returnsRegistrarJson() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of());
    assertThat(response).containsExactly(
        "status", "SUCCESS",
        "message", "Success",
        "results", asList(loadRegistrar(CLIENT_ID).toJsonMap()));
  }

  @Test
  public void testUpdate_emptyJsonObject_errorLastUpdateTimeFieldRequired() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of()));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "lastUpdateTime",
        "results", ImmutableList.of(),
        "message", "This field is required.");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testUpdate_noEmail_errorEmailFieldRequired() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of("lastUpdateTime", getLastUpdateTime())));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "emailAddress",
        "results", ImmutableList.of(),
        "message", "This field is required.");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testUpdate_emptyJsonObject_emailFieldNotRequiredWhenEmpty() throws Exception {
    persistResource(loadRegistrar(CLIENT_ID).asBuilder().setEmailAddress(null).build());

    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of("lastUpdateTime", getLastUpdateTime())));
    assertThat(response).containsExactly(
        "status", "SUCCESS",
        "message", "Saved TheRegistrar",
        "results", asList(loadRegistrar(CLIENT_ID).toJsonMap()));
  }

  @Test
  public void testUpdate_badEmail_errorEmailField() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "lastUpdateTime", getLastUpdateTime(),
            "emailAddress", "lolcat")));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "emailAddress",
        "results", ImmutableList.of(),
        "message", "Please enter a valid email address.");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testPost_nonParsableTime_getsAngry() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of("lastUpdateTime", "cookies")));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "lastUpdateTime",
        "results", ImmutableList.of(),
        "message", "Not a valid ISO date-time string.");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testPost_nonAsciiCharacters_getsAngry() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "lastUpdateTime", getLastUpdateTime(),
            "emailAddress", "ヘ(◕。◕ヘ)@example.com")));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "emailAddress",
        "results", ImmutableList.of(),
        "message", "Please only use ASCII-US characters.");
    assertNoTasksEnqueued("sheet");
  }

  private <T> void doTestUpdate(
      Function<Registrar, T> getter,
      T newValue,
      BiFunction<Registrar.Builder, T, Registrar.Builder> setter) {
    Registrar registrar = loadRegistrar(CLIENT_ID);
    assertThat(getter.apply(registrar)).isNotEqualTo(newValue);

    Map<String, Object> response =
        action.handleJsonRequest(
            ImmutableMap.of(
                "op", "update",
                "args", setter.apply(registrar.asBuilder(), newValue).build().toJsonMap()));

    registrar = loadRegistrar(CLIENT_ID);
    assertThat(response).containsEntry("status", "SUCCESS");
    assertThat(response).containsEntry("results", asList(registrar.toJsonMap()));
    assertThat(getter.apply(registrar)).isEqualTo(newValue);
  }

  @Test
  public void testUpdate_premiumPriceAck() throws Exception {
    doTestUpdate(
        Registrar::getPremiumPriceAckRequired, true, Registrar.Builder::setPremiumPriceAckRequired);
  }

  @Test
  public void testUpdate_whoisServer() throws Exception {
    doTestUpdate(Registrar::getWhoisServer, "new-whois.example", Registrar.Builder::setWhoisServer);
  }

  @Test
  public void testUpdate_phoneNumber() throws Exception {
    doTestUpdate(Registrar::getPhoneNumber, "+1.2345678900", Registrar.Builder::setPhoneNumber);
  }

  @Test
  public void testUpdate_faxNumber() throws Exception {
    doTestUpdate(Registrar::getFaxNumber, "+1.2345678900", Registrar.Builder::setFaxNumber);
  }

  @Test
  public void testUpdate_url() throws Exception {
    doTestUpdate(Registrar::getUrl, "new-url.example", Registrar.Builder::setUrl);
  }

  @Test
  public void testUpdate_ipAddressWhitelist() throws Exception {
    doTestUpdate(
        Registrar::getIpAddressWhitelist,
        ImmutableList.of(CidrAddressBlock.create("1.1.1.0/24")),
        Registrar.Builder::setIpAddressWhitelist);
  }

  @Test
  public void testUpdate_clientCertificate() throws Exception {
    doTestUpdate(
        Registrar::getClientCertificate,
        CertificateSamples.SAMPLE_CERT,
        (builder, s) -> builder.setClientCertificate(s, clock.nowUtc()));
  }

  @Test
  public void testUpdate_failoverClientCertificate() throws Exception {
    doTestUpdate(
        Registrar::getFailoverClientCertificate,
        CertificateSamples.SAMPLE_CERT,
        (builder, s) -> builder.setFailoverClientCertificate(s, clock.nowUtc()));
  }

  @Test
  public void testUpdate_localizedAddress_city() throws Exception {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID).getLocalizedAddress().asBuilder().setCity("newCity").build(),
        Registrar.Builder::setLocalizedAddress);
  }

  @Test
  public void testUpdate_localizedAddress_countryCode() throws Exception {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID).getLocalizedAddress().asBuilder().setCountryCode("GB").build(),
        Registrar.Builder::setLocalizedAddress);
  }

  @Test
  public void testUpdate_localizedAddress_state() throws Exception {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID).getLocalizedAddress().asBuilder().setState("NJ").build(),
        Registrar.Builder::setLocalizedAddress);
  }

  @Test
  public void testUpdate_localizedAddress_street() throws Exception {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID)
            .getLocalizedAddress()
            .asBuilder()
            .setStreet(ImmutableList.of("new street"))
            .build(),
        Registrar.Builder::setLocalizedAddress);
  }

  @Test
  public void testUpdate_localizedAddress_zip() throws Exception {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID).getLocalizedAddress().asBuilder().setZip("new zip").build(),
        Registrar.Builder::setLocalizedAddress);
  }

  private static String getLastUpdateTime() {
    return loadRegistrar(CLIENT_ID).getLastUpdateTime().toString();
  }

  static Map<String, Object> readJsonFromFile(String filename, String lastUpdateTime) {
    String contents =
        loadFile(
            RegistrarSettingsActionTestCase.class,
            filename,
            ImmutableMap.of("LAST_UPDATE_TIME", lastUpdateTime));
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> json = (Map<String, Object>) JSONValue.parseWithException(contents);
      return json;
    } catch (ParseException ex) {
      throw new RuntimeException(ex);
    }
  }
}
