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

package google.registry.model.domain.fee12;

import static google.registry.util.CollectionUtils.forceEmptyToNull;

import com.google.common.collect.ImmutableList;
import google.registry.model.Buildable.GenericBuilder;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.DomainObjectSpec;
import google.registry.model.domain.Period;
import google.registry.model.domain.fee.Fee;
import google.registry.model.domain.fee.FeeCheckResponseExtensionItem;
import google.registry.model.domain.fee.FeeQueryCommandExtensionItem.CommandName;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;

/**
 * The version 0.12 response for a domain check on a single resource.
 */
@XmlType(propOrder = {"object", "command"})
public class FeeCheckResponseExtensionItemV12
    extends ImmutableObject implements FeeCheckResponseExtensionItem {

  /** The domain that was checked. */
  DomainObjectSpec object;

  /** The command that was checked. */
  FeeCheckResponseExtensionItemCommandV12 command;

  @Override
  public String getFeeClass() {
    return command.getFeeClass();
  }

  /** Builder for {@link FeeCheckResponseExtensionItemV12}. */
  public static class Builder
      extends GenericBuilder<FeeCheckResponseExtensionItemV12, Builder>
      implements FeeCheckResponseExtensionItem.Builder {

    final FeeCheckResponseExtensionItemCommandV12.Builder commandBuilder;

    Builder() {
      super();
      commandBuilder = new FeeCheckResponseExtensionItemCommandV12.Builder();
    }

    @Override
    public Builder setCommand(CommandName commandName, String phase, String subphase) {
      commandBuilder.setCommandName(commandName);
      commandBuilder.setPhase(phase);
      commandBuilder.setSubphase(subphase);
      return this;
    }

    @Override
    public Builder setPeriod(Period period) {
      commandBuilder.setPeriod(period);
      return this;
    }

    @Override
    public Builder setFees(List<Fee> fees) {
      commandBuilder.setFee(forceEmptyToNull(ImmutableList.copyOf(fees)));
      return this;
    }

    @Override
    public Builder setClass(String feeClass) {
      commandBuilder.setClass(feeClass);
      return this;
    }

    @Override
    public Builder setDomainNameIfSupported(String name) {
      getInstance().object = new DomainObjectSpec(name);
      return this;
    }

    /** Version 0.12 does not support currency in check items. */
    @Override
    public Builder setCurrencyIfSupported(CurrencyUnit currency) {
      return this;
    }

    @Override
    public Builder setAvailIfSupported(boolean avail) {
      return this;
    }

    @Override
    public Builder setReasonIfSupported(String reason) {
      return this;
    }

    @Override
    public FeeCheckResponseExtensionItemV12 build() {
      getInstance().command = commandBuilder.build();
      return super.build();
    }

    @Override
    public Builder setEffectiveDateIfSupported(DateTime effectiveDate) {
      commandBuilder.setEffectiveDate(effectiveDate);
      return this;
    }

    @Override
    public Builder setNotAfterDateIfSupported(DateTime notAfterDate) {
      commandBuilder.setNotAfterDate(notAfterDate);
      return this;
    }
  }
}
