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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Verify.verify;
import static google.registry.security.JsonResponseHelper.Status.ERROR;
import static google.registry.security.JsonResponseHelper.Status.SUCCESS;
import static java.util.Arrays.asList;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.TransactionRequest;
import com.braintreegateway.ValidationError;
import com.braintreegateway.ValidationErrors;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.re2j.Pattern;
import google.registry.config.ConfigModule.Config;
import google.registry.model.registrar.Registrar;
import google.registry.request.Action;
import google.registry.request.JsonActionRunner;
import google.registry.request.JsonActionRunner.JsonAction;
import google.registry.security.JsonResponseHelper;
import google.registry.ui.forms.FormField;
import google.registry.ui.forms.FormFieldException;
import google.registry.util.FormattingLogger;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import org.joda.money.CurrencyUnit;
import org.joda.money.IllegalCurrencyException;
import org.joda.money.Money;

/**
 * Action handling submission of customer payment form.
 *
 * <h3>Request Object</h3>
 *
 * <p>The request payload is a JSON object with the following fields:
 *
 * <dl>
 * <dt>amount
 * <dd>String containing a fixed point value representing the amount of money the registrar
 *     customer wishes to send the registry. This amount is arbitrary and entered manually by the
 *     customer in the payment form, as there is currently no integration with the billing system.
 * <dt>currency
 * <dd>String containing a three letter ISO currency code, which is used to look up the Braintree
 *     merchant account ID to which payment should be posted.
 * <dt>paymentMethodNonce
 * <dd>UUID nonce string supplied by the Braintree JS SDK representing the selected payment method.
 * </dl>
 *
 * <h3>Response Object</h3>
 *
 * <p>The response payload will be a JSON response object (as defined by {@link JsonResponseHelper})
 * which, if successful, will contain a single result object with the following fields:
 *
 * <dl>
 * <dt>id
 * <dd>String containing transaction ID returned by Braintree gateway.
 * <dt>formattedAmount
 * <dd>String containing amount paid, which can be displayed to the customer on a success page.
 * </dl>
 *
 * <p><b>Note:</b> These definitions corresponds to Closure Compiler extern
 * {@code registry.rpc.Payment} which must be updated should these definitions change.
 *
 * <h3>PCI Compliance</h3>
 *
 * <p>The request object will not contain credit card information, but rather a
 * {@code payment_method_nonce} field that's populated by the Braintree JS SDK iframe.
 *
 * @see RegistrarPaymentSetupAction
 */
@Action(
    path = "/registrar-payment",
    method = Action.Method.POST,
    xsrfProtection = true,
    xsrfScope = "console",
    requireLogin = true)
public final class RegistrarPaymentAction implements Runnable, JsonAction {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private static final FormField<String, BigDecimal> AMOUNT_FIELD =
      FormField.named("amount")
          .trimmed()
          .emptyToNull()
          .required()
          .matches(Pattern.compile("-?\\d+(?:\\.\\d+)?"), "Invalid number.")
          .transform(BigDecimal.class, new Function<String, BigDecimal>() {
            @Override
            public BigDecimal apply(String value) {
              BigDecimal result = new BigDecimal(value);
              if (result.signum() != 1) {
                throw new FormFieldException("Must be a positive number.");
              }
              return result;
            }})
          .build();

  private static final FormField<String, CurrencyUnit> CURRENCY_FIELD =
      FormField.named("currency")
          .trimmed()
          .emptyToNull()
          .required()
          .matches(Pattern.compile("[A-Z]{3}"), "Invalid currency code.")
          .transform(CurrencyUnit.class, new Function<String, CurrencyUnit>() {
            @Override
            public CurrencyUnit apply(String value) {
              try {
                return CurrencyUnit.of(value);
              } catch (IllegalCurrencyException ignored) {
                throw new FormFieldException("Unknown ISO currency code.");
              }
            }})
          .build();

  private static final FormField<String, String> PAYMENT_METHOD_NONCE_FIELD =
      FormField.named("paymentMethodNonce")
          .trimmed()
          .emptyToNull()
          .required()
          .build();

