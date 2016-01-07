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

package google.registry.model.billing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Verify.verifyNotNull;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Parent;
import google.registry.model.Buildable;
import google.registry.model.ImmutableObject;
import google.registry.model.JsonMapBuilder;
import google.registry.model.Jsonifiable;
import google.registry.model.registrar.Registrar;
import java.util.Map;
import javax.annotation.Nullable;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * Log of monthly invoices and payments for a Registrar customer.
 *
 * <p>This is a one-off single-entry bookkeeping system. There is a separate account for each
 * (registrar, currency) pair.
 *
 * <p>You should never update these entities once they've been inserted into datastore. If you need
 * to change something, add a correction entry.
 */
@Entity
public class RegistrarBillingEntry extends ImmutableObject implements Jsonifiable {

  @Parent
  Key<Registrar> parent;

  /** Arbitrary unique identifier. */
  @Id
  long id;

  /**
   * External transaction identifier or {@code null} if this is an invoice entry.
   *
   * <p>This is the ID or token that the payment gateway gives us, which represents the transaction
   * in their database.
   */
  @Nullable
  String transactionId;

  /**
   * Time at which this entry was created.
   *
   * <p>This value is unique and monotonic for a given ({@link #parent}, {@link #currency}) pair.
   */
  @Index
  DateTime created;

  /** Completely arbitrary description of payment. */
  String description;

  /**
   * Currency of transaction.
   *
   * <p>This field is identical to {@code amount.getCurrencyUnit()} and is only here so it can be
   * indexed in datastore.
   */
  @Index
  CurrencyUnit currency;

  /**
   * Amount and currency of invoice or payment.
   *
   * <p>This field is positive for debits (e.g. monthly invoice entries) and negative for credits
   * (e.g. credit card payment transaction entries.)
   */
  Money amount;

  /**
   * Balance of account for this currency.
   *
   * <p>This is {@code amount + previous.balance}.
   */
  Money balance;

  public Key<Registrar> getParent() {
    return parent;
  }

  public long getId() {
    return id;
  }

  @Nullable
  public String getTransactionId() {
    return transactionId;
  }

  public DateTime getCreated() {
    return verifyNotNull(created, "created missing: %s", this);
  }

  public String getDescription() {
    return verifyNotNull(description, "description missing: %s", this);
  }

  public CurrencyUnit getCurrency() {
    return verifyNotNull(currency, "currency missing: %s", this);
  }

  public Money getAmount() {
    return verifyNotNull(amount, "amount missing: %s", this);
  }

  public Money getBalance() {
    return verifyNotNull(balance, "balance missing: %s", this);
  }

  @Override
  public Map<String, Object> toJsonMap() {
    return new JsonMapBuilder()
        .put("id", id)
        .put("transactionId", getTransactionId())
        .putString("created", getCreated())
        .put("description", getDescription())
        .putString("currency", getCurrency())
        .putString("amount", getAmount().getAmount())
        .putString("balance", getBalance().getAmount())
        .build();
  }

  /** A builder for constructing a {@link RegistrarBillingEntry}, since it's immutable. */
  public static class Builder extends Buildable.Builder<RegistrarBillingEntry> {

    @Nullable
    private RegistrarBillingEntry previous;

    public Builder() {}

    public Builder setParent(Registrar parent) {
      getInstance().parent = Key.create(parent);
      return this;
    }

    public Builder setCreated(DateTime created) {
      getInstance().created = created;
      return this;
    }

    public Builder setPrevious(@Nullable RegistrarBillingEntry previous) {
      this.previous = previous;
      return this;
    }

    public Builder setTransactionId(@Nullable String transactionId) {
      getInstance().transactionId = transactionId;
      return this;
    }

    public Builder setDescription(String description) {
      getInstance().description = checkNotNull(emptyToNull(description));
      return this;
    }

    public Builder setAmount(Money amount) {
      checkArgument(!amount.isZero(), "Amount can't be zero");
      getInstance().amount = amount;
      getInstance().currency = amount.getCurrencyUnit();
      return this;
    }

    @Override
    public RegistrarBillingEntry build() {
      checkNotNull(getInstance().parent, "parent");
      checkNotNull(getInstance().created, "created");
      checkNotNull(getInstance().description, "description");
      checkNotNull(getInstance().amount, "amount");
      if (previous == null) {
        getInstance().balance = getInstance().amount;
      } else {
        getInstance().balance = previous.balance.plus(getInstance().amount);
        checkState(getInstance().parent.equals(previous.parent),
            "Parent not same as previous:\nNew: %s\nPrevious: %s",
            getInstance(), previous);
        checkState(getInstance().created.isAfter(previous.created),
            "Created timestamp not after previous:\nNew: %s\nPrevious: %s",
            getInstance(), previous);
      }
      return cloneEmptyToNull(super.build());
    }
  }
}
