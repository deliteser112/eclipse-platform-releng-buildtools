// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.model.reporting;

import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.annotation.Embed;
import google.registry.model.Buildable;
import google.registry.model.ImmutableObject;
import java.util.Set;
import org.joda.time.DateTime;

/**
 * The record of the mutations which contribute to transaction reporting.
 *
 * <p>This will only be constructed for a HistoryEntry which contributes to the transaction report,
 * i.e. only domain mutations.
 *
 * <p>The registrar accredited with this transaction is the enclosing HistoryEntry.clientId. The
 * only exception is for reportField = TRANSFER_LOSING_SUCCESSFUL or TRANSFER_LOSING_NACKED, which
 * uses HistoryEntry.otherClientId because the losing party in a transfer is always the otherClient.
 */
@Embed
public class DomainTransactionRecord extends ImmutableObject implements Buildable {

  /**
   * The time this Transaction takes effect (counting grace periods and other nuances).
   *
   * <p>Net adds, renews and transfers are modificationTime + 5 days for the grace period, while
   * Autorenews have a 45 day grace period. For deletions, this is the purge date of the domain. And
   * for restored names, this is the modificationTime, if done in the 30 day redemption period.
   *
   * @see <a
   *     href="https://www.icann.org/resources/unthemed-pages/registry-agmt-appc-10-2001-05-11-en">
   *     Grace period spec</a>
   */
  DateTime reportingTime;

  /** The TLD this record operates on. */
  String tld;

  /** The fields affected by this transaction, and the amounts they're affected by. */
  Set<TransactionFieldAmount> transactionFieldAmounts;

  /** A tuple that encapsulates an amount to add to a specified field in the report. */
  @Embed
  public static class TransactionFieldAmount extends ImmutableObject {

    public static TransactionFieldAmount create(
        TransactionReportField reportField, int reportAmount) {
      TransactionFieldAmount instance = new TransactionFieldAmount();
      instance.reportField = reportField;
      instance.reportAmount = reportAmount;
      return instance;
    }

    /** The transaction report field we add reportAmount to for this registrar. */
    TransactionReportField reportField;

    /**
     * The amount this record increases or decreases a registrar's report field.
     *
     * <p>For adds, renews, deletes, and restores, this is +1. For their respective cancellations,
     * this is -1.
     *
     * <p>For transfers, the gaining party gets a +1 for TRANSFER_GAINING_SUCCESSFUL, whereas the
     * losing party gets a +1 for TRANSFER_LOSING_SUCCESSFUL. Nacks result in +1 for
     * TRANSFER_GAINING_NACKED and TRANSFER_LOSING_NACKED, as well as -1 entries to cancel out the
     * original SUCCESSFUL transfer counters. Finally, if we explicitly allow a transfer, the report
     * amount is 0, as we've already counted the transfer in the original request.
     */
    int reportAmount;

    /**
     * The field added to by reportAmount within the transaction report.
     *
     * <p>The reportField specifies which column the reportAmount contributes to in the overall
     * report. ICANN wants a column for every add/renew broken down by number of years, so we have
     * the NET_ADDS_#_YR and NET_RENEWS_#_YR boilerplate to facilitate report generation.
     */
    public enum TransactionReportField {
      NET_ADDS_1_YR,
      NET_ADDS_2_YR,
      NET_ADDS_3_YR,
      NET_ADDS_4_YR,
      NET_ADDS_5_YR,
      NET_ADDS_6_YR,
      NET_ADDS_7_YR,
      NET_ADDS_8_YR,
      NET_ADDS_9_YR,
      NET_ADDS_10_YR,
      NET_RENEWS_1_YR,
      NET_RENEWS_2_YR,
      NET_RENEWS_3_YR,
      NET_RENEWS_4_YR,
      NET_RENEWS_5_YR,
      NET_RENEWS_6_YR,
      NET_RENEWS_7_YR,
      NET_RENEWS_8_YR,
      NET_RENEWS_9_YR,
      NET_RENEWS_10_YR,
      TRANSFER_GAINING_SUCCESSFUL,
      TRANSFER_GAINING_NACKED,
      TRANSFER_LOSING_SUCCESSFUL,
      TRANSFER_LOSING_NACKED,
      DELETED_DOMAINS_GRACE,
      DELETED_DOMAINS_NOGRACE,
      RESTORED_DOMAINS
    }
  }

  public DateTime getReportingTime() {
    return reportingTime;
  }

  public String getTld() {
    return tld;
  }

  public Set<TransactionFieldAmount> getTransactionFieldAmounts() {
    return transactionFieldAmounts;
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for {@link DomainTransactionRecord} since it is immutable. */
  public static class Builder extends Buildable.Builder<DomainTransactionRecord> {

    public Builder() {}

    public Builder(DomainTransactionRecord instance) {
      super(instance);
      checkArgumentNotNull(instance, "DomainTransactionRecord instance must not be null");
    }

    public Builder setReportingTime(DateTime reportingTime) {
      checkArgumentNotNull(reportingTime, "reportingTime must not be mull");
      getInstance().reportingTime = reportingTime;
      return this;
    }

    public Builder setTld(String tld) {
      checkArgumentNotNull(tld, "tld must not be null");
      getInstance().tld = tld;
      return this;
    }

    public Builder setTransactionFieldAmounts(
        ImmutableSet<TransactionFieldAmount> transactionFieldAmounts) {
      getInstance().transactionFieldAmounts = transactionFieldAmounts;
      return this;
    }

    @Override
    public DomainTransactionRecord build() {
      checkArgumentNotNull(getInstance().reportingTime, "reportingTime must not be null");
      checkArgumentNotNull(getInstance().tld, "tld must not be null");
      checkArgumentNotNull(
          getInstance().transactionFieldAmounts, "transactionFieldAmounts must not be null");
      return super.build();
    }
  }
}
