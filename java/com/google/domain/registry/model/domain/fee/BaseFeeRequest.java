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

package com.google.domain.registry.model.domain.fee;

import com.google.common.base.Optional;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.domain.Period;

import org.joda.money.CurrencyUnit;

import javax.xml.bind.annotation.XmlTransient;

/** Base class for the fee requests on check and info. */
@XmlTransient
public class BaseFeeRequest extends ImmutableObject {

  /** The default validity period (if not specified) is 1 year for all operations. */
  static final Period DEFAULT_PERIOD = Period.create(1, Period.Unit.YEARS);

  /** A three-character ISO4217 currency code. */
  CurrencyUnit currency;

  /** The command being checked. */
  FeeCommandDescriptor command;

  /** The period for the command being checked. */
  Period period;

  public CurrencyUnit getCurrency() {
    return currency;
  }

  public FeeCommandDescriptor getCommand() {
    return command;
  }

  public Period getPeriod() {
    return Optional.fromNullable(period).or(DEFAULT_PERIOD);
  }
}
