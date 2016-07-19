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

import google.registry.model.domain.Period;
import org.joda.money.CurrencyUnit;

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

  /** True if this version of fee extension includes a currency in this type of query item. */
  public boolean isCurrencySupported();

  /** A three-character ISO4217 currency code; throws an exception if currency is not supported. */
  public CurrencyUnit getCurrency() throws UnsupportedOperationException;

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
