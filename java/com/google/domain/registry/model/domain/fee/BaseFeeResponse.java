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

import static com.google.domain.registry.util.CollectionUtils.forceEmptyToNull;

import com.google.common.collect.ImmutableList;
import com.google.domain.registry.model.Buildable.GenericBuilder;
import com.google.domain.registry.model.ImmutableObject;
import com.google.domain.registry.model.domain.Period;

import org.joda.money.CurrencyUnit;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

/** Base class for the fee responses on check and info. */
@XmlTransient
public class BaseFeeResponse extends ImmutableObject {
  /** The currency of the fee. */
  CurrencyUnit currency;

  /** The command that was checked. */
  FeeCommandDescriptor command;

  /** The period that was checked. */
  Period period;

  /**
   * The magnitude of the fee, in the specified units, with an optional description.
   * <p>
   * This is a list because a single operation can involve multiple fees.
   */
  List<Fee> fee;

  /**
   * The type of the fee.
   * <p>
   * We will use "premium" for fees on premium names, and omit the field otherwise.
   */
  @XmlElement(name = "class")
  String feeClass;

  public String getFeeClass() {
    return feeClass;
  }

  /** Abstract builder for {@link BaseFeeResponse}. */
  public abstract static class Builder<T extends BaseFeeResponse, B extends Builder<?, ?>>
      extends GenericBuilder<T, B> {
    public B setCurrency(CurrencyUnit currency) {
      getInstance().currency = currency;
      return thisCastToDerived();
    }

    public B setCommand(FeeCommandDescriptor command) {
      getInstance().command = command;
      return thisCastToDerived();
    }

    public B setPeriod(Period period) {
      getInstance().period = period;
      return thisCastToDerived();
    }

    public B setFee(Fee... fee) {
      // If there are no fees, set the field to null to suppress the 'fee' section in the xml.
      getInstance().fee = forceEmptyToNull(ImmutableList.copyOf(fee));
      return thisCastToDerived();
    }

    public B setClass(String feeClass) {
      getInstance().feeClass = feeClass;
      return thisCastToDerived();
    }
  }
}
