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
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.testing.TestDataHelper.loadFile;
import static java.util.Arrays.asList;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.export.sheet.SyncRegistrarsSheetAction;
import google.registry.model.registrar.Registrar;
import google.registry.testing.CertificateSamples;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.util.CidrAddressBlock;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.mail.internet.InternetAddress;
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
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testFailure_updateRegistrarInfo_duplicateContacts() {
    Map<String, Object> response = action.handleJsonRequest(
        readJsonFromFile("update_registrar_duplicate_contacts.json", getLastUpdateTime()));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "results", ImmutableList.of(),
        "message",
        "One email address (etphonehome@example.com) cannot be used for multiple contacts");
    assertNoTasksEnqueued("sheet");
    assertMetric(CLIENT_ID, "update", "[OWNER]", "ERROR: ContactRequirementException");
  }

  /**
   * Make sure that if someone spoofs a different registrar (they don't have access to), we fail.
   * Also relevant if the person's privilege were revoked after the page load.
   */
  @Test
  public void testFailure_readRegistrarInfo_notAuthorized() {
    setUserWithoutAccess();
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of("id", CLIENT_ID));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "results", ImmutableList.of(),
        "message", "forbidden test error");
    assertNoTasksEnqueued("sheet");
    assertMetric(CLIENT_ID, "read", "[]", "ERROR: ForbiddenException");
  }

  /** This is the default read test for the registrar settings actions. */
  @Test
  public void testSuccess_readRegistrarInfo_authorizedReadWrite() {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of("id", CLIENT_ID));
    assertThat(response)
        .containsExactly(
            "status", "SUCCESS",
            "message", "Success",
            "results", asList(loadRegistrar(CLIENT_ID).toJsonMap()));
    assertMetric(CLIENT_ID, "read", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_emptyJsonObject_errorLastUpdateTimeFieldRequired() {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "id", CLIENT_ID,
        "args", ImmutableMap.of()));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "lastUpdateTime",
        "results", ImmutableList.of(),
        "message", "This field is required.");
    assertNoTasksEnqueued("sheet");
    assertMetric(CLIENT_ID, "update", "[OWNER]", "ERROR: FormFieldException");
  }

  @Test
  public void testUpdate_noEmail_errorEmailFieldRequired() {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "id", CLIENT_ID,
        "args", ImmutableMap.of("lastUpdateTime", getLastUpdateTime())));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "emailAddress",
        "results", ImmutableList.of(),
        "message", "This field is required.");
    assertNoTasksEnqueued("sheet");
    assertMetric(CLIENT_ID, "update", "[OWNER]", "ERROR: FormFieldException");
  }

  @Test
  public void testUpdate_emptyJsonObject_emailFieldNotRequiredWhenEmpty() {
    persistResource(loadRegistrar(CLIENT_ID).asBuilder().setEmailAddress(null).build());

    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "id", CLIENT_ID,
        "args", ImmutableMap.of(
            "allowedTlds", ImmutableList.of("currenttld"),
            "lastUpdateTime", getLastUpdateTime())));
    assertThat(response).containsExactly(
        "status", "SUCCESS",
        "message", "Saved TheRegistrar",
        "results", asList(loadRegistrar(CLIENT_ID).toJsonMap()));
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testFailure_updateRegistrarInfo_notAuthorized() {
    setUserWithoutAccess();
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "id", CLIENT_ID,
        "args", ImmutableMap.of("lastUpdateTime", getLastUpdateTime())));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "results", ImmutableList.of(),
        "message", "forbidden test error");
    assertNoTasksEnqueued("sheet");
    assertMetric(CLIENT_ID, "update", "[]", "ERROR: ForbiddenException");
  }

  @Test
  public void testUpdate_badEmail_errorEmailField() {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "id", CLIENT_ID,
        "args", ImmutableMap.of(
            "lastUpdateTime", getLastUpdateTime(),
            "emailAddress", "lolcat")));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "emailAddress",
        "results", ImmutableList.of(),
        "message", "Please enter a valid email address.");
    assertNoTasksEnqueued("sheet");
    assertMetric(CLIENT_ID, "update", "[OWNER]", "ERROR: FormFieldException");
  }

  @Test
  public void testPost_nonParsableTime_getsAngry() {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "id", CLIENT_ID,
        "args", ImmutableMap.of("lastUpdateTime", "cookies")));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "lastUpdateTime",
        "results", ImmutableList.of(),
        "message", "Not a valid ISO date-time string.");
    assertNoTasksEnqueued("sheet");
    assertMetric(CLIENT_ID, "update", "[OWNER]", "ERROR: FormFieldException");
  }

  @Test
  public void testPost_nonAsciiCharacters_getsAngry() {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "id", CLIENT_ID,
        "args", ImmutableMap.of(
            "lastUpdateTime", getLastUpdateTime(),
            "emailAddress", "ヘ(◕。◕ヘ)@example.com")));
    assertThat(response).containsExactly(
        "status", "ERROR",
        "field", "emailAddress",
        "results", ImmutableList.of(),
        "message", "Please only use ASCII-US characters.");
    assertNoTasksEnqueued("sheet");
    assertMetric(CLIENT_ID, "update", "[OWNER]", "ERROR: FormFieldException");
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
                "id", CLIENT_ID,
                "args", setter.apply(registrar.asBuilder(), newValue).build().toJsonMap()));

    registrar = loadRegistrar(CLIENT_ID);
    assertThat(response).containsEntry("status", "SUCCESS");
    assertThat(response).containsEntry("results", asList(registrar.toJsonMap()));
    assertThat(getter.apply(registrar)).isEqualTo(newValue);
  }

  @Test
  public void testUpdate_premiumPriceAck() {
    doTestUpdate(
        Registrar::getPremiumPriceAckRequired, true, Registrar.Builder::setPremiumPriceAckRequired);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_whoisServer() {
    doTestUpdate(Registrar::getWhoisServer, "new-whois.example", Registrar.Builder::setWhoisServer);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_phoneNumber() {
    doTestUpdate(Registrar::getPhoneNumber, "+1.2345678900", Registrar.Builder::setPhoneNumber);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_faxNumber() {
    doTestUpdate(Registrar::getFaxNumber, "+1.2345678900", Registrar.Builder::setFaxNumber);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_url() {
    doTestUpdate(Registrar::getUrl, "new-url.example", Registrar.Builder::setUrl);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_ipAddressWhitelist() {
    doTestUpdate(
        Registrar::getIpAddressWhitelist,
        ImmutableList.of(CidrAddressBlock.create("1.1.1.0/24")),
        Registrar.Builder::setIpAddressWhitelist);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_clientCertificate() {
    doTestUpdate(
        Registrar::getClientCertificate,
        CertificateSamples.SAMPLE_CERT,
        (builder, s) -> builder.setClientCertificate(s, clock.nowUtc()));
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_failoverClientCertificate() {
    doTestUpdate(
        Registrar::getFailoverClientCertificate,
        CertificateSamples.SAMPLE_CERT,
        (builder, s) -> builder.setFailoverClientCertificate(s, clock.nowUtc()));
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_allowedTlds_succeedWhenUserIsAdmin() {
    setUserAdmin();
    doTestUpdate(
        Registrar::getAllowedTlds,
        ImmutableSet.of("newtld"),
        (builder, s) -> builder.setAllowedTlds(s));
    assertMetric(CLIENT_ID, "update", "[ADMIN]", "SUCCESS");
  }

  @Test
  public void testUpdate_allowedTlds_failedWhenUserIsNotAdmin() {
    Map<String, Object> response =
        action.handleJsonRequest(
            ImmutableMap.of(
                "op", "update",
                "id", CLIENT_ID,
                "args",
                ImmutableMap.of(
                    "lastUpdateTime", getLastUpdateTime(),
                    "emailAddress", "abc@def.com",
                    "allowedTlds", ImmutableList.of("newtld"))));
    assertThat(response)
        .containsExactly(
            "status", "ERROR",
            "results", ImmutableList.of(),
            "message", "Only admin can update allowed TLDs.");
    assertMetric(CLIENT_ID, "update", "[OWNER]", "ERROR: ForbiddenException");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testUpdate_allowedTlds_failedWhenTldNotExist() {
    setUserAdmin();
    Map<String, Object> response =
        action.handleJsonRequest(
            ImmutableMap.of(
                "op", "update",
                "id", CLIENT_ID,
                "args",
                ImmutableMap.of(
                    "lastUpdateTime", getLastUpdateTime(),
                    "emailAddress", "abc@def.com",
                    "allowedTlds", ImmutableList.of("invalidtld"))));
    assertThat(response)
        .containsExactly(
            "status", "ERROR",
            "results", ImmutableList.of(),
            "message", "TLDs do not exist: invalidtld");
    assertMetric(CLIENT_ID, "update", "[ADMIN]", "ERROR: IllegalArgumentException");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testUpdate_allowedTlds_noChange_successWhenUserIsNotAdmin() {
    Map<String, Object> response =
        action.handleJsonRequest(
            ImmutableMap.of(
                "op", "update",
                "id", CLIENT_ID,
                "args",
                ImmutableMap.of(
                    "lastUpdateTime", getLastUpdateTime(),
                    "emailAddress", "abc@def.com",
                    "allowedTlds", ImmutableList.of("currenttld"))));
    assertThat(response)
        .containsExactly(
            "status", "SUCCESS",
            "message", "Saved TheRegistrar",
            "results", asList(loadRegistrar(CLIENT_ID).toJsonMap()));
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_localizedAddress_city() {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID).getLocalizedAddress().asBuilder().setCity("newCity").build(),
        Registrar.Builder::setLocalizedAddress);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_localizedAddress_countryCode() {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID).getLocalizedAddress().asBuilder().setCountryCode("GB").build(),
        Registrar.Builder::setLocalizedAddress);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_localizedAddress_state() {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID).getLocalizedAddress().asBuilder().setState("NJ").build(),
        Registrar.Builder::setLocalizedAddress);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_localizedAddress_street() {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID)
            .getLocalizedAddress()
            .asBuilder()
            .setStreet(ImmutableList.of("new street"))
            .build(),
        Registrar.Builder::setLocalizedAddress);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
  }

  @Test
  public void testUpdate_localizedAddress_zip() {
    doTestUpdate(
        Registrar::getLocalizedAddress,
        loadRegistrar(CLIENT_ID).getLocalizedAddress().asBuilder().setZip("new zip").build(),
        Registrar.Builder::setLocalizedAddress);
    assertMetric(CLIENT_ID, "update", "[OWNER]", "SUCCESS");
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
