// Copyright 2016 Google Inc. All Rights Reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.domain.registry.model.Buildable.GenericBuilder;
import com.google.domain.registry.model.ImmutableObject;

import org.joda.money.CurrencyUnit;

import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

/** Base class for fee responses on general transform commands (create, update, renew, transfer). */
@XmlTransient
public class BaseFeeCommandResponse extends ImmutableObject {

  /** The currency of the fee. */
  CurrencyUnit currency;

  /**
   * The magnitude of the fee, in the specified units, with an optional description.
   * <p>
   * This is a list because a single operation can involve multiple fees.
   */
  List<Fee> fee;

  /** Abstract builder for {@link BaseFeeCommandResponse}. */
  public abstract static class Builder<T extends BaseFeeCommandResponse, B extends Builder<?, ?>>
      extends GenericBuilder<T, B> {
    public B setCurrency(CurrencyUnit currency) {
      getInstance().currency = currency;
      return thisCastToDerived();
    }

    public B setFee(ImmutableList<Fee> fee) {
      getInstance().fee = fee;
      return thisCastToDerived();
    }
  }
}
