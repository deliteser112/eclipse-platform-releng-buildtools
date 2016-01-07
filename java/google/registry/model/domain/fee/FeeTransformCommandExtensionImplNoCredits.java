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

import com.google.common.collect.ImmutableList;
import google.registry.model.ImmutableObject;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import org.joda.money.CurrencyUnit;

/**
 * Base class for general transform commands with fees (create, renew, update, transfer). This
 * version of the class is for fee extensions that do not support credits (certain older versions
 * allow credits only for some commands).
 */
@XmlTransient
public abstract class FeeTransformCommandExtensionImplNoCredits
    extends ImmutableObject implements FeeTransformCommandExtension {

  /** The currency of the fee. */
  CurrencyUnit currency;

  /**
   * The magnitude of the fee, in the specified units, with an optional description.
   *
   * <p>This is a list because a single operation can involve multiple fees.
   */
  @XmlElement(name = "fee")
  List<Fee> fees;

  @Override
  public CurrencyUnit getCurrency() {
    return currency;
  }

  @Override
  public List<Fee> getFees() {
    return fees;
  }

  @Override
  public List<Credit> getCredits() {
    return ImmutableList.of();
  }
}
