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

import com.google.common.base.Optional;
import google.registry.model.domain.Period;
import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;

/**
 * Interface for individual query items in Check and Info commands. Each item indicates the command
 * to be checked, and the number of years for which the prices is requested. It may also contain the
 * currency, but some versions of the fee extension specify the currency at the top level of the
 * extension.
 */
public interface FeeQueryCommandExtensionItem {

  /** The name of a command that might have an associated fee. */
  public enum CommandName {
    UNKNOWN,
    CREATE,
    RENEW,
    TRANSFER,
    RESTORE,
    UPDATE
  }

  /**
   * Three-character ISO4217 currency code.
   *
   * <p>Returns null if this version of the fee extension doesn't specify currency at the top level.
   */
  public CurrencyUnit getCurrency();

  /** The as-of date for the fee extension to run. */
  public Optional<DateTime> getEffectiveDate();

  /** The name of the command being checked. */
  public CommandName getCommandName();

  /** The unparse name of the command being checked, for use in error strings. */
  public String getUnparsedCommandName();

  /** The phase of the command being checked. */
  public String getPhase();

  /** The subphase of the command being checked. */
  public String getSubphase();

  /** The period for the command being checked. */
  public Period getPeriod();
}
