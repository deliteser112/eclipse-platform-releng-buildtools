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
import static google.registry.testing.DatastoreHelper.persistResource;
import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenGateway;
import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableMap;
import google.registry.braintree.BraintreeRegistrarSyncer;
import google.registry.model.registrar.Registrar;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import javax.servlet.http.HttpServletRequest;
import org.joda.money.CurrencyUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RegistrarPaymentSetupAction}. */
@RunWith(JUnit4.class)
public class RegistrarPaymentSetupActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  private final BraintreeGateway braintreeGateway = mock(BraintreeGateway.class);
  private final ClientTokenGateway clientTokenGateway = mock(ClientTokenGateway.class);
  private final BraintreeRegistrarSyncer customerSyncer = mock(BraintreeRegistrarSyncer.class);
  private final SessionUtils sessionUtils = mock(SessionUtils.class);

  private final User user = new User("marla.singer@example.com", "gmail.com", "12345");
  private final RegistrarPaymentSetupAction action = new RegistrarPaymentSetupAction();

  @Before
  public void before() throws Exception {
    action.sessionUtils = sessionUtils;
    action.authResult = AuthResult.create(AuthLevel.USER, UserAuthInfo.create(user, false));
    action.braintreeGateway = braintreeGateway;
    action.customerSyncer = customerSyncer;
    Registrar registrar = persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setBillingMethod(Registrar.BillingMethod.BRAINTREE)
            .build());
    when(sessionUtils.getRegistrarForAuthResult(
            any(HttpServletRequest.class), any(AuthResult.class)))
        .thenReturn(registrar);
    when(braintreeGateway.clientToken()).thenReturn(clientTokenGateway);
  }

  @Test
  public void testTokenGeneration() throws Exception {
    action.brainframe = "/doodle";
    action.accountIds =
        ImmutableMap.of(
            CurrencyUnit.USD, "sorrow",
            CurrencyUnit.JPY, "torment");
    String blanketsOfSadness = "our hearts are beating, but no one is breathing";
    when(clientTokenGateway.generate()).thenReturn(blanketsOfSadness);
    assertThat(action.handleJsonRequest(ImmutableMap.<String, Object>of()))
        .containsExactly(
            "status", "SUCCESS",
            "message", "Success",
            "results", asList(
                ImmutableMap.of(
                    "token", blanketsOfSadness,
                    "currencies", asList("USD", "JPY"),
                    "brainframe", "/doodle")));
    verify(customerSyncer).sync(eq(Registrar.loadByClientId("TheRegistrar")));
  }

  @Test
  public void testNonEmptyRequestObject_returnsError() throws Exception {
    assertThat(action.handleJsonRequest(ImmutableMap.of("oh", "no")))
        .containsExactly(
            "status", "ERROR",
            "message", "JSON request object must be empty",
            "results", asList());
  }

  @Test
  public void testNotOnCreditCardBillingTerms_showsErrorPage() throws Exception {
    Registrar registrar = persistResource(
        Registrar.loadByClientId("TheRegistrar")
            .asBuilder()
            .setBillingMethod(Registrar.BillingMethod.EXTERNAL)
            .build());
    when(sessionUtils.getRegistrarForAuthResult(
            any(HttpServletRequest.class), any(AuthResult.class)))
        .thenReturn(registrar);
    assertThat(action.handleJsonRequest(ImmutableMap.<String, Object>of()))
        .containsExactly(
            "status", "ERROR",
            "message", "not-using-cc-billing",
            "results", asList());
  }
}