  @Inject BraintreeGateway braintreeGateway;
  @Inject JsonActionRunner jsonActionRunner;
  @Inject Registrar registrar;
  @Inject @Config("braintreeMerchantAccountIds") ImmutableMap<CurrencyUnit, String> accountIds;
  @Inject RegistrarPaymentAction() {}

  @Override
  public void run() {
    jsonActionRunner.run(this);
  }

  @Override
  public Map<String, Object> handleJsonRequest(Map<String, ?> json) {
    logger.infofmt("Processing payment: %s", json);
    String paymentMethodNonce;
    Money amount;
    String merchantAccountId;
    try {
      paymentMethodNonce = PAYMENT_METHOD_NONCE_FIELD.extractUntyped(json).get();
      try {
        amount = Money.of(
            CURRENCY_FIELD.extractUntyped(json).get(),
            AMOUNT_FIELD.extractUntyped(json).get());
      } catch (ArithmeticException e) {
        // This happens when amount has more precision than the currency allows, e.g. $3.141.
        throw new FormFieldException(AMOUNT_FIELD.name(), e.getMessage());
      }
      merchantAccountId = accountIds.get(amount.getCurrencyUnit());
      if (merchantAccountId == null) {
        throw new FormFieldException(CURRENCY_FIELD.name(), "Unsupported currency.");
      }
    } catch (FormFieldException e) {
      logger.warning(e.toString());
      return JsonResponseHelper.createFormFieldError(e.getMessage(), e.getFieldName());
    }
    Result<Transaction> result =
        braintreeGateway.transaction().sale(
            new TransactionRequest()
                .amount(amount.getAmount())
                .paymentMethodNonce(paymentMethodNonce)
                .merchantAccountId(merchantAccountId)
                .customerId(registrar.getClientIdentifier())
                .options()
                    .submitForSettlement(true)
                    .done());
    if (result.isSuccess()) {
      return handleSuccessResponse(result.getTarget());
    } else if (result.getTransaction() != null) {
      Transaction transaction = result.getTransaction();
      switch (transaction.getStatus()) {
        case PROCESSOR_DECLINED:
          return handleProcessorDeclined(transaction);
        case SETTLEMENT_DECLINED:
          return handleSettlementDecline(transaction);
        case GATEWAY_REJECTED:
          return handleRejection(transaction);
        default:
          return handleMiscProcessorError(transaction);
      }
    } else {
      return handleValidationErrorResponse(result.getErrors());
    }
  }

  /**
   * Handles a transaction success response.
   *
   * @see "https://developers.braintreepayments.com/reference/response/transaction/java#success"
   * @see "https://developers.braintreepayments.com/reference/general/statuses#transaction"
   */
  private Map<String, Object> handleSuccessResponse(Transaction transaction) {
    // XXX: Currency scaling: https://github.com/braintree/braintree_java/issues/33
    Money amount =
        Money.of(CurrencyUnit.of(transaction.getCurrencyIsoCode()),
            transaction.getAmount().stripTrailingZeros());
    logger.infofmt("Transaction for %s via %s %s with ID: %s",
        amount,
        transaction.getPaymentInstrumentType(),  // e.g. credit_card, paypal_account
        transaction.getStatus(),                 // e.g. SUBMITTED_FOR_SETTLEMENT
        transaction.getId());
    return JsonResponseHelper
        .create(SUCCESS, "Payment processed successfully", asList(
            ImmutableMap.of(
                "id", transaction.getId(),
                "formattedAmount", formatMoney(amount))));
  }

  /**
   * Handles a processor declined response.
   *
   * <p>This happens when the customer's bank blocks the transaction.
   *
   * @see "https://developers.braintreepayments.com/reference/response/transaction/java#processor-declined"
   * @see "https://articles.braintreepayments.com/control-panel/transactions/declines"
   */
  private Map<String, Object> handleProcessorDeclined(Transaction transaction) {
    logger.warningfmt("Processor declined: %s %s",
        transaction.getProcessorResponseCode(), transaction.getProcessorResponseText());
    return JsonResponseHelper.create(ERROR,
        "Payment declined: " + transaction.getProcessorResponseText());
  }

