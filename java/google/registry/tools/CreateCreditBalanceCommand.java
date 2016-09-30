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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import google.registry.model.billing.RegistrarCredit;
import google.registry.model.billing.RegistrarCreditBalance;
import google.registry.model.registrar.Registrar;
import google.registry.util.SystemClock;
import org.joda.money.Money;
import org.joda.time.DateTime;

/** Command for creating a new balance for a registrar credit. */
@Parameters(separators = " =", commandDescription = "Create a new registrar credit balance")
final class CreateCreditBalanceCommand extends MutatingCommand {

  @Parameter(
      names = "--registrar",
      description = "Client ID of the registrar owning the credit to create a new balance for",
      required = true)
  private String registrarId;

  @Parameter(
      names = "--credit_id",
      description = "ID of credit to create a new balance for",
      required = true)
  private long creditId;

  @Parameter(
      names = "--balance",
      description = "The new balance amount",
      required = true)
  private Money balance;

  @Parameter(
      names = "--effective_time",
      description = "Point in time at which the new balance amount becomes effective",
      required = true)
  private DateTime effectiveTime;

  @Override
  public void init() throws Exception {
    Registrar registrar = checkNotNull(
        Registrar.loadByClientId(registrarId), "Registrar %s not found", registrarId);
    RegistrarCredit credit = ofy().load()
        .type(RegistrarCredit.class)
        .parent(registrar)
        .id(creditId)
        .now();
    checkNotNull(credit, "Registrar credit for %s with ID %s not found", registrarId, creditId);
    RegistrarCreditBalance newBalance = new RegistrarCreditBalance.Builder()
        .setParent(credit)
        .setEffectiveTime(effectiveTime)
        .setWrittenTime(new SystemClock().nowUtc())
        .setAmount(balance)
        .build();
    stageEntityChange(null, newBalance);
  }
}
