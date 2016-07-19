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

package google.registry.model.domain.fee06;

import google.registry.model.domain.fee.FeeQueryResponseExtensionItem;
import google.registry.model.domain.fee.FeeQueryResponseExtensionItemImpl;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.joda.money.CurrencyUnit;

/**
 * An XML data object that represents a fee extension that may be present on the response to EPP
 * domain info commands.
 */
@XmlRootElement(name = "infData")
@XmlType(propOrder = {"currency", "command", "period", "fee", "feeClass"})
public class FeeInfoResponseExtensionV06
    extends FeeQueryResponseExtensionItemImpl implements ResponseExtension {
  
  CurrencyUnit currency;
  
  /** Builder for {@link FeeInfoResponseExtensionV06}. */
  public static class Builder
      extends FeeQueryResponseExtensionItemImpl.Builder<FeeInfoResponseExtensionV06, Builder> {
    
    @Override
    public Builder setAvailIfSupported(boolean avail) {
      return this;
    }
    
    @Override
    public Builder setReasonIfSupported(String reason) {
      return this;
    }

    @Override
    public FeeQueryResponseExtensionItem.Builder
        setCurrencyIfSupported(CurrencyUnit currency) {
      getInstance().currency = currency;
      return this;
    }
  }
}
