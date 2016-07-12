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
import static google.registry.tools.BigqueryCommandUtilities.handleTableCreation;
import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import google.registry.bigquery.BigqueryUtils.TableType;
import google.registry.tools.BigqueryCommandUtilities.TableCreationException;
import google.registry.util.SqlTemplate;
import java.util.List;

/** Command to make synthetic billing tables and views in Bigquery. */
@Parameters(separators = " =", commandDescription = "Make synthetic billing tables in Bigquery")
final class MakeBillingTablesCommand extends BigqueryCommand {

  @Parameter(
      names = "--source_dataset",
      description = "Name of the dataset containing the source entity tables.")
  private String sourceDatasetId;

  // TODO(b/14297938): we should be picking up the TLDs to bill automatically based on the tldType
  // and tldState rather than having to manually pass them in.
  @Parameter(
      names = "--tlds",
      description = "TLD(s) to include in billing data table",
      required = true)
  private List<String> tlds;

  private static final SqlTemplate CURRENCY_TABLE_SQL = getSql("currency_table.sql");
  private static final SqlTemplate REGISTRAR_DATA_SQL = getSql("registrar_data_view.sql");
  private static final SqlTemplate REGISTRY_DATA_SQL = getSql("registry_data_view.sql");
  private static final SqlTemplate CREDIT_DATA_SQL = getSql("credit_data_view.sql");
  private static final SqlTemplate CREDIT_BALANCE_DATA_SQL = getSql("credit_balance_data_view.sql");
  private static final SqlTemplate PREMIUM_LIST_DATA_SQL = getSql("premium_list_data_view.sql");
  private static final SqlTemplate RECURRING_DATA_SQL = getSql("recurring_event_data_view.sql");
  private static final SqlTemplate BILLING_DATA_SQL = getSql("billing_data_view.sql");

  /** Runs the main billing table/view creation logic. */
  @Override
  public void runWithBigquery() throws Exception {
    // Make the source dataset default to the default destination dataset if it has not been set.
    sourceDatasetId = Optional.fromNullable(sourceDatasetId).or(bigquery().getDatasetId());
    checkArgument(!tlds.isEmpty(), "Must specify at least 1 TLD to include in billing data table");
    // TODO(b/19016191): Should check that input tables exist up front, and avoid later errors.
    try {
      makeCurrencyTable();
      makeRegistrarView();
      makeRegistryView();
      makeCreditView();
      makeCreditBalanceView();
      makePremiumListView();
      makeRecurringEventView();
      makeBillingView();
    } catch (TableCreationException e) {
      // Swallow since we already will have printed an error message.
    }
  }

  /** Generates a table of currency information. */
  // TODO(b/19016720): once there is a registry-wide currency, this method should compute the set of
  // currencies needed for the active registries, look up the exponent for each currency from Joda
  // CurrencyUnit data, and then auto-generate the query to construct this table.
  // NB: "exponent" is the ISO 4217 name for the number of decimal places used by a currency.
  private void makeCurrencyTable() throws Exception {
    handleTableCreation(
        "currency table",
        bigquery().query(
            CURRENCY_TABLE_SQL
                .build(),
            bigquery().buildDestinationTable("Currency")
                .description("Generated table of currency information.")
                .build()));
  }

  /**
   * Generates a view of registrar data, used as an intermediate so that the invoice generation
   * stage doesn't have to refer all the way back to the original managed backup dataset.
   */
  private void makeRegistrarView() throws Exception {
    handleTableCreation(
        "registrar data view",
        bigquery().query(
            REGISTRAR_DATA_SQL
                .put("SOURCE_DATASET", sourceDatasetId)
                .build(),
            bigquery().buildDestinationTable("RegistrarData")
                .description("Synthetic view of registrar information.")
                .type(TableType.VIEW)
                .build()));
  }

  /** Generates a view of registry data to feed into later views. */
  private void makeRegistryView() throws Exception {
    handleTableCreation(
        "registry data view",
        bigquery().query(
            REGISTRY_DATA_SQL
                .put("SOURCE_DATASET", sourceDatasetId)
                .build(),
            bigquery().buildDestinationTable("RegistryData")
                .description("Synthetic view of registry information.")
                .type(TableType.VIEW)
                .build()));
  }

  /**
   * Generates a view of registrar credit entities that links in information from the owning
   * Registrar (e.g. billing ID).
   */
  private void makeCreditView() throws Exception {
    handleTableCreation(
        "credit data view",
        bigquery().query(
            CREDIT_DATA_SQL
                .put("SOURCE_DATASET", sourceDatasetId)
                .put("DEST_DATASET", bigquery().getDatasetId())
                .build(),
            bigquery().buildDestinationTable("CreditData")
                .description("Synthetic view of registrar credit information.")
                .type(TableType.VIEW)
                .build()));
  }

  /**
   * Generates a view of registrar credit balance entities that collapses them down to the one
   * 'true' credit balance for a given credit ID and effective time, eliminating any duplicates by
   * choosing the most recently written balance entry of the set.
   *
   * <p>The result is a list of the historical balances of each credit (according to the most recent
   * data written) that can be used to find the active balance of a credit at any point in time.
   */
  private void makeCreditBalanceView() throws Exception {
    handleTableCreation(
        "credit balance data view",
        bigquery().query(
            CREDIT_BALANCE_DATA_SQL
                .put("SOURCE_DATASET", sourceDatasetId)
                .put("DEST_DATASET", bigquery().getDatasetId())
                .build(),
            bigquery().buildDestinationTable("CreditBalanceData")
                .description("Synthetic view of registrar credit balance information.")
                .type(TableType.VIEW)
                .build()));
  }

  /** Generates a view of premium list data for each TLD. */
  private void makePremiumListView() throws Exception {
    handleTableCreation(
        "premium list data view",
        bigquery().query(
            PREMIUM_LIST_DATA_SQL
                .put("SOURCE_DATASET", sourceDatasetId)
                .put("DEST_DATASET", bigquery().getDatasetId())
                .build(),
            bigquery().buildDestinationTable("PremiumListData")
                .description("Synthetic view of premium list data.")
                .type(TableType.VIEW)
                .build()));
  }

  /** Generates a view of recurring billing events expanded into individual recurrences. */
  private void makeRecurringEventView() throws Exception {
    handleTableCreation(
        "recurring event data view",
        bigquery().query(
            RECURRING_DATA_SQL
                .put("SOURCE_DATASET", sourceDatasetId)
                .put("DEST_DATASET", bigquery().getDatasetId())
                .build(),
            bigquery().buildDestinationTable("RecurringEventData")
                .description("Synthetic view of recurring billing event recurrence data.")
                .type(TableType.VIEW)
                .build()));
  }

  /**
   * Generates a view of consolidated billing information that includes currency conversions,
   * registrar details, and cancellation flags on top of the original BillingEvent.OneTime data.
   */
  private void makeBillingView() throws Exception {
    handleTableCreation(
        "billing data view",
        bigquery().query(
            BILLING_DATA_SQL
                .put("SOURCE_DATASET", sourceDatasetId)
                .put("DEST_DATASET", bigquery().getDatasetId())
                .put("TLDS", Joiner.on(",").join(tlds))
                .build(),
            bigquery().buildDestinationTable("BillingData")
                .description("Synthetic view of consolidated billing information.")
                .type(TableType.VIEW)
                .build()));
  }

  private static SqlTemplate getSql(String filename) {
    return SqlTemplate.create(
        readResourceUtf8(MakeBillingTablesCommand.class, "sql/" + filename));
  }
}
