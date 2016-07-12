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

package google.registry.flows.contact;

import static google.registry.model.contact.PostalInfo.Type.INTERNATIONALIZED;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import google.registry.flows.EppException;
import google.registry.flows.EppException.ParameterValuePolicyErrorException;
import google.registry.flows.EppException.ParameterValueSyntaxErrorException;
import google.registry.model.contact.ContactAddress;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.PostalInfo;
import java.util.Set;
import javax.annotation.Nullable;

/** Static utility functions for contact flows. */
public class ContactFlowUtils {
  /** Check that an internationalized postal info has only ascii characters. */
  static void validateAsciiPostalInfo(@Nullable PostalInfo internationalized) throws EppException {
    if (internationalized != null) {
      Preconditions.checkState(INTERNATIONALIZED.equals(internationalized.getType()));
      ContactAddress address = internationalized.getAddress();
      Set<String> fields = Sets.newHashSet(
          internationalized.getName(),
          internationalized.getOrg(),
          address.getCity(),
          address.getCountryCode(),
          address.getState(),
          address.getZip());
      fields.addAll(address.getStreet());
      for (String field : fields) {
        if (field != null && !CharMatcher.ascii().matchesAllOf(field)) {
          throw new BadInternationalizedPostalInfoException();
        }
      }
    }
  }
  
  /** Check contact's state against server policy. */
  static void validateContactAgainstPolicy(ContactResource contact) throws EppException {
    if (contact.getDisclose() != null && !contact.getDisclose().getFlag()) {
      throw new DeclineContactDisclosureFieldDisallowedPolicyException();
    }
  }

  /** Declining contact disclosure is disallowed by server policy. */
  static class DeclineContactDisclosureFieldDisallowedPolicyException
      extends ParameterValuePolicyErrorException {
    public DeclineContactDisclosureFieldDisallowedPolicyException() {
      super("Declining contact disclosure is disallowed by server policy.");
    }
  }
  
  /** Internationalized postal infos can only contain ASCII characters. */
  static class BadInternationalizedPostalInfoException extends ParameterValueSyntaxErrorException {
    public BadInternationalizedPostalInfoException() {
      super("Internationalized postal infos can only contain ASCII characters");
    }
  }
}