  /**
   * Handles a settlement declined response.
   *
   * <p>This is a very rare condition that, for all intents and purposes, means the same thing as a
   * processor declined response.
   *
   * @see "https://developers.braintreepayments.com/reference/response/transaction/java#processor-settlement-declined"
   * @see "https://articles.braintreepayments.com/control-panel/transactions/declines"
   */
  private Map<String, Object> handleSettlementDecline(Transaction transaction) {
    logger.warningfmt("Settlement declined: %s %s",
        transaction.getProcessorSettlementResponseCode(),
        transaction.getProcessorSettlementResponseText());
    return JsonResponseHelper.create(ERROR,
        "Payment declined: " + transaction.getProcessorSettlementResponseText());
  }

  /**
   * Handles a gateway rejection response.
   *
   * <p>This happens when a transaction is blocked due to settings we configured ourselves in the
   * Braintree control panel.
   *
   * @see "https://developers.braintreepayments.com/reference/response/transaction/java#gateway-rejection"
   * @see "https://articles.braintreepayments.com/control-panel/transactions/gateway-rejections"
   * @see "https://articles.braintreepayments.com/guides/fraud-tools/avs-cvv"
   * @see "https://articles.braintreepayments.com/guides/fraud-tools/overview"
   */
  private Map<String, Object> handleRejection(Transaction transaction) {
    logger.warningfmt("Gateway rejection: %s", transaction.getGatewayRejectionReason());
    switch (transaction.getGatewayRejectionReason()) {
      case DUPLICATE:
        return JsonResponseHelper.create(ERROR, "Payment rejected: Possible duplicate.");
      case AVS:
        return JsonResponseHelper.create(ERROR, "Payment rejected: Invalid address.");
      case CVV:
        return JsonResponseHelper.create(ERROR, "Payment rejected: Invalid CVV code.");
      case AVS_AND_CVV:
        return JsonResponseHelper.create(ERROR, "Payment rejected: Invalid address and CVV code.");
      case FRAUD:
        return JsonResponseHelper.create(ERROR,
            "Our merchant gateway suspects this payment of fraud. Please contact support.");
      default:
        return JsonResponseHelper.create(ERROR,
            "Payment rejected: " + transaction.getGatewayRejectionReason());
    }
  }

  /** Handles a miscellaneous transaction processing error response. */
  private Map<String, Object> handleMiscProcessorError(Transaction transaction) {
    logger.warningfmt("Error processing transaction: %s %s %s",
        transaction.getStatus(),
        transaction.getProcessorResponseCode(),
        transaction.getProcessorResponseText());
    return JsonResponseHelper.create(ERROR,
        "Payment failure: "
            + firstNonNull(
                emptyToNull(transaction.getProcessorResponseText()),
                transaction.getStatus().toString()));
  }

  /**
   * Handles a validation error response from Braintree.
   *
   * @see "https://developers.braintreepayments.com/reference/response/transaction/java#validation-errors"
   * @see "https://developers.braintreepayments.com/reference/general/validation-errors/all/java"
   */
  private Map<String, Object> handleValidationErrorResponse(ValidationErrors validationErrors) {
    List<ValidationError> errors = validationErrors.getAllDeepValidationErrors();
    verify(!errors.isEmpty(), "Payment failed but validation error list was empty");
    for (ValidationError error : errors) {
      logger.warningfmt("Payment validation failed on field: %s\nCode: %s\nMessage: %s",
          error.getAttribute(), error.getCode(), error.getMessage());
    }
    return JsonResponseHelper
        .createFormFieldError(errors.get(0).getMessage(), errors.get(0).getAttribute());
  }

  private static String formatMoney(Money amount) {
    String symbol = amount.getCurrencyUnit().getSymbol(Locale.US);
    BigDecimal number = amount.getAmount().setScale(amount.getCurrencyUnit().getDecimalPlaces());
    return symbol.length() == 1 ? symbol + number : amount.toString();
  }
}
