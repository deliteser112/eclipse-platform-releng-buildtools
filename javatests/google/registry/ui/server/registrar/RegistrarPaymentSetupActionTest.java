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
import static java.util.Arrays.asList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenGateway;
import com.google.common.collect.ImmutableMap;
import google.registry.braintree.BraintreeRegistrarSyncer;
import google.registry.model.registrar.Registrar;
import google.registry.testing.AppEngineRule;
import org.joda.money.CurrencyUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Tests for {@link RegistrarPaymentSetupAction}. */
@RunWith(MockitoJUnitRunner.class)
public class RegistrarPaymentSetupActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Mock
  private BraintreeGateway braintreeGateway;

  @Mock
  private ClientTokenGateway clientTokenGateway;

  @Mock
  private BraintreeRegistrarSyncer customerSyncer;

  private final RegistrarPaymentSetupAction action = new RegistrarPaymentSetupAction();

  @Before
  public void before() throws Exception {
    action.braintreeGateway = braintreeGateway;
    action.customerSyncer = customerSyncer;
    action.registrar =
        Registrar.loadByClientId("TheRegistrar").asBuilder()
            .setBillingMethod(Registrar.BillingMethod.BRAINTREE)
            .build();
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
    verify(customerSyncer).sync(eq(action.registrar));
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
    action.registrar =
        Registrar.loadByClientId("TheRegistrar").asBuilder()
            .setBillingMethod(Registrar.BillingMethod.EXTERNAL)
            .build();
    assertThat(action.handleJsonRequest(ImmutableMap.<String, Object>of()))
        .containsExactly(
            "status", "ERROR",
            "message", "not-using-cc-billing",
            "results", asList());
  }
}
