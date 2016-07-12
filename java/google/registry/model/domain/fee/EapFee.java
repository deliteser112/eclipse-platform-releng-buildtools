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

import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.common.collect.Range;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * An object representing an EAP fee (as a {@link Money}) along with the interval for which the
 * fee applies.
 */
public class EapFee {

  /** The EAP fee (as applied on top of the domain registration cost). */
  Money cost;

  /** The time period for which the fee applies. */
  Range<DateTime> period;

  public Money getCost() {
    return cost;
  }

  public Range<DateTime> getPeriod() {
    return period;
  }

  public static EapFee create(Money cost, Range<DateTime> period) {
    EapFee instance = new EapFee();
    instance.cost = checkArgumentNotNull(cost, "EAP fee cost cannot be null.");
    instance.period = checkArgumentNotNull(period, "EAP fee period cannot be null.");
    return instance;
  }
}

