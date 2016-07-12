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
import static google.registry.testing.ReflectiveFieldExtractor.extractField;
import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.Transaction.GatewayRejectionReason;
import com.braintreegateway.TransactionGateway;
import com.braintreegateway.TransactionRequest;
import com.braintreegateway.ValidationError;
import com.braintreegateway.ValidationErrorCode;
import com.braintreegateway.ValidationErrors;
import com.google.common.collect.ImmutableMap;
import google.registry.model.registrar.Registrar;
import google.registry.testing.AppEngineRule;
import java.math.BigDecimal;
import org.joda.money.CurrencyUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Tests for {@link RegistrarPaymentAction}. */
@RunWith(MockitoJUnitRunner.class)
public class RegistrarPaymentActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Mock
  private BraintreeGateway braintreeGateway;

  @Mock
  private TransactionGateway transactionGateway;

  @Mock
  private Result<Transaction> result;

  @Mock
  private Transaction transaction;

  @Mock
  private ValidationErrors validationErrors;

  @Captor
  private ArgumentCaptor<TransactionRequest> transactionRequestCaptor;

  private final RegistrarPaymentAction paymentAction = new RegistrarPaymentAction();

  @Before
  public void before() throws Exception {
    paymentAction.registrar = Registrar.loadByClientId("TheRegistrar");
    paymentAction.accountIds =
        ImmutableMap.of(
            CurrencyUnit.USD, "merchant-account-usd",
            CurrencyUnit.JPY, "merchant-account-jpy");
    paymentAction.braintreeGateway = braintreeGateway;
    when(braintreeGateway.transaction()).thenReturn(transactionGateway);
    when(transactionGateway.sale(any(TransactionRequest.class))).thenReturn(result);
  }

  @Test
  public void testCurrencyIsUsd_usesAmericanMerchantAccount() throws Exception {
    when(result.isSuccess()).thenReturn(true);
    when(result.getTarget()).thenReturn(transaction);
    when(transaction.getId()).thenReturn("omg-im-an-id");
    when(transaction.getAmount()).thenReturn(BigDecimal.valueOf(123.4));
    when(transaction.getCurrencyIsoCode()).thenReturn("USD");
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", " 123.4 ",
                    "currency", "USD",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "SUCCESS",
            "message", "Payment processed successfully",
            "results", asList(
                ImmutableMap.of(
                    "id", "omg-im-an-id",
                    "formattedAmount", "$123.40")));
    verify(transactionGateway).sale(transactionRequestCaptor.capture());
    TransactionRequest transactionRequest = transactionRequestCaptor.getAllValues().get(0);
    assertThat(extractField(BigDecimal.class, transactionRequest, "amount"))
        .isEqualTo(BigDecimal.valueOf(123.4).setScale(2));
    assertThat(extractField(String.class, transactionRequest, "merchantAccountId"))
        .isEqualTo("merchant-account-usd");
    assertThat(extractField(String.class, transactionRequest, "customerId"))
        .isEqualTo("TheRegistrar");
  }

  @Test
  public void testCurrencyIsJpy_usesJapaneseMerchantAccount() throws Exception {
    when(result.isSuccess()).thenReturn(true);
    when(result.getTarget()).thenReturn(transaction);
    when(transaction.getId()).thenReturn("omg-im-an-id");
    when(transaction.getAmount()).thenReturn(BigDecimal.valueOf(123));
    when(transaction.getCurrencyIsoCode()).thenReturn("JPY");
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "JPY",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "SUCCESS",
            "message", "Payment processed successfully",
            "results", asList(
                ImmutableMap.of(
                    "id", "omg-im-an-id",
                    "formattedAmount", "JPY 123")));
    verify(transactionGateway).sale(transactionRequestCaptor.capture());
    TransactionRequest transactionRequest = transactionRequestCaptor.getAllValues().get(0);
    assertThat(extractField(BigDecimal.class, transactionRequest, "amount"))
        .isEqualTo(BigDecimal.valueOf(123));
    assertThat(extractField(String.class, transactionRequest, "merchantAccountId"))
        .isEqualTo("merchant-account-jpy");
  }

  @Test
  public void testAmountNotPresent_returnsErrorOnAmountField() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "currency", "JPY",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "This field is required.",
            "field", "amount",
            "results", asList());
  }

  @Test
  public void testAmountNotNumber_returnsErrorOnAmountField() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "abc",
                    "currency", "JPY",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Invalid number.",
            "field", "amount",
            "results", asList());
  }

  @Test
  public void testNegativeAmount_returnsErrorOnAmountField() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "-10",
                    "currency", "JPY",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Must be a positive number.",
            "field", "amount",
            "results", asList());
  }

  @Test
  public void testZeroAmount_returnsErrorOnAmountField() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "0",
                    "currency", "JPY",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Must be a positive number.",
            "field", "amount",
            "results", asList());
  }

  @Test
  public void testAmountHasMorePrecisionThanUsdCurrencyAllows_returnsError() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123.456",
                    "currency", "USD",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Scale of amount 123.456 is greater than the scale of the currency USD",
            "field", "amount",
            "results", asList());
  }

  @Test
  public void testAmountHasMorePrecisionThanJpyCurrencyAllows_returnsError() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "1.1",
                    "currency", "JPY",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Scale of amount 1.1 is greater than the scale of the currency JPY",
            "field", "amount",
            "results", asList());
  }

  @Test
  public void testCurrencyValidButNotSupported_returnsErrorOnCurrencyField() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "EUR",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Unsupported currency.",
            "field", "currency",
            "results", asList());
  }

  @Test
  public void testCurrencyBogus_returnsErrorOnCurrencyField() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "rm -rf /etc/passwd",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Invalid currency code.",
            "field", "currency",
            "results", asList());
  }

  @Test
  public void testCurrencyCodeNotAssigned_returnsErrorOnCurrencyField() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "ZZZ",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Unknown ISO currency code.",
            "field", "currency",
            "results", asList());
  }

  @Test
  public void testCurrencyMissing_returnsErrorOnCurrencyField() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "paymentMethodNonce", "omg")))
        .containsExactly(
            "status", "ERROR",
            "message", "This field is required.",
            "field", "currency",
            "results", asList());
  }

  @Test
  public void testPaymentMethodMissing_returnsErrorOnPaymentMethodField() throws Exception {
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "JPY")))
        .containsExactly(
            "status", "ERROR",
            "message", "This field is required.",
            "field", "paymentMethodNonce",
            "results", asList());
  }

  @Test
  public void testProcessorDeclined_returnsErrorResponse() throws Exception {
    when(result.isSuccess()).thenReturn(false);
    when(result.getTransaction()).thenReturn(transaction);
    when(transaction.getStatus()).thenReturn(Transaction.Status.PROCESSOR_DECLINED);
    when(transaction.getProcessorResponseText())
        .thenReturn("You don't know the power of the dark side");
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "USD",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Payment declined: You don't know the power of the dark side",
            "results", asList());
  }

  @Test
  public void testGatewayRejectedDueToCvv_returnsErrorResponse() throws Exception {
    when(result.isSuccess()).thenReturn(false);
    when(result.getTransaction()).thenReturn(transaction);
    when(transaction.getStatus()).thenReturn(Transaction.Status.GATEWAY_REJECTED);
    when(transaction.getGatewayRejectionReason()).thenReturn(GatewayRejectionReason.CVV);
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "USD",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Payment rejected: Invalid CVV code.",
            "results", asList());
  }

  @Test
  public void testGatewayRejectedDueToAddress_returnsErrorResponse() throws Exception {
    when(result.isSuccess()).thenReturn(false);
    when(result.getTransaction()).thenReturn(transaction);
    when(transaction.getStatus()).thenReturn(Transaction.Status.GATEWAY_REJECTED);
    when(transaction.getGatewayRejectionReason()).thenReturn(GatewayRejectionReason.AVS);
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "USD",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Payment rejected: Invalid address.",
            "results", asList());
  }

  @Test
  public void testSettlementDeclined_returnsErrorResponse() throws Exception {
    when(result.isSuccess()).thenReturn(false);
    when(result.getTransaction()).thenReturn(transaction);
    when(transaction.getStatus()).thenReturn(Transaction.Status.SETTLEMENT_DECLINED);
    when(transaction.getProcessorSettlementResponseText())
        .thenReturn("mine eyes have seen the glory of the coming of the borg");
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "USD",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Payment declined: mine eyes have seen the glory of the coming of the borg",
            "results", asList());
  }

  @Test
  public void testHasTransactionObjectWithWeirdStatus_returnsErrorResponse() throws Exception {
    when(result.isSuccess()).thenReturn(false);
    when(result.getTransaction()).thenReturn(transaction);
    when(transaction.getStatus()).thenReturn(Transaction.Status.UNRECOGNIZED);
    when(transaction.getProcessorResponseText())
        .thenReturn("he is preempting the instance where the deadlocked shards are stored");
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "USD",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Payment failure: "
                + "he is preempting the instance where the deadlocked shards are stored",
            "results", asList());
  }

  @Test
  public void testFailedWithWeirdStatusButNoHelpfulText_usesWeirdStatusAsMsg() throws Exception {
    when(result.isSuccess()).thenReturn(false);
    when(result.getTransaction()).thenReturn(transaction);
    when(transaction.getStatus()).thenReturn(Transaction.Status.FAILED);
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "USD",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Payment failure: FAILED",
            "results", asList());
  }

  @Test
  public void testRemoteValidationError_showsErrorOnlyOnFirstFormField() throws Exception {
    when(result.isSuccess()).thenReturn(false);
    when(result.getErrors()).thenReturn(validationErrors);
    when(validationErrors.getAllDeepValidationErrors())
        .thenReturn(asList(
            new ValidationError(
                "amount",
                ValidationErrorCode.TRANSACTION_SETTLEMENT_AMOUNT_IS_LESS_THAN_SERVICE_FEE_AMOUNT,
                "Gimmeh moar moneys"),
            new ValidationError(
                "fax",
                ValidationErrorCode.CUSTOMER_FAX_IS_TOO_LONG,
                "Fax is too long")));
    assertThat(
            paymentAction.handleJsonRequest(
                ImmutableMap.of(
                    "amount", "123",
                    "currency", "USD",
                    "paymentMethodNonce", "abc-123")))
        .containsExactly(
            "status", "ERROR",
            "message", "Gimmeh moar moneys",
            "field", "amount",
            "results", asList());
  }
}
