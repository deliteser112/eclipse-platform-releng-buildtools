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

import static com.google.common.base.Functions.toStringFunction;
import static google.registry.security.JsonResponseHelper.Status.ERROR;
import static google.registry.security.JsonResponseHelper.Status.SUCCESS;
import static java.util.Arrays.asList;

import com.braintreegateway.BraintreeGateway;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import google.registry.braintree.BraintreeRegistrarSyncer;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.registrar.Registrar;
import google.registry.request.Action;
import google.registry.request.JsonActionRunner;
import google.registry.request.JsonActionRunner.JsonAction;
import google.registry.security.JsonResponseHelper;
import java.util.Map;
import javax.inject.Inject;
import org.joda.money.CurrencyUnit;

/**
 * Action returning information needed to render payment form in browser.
 *
 * <h3>Request Object</h3>
 *
 * <p>The request payload must be an empty JSON object.
 *
 * <h3>Response Object</h3>
 *
 * <p>The response payload will be a JSON response object (as defined by {@link JsonResponseHelper})
 * containing a single result object with the following fields:
 *
 * <dl>
 * <dt>brainframe
 * <dd>URL for iframe that loads Braintree payment method selector.
 * <dt>token
 * <dd>Nonce string obtained from the Braintree API which is needed by the Braintree JS SDK.
 * <dt>currencies
 * <dd>Array of strings, each containing a three letter currency code, which should be displayed to
 *     the customer in a drop-down field. This will be all currencies for which a Braintree merchant
 *     account exists. A currency will even be displayed if no TLD is enabled on the customer
 *     account that bills in that currency.
 * </dl>
 *
 * <p><b>Note:</b> These definitions corresponds to Closure Compiler extern
 * {@code registry.rpc.PaymentSetup} which must be updated should these definitions change.
 *
 * @see RegistrarPaymentAction
 * @see <a href="https://developers.braintreepayments.com/start/hello-server/java#generate-a-client-token">Generate a client token</a>
 */
@Action(
    path = "/registrar-payment-setup",
    method = Action.Method.POST,
    xsrfProtection = true,
    xsrfScope = "console",
    requireLogin = true)
public final class RegistrarPaymentSetupAction implements Runnable, JsonAction {

  @Inject BraintreeGateway braintreeGateway;
  @Inject BraintreeRegistrarSyncer customerSyncer;
  @Inject JsonActionRunner jsonActionRunner;
  @Inject Registrar registrar;
  @Inject @Config("brainframe") String brainframe;
  @Inject @Config("braintreeMerchantAccountIds") ImmutableMap<CurrencyUnit, String> accountIds;
  @Inject RegistrarPaymentSetupAction() {}

  @Override
  public void run() {
    jsonActionRunner.run(this);
  }

  @Override
  public Map<String, Object> handleJsonRequest(Map<String, ?> json) {
    if (!json.isEmpty()) {
      return JsonResponseHelper.create(ERROR, "JSON request object must be empty");
    }

    // payment.js is hard-coded to display a specific SOY error template for certain error messages.
    if (registrar.getBillingMethod() != Registrar.BillingMethod.BRAINTREE) {
      // Registrar needs to contact support to have their billing bit flipped.
      return JsonResponseHelper.create(ERROR, "not-using-cc-billing");
    }

    // In order to set the customerId field on the payment, the customer must exist.
    customerSyncer.sync(registrar);

    return JsonResponseHelper
        .create(SUCCESS, "Success", asList(
            ImmutableMap.of(
                "brainframe", brainframe,
                "token", braintreeGateway.clientToken().generate(),
                "currencies",
                    FluentIterable.from(accountIds.keySet())
                        .transform(toStringFunction())
                        .toList())));
  }
}
