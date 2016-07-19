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

import google.registry.model.domain.fee.FeeCheckCommandExtensionItem;
import google.registry.model.domain.fee.FeeCheckResponseExtensionItem;
import google.registry.model.domain.fee.FeeQueryCommandExtensionItemImpl;
import javax.xml.bind.annotation.XmlType;
import org.joda.money.CurrencyUnit;

/** An individual price check item in version 0.6 of the fee extension on Check commands. */
@XmlType(propOrder = {"name", "currency", "command", "period"})
public class FeeCheckCommandExtensionItemV06
    extends FeeQueryCommandExtensionItemImpl implements FeeCheckCommandExtensionItem {

  /** The fully qualified domain name being checked. */
  String name;

  CurrencyUnit currency;
  
  @Override
  public boolean isDomainNameSupported() {
    return true;
  }

  @Override
  public String getDomainName() {
    return name;
  }

  @Override
  public boolean isCurrencySupported() {
    return true;
  }

  @Override
  public CurrencyUnit getCurrency() {
    return currency;
  }

  @Override
  public FeeCheckResponseExtensionItem.Builder createResponseBuilder() {
    return new FeeCheckResponseExtensionItemV06.Builder();
  }
}
