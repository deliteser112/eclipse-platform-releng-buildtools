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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static google.registry.bigquery.BigqueryUtils.fromBigqueryTimestampString;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import google.registry.model.billing.RegistrarCredit;
import google.registry.model.billing.RegistrarCreditBalance;
import google.registry.model.billing.RegistrarCreditBalance.BalanceMap;
import google.registry.model.registrar.Registrar;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.joda.money.Money;
import org.joda.time.DateTime;

/** Command for writing out new balances of credits updated in an invoicing cycle. */
@Parameters(separators = " =", commandDescription = "Update a set of registrar credit balances")
final class UpdateCreditsCommand extends MutatingCommand {

  @Parameter(
      names = "--input_file",
      description = "CSV file of updated credit balance information with this row format: "
          + "  <registrar_id>,<credit_id>,<old_balance>,<new_balance>,<effective_time> "
          + "(typically produced by running the generate_invoice command)",
      required = true)
  private Path inputFile;

  /** Struct-like container for an individual credit's balance update information. */
  private static class CreditBalanceUpdate {
    /** The id of the registrar to whom the credit being updated belongs. */
    final String registrarId;

    /** The id of the credit being updated (i.e. having a new balance written out). */
    final Long creditId;

    /** The old balance amount of the credit in minor units of the credit's currency. */
    final Integer oldBalance;

    /** The new balance amount of the credit in minor units of the credit's currency. */
    final Integer newBalance;

    /** The exact moment at which the new balance becomes active, replacing the old balance. */
    final DateTime effectiveTime;

    public CreditBalanceUpdate(List<String> fields)
        throws IllegalArgumentException {
      checkArgument(fields.size() == 5, "Wrong number of fields provided: %s", fields);
      this.registrarId = fields.get(0);
      this.creditId = Long.valueOf(fields.get(1));
      this.oldBalance = Integer.valueOf(fields.get(2));
      this.newBalance = Integer.valueOf(fields.get(3));
      this.effectiveTime = fromBigqueryTimestampString(fields.get(4));
    }
  }

  @Override
  protected void init() throws Exception {
    DateTime now = DateTime.now(UTC);
    for (String line : Files.readAllLines(inputFile, StandardCharsets.UTF_8)) {
      List<String> fields = Splitter.on(',').splitToList(line);
      try {
        stageCreditUpdate(new CreditBalanceUpdate(fields), now);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Cannot parse fields in line: " + line, e);
      }
      System.out.println();
    }
  }

  /** Stage the actual new balance creation corresponding to the given credit balance update. */
  private void stageCreditUpdate(CreditBalanceUpdate update, DateTime now) {
    System.out.printf(
        "Creating balance update for credit %s/%d at %s:\n",
        update.registrarId,
        update.creditId,
        update.effectiveTime.toString());

    // Load the registrar and the credit, checking for non-existence.
    Registrar registrar = checkNotNull(Registrar.loadByClientId(update.registrarId),
        "Registrar %s not found", update.registrarId);
    RegistrarCredit credit = ofy().load()
        .type(RegistrarCredit.class)
        .parent(registrar)
        .id(update.creditId)
        .now();
    checkNotNull(credit,
        "Registrar credit for %s with ID %s not found",
        update.registrarId, update.creditId.toString());
    System.out.printf(" - Credit info: %s\n", credit.getSummary());

    // Load the actual old balance at the moment before the update's effective time and ensure
    // that it matches the expected old balance amount passed along in the update.
    Optional<Money> oldBalance =
        BalanceMap.createForCredit(credit).getActiveBalanceBeforeTime(update.effectiveTime);
    checkState(oldBalance.isPresent(), "No balance found before effective time");
    Money actualOldBalance = oldBalance.get();
    Money expectedOldBalance = Money.ofMinor(credit.getCurrency(), update.oldBalance);
    checkState(actualOldBalance.equals(expectedOldBalance),
        "Real old balance does not match expected old balance (%s vs. %s)",
        actualOldBalance,
        expectedOldBalance);

    // Create the new balance amount.
    Money newBalance = Money.ofMinor(credit.getCurrency(), update.newBalance);
    System.out.printf(" - %s -> %s\n", actualOldBalance, newBalance);

    // Create and stage the new credit balance object for the new balance amount.
    RegistrarCreditBalance newCreditBalance = new RegistrarCreditBalance.Builder()
        .setParent(credit)
        .setEffectiveTime(update.effectiveTime)
        .setWrittenTime(now)
        .setAmount(newBalance)
        .build();
    stageEntityChange(null, newCreditBalance);
  }
}
