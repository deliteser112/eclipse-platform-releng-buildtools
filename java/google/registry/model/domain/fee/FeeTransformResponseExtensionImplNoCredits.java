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

package google.registry.model.domain.fee;

import google.registry.model.Buildable.GenericBuilder;
import google.registry.model.ImmutableObject;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import org.joda.money.CurrencyUnit;

/**
 * Base class for fee responses on general transform commands (create, update, renew, transfer).
 * This version of the class is for fee extensions that do not support credits (certain older
 * versions allow credits only for some commands).
 */
@XmlTransient
public class FeeTransformResponseExtensionImplNoCredits extends ImmutableObject
    implements FeeTransformResponseExtension {

  /** The currency of the fee. */
  CurrencyUnit currency;

  /**
   * The magnitude of the fee, in the specified units, with an optional description.
   *
   * <p>This is a list because a single operation can involve multiple fees.
   */
  @XmlElement(name = "fee")
  List<Fee> fees;

  /** Abstract builder for {@link FeeTransformResponseExtensionImplNoCredits}. */
  public abstract static class
      Builder<T extends FeeTransformResponseExtensionImplNoCredits, B extends Builder<?, ?>>
          extends GenericBuilder<T, B> implements FeeTransformResponseExtension.Builder {

    @Override
    public B setCurrency(CurrencyUnit currency) {
      getInstance().currency = currency;
      return thisCastToDerived();
    }

    @Override
    public B setFees(List<Fee> fees) {
      getInstance().fees = fees;
      return thisCastToDerived();
    }

    @Override
    public B setCredits(List<Credit> credits) {
      return thisCastToDerived();
    }
  }
}
