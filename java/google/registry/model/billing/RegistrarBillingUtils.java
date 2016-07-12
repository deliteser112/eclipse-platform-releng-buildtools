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

package google.registry.model.billing;

import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Maps.EntryTransformer;
import com.google.common.collect.Ordering;
import com.googlecode.objectify.cmd.Query;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registries;
import google.registry.model.registry.Registry;
import google.registry.util.CacheUtils;
import java.util.Map;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

/** Utilities for managing the billing of {@link Registrar} customers. */
public final class RegistrarBillingUtils {

  private static final Supplier<ImmutableSortedSet<CurrencyUnit>> CURRENCIES_CACHE =
      CacheUtils.memoizeWithShortExpiration(
          new Supplier<ImmutableSortedSet<CurrencyUnit>>() {
            @Override
            public ImmutableSortedSet<CurrencyUnit> get() {
              return FluentIterable
                  .from(Registries.getTlds())
                  .transform(new Function<String, CurrencyUnit>() {
                    @Override
                    public CurrencyUnit apply(String tld) {
                      return Registry.get(tld).getCurrency();
                    }})
                  .toSortedSet(Ordering.natural());
            }
          });

  /**
   * Returns set of currencies in which registrars may be billed.
   *
   * <p>Each TLD has a currency associated with it. We don't do conversions. The registrar customer
   * gets a separate bill for each currency.
   */
  public static ImmutableSortedSet<CurrencyUnit> getCurrencies() {
    return CURRENCIES_CACHE.get();
  }

  /**
   * Returns query of {@link RegistrarBillingEntry} for each currency, most recent first.
   *
   * <p><b>Note:</b> Currency map keys are returned in sorted order, from {@link #getCurrencies()}.
   */
  public static ImmutableMap<CurrencyUnit, Query<RegistrarBillingEntry>> getBillingEntryQueries(
      final Registrar registrar) {
    return Maps.toMap(getCurrencies(),
        new Function<CurrencyUnit, Query<RegistrarBillingEntry>>() {
          @Override
          public Query<RegistrarBillingEntry> apply(CurrencyUnit currency) {
            return ofy().load()
                .type(RegistrarBillingEntry.class)
                .ancestor(registrar)
                .filter("currency", currency)
                .order("-created");
          }});
  }

  /** Returns amount of money registrar currently owes registry in each currency. */
  public static Map<CurrencyUnit, Money> loadBalance(Registrar registrar) {
    return Maps.transformEntries(getBillingEntryQueries(registrar),
        new EntryTransformer<CurrencyUnit, Query<RegistrarBillingEntry>, Money>() {
          @Override
          public Money transformEntry(
              CurrencyUnit currency, Query<RegistrarBillingEntry> query) {
            RegistrarBillingEntry entry = query.first().now();
            return entry != null ? entry.getBalance() : Money.zero(currency);
          }});
  }

  private RegistrarBillingUtils() {}
}
