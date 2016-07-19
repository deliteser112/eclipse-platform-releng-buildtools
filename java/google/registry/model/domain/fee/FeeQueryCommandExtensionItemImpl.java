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

import com.google.common.base.Optional;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.Period;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Abstract base class for the fee request query items used in Check and Info commands. It handles
 * command and period, which are always present. Derived classes must handle currency, which may or
 * may not be present, depending on the version of the extension being used.
 */
@XmlTransient
public abstract class FeeQueryCommandExtensionItemImpl
    extends ImmutableObject implements FeeQueryCommandExtensionItem {

  /** The default validity period (if not specified) is 1 year for all operations. */
  static final Period DEFAULT_PERIOD = Period.create(1, Period.Unit.YEARS);

  /** The command being checked. */
  FeeExtensionCommandDescriptor command;

  /** The period for the command being checked. */
  Period period;

  /** The name of the command being checked. */
  @Override
  public CommandName getCommandName() {
    return command.getCommand();
  }
  
  /** The command name before being parsed into an enum, for use in error strings. */
  @Override
  public String getUnparsedCommandName() {
    return command.getUnparsedCommandName();
  }
  
  /** The phase of the command being checked. */
  @Override
  public String getPhase() {
    return command.getPhase();
  }

  /** The subphase of the command being checked. */
  @Override
  public String getSubphase() {
    return command.getSubphase();
  }

  @Override
  public Period getPeriod() {
    return Optional.fromNullable(period).or(DEFAULT_PERIOD);
  }
}
