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

package google.registry.braintree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Result;
import com.braintreegateway.exceptions.NotFoundException;
import com.google.common.base.Optional;
import com.google.common.base.VerifyException;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import javax.inject.Inject;

/** Helper for creating Braintree customer entries for registrars. */
public class BraintreeRegistrarSyncer {

  private final BraintreeGateway braintree;

  @Inject
  BraintreeRegistrarSyncer(BraintreeGateway braintreeGateway) {
    this.braintree = braintreeGateway;
  }

  /**
   * Syncs {@code registrar} with Braintree customer entry, creating it if one doesn't exist.
   *
   * <p>The customer ID will be the same as {@link Registrar#getClientId()}.
   *
   * <p>Creating a customer object in Braintree's database is a necessary step in order to associate
   * a payment with a registrar. The transaction will fail if the customer object doesn't exist.
   *
   * @throws IllegalArgumentException if {@code registrar} is not using BRAINTREE billing
   * @throws VerifyException if the Braintree API returned a failure response
   */
  public void sync(Registrar registrar) {
    String id = registrar.getClientId();
    checkArgument(registrar.getBillingMethod() == Registrar.BillingMethod.BRAINTREE,
        "Registrar (%s) billing method (%s) is not BRAINTREE", id, registrar.getBillingMethod());
    CustomerRequest request = createRequest(registrar);
    Result<Customer> result;
    if (doesCustomerExist(id)) {
      result = braintree.customer().update(id, request);
    } else {
      result = braintree.customer().create(request);
    }
    verify(result.isSuccess(),
        "Failed to sync registrar (%s) to braintree customer: %s", id, result.getMessage());
  }

  private CustomerRequest createRequest(Registrar registrar) {
    CustomerRequest result =
        new CustomerRequest()
            .id(registrar.getClientId())
            .customerId(registrar.getClientId())
            .company(registrar.getRegistrarName());
    Optional<RegistrarContact> contact = getBillingContact(registrar);
    if (contact.isPresent()) {
      result.email(contact.get().getEmailAddress());
      result.phone(contact.get().getPhoneNumber());
      result.fax(contact.get().getFaxNumber());
    } else {
      result.email(registrar.getEmailAddress());
      result.phone(registrar.getPhoneNumber());
      result.fax(registrar.getFaxNumber());
    }
    return result;
  }

  private Optional<RegistrarContact> getBillingContact(Registrar registrar) {
    for (RegistrarContact contact : registrar.getContacts()) {
      if (contact.getTypes().contains(RegistrarContact.Type.BILLING)) {
        return Optional.of(contact);
      }
    }
    return Optional.absent();
  }

  private boolean doesCustomerExist(String id) {
    try {
      braintree.customer().find(id);
      return true;
    } catch (NotFoundException e) {
      return false;
    }
  }
}
