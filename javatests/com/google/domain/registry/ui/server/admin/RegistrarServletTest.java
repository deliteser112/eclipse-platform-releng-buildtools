// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.ui.server.admin;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static com.google.domain.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static org.mockito.Mockito.when;

import com.google.appengine.api.modules.ModulesService;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.config.RegistryEnvironment;
import com.google.domain.registry.export.sheet.SyncRegistrarsSheetTask;
import com.google.domain.registry.model.common.EntityGroupRoot;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.Registrar.State;
import com.google.domain.registry.model.registrar.RegistrarAddress;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.CertificateSamples;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.InjectRule;
import com.google.domain.registry.testing.TaskQueueHelper.TaskMatcher;
import com.google.domain.registry.util.CidrAddressBlock;
import com.google.domain.registry.util.DateTimeUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/** Tests for {@link RegistrarServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class RegistrarServletTest {

  //TODO(pmy): remove this duplicate of the registrar console test.

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Mock
  private ModulesService modulesService;

  final String clientId = "NewDaddy";
  final String tld = "xn--q9jyb4c";

  FakeClock clock = new FakeClock(DateTimeUtils.START_OF_TIME.plusMillis(1));
  HttpServletRequest req;
  Registrar registrar;
  Map<String, Object> regMap;
  RegistrarServlet servlet;

  /**
   * Empty whoisServer and referralUrl fields should be set to
   * defaults by server.
   */
  static Registrar simulateDefaultsForEmptyFields(Registrar registrar) {
    return registrar.asBuilder()
        .setWhoisServer(RegistryEnvironment.get().config().getRegistrarDefaultWhoisServer())
        .setReferralUrl(
            RegistryEnvironment.get().config().getRegistrarDefaultReferralUrl().toString())
        .build();
  }

  @Before
  public void init() {
    inject.setStaticField(Ofy.class, "clock", clock);
    inject.setStaticField(
        Registrar.class, "saltSupplier", Suppliers.ofInstance(new byte[]{1, 2, 3, 4}));
    inject.setStaticField(SyncRegistrarsSheetTask.class, "modulesService", modulesService);
    req = Mockito.mock(HttpServletRequest.class);
    when(req.getContextPath()).thenReturn("/_dr/admin");
    when(req.getRequestURI()).thenReturn("/_dr/admin/registrar/" + clientId);
    when(modulesService.getVersionHostname("backend", null)).thenReturn("backend.hostname");
    createTld(tld);
  }

  @Test
  public void testCreateRegistrar() {
    registrar = loadRegistrar(clientId);
    assertThat(registrar).isNull();

    // A minimal registrar.
    registrar = new Registrar.Builder()
        .setClientIdentifier(clientId)
        .setRegistrarName("TheRegistrar")
        .setEmailAddress("admin@registrar.com")
        .setIcannReferralEmail("lol@sloth.test")
        .setState(State.ACTIVE)
        .setType(Registrar.Type.TEST)
        .build();
    regMap = registrar.toJsonMap();
    servlet = new RegistrarServlet();
    Map<String, ?> result = servlet.doJsonPost(req, ImmutableMap.of(
        "op", "create",
        "args", regMap));
    assertThat(result).containsEntry("results", ImmutableList.of(clientId + ": ok"));

    registrar = simulateDefaultsForEmptyFields(registrar);
    Registrar createdRegistrar = loadRegistrar(clientId);
    assertThat(createdRegistrar).isEqualTo(registrar);
    registrar = createdRegistrar;
  }

  @Test
  public void testFullRegistrarUpdate() throws Exception {
    testCreateRegistrar();
    registrar = makeFullRegistrarObject();
    regMap = registrar.toJsonMap();
    regMap.put("password", "password");
    sendUpdate();
    registrar = registrar.asBuilder()
        .build();
    assertThat(loadRegistrar(clientId)).isEqualTo(registrar);
    assertTasksEnqueued("sheet", new TaskMatcher()
        .url(SyncRegistrarsSheetTask.PATH)
        .method("GET")
        .header("Host", "backend.hostname"));
  }

  @Test
  public void testMoreUpdates() throws Exception {
    testFullRegistrarUpdate();

    // Toggle the block premium names flag.
    regMap.put("blockPremiumNames", true);
    sendUpdate();
    registrar = registrar.asBuilder()
        .setBlockPremiumNames(true)
        .build();
    assertThat(loadRegistrar(clientId)).isEqualTo(registrar);

    testSetAndNull("whoisServer", "foo.bar.com");
    testSetAndNull("url", "http://acme.com/");
    testSetAndNull("referralUrl", "http://acme.com/");
    testSetAndNull("phoneNumber", "+1.5551234444");
    testSetAndNull("faxNumber", "+1.5551234444");

    regMap.put("clientCertificate", CertificateSamples.SAMPLE_CERT);
    sendUpdate();
    registrar = registrar.asBuilder()
        .setClientCertificate((String) regMap.get("clientCertificate"), clock.nowUtc())
        .build();
    assertThat(loadRegistrar(clientId)).isEqualTo(registrar);
    regMap.put("clientCertificate", CertificateSamples.SAMPLE_CERT2);
    sendUpdate();
    registrar = registrar.asBuilder()
        .setClientCertificate(CertificateSamples.SAMPLE_CERT2, clock.nowUtc())
        .build();
    assertThat(loadRegistrar(clientId)).isEqualTo(registrar);

    // Update the password.
    regMap.put("password", "newPassword");
    sendUpdate();
    registrar = registrar.asBuilder().setPassword("newPassword").build();
    assertThat(loadRegistrar(clientId).testPassword("newPassword")).isTrue();
  }

  @Test
  public void testContactCreate() throws Exception {
    testFullRegistrarUpdate();

    Map<String, /* @Nullable */ Object> contact = new HashMap<>();
    contact.put("name", "foo");
    contact.put("emailAddress", "foo@lol.com");
    contact.put("types", "ADMIN,LEGAL");
    contact.put("gaeUserId", "1234");

    regMap.put("contacts", ImmutableList.of(contact));
    sendUpdate();

    assertThat(registrar.getContacts()).containsExactly(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("foo")
            .setEmailAddress("foo@lol.com")
            .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN, RegistrarContact.Type.LEGAL))
            .setGaeUserId("1234")
        .build());
  }

  @Test
  public void testUpdate_badState_returnsFormFieldError() throws Exception {
    testCreateRegistrar();
    Map<String, ?> result = new RegistrarServlet().doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", ImmutableMap.of(
            "emailAddress", registrar.getEmailAddress(),
            "icannReferralEmail", registrar.getIcannReferralEmail(),
            "registrarName", registrar.getRegistrarName(),
            "state", "boo!")));
    assertThat(result).containsEntry("status", "ERROR");
    assertThat(result).containsEntry("field", "state");
    assertThat(result).containsEntry("message", "Enum State does not contain 'boo!'");
    assertNoTasksEnqueued("sheet");
  }

  @Test
  public void testContactUpdates() throws Exception {
    testFullRegistrarUpdate();

    Map<String, /* @Nullable */ Object> adminContact1 = new HashMap<>();
    adminContact1.put("name", "contact1");
    adminContact1.put("emailAddress", "contact1@email.com");
    adminContact1.put("phoneNumber", "+1.2125650666");
    adminContact1.put("faxNumber", null);
    adminContact1.put("types", "ADMIN");

    Map<String, /* @Nullable */ Object> adminContact2 = new HashMap<>();
    adminContact2.put("name", "foo");
    adminContact2.put("emailAddress", "foo@lol.com");
    adminContact2.put("types", "ADMIN");
    adminContact2.put("gaeUserId", "1234");

    regMap.put("contacts", ImmutableList.of(adminContact1, adminContact2));
    sendUpdate();

    assertThat(loadRegistrar(clientId).getContacts()).containsExactly(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("contact1")
            .setEmailAddress("contact1@email.com")
            .setPhoneNumber("+1.2125650666")
            .setFaxNumber(null)
            .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
            .build(),
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("foo")
            .setEmailAddress("foo@lol.com")
            .setPhoneNumber(null)
            .setFaxNumber(null)
            .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN))
            .setGaeUserId("1234")
            .build());
  }

  @Test
  public void testCertificateUpdate() throws Exception {
    testFullRegistrarUpdate();
    regMap.put("clientCertificate", CertificateSamples.SAMPLE_CERT2);
    sendUpdate();
    assertThat(loadRegistrar(clientId).getClientCertificate())
        .isEqualTo(CertificateSamples.SAMPLE_CERT2);
    assertThat(loadRegistrar(clientId).getClientCertificateHash())
        .isEqualTo(CertificateSamples.SAMPLE_CERT2_HASH);
  }

  private Registrar makeFullRegistrarObject() {
    return registrar.asBuilder()
        .setRegistrarName("TRO LLC")
        .setAllowedTlds(ImmutableSet.of(tld))
        .setBillingIdentifier(12345L)
        .setBlockPremiumNames(false)
        .setEmailAddress("email@foo.bar")
        .setPhoneNumber("+1.2125551212")
        .setFaxNumber("+1.2125551212")
        .setLocalizedAddress(new RegistrarAddress.Builder()
            .setCity("loc city")
            .setCountryCode("CC")
            .setState("loc state")
            .setStreet(ImmutableList.of("loc street1", "loc street2"))
            .setZip("loc zip")
            .build())
        .setIpAddressWhitelist(ImmutableList.of(CidrAddressBlock.create("192.168.0.1/32")))
        .setPassword("password")
        .setReferralUrl("referral url")
        .setWhoisServer("whois.server")
        .setUrl("url")
        .build();
  }

  private static Registrar loadRegistrar(String id) {
    return ofy().load().type(Registrar.class)
        .parent(EntityGroupRoot.getCrossTldKey()).id(id).now();
  }

  private void testSetAndNull(String field, String val) throws Exception {
    // Test null first and then restore value second to not affect
    // full registrar state in later tests.
    regMap.put(field, null);
    sendUpdate();
    registrar = setBuilderField(registrar.asBuilder(), field, null, val.getClass()).build();
    assertThat(loadRegistrar(clientId)).isEqualTo(registrar);

    regMap.put(field, val);
    sendUpdate();
    registrar = setBuilderField(registrar.asBuilder(), field, val).build();
    assertThat(loadRegistrar(clientId)).isEqualTo(registrar);
  }

  private Registrar.Builder setBuilderField(
      Registrar.Builder builder, String field, Object val, Class<?>... nullClass) throws Exception {
    java.lang.reflect.Method setter = Registrar.Builder.class.getMethod(
        "set" + field.substring(0, 1).toUpperCase() + field.substring(1),
        val == null ? nullClass[0] : val.getClass());
    setter.invoke(builder, val);
    return builder;
  }

  private void sendUpdate() {
    clock.setTo(clock.nowUtc().plusMinutes(1));
    Map<String, ?> result = servlet.doJsonPost(req, ImmutableMap.of(
        "op", "update",
        "args", regMap));
    assertThat(result).doesNotContainEntry("status", "ERROR");
  }
}
