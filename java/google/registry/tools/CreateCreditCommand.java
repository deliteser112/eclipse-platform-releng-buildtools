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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import google.registry.model.billing.RegistrarCredit;
import google.registry.model.billing.RegistrarCredit.CreditType;
import google.registry.model.billing.RegistrarCreditBalance;
import google.registry.model.registrar.Registrar;
import javax.annotation.Nullable;
import org.joda.money.Money;
import org.joda.time.DateTime;

/** Command for creating a registrar credit object with an initial balance. */
@Parameters(separators = " =", commandDescription = "Create a new registrar credit")
final class CreateCreditCommand extends MutatingCommand {

  @Parameter(
      names = "--registrar",
      description = "Client ID of the registrar who will be awarded this credit",
      required = true)
  private String registrarId;

  @Parameter(
      names = "--type",
      description = "Type of credit (AUCTION or PROMOTION)",
      required = true)
  private CreditType type;

  @Nullable
  @Parameter(
      names = "--description",
      description = "Custom description that will appear on invoice line for this credit")
  private String description;

  @Parameter(
      names = "--tld",
      description = "TLD for which this credit applies",
      required = true)
  private String tld;

  @Parameter(
      names = "--balance",
      description = "Initial balance of this credit",
      required = true)
  private Money balance;

  @Parameter(
      names = "--effective_time",
      description = "Point in time at which the initial balance becomes effective",
      required = true)
  private DateTime effectiveTime;

  @Override
  protected void init() throws Exception {
    DateTime now = DateTime.now(UTC);
    Registrar registrar = checkNotNull(
        Registrar.loadByClientId(registrarId), "Registrar %s not found", registrarId);
    RegistrarCredit credit = new RegistrarCredit.Builder()
        .setParent(registrar)
        .setType(type)
        .setCreationTime(now)
        .setCurrency(balance.getCurrencyUnit())
        .setDescription(description)
        .setTld(tld)
        .build();
    RegistrarCreditBalance creditBalance = new RegistrarCreditBalance.Builder()
        .setParent(credit)
        .setEffectiveTime(effectiveTime)
        .setWrittenTime(now)
        .setAmount(balance)
        .build();
    stageEntityChange(null, credit);
    stageEntityChange(null, creditBalance);
  }
}
