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

import static google.registry.util.CollectionUtils.forceEmptyToNull;

import com.google.common.collect.ImmutableList;
import google.registry.model.Buildable.GenericBuilder;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.Period;
import google.registry.model.domain.fee.FeeQueryCommandExtensionItem.CommandName;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Abstract base class for the fee request query items used in Check and Info responses. It handles
 * command, period, fees and class, which are always present. Derived classes must handle currency,
 * which may or may not be present, depending on the version of the extension being used.
 */
@XmlTransient
public class FeeQueryResponseExtensionItemImpl
    extends ImmutableObject implements FeeQueryResponseExtensionItem {

  /** The command that was checked. */
  FeeExtensionCommandDescriptor command;

  /** The period that was checked. */
  Period period;

  /**
   * The magnitude of the fee, in the specified units, with an optional description.
   *
   * <p>This is a list because a single operation can involve multiple fees.
   */
  List<Fee> fee;

  /**
   * The type of the fee.
   *
   * <p>We will use "premium" for fees on premium names, and omit the field otherwise.
   */
  @XmlElement(name = "class")
  String feeClass;

  @Override
  public String getFeeClass() {
    return feeClass;
  }

  /** Abstract builder for {@link FeeQueryResponseExtensionItemImpl}. */
  public abstract static class
      Builder<T extends FeeQueryResponseExtensionItemImpl, B extends Builder<?, ?>>
          extends GenericBuilder<T, B> implements FeeQueryResponseExtensionItem.Builder {

    @Override
    public B setCommand(CommandName commandName, String phase, String subphase) {
      getInstance().command = FeeExtensionCommandDescriptor.create(commandName, phase, subphase);
      return thisCastToDerived();
    }

    @Override
    public B setPeriod(Period period) {
      getInstance().period = period;
      return thisCastToDerived();
    }

    @Override
    public B setFees(List<Fee> fees) {
      // If there are no fees, set the field to null to suppress the 'fee' section in the xml.
      getInstance().fee = forceEmptyToNull(ImmutableList.copyOf(fees));
      return thisCastToDerived();
    }

    public B setFee(List<Fee> fees) {
      getInstance().fee = forceEmptyToNull(ImmutableList.copyOf(fees));
      return thisCastToDerived();
    }

    @Override
    public B setClass(String feeClass) {
      getInstance().feeClass = feeClass;
      return thisCastToDerived();
    }
  }
}
