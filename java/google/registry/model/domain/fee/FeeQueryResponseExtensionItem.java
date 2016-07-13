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

package google.registry.model.domain.fee;

import google.registry.model.domain.Period;
import google.registry.model.domain.fee.FeeQueryCommandExtensionItem.CommandName;
import java.util.List;
import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;

/**
 * Interface for individual query items in Check and Info response. Each item indicates the fees and
 * class associated with a particular command and period. The currency may also be listed, but some
 * versions of the fee extension specify the currency at the top level of the extension.
 */

public interface FeeQueryResponseExtensionItem {

  /**
   * The type of the fee. We will use "premium" for fees on premium names, and omit the field
   * otherwise.
   */
  public String getFeeClass();

  /** Builder for {@link FeeCheckResponseExtensionItem}. */
  public interface Builder {

    /**
     * If currency is not supported in this type of query response item for this version of the fee
     * extension, this function has no effect.
     */
    public Builder setCurrencyIfSupported(CurrencyUnit currency);

     /** Whether this check item can be calculated. If so, reason should be null. If not, fees
      * should not be set.
      */
    public Builder setAvailIfSupported(boolean avail);

    /** The reason that the check item cannot be calculated. */
    public Builder setReasonIfSupported(String reason);

    /** The effective date that the check is run on. */
    public Builder setEffectiveDateIfSupported(DateTime effectiveDate);

    /** The date after which the quoted fees are no longer valid. */
    public Builder setNotAfterDateIfSupported(DateTime notAfterDate);

    public Builder setCommand(CommandName commandName, String phase, String subphase);

    public Builder setPeriod(Period period);

    public Builder setFees(List<Fee> fees);

    public Builder setClass(String feeClass);
  }
}

